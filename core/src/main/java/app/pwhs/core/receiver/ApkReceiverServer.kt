package app.pwhs.core.receiver

import android.content.Context
import android.util.Log
import app.pwhs.core.security.HtmlEscape
import app.pwhs.core.security.PairingGuard
import app.pwhs.core.security.PairingToken
import app.pwhs.core.security.PinAttemptLimiter
import app.pwhs.core.security.UrlPolicy
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * LAN receiver: a phone (scanning the TV's QR) opens the upload page or POSTs an APK here;
 * the file is staged in cache and emitted via [TvReceiverState] for the TV UI to install.
 *
 * Streams uploads directly from the socket input stream with live progress updates.
 *
 * ## Authentication
 * Every mutating endpoint requires the [pairingToken] this receiver was started with. Before that
 * token existed, anything on the same Wi-Fi could push an APK to the TV — the on-screen confirm
 * does not help when the attacker's file is the one being confirmed — and could use `POST /url` to
 * make the TV fetch an arbitrary address. The token is published only through the QR code and the
 * NSD TXT record, so pairing still needs physical or on-screen access to the TV.
 *
 * Wrong tokens are throttled per client IP by [attemptLimiter].
 */
class ApkReceiverServer(
    private val context: Context,
    port: Int,
    private val pairingToken: String,
    attemptLimiter: PinAttemptLimiter = PinAttemptLimiter(),
) : NanoHTTPD(port) {

    /** Pure decision logic, split out so the gate itself is unit-testable without a Context. */
    private val guard = PairingGuard(pairingToken, attemptLimiter)

    private val stageDir: File = File(context.cacheDir, "received").apply { mkdirs() }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // The only endpoint that stays open: a static icon, carrying no information about the
        // device beyond the app it already advertises over NSD.
        if (method == Method.GET && uri == "/logo.png") return serveLogo()

        // Evaluated exactly once per request: a wrong token is a guess, and guessing must not be
        // counted twice just because the rejection path also wants to know the verdict.
        val verdict = verdictFor(session)

        // Liveness probe. The phone pings candidate ports while it is still working out which
        // device it reached, and requiring the token here would break manually entered addresses.
        // It answers "pong" and nothing else; the connected-client label on the TV is only updated
        // for a paired caller, so an unpaired device cannot spoof it.
        if (method == Method.GET && uri == "/ping") {
            if (verdict == PairingGuard.Verdict.ALLOWED) recordClient(session)
            return plainText(Response.Status.OK, "pong")
        }

        if (verdict != PairingGuard.Verdict.ALLOWED) return rejectUnpaired(session, uri, verdict)

        return when {
            method == Method.GET && uri == "/" -> uploadPage(session)
            method == Method.GET && uri == "/disconnect" -> handleDisconnect(session)
            method == Method.POST && uri == "/upload" -> handleUpload(session)
            method == Method.POST && uri == "/url" -> handleRemoteUrl(session)
            else -> plainText(Response.Status.NOT_FOUND, "Not found")
        }
    }

    // ── Pairing ────────────────────────────────────────────────────────

    private fun verdictFor(session: IHTTPSession): PairingGuard.Verdict {
        val clientKey = cleanIp(session.remoteIpAddress)
        val verdict = guard.evaluate(
            clientKey = clientKey,
            // NanoHTTPD lower-cases header names before they reach the handler.
            headerValue = session.headers[PairingToken.HEADER.lowercase()],
            // Query parameters are decoded eagerly by NanoHTTPD, so this is safe to read before
            // the (possibly huge) request body is parsed.
            queryValue = session.parameters[PairingToken.QUERY_PARAM]?.firstOrNull(),
        )
        if (verdict == PairingGuard.Verdict.INVALID || verdict == PairingGuard.Verdict.LOCKED_OUT) {
            Log.w(TAG, "Rejected a ${verdict.name.lowercase()} pairing token from $clientKey")
        }
        return verdict
    }

    private fun rejectUnpaired(
        session: IHTTPSession,
        uri: String,
        verdict: PairingGuard.Verdict,
    ): Response {
        val status = if (verdict == PairingGuard.Verdict.LOCKED_OUT) {
            Response.Status.FORBIDDEN
        } else {
            Response.Status.UNAUTHORIZED
        }
        // The browser landing page gets an explanation; API callers get one line of text.
        return if (session.method == Method.GET && uri == "/") {
            val html = """
                <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Multiversal-installer — Pairing required</title></head>
                <body style="font-family:system-ui,sans-serif;background:#0f0f13;color:#e4e4e7;
                     display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0">
                <div style="text-align:center;padding:32px;max-width:420px">
                <h2 style="margin-bottom:8px">Pairing required</h2>
                <p style="line-height:1.6;color:#a1a1aa">Open the <strong>Receive</strong> screen on
                your TV and scan its QR code with Multiversal-installer, or open the address shown
                under the QR code — it carries the pairing code this page needs.</p>
                </div></body></html>
            """.trimIndent()
            val response = newFixedLengthResponse(status, MIME_HTML, html)
            response.addHeader(NOSNIFF_HEADER, "nosniff")
            response
        } else {
            plainText(status, "Pairing token missing or invalid")
        }
    }

    private fun plainText(status: Response.IStatus, body: String): Response {
        val response = newFixedLengthResponse(status, MIME_PLAINTEXT, body)
        // Several of these bodies interpolate exception text. They are text/plain, and nosniff
        // stops an old browser from re-interpreting them as HTML.
        response.addHeader(NOSNIFF_HEADER, "nosniff")
        return response
    }

    // ── Endpoints ──────────────────────────────────────────────────────

    private fun serveLogo(): Response {
        return try {
            val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 256
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 256
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            newFixedLengthResponse(Response.Status.OK, "image/png", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: Exception) {
            plainText(Response.Status.NOT_FOUND, "Logo not found")
        }
    }

    private fun uploadPage(session: IHTTPSession): Response {
        recordClient(session)
        val html = try {
            val template = context.assets.open("upload_page.html").bufferedReader().use { it.readText() }
            // The page needs the token to call back with; it arrived holding it, so handing it to
            // its own script adds nothing an attacker did not already have.
            template.replace(TOKEN_PLACEHOLDER, HtmlEscape.escape(pairingToken))
        } catch (e: Exception) {
            "<html><body><h2>Error loading UI</h2><p>${HtmlEscape.escape(e.message)}</p></body></html>"
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun handleDisconnect(session: IHTTPSession): Response {
        TvReceiverState.updateConnectedClient(null)
        TvReceiverState.emitReceivingProgress(null)
        return plainText(Response.Status.OK, "ok")
    }

    private fun cleanIp(raw: String?): String {
        if (raw.isNullOrBlank()) return "Phone"
        var ip = raw.trimStart('/')
        if (ip == "0:0:0:0:0:0:0:1" || ip == "::1") ip = "127.0.0.1"
        return ip
    }

    private fun recordClient(session: IHTTPSession) {
        val clientIp = cleanIp(session.remoteIpAddress)
        val userAgent = session.headers["user-agent"]
        val deviceName = parseDeviceFromUserAgent(userAgent) ?: clientIp
        TvReceiverState.updateConnectedClient(ConnectedClient(ip = clientIp, deviceName = deviceName))
    }

    private fun parseDeviceFromUserAgent(ua: String?): String? {
        if (ua == null) return null
        return when {
            ua.contains("Multiversal-installer App", ignoreCase = true) -> {
                ua.substringBefore(" (Multiversal-installer").trim()
            }
            // Older builds still send the pre-rename token; keep them working.
            ua.contains("Universal Installer App", ignoreCase = true) -> {
                ua.substringBefore(" (Universal").trim()
            }
            ua.contains("Android", ignoreCase = true) -> {
                val match = Regex(";\\s*([^;]+)\\s*Build").find(ua)?.groupValues?.get(1)
                match?.trim() ?: "Android Device"
            }
            ua.contains("iPhone", ignoreCase = true) -> "iPhone"
            ua.contains("iPad", ignoreCase = true) -> "iPad"
            ua.contains("Macintosh", ignoreCase = true) -> "Mac"
            ua.contains("Windows", ignoreCase = true) -> "Windows PC"
            ua.contains("Linux", ignoreCase = true) -> "Linux PC"
            else -> null
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        recordClient(session)

        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val contentType = session.headers["content-type"] ?: ""
        val boundary = Regex("boundary=([^;\\s]+)").find(contentType)?.groupValues?.get(1)?.trim('"')

        // Initial progress: 0%
        TvReceiverState.emitReceivingProgress(
            ReceivingProgress(bytesReceived = 0L, totalBytes = contentLength.coerceAtLeast(1L))
        )

        return try {
            val savedFile = if (!boundary.isNullOrBlank()) {
                when (val outcome = readMultipart(session, boundary, contentLength)) {
                    is MultipartStreamReader.Outcome.Stored -> {
                        if (outcome.skippedParts > 0 || outcome.trailingParts > 0) {
                            Log.w(
                                TAG,
                                "Upload carried ${outcome.skippedParts} field(s) before and " +
                                    "${outcome.trailingParts} part(s) after the APK — discarded, not appended to the file",
                            )
                        }
                        outcome.file
                    }
                    is MultipartStreamReader.Outcome.Malformed -> {
                        Log.w(TAG, "Rejected upload: ${outcome.reason}")
                        TvReceiverState.emitReceivingProgress(null)
                        return plainText(Response.Status.BAD_REQUEST, "Upload rejected: ${outcome.reason}")
                    }
                }
            } else {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val tempPath = files.values.firstOrNull()
                    ?: return plainText(Response.Status.BAD_REQUEST, "No file")
                val original = session.parameters["apk"]?.firstOrNull()?.substringAfterLast('/')
                val dest = File(stageDir, FileNameSanitizer.sanitize(original))
                File(tempPath).copyTo(dest, overwrite = true)
                dest
            }

            // Show 100% briefly. NanoHTTPD gives every request its own thread and the response has
            // not been written yet, so holding it for one frame costs nothing but lets the TV's
            // progress bar reach 100% instead of jumping straight to the confirm dialog.
            TvReceiverState.emitReceivingProgress(
                ReceivingProgress(bytesReceived = savedFile.length(), totalBytes = savedFile.length())
            )
            Thread.sleep(PROGRESS_HOLD_MILLIS)
            TvReceiverState.emitReceivingProgress(null)
            TvReceiverState.emitReceived(
                ReceivedApk(path = savedFile.absolutePath, fileName = savedFile.name, sizeBytes = savedFile.length())
            )

            newFixedLengthResponse(
                Response.Status.OK,
                MIME_HTML,
                "<h2>Sent &#10003; — confirm the install on your TV.</h2>",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Upload failed", t)
            TvReceiverState.emitReceivingProgress(null)
            plainText(Response.Status.INTERNAL_ERROR, "Upload failed: ${t.message}")
        }
    }

    /** Streams the request body to disk, reporting live transfer progress to the TV UI. */
    private fun readMultipart(
        session: IHTTPSession,
        boundary: String,
        contentLength: Long,
    ): MultipartStreamReader.Outcome {
        val estimator = app.pwhs.core.util.TransferEstimator()
        return MultipartStreamReader.readSingleFile(
            input = session.inputStream,
            boundary = boundary,
            contentLength = contentLength,
            destDir = stageDir,
            onProgress = { written, total ->
                val estimate = estimator.update(written, total)
                TvReceiverState.emitReceivingProgress(
                    ReceivingProgress(
                        bytesReceived = written,
                        totalBytes = total,
                        speedBytesPerSec = estimate.speedBytesPerSec,
                        etaSeconds = estimate.etaSeconds,
                    )
                )
            },
        )
    }

    private fun handleRemoteUrl(session: IHTTPSession): Response {
        recordClient(session)
        val files = HashMap<String, String>()
        val urlParam = runCatching {
            session.parseBody(files)
            session.parameters["url"]?.firstOrNull()?.trim()
                ?: files["postData"]?.let { body ->
                    Regex(""""url"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)?.trim()
                }
        }.getOrNull()

        if (urlParam.isNullOrBlank()) {
            return plainText(Response.Status.BAD_REQUEST, "Missing 'url' parameter")
        }

        // The endpoint makes this device fetch whatever it is handed, so the address itself is
        // validated on top of the pairing token: absolute http(s), real host, no embedded
        // credentials, no control characters.
        val safeUrl = UrlPolicy.normalize(urlParam)
            ?: return plainText(Response.Status.BAD_REQUEST, "Only http and https download links are accepted")

        TvReceiverState.emitReceivingProgress(
            ReceivingProgress(bytesReceived = 0L, totalBytes = 1L)
        )

        val downloader = app.pwhs.core.network.NetworkApkDownloader(context)
        return try {
            val result = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                downloader.download(safeUrl) { progress ->
                    val total = progress.totalBytes.takeIf { it > 0 } ?: 0L
                    TvReceiverState.emitReceivingProgress(
                        ReceivingProgress(
                            bytesReceived = progress.bytesDownloaded,
                            totalBytes = total,
                            speedBytesPerSec = progress.speedBytesPerSec,
                            etaSeconds = progress.etaSeconds,
                        )
                    )
                }
            }

            when (result) {
                is app.pwhs.core.network.DownloadResult.Success -> {
                    TvReceiverState.emitReceivingProgress(null)
                    TvReceiverState.emitReceived(
                        ReceivedApk(
                            path = result.file.absolutePath,
                            fileName = result.fileName,
                            sizeBytes = result.totalBytes,
                        )
                    )
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        """{"status":"ok","fileName":"${HtmlEscape.escape(result.fileName)}"}""",
                    )
                }
                is app.pwhs.core.network.DownloadResult.Error -> {
                    TvReceiverState.emitReceivingProgress(null)
                    plainText(Response.Status.BAD_REQUEST, result.message)
                }
                app.pwhs.core.network.DownloadResult.Cancelled -> {
                    TvReceiverState.emitReceivingProgress(null)
                    plainText(Response.Status.BAD_REQUEST, "Download cancelled")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Remote URL download failed", t)
            TvReceiverState.emitReceivingProgress(null)
            plainText(Response.Status.INTERNAL_ERROR, "Error: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "ApkReceiverServer"
        private const val NOSNIFF_HEADER = "X-Content-Type-Options"
        private const val TOKEN_PLACEHOLDER = "{{token}}"
        private const val PROGRESS_HOLD_MILLIS = 100L
    }
}
