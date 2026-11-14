package app.pwhs.universalinstaller.presentation.sync

import android.content.Context
import android.os.Environment
import app.pwhs.core.security.HtmlEscape
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLEncoder

/**
 * Serves the phone's "shared packages" folder over the LAN so another device can download them.
 *
 * Authentication is delegated to [SyncAuthenticator]: when the PIN gate is on, a client exchanges
 * the PIN once for a random session token that comes back as an `HttpOnly` cookie, and every later
 * request is validated against the server-side session store. This handler only turns that
 * decision into an HTTP response.
 *
 * Responses carrying interpolated content set `X-Content-Type-Options: nosniff`, and every value
 * interpolated into HTML goes through [HtmlEscape] — a file dropped into the share folder can be
 * named `<img src=x onerror=…>.apk`, and that name used to reach the listing page verbatim.
 */
class ApkHttpServer(
    private val context: Context,
    port: Int,
    private val requirePin: Boolean,
    pinCode: String,
    private val onConnectionChange: (Int) -> Unit,
    private val authenticator: SyncAuthenticator = SyncAuthenticator(requirePin, pinCode),
) : NanoHTTPD(port) {

    private val baseDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "UniversalInstaller",
    )

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    /** Drops every live session — called when the share stops or the PIN is rotated. */
    fun invalidateSessions() = authenticator.invalidate()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        if (requirePin) {
            when (val decision = decideFor(session)) {
                is SyncAuthenticator.Decision.IssueSession ->
                    return issueSession(uri, decision.token)

                is SyncAuthenticator.Decision.Lockout ->
                    return lockoutResponse(decision.retryAfterSeconds)

                SyncAuthenticator.Decision.ShowPinForm ->
                    return html(Response.Status.UNAUTHORIZED, pinFormHtml())

                SyncAuthenticator.Decision.Granted -> Unit
            }
        }

        return if (uri == "/") serveDirectoryList() else serveFile(uri)
    }

    // ── Authentication ─────────────────────────────────────────────────

    private fun decideFor(session: IHTTPSession): SyncAuthenticator.Decision {
        var submittedPin: String? = null
        if (session.method == Method.POST) {
            // The body has to be consumed before session.parameters carries the form fields.
            runCatching { session.parseBody(HashMap<String, String>()) }
            submittedPin = session.parameters["pin"]?.firstOrNull()
        }
        return authenticator.decide(
            cookieToken = session.cookies.read(SyncAuthenticator.COOKIE_NAME),
            clientKey = clientKey(session),
            isPost = session.method == Method.POST,
            submittedPin = submittedPin,
        )
    }

    private fun issueSession(uri: String, token: String): Response {
        // Redirect back to the listing, never to an arbitrary attacker-chosen target.
        val target = if (uri.startsWith("/") && !uri.contains("//")) uri else "/"
        val response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
        response.addHeader("Location", target)
        response.addHeader("Set-Cookie", authenticator.cookieHeader(token))
        return response
    }

    private fun clientKey(session: IHTTPSession): String =
        session.remoteIpAddress?.takeIf { it.isNotBlank() } ?: "unknown"

    private fun pinFormHtml(): String = try {
        context.assets.open("sync_auth.html").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "<html><body>Error loading auth template</body></html>"
    }

    private fun lockoutResponse(retryAfterSeconds: Long): Response {
        val body = """
            <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Multiversal-installer — Try again later</title></head>
            <body style="font-family:system-ui,sans-serif;background:#0f0f13;color:#e4e4e7;
                 display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0">
            <div style="text-align:center;padding:32px">
            <h2 style="margin-bottom:8px">Too many attempts</h2>
            <p>Wait about ${HtmlEscape.escape(retryAfterSeconds.toString())} seconds and try again.</p>
            </div></body></html>
        """.trimIndent()
        val response = html(TooManyRequests, body)
        response.addHeader("Retry-After", retryAfterSeconds.toString())
        return response
    }

    // ── Response helpers ───────────────────────────────────────────────

    private fun html(status: Response.IStatus, body: String): Response {
        val response = newFixedLengthResponse(status, MIME_HTML, body)
        response.addHeader(NOSNIFF_HEADER, "nosniff")
        return response
    }

    private fun plainText(status: Response.IStatus, body: String): Response {
        val response = newFixedLengthResponse(status, MIME_PLAINTEXT, body)
        response.addHeader(NOSNIFF_HEADER, "nosniff")
        return response
    }

    // ── File serving ───────────────────────────────────────────────────

    private fun serveFile(uri: String): Response {
        val fileName = uri.substringAfterLast("/")
        val file = File(baseDir, fileName)

        if (!file.exists() || file.isDirectory) {
            return plainText(Response.Status.NOT_FOUND, "File not found.")
        }

        return try {
            val totalBytes = file.length()
            val fis = FileInputStream(file)
            // Wrap with tracking to detect download start/finish and report progress
            onConnectionChange(1)
            val transferId = SyncManager.nextTransferId()
            val trackingStream = TrackingInputStream(
                stream = fis,
                transferId = transferId,
                fileName = file.name,
                totalBytes = totalBytes,
                onClose = {
                    SyncManager.removeTransfer(transferId)
                    onConnectionChange(-1)
                }
            )

            // Fixed length, not chunked: the size is known, and a chunked response must not also
            // carry the manual Content-Length this used to add — RFC 7230 §3.3.2 forbids both and
            // some proxies and clients hang on the combination.
            val response = newFixedLengthResponse(
                Response.Status.OK, mimeTypeFor(fileName), trackingStream, totalBytes,
            )
            // Use RFC 5987 filename* for proper encoding of special characters like ()
            val safeFilename = HtmlEscape.headerQuoted(file.name)
            val encodedFilename = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            response.addHeader(
                "Content-Disposition",
                "attachment; filename=\"$safeFilename\"; filename*=UTF-8''$encodedFilename",
            )
            // Range requests are not implemented, so say so rather than let a client assume them.
            response.addHeader("Accept-Ranges", "none")
            response
        } catch (e: Exception) {
            onConnectionChange(-1) // Ensure decrement on error
            plainText(Response.Status.INTERNAL_ERROR, "Error reading file.")
        }
    }

    private fun mimeTypeFor(fileName: String): String = when {
        fileName.endsWith(".apk", true) -> "application/vnd.android.package-archive"
        fileName.endsWith(".apks", true) -> "application/vnd.apks"
        fileName.endsWith(".xapk", true) -> "application/vnd.xapk"
        fileName.endsWith(".apkm", true) -> "application/vnd.apkm"
        fileName.endsWith(".apk+", true) -> "application/vnd.apks" // Same as APKS
        else -> "application/octet-stream"
    }

    private fun serveDirectoryList(): Response {
        val files = baseDir.listFiles()?.filter {
            it.isFile && SUPPORTED_EXTENSIONS.any { ext -> it.name.endsWith(ext, true) }
        }?.sortedBy { it.name } ?: emptyList()

        val listHtml = if (files.isEmpty()) {
            """<div class="empty"><div class="empty-icon">📭</div><div class="empty-text">No packages found.<br>Add files via Multiversal-installer app.</div></div>"""
        } else {
            files.joinToString("\n") { file -> fileItemHtml(file) }
        }

        val templateHtml = try {
            context.assets.open("sync_index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body>Error loading index template</body></html>"
        }

        return html(
            Response.Status.OK,
            templateHtml
                .replace("{{listHtml}}", listHtml)
                .replace("{{fileCount}}", HtmlEscape.escape(files.size.toString()))
                .replace("{{totalSize}}", HtmlEscape.escape(formatFileSize(files.sumOf { it.length() }))),
        )
    }

    /**
     * One row of the share listing. The name comes off shared storage and is fully
     * attacker-controlled, so it is escaped before it reaches the page.
     */
    private fun fileItemHtml(file: File): String {
        val href = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        return """
            <a href="/$href" class="file-item">
                <div class="file-icon">📦</div>
                <div class="file-info">
                    <div class="file-name">${HtmlEscape.escape(file.name)}</div>
                    <div class="file-size">${HtmlEscape.escape(formatFileSize(file.length()))}</div>
                </div>
                <div class="download-btn">
                    <svg viewBox="0 0 24 24"><path d="M12 4v12m0 0l-4-4m4 4l4-4M5 18h14" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
                </div>
            </a>
        """.trimIndent()
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.1f GB".format(gb)
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.0f KB".format(kb)
            else -> "$bytes B"
        }
    }

    private companion object {
        const val NOSNIFF_HEADER = "X-Content-Type-Options"
        val SUPPORTED_EXTENSIONS = listOf(".apk", ".apks", ".xapk", ".apkm", ".apk+")
    }
}

