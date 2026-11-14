package app.pwhs.universalinstaller.presentation.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.base.BaseActivity
import app.pwhs.universalinstaller.presentation.install.util.PackageFileFilter
import app.pwhs.universalinstaller.util.extension.getDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mobile client screen for sending APKs to Android TV.
 */
class SendToTvActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme { SendToTvScreen(onBack = { finish() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendToTvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scanned by rememberSaveable { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var uploadBytes by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var uploadSpeed by remember { mutableLongStateOf(0L) }
    var uploadEta by remember { mutableStateOf<Long?>(null) }
    var currentFileName by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    var isConnectingManual by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }
    var savedTvIp by rememberSaveable { mutableStateOf("") }
    val discoveredTvs by remember { TvDiscovery.discover(context) }.collectAsState(initial = emptyList())

    fun connectToTv(rawInput: String, isFromQr: Boolean = false) {
        // Handles every shape the address arrives in — scanned QR (with its pairing token), the
        // 4-letter IP mnemonic from the TV screen, a bare ip:port, an mDNS name — and picks up the
        // NSD-advertised token when the input carried none, so manual entry still pairs.
        val address = TvAddressResolver.resolve(
            rawInput = rawInput,
            localIp = app.pwhs.core.receiver.LanAddress.siteLocalIpv4(),
            discoveredTokenFor = { host -> discoveredTvs.firstOrNull { it.host == host }?.token.orEmpty() },
        )
        if (address == null) {
            val err = context.getString(R.string.tv_sync_error_invalid_input)
            if (isFromQr) {
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            } else {
                manualError = err
            }
            return
        }
        val host = address.host
        val pairingToken = address.pairingToken

        if (!isFromQr) {
            isConnectingManual = true
            manualError = null
        }

        scope.launch {
            val candidatePorts = address.candidatePorts()
            var connectedTarget: String? = null
            var connectedPort: Int = address.port

            withContext(Dispatchers.IO) {
                for (p in candidatePorts) {
                    val pingUrl = "http://$host:$p/ping"
                    val ok = runCatching {
                        val conn = URL(pingUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 2500
                        conn.readTimeout = 2500
                        conn.setRequestProperty("User-Agent", "${Build.MANUFACTURER} ${Build.MODEL} (Multiversal-installer App)")
                        val code = conn.responseCode
                        val body = if (code == 200) conn.inputStream.bufferedReader().use { it.readText().trim() } else ""
                        conn.disconnect()
                        code == 200 && (body == "pong" || body.isEmpty())
                    }.getOrDefault(false)

                    if (ok) {
                        connectedTarget = app.pwhs.core.security.PairingToken.appendToUrl(
                            "http://$host:$p/",
                            pairingToken,
                        )
                        connectedPort = p
                        break
                    }
                }
            }

            if (!isFromQr) {
                isConnectingManual = false
            }

            if (connectedTarget != null) {
                savedTvIp = "$host:$connectedPort"
                scanned = connectedTarget
                errorMessage = null
                isSuccess = false
                showManualDialog = false
            } else {
                val err = context.getString(R.string.tv_sync_error_connect_failed)
                if (isFromQr) {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                } else {
                    manualError = err
                }
            }
        }
    }

    val launchQrScanner = rememberQrScanner(
        onScanned = { contents ->
            android.util.Log.d("SendToTv", "Scan result: contents=$contents")
            connectToTv(contents, isFromQr = true)
        },
        onError = { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    )

    val onApkPicked: (Uri?) -> Unit = { uri ->
        val target = scanned
        if (uri != null && target != null) {
            val name = context.contentResolver.getDisplayName(uri)
            if (!PackageFileFilter.isSupportedPackage(context, uri, name)) {
                Toast.makeText(
                    context,
                    context.getString(R.string.install_unsupported_file),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                currentFileName = name
                uploading = true
                uploadProgress = 0
                uploadBytes = null
                uploadSpeed = 0L
                uploadEta = null
                errorMessage = null
                isSuccess = false

                scope.launch {
                val estimator = app.pwhs.core.util.TransferEstimator()
                val result = TvUploadClient.upload(context, target, uri, name) { copied, total, pct ->
                    val est = estimator.update(copied, total)
                    uploadProgress = pct
                    uploadBytes = Pair(copied, total)
                    uploadSpeed = est.speedBytesPerSec
                    uploadEta = est.etaSeconds
                }
                uploading = false
                when (result) {
                    is TvUploadClient.Result.Success -> {
                        isSuccess = true
                    }
                    is TvUploadClient.Result.Failure -> {
                        errorMessage = result.message
                    }
                }
            }
        }
    }
}

    val apkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        onApkPicked
    )
    val fallbackApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
        onApkPicked
    )

    val safeLaunchApkPicker = {
        val launched = runCatching {
            apkLauncher.launch(PackageFileFilter.PACKAGE_MIME_TYPES)
        }.isSuccess
        if (!launched) {
            val fallbackLaunched = runCatching {
                fallbackApkLauncher.launch("application/vnd.android.package-archive")
            }.isSuccess
            if (!fallbackLaunched) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_no_file_picker),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun disconnect() {
        val currentTarget = scanned
        if (currentTarget != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val parsed = Uri.parse(currentTarget)
                    val host = parsed.host
                    val port = parsed.port.takeIf { it > 0 } ?: 8787
                    val conn = URL("http://$host:$port/disconnect").openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.responseCode
                    conn.disconnect()
                }
            }
        }
        scanned = null
        uploading = false
        errorMessage = null
        isSuccess = false
        currentFileName = null
        uploadProgress = 0
        uploadBytes = null
        uploadSpeed = 0L
        uploadEta = null
    }

    // Ping TV periodically to keep connection active
    LaunchedEffect(scanned) {
        val target = scanned ?: return@LaunchedEffect
        val parsed = runCatching { Uri.parse(target) }.getOrNull() ?: return@LaunchedEffect
        val host = parsed.host
        if (host.isNullOrBlank()) return@LaunchedEffect
        val port = parsed.port.takeIf { it > 0 } ?: 8787
        val pingUrl = "http://$host:$port/ping"

        while (isActive) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(pingUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.setRequestProperty("User-Agent", "${Build.MANUFACTURER} ${Build.MODEL} (Multiversal-installer App)")
                    val code = conn.responseCode
                    if (code == 200) {
                        conn.inputStream.use { it.readBytes() }
                    }
                    conn.disconnect()
                }
            }
            delay(2500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tv_sync_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
                    }
                },
                actions = {
                    if (scanned != null && !uploading) {
                        IconButton(onClick = { disconnect() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = stringResource(R.string.tv_sync_btn_disconnect),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                TvSyncHeroSection(isConnected = scanned != null)
            }

            item {
                AnimatedContent(
                    targetState = when {
                        scanned == null -> TvScreenMode.Scan
                        uploading -> TvScreenMode.Uploading
                        isSuccess -> TvScreenMode.Success
                        errorMessage != null -> TvScreenMode.Error
                        else -> TvScreenMode.Connected
                    },
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 50)) +
                                slideInVertically(animationSpec = tween(250)) { it / 8 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(150)) +
                                        slideOutVertically(animationSpec = tween(150)) { -it / 8 }
                            )
                    },
                    label = "TvSyncModeTransition"
                ) { mode ->
                    when (mode) {
                        TvScreenMode.Scan -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TvDiscoveredDevicesSection(
                                    devices = discoveredTvs,
                                    isSearching = true,
                                    onSelectTv = { tv ->
                                        // Carry the pairing token NSD advertised, or the receiver
                                        // will refuse the upload as unpaired.
                                        scanned = app.pwhs.core.security.PairingToken.appendToUrl(
                                            "http://${tv.host}:${tv.port}/",
                                            tv.token,
                                        )
                                        errorMessage = null
                                        isSuccess = false
                                    }
                                )

                                OutlinedButton(
                                    onClick = {
                                        manualError = null
                                        showManualDialog = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Rounded.Pin, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.tv_sync_btn_enter_code),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                TextButton(
                                    onClick = launchQrScanner,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.tv_sync_btn_scan), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        TvScreenMode.Connected -> {
                            val host = scanned?.let { Uri.parse(it).host } ?: "Android TV"
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TvConnectedDeviceCard(host = host, onDisconnect = { disconnect() })
                                TvFileDropzoneCard(
                                    onPickApk = safeLaunchApkPicker
                                )
                                OutlinedButton(
                                    onClick = launchQrScanner,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.tv_sync_btn_rescan))
                                }
                            }
                        }

                        TvScreenMode.Uploading -> {
                            TvUploadingProgressCard(
                                fileName = currentFileName ?: "application.apk",
                                progress = uploadProgress,
                                bytes = uploadBytes,
                                speedBytesPerSec = uploadSpeed,
                                etaSeconds = uploadEta,
                            )
                        }

                        TvScreenMode.Success -> {
                            TvUploadSuccessCard(
                                fileName = currentFileName ?: "application.apk",
                                onSendAnother = {
                                    isSuccess = false
                                    errorMessage = null
                                    currentFileName = null
                                    safeLaunchApkPicker()
                                },
                                onDone = { onBack() }
                            )
                        }

                        TvScreenMode.Error -> {
                            val host = scanned?.let { Uri.parse(it).host } ?: "Android TV"
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TvConnectedDeviceCard(host = host, onDisconnect = { disconnect() })
                                TvErrorCard(
                                    error = errorMessage ?: "Upload failed",
                                    onRetry = {
                                        errorMessage = null
                                        safeLaunchApkPicker()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualConnectDialog(
            initialIp = savedTvIp,
            isConnecting = isConnectingManual,
            errorMessage = manualError,
            onDismiss = {
                if (!isConnectingManual) {
                    showManualDialog = false
                    manualError = null
                }
            },
            onConnect = { ip ->
                connectToTv(ip)
            }
        )
    }
}
