package app.pwhs.universalinstaller.presentation.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.security.LanSessionStore
import app.pwhs.core.security.PinAttemptLimiter
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface

data class TransferProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
) {
    val percentage: Int
        get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

object SyncManager {
    val state = MutableStateFlow(SyncState.STOPPED)
    val serverUrl = MutableStateFlow<String?>(null)

    /**
     * The PIN currently guarding the share, or null when the gate is off. Starts empty rather
     * than seeded with a constant — the real value is published by [SyncService] when the server
     * comes up, so the UI can never show a PIN that is not actually in force.
     */
    val pinCode = MutableStateFlow<String?>(null)

    /** True while [pinCode] is a per-session generated PIN the user has not configured themselves. */
    val pinIsTemporary = MutableStateFlow(false)
    val activeConnections = MutableStateFlow(0)
    val sharedFiles = MutableStateFlow<List<java.io.File>>(emptyList())
    val activeTransfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())

    private val connectionCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val transferIdCounter = java.util.concurrent.atomic.AtomicLong(0)
    private val estimators = java.util.concurrent.ConcurrentHashMap<String, app.pwhs.core.util.TransferEstimator>()

    fun nextTransferId(): String = "transfer_${transferIdCounter.incrementAndGet()}"

    fun incrementConnections() {
        val newVal = connectionCounter.incrementAndGet()
        activeConnections.update { newVal }
    }

    fun decrementConnections() {
        val newVal = connectionCounter.decrementAndGet().coerceAtLeast(0)
        activeConnections.update { newVal }
    }

    fun resetConnections() {
        connectionCounter.set(0)
        activeConnections.update { 0 }
        activeTransfers.value = emptyMap()
        estimators.clear()
    }

    fun updateProgress(transferId: String, fileName: String, bytesTransferred: Long, totalBytes: Long) {
        val estimator = estimators.getOrPut(transferId) { app.pwhs.core.util.TransferEstimator() }
        val estimate = estimator.update(bytesTransferred, totalBytes)
        activeTransfers.update { map ->
            map + (transferId to TransferProgress(
                fileName = fileName,
                bytesTransferred = bytesTransferred,
                totalBytes = totalBytes,
                speedBytesPerSec = estimate.speedBytesPerSec,
                etaSeconds = estimate.etaSeconds,
            ))
        }
    }

    fun removeTransfer(transferId: String) {
        estimators.remove(transferId)
        activeTransfers.update { map -> map - transferId }
    }
}

enum class SyncState { STOPPED, STARTING, RUNNING, ERROR }

class SyncService : Service() {
    private var server: ApkHttpServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var serverUrlCache: String? = null

    /**
     * Session tokens + PIN throttling for the share. Owned by the service so every restart of the
     * share begins with no live sessions — a token minted for an earlier PIN must not survive the
     * PIN being rotated.
     */
    private val sessionStore = LanSessionStore(ttlMillis = SyncAuthenticator.COOKIE_TTL_MILLIS)
    private val attemptLimiter = PinAttemptLimiter()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        SyncManager.state.value = SyncState.STARTING
        if (!startForegroundService()) {
            return START_NOT_STICKY
        }
        