/**
 * 429 is not in NanoHTTPD's `Status` enum, and the lockout response has to be distinguishable
 * from a plain 403 so a browser — and the person staring at it — knows to wait rather than retry.
 */
private object TooManyRequests : NanoHTTPD.Response.IStatus {
    override fun getDescription(): String = "429 Too Many Requests"
    override fun getRequestStatus(): Int = 429
}

/**
 * A stream wrapper that fires [onClose] exactly once when the stream is closed
 * and reports transfer progress to [SyncManager].
 * NanoHTTPD always closes the response data stream after sending, so this
 * reliably tracks when a download finishes (or the client disconnects).
 */
private class TrackingInputStream(
    stream: InputStream,
    private val transferId: String,
    private val fileName: String,
    private val totalBytes: Long,
    private val onClose: () -> Unit,
) : FilterInputStream(stream) {
    @Volatile
    private var closed = false
    private var bytesRead: Long = 0L
    private var lastReportedPercent: Int = -1
    private var lastReportTime: Long = 0L

    override fun read(): Int {
        val b = super.read()
        if (b != -1) {
            bytesRead++
            reportProgress()
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            bytesRead += count
            reportProgress()
        }
        return count
    }

    private fun reportProgress() {
        if (totalBytes <= 0) return
        val percent = ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
        val now = System.currentTimeMillis()
        // Update when percentage changes or at least every 400ms for smooth speed/ETA
        if (percent != lastReportedPercent || now - lastReportTime >= 400L) {
            lastReportedPercent = percent
            lastReportTime = now
            SyncManager.updateProgress(transferId, fileName, bytesRead, totalBytes)
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            try {
                super.close()
            } finally {
                onClose()
            }
        }
    }
}
