package app.pwhs.core.receiver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import app.pwhs.core.security.PairingToken
import app.pwhs.core.security.PinAttemptLimiter

/**
 * Owns the [ApkReceiverServer] lifecycle so callers (the TV foreground service) never touch
 * NanoHTTPD directly — keeps the server library an internal detail of :core. Computes the
 * LAN URL + upload token and publishes status via [TvReceiverState].
 *
 * Also registers a NSD (Network Service Discovery) service so phone apps on the
 * same Wi-Fi can auto-discover this TV without manual IP entry.
 *
 * The pairing token is minted here and reaches a phone through exactly two channels: the QR code
 * on the TV screen and the NSD TXT record. Both require being on the same network *and* looking at
 * (or scanning) the TV, which is the whole point — the receiver itself no longer trusts a bare IP.
 */
object TvReceiver {

    private const val DEFAULT_PORT = 8787
    /** NSD service type — phone discovers services of this type. */
    const val NSD_SERVICE_TYPE = "_uinstaller._tcp."
    /** NSD TXT key carrying the pairing token; read back by the phone's `TvDiscovery`. */
    private const val NSD_TOKEN_ATTRIBUTE = "token"

    private var currentPort = DEFAULT_PORT
    private var server: ApkReceiverServer? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegistered = false

    /** Live pairing token. Regenerated on every cold start, kept across [restart]. */
    private var pairingToken: String = ""

    /** Shared with the server so [stop] can clear every client's throttle state. */
    private val attemptLimiter = PinAttemptLimiter()


    @Synchronized
    fun start(context: Context): ReceiverStatus {
        val currentServer = server
        if (currentServer != null && currentServer.isAlive) {
            val currentStatus = TvReceiverState.status.value
            if (currentStatus is ReceiverStatus.Running) return currentStatus
        }
        val ip = LanAddress.siteLocalIpv4() ?: "0.0.0.0"
        // A fresh token per server lifetime: whatever a previous session leaked stops working.
        pairingToken = PairingToken.generate()
        attemptLimiter.reset()

        val candidatePorts = listOf(DEFAULT_PORT, 8788, 8789, 8790, 8791, 8792)
        for (port in candidatePorts) {
            try {
                server?.stop()
                val newServer = ApkReceiverServer(context.applicationContext, port, pairingToken, attemptLimiter)
                newServer.start(60_000, false)
                server = newServer
                currentPort = port
                val running = runningStatus(ip, port)
                TvReceiverState.setStatus(running)
                registerNsd(context, port, android.os.Build.MODEL, ip)
                return running
            } catch (e: Throwable) {
                android.util.Log.w("TvReceiver", "Port $port unavailable, trying next...", e)
            }
        }

        android.util.Log.e("TvReceiver", "All candidate ports failed to bind")
        TvReceiverState.setStatus(ReceiverStatus.Stopped)
        return ReceiverStatus.Stopped
    }

    @Synchronized
    fun stop() {
        unregisterNsd()
        runCatching { server?.stop() }
        server = null
        pairingToken = ""
        attemptLimiter.reset()
        TvReceiverState.setStatus(ReceiverStatus.Stopped)
    }

    @Synchronized
    fun restart(context: Context): ReceiverStatus {
        val currentServer = server
        val ip = LanAddress.siteLocalIpv4() ?: "0.0.0.0"
        TvReceiverState.updateConnectedClient(null)
        TvReceiverState.emitReceivingProgress(null)

        if (currentServer != null && currentServer.isAlive) {
            // Same server, same token — a phone that already paired stays paired across an
            // IP change (Wi-Fi roam) without having to scan again.
            val newRunning = runningStatus(ip, currentPort)
            TvReceiverState.setStatus(newRunning)
            registerNsd(context, currentPort, android.os.Build.MODEL, ip)
            return newRunning
        }
        return start(context)
    }

    /**
     * [ReceiverStatus.Running] for a live server. [ReceiverStatus.Running.url] is what the QR
     * encodes and therefore carries the token; the TV screen renders the bare `http://ip:port`
     * from [ReceiverStatus.Running.ip] and [ReceiverStatus.Running.port] so the code stays off the
     * 10-foot UI.
     */
    private fun runningStatus(ip: String, port: Int) = ReceiverStatus.Running(
        ip = ip,
        port = port,
        token = pairingToken,
        url = PairingToken.appendToUrl("http://$ip:$port/", pairingToken),
    )

    // ── NSD Registration ────────────────────────────────────────────────

    private var activeRegistrationListener: NsdManager.RegistrationListener? = null

    private fun registerNsd(context: Context, port: Int, deviceName: String, ip: String) {
        unregisterNsd()
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "UIns-${deviceName.replace(" ", "_")}"
                serviceType = NSD_SERVICE_TYPE
                setPort(port)
                IpEncoder.encode(ip)?.let { setAttribute("ipcode", it) }
                // Published so a discovered TV can be paired without scanning its QR code. The
                // phone reads it back in TvDiscovery and appends it to the upload URL.
                if (pairingToken.isNotBlank()) setAttribute(NSD_TOKEN_ATTRIBUTE, pairingToken)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    android.util.Log.i("TvReceiver", "NSD registered: ${serviceInfo.serviceName}")
                    nsdRegistered = true
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    android.util.Log.w("TvReceiver", "NSD registration failed: $errorCode")
                    nsdRegistered = false
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    android.util.Log.i("TvReceiver", "NSD unregistered: ${serviceInfo.serviceName}")
                    nsdRegistered = false
                }
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    android.util.Log.w("TvReceiver", "NSD unregistration failed: $errorCode")
                }
            }
            activeRegistrationListener = listener
            val mgr = (context.getSystemService(Context.NSD_SERVICE) as? NsdManager)
            nsdManager = mgr
            mgr?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            android.util.Log.w("TvReceiver", "Failed to register NSD", e)
        }
    }

    private fun unregisterNsd() {
        val listener = activeRegistrationListener ?: return
        activeRegistrationListener = null
        try {
            nsdManager?.unregisterService(listener)
        } catch (e: Exception) {
            android.util.Log.w("TvReceiver", "Failed to unregister NSD", e)
        }
        nsdRegistered = false
    }
}