        runBlocking {
            val prefs = applicationContext.dataStore.data.first()
            val requirePin = prefs[PreferencesKeys.SYNC_REQUIRE_PIN] ?: true
            val storedPin = prefs[PreferencesKeys.SYNC_PIN_CODE].orEmpty()
            val portStr = prefs[PreferencesKeys.SYNC_SERVER_PORT] ?: "8080"
            val port = portStr.toIntOrNull() ?: 8080

            // No constant fallback PIN. If the gate is on but nothing was configured, mint a
            // random PIN for this session and surface it in the UI and the notification — the
            // user still sees a working gate, an attacker on the LAN no longer gets "1234".
            val temporaryPin = requirePin && storedPin.isBlank()
            val pin = storedPin.ifBlank { SyncPinFactory.generatePin() }

            SyncManager.pinIsTemporary.value = temporaryPin
            SyncManager.pinCode.value = if (requirePin) pin else null

            try {
                // If there's an existing server running, stop it. Sessions from the previous run
                // (possibly minted under a different PIN) must not stay valid.
                server?.stop()
                sessionStore.revokeAll()
                attemptLimiter.reset()

                server = ApkHttpServer(
                    context = this@SyncService,
                    port = port,
                    requirePin = requirePin,
                    pinCode = pin,
                    onConnectionChange = { delta ->
                        if (delta > 0) SyncManager.incrementConnections()
                        else SyncManager.decrementConnections()
                    },
                    authenticator = SyncAuthenticator(
                        requirePin = requirePin,
                        pinCode = pin,
                        sessionStore = sessionStore,
                        attemptLimiter = attemptLimiter,
                    ),
                )
                server?.start()

                val ip = getLocalIpAddress()
                if (ip != null) {
                    serverUrlCache = "http://$ip:$port"
                    SyncManager.serverUrl.value = serverUrlCache
                    SyncManager.state.value = SyncState.RUNNING
                    refreshSharedFiles()
                    idleNotificationText = runningNotificationText(serverUrlCache!!, requirePin, pin)
                    updateNotification(idleNotificationText)
                    startProgressObserver()
                } else {
                    SyncManager.state.value = SyncState.ERROR
                    stopSelf()
                }
            } catch (e: Exception) {
                SyncManager.state.value = SyncState.ERROR
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundService(): Boolean {
        val channelId = "sync_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = createNotification(channelId, "Server starting...")
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, 
                    1001, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1001, notification)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start SyncService in foreground")
            SyncManager.state.value = SyncState.ERROR
            stopSelf()
            false
        }
    }

    /** Idle-state notification text; also reused by the progress observer between transfers. */
    private var idleNotificationText: String = ""

    /**
     * The PIN is appended to the ongoing notification so a generated per-session PIN is still
     * readable when the app UI is not open — that is the whole point of not defaulting to "1234".
     */
    private fun runningNotificationText(url: String, requirePin: Boolean, pin: String): String {
        val base = "Server running at $url"
        return if (requirePin) "$base · PIN $pin" else base
    }

    private fun updateNotification(text: String) {
        val channelId = "sync_service_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, createNotification(channelId, text))
    }

    private fun createNotification(
        channelId: String,
        text: String,
        progress: Int = -1,
    ): Notification {
        val stopIntent = Intent(this, SyncService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Universal Installer Sync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
                .setSubText("$progress%")
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Timber.w(e, "Failed to stop foreground for SyncService")
        }
        serviceScope.cancel()
        server?.stop()
        sessionStore.revokeAll()
        attemptLimiter.reset()
        SyncManager.state.value = SyncState.STOPPED
        SyncManager.serverUrl.value = null
        SyncManager.pinCode.value = null
        SyncManager.pinIsTemporary.value = false
        SyncManager.resetConnections()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProgressObserver() {
        serviceScope.launch {
            SyncManager.activeTransfers.collectLatest { transfers ->
                val channelId = "sync_service_channel"
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (transfers.isNotEmpty()) {
                    val totalBytes = transfers.values.sumOf { it.totalBytes }
                    val transferredBytes = transfers.values.sumOf { it.bytesTransferred }
                    val overallPercent = if (totalBytes > 0) ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                    val text = if (transfers.size == 1) {
                        val single = transfers.values.first()
                        val eta = app.pwhs.core.util.TransferFormatter.formatEta(this@SyncService, single.etaSeconds)
                        if (eta != null) {
                            "Sending: ${single.fileName} ($eta)"
                        } else {
                            "Sending: ${single.fileName}"
                        }
                    } else {
                        "Sending ${transfers.size} files"
                    }
                    val notification = createNotification(channelId, text, overallPercent)
                    manager.notify(1001, notification)
                } else if (serverUrlCache != null) {
                    val notification = createNotification(channelId, idleNotificationText)
                    manager.notify(1001, notification)
                }
            }
        }
    }

    private val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")

    private fun refreshSharedFiles() {
        val baseDir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "UniversalInstaller",
        )
        val files = baseDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in validExtensions }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        SyncManager.sharedFiles.value = files
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
