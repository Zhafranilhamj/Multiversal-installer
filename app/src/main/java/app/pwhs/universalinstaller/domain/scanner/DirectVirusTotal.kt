package app.pwhs.universalinstaller.domain.scanner

import app.pwhs.core.domain.model.ScanLimits
import app.pwhs.core.domain.model.VtEngineResult
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL

/** Direct VirusTotal v3 only. No proxy or cross-provider fallback; never logs credentials. */
class DirectVirusTotal(private val client: HttpClient = defaultClient) {
    companion object {
        /** VirusTotal's `POST /files` accepts at most this much; larger files need a pre-signed URL. */
        const val DIRECT_UPLOAD_MAX_BYTES = ScanLimits.DIRECT_UPLOAD_MAX_BYTES

        /** The pre-signed `upload_url` endpoint accepts up to this much. */
        const val MAX_UPLOAD_BYTES = ScanLimits.MAX_UPLOAD_BYTES

        private const val BASE = "https://www.virustotal.com/api/v3"

        /** Uploads get their own timeout: 650 MB over mobile data takes far longer than a lookup. */
        private const val UPLOAD_TIMEOUT_MS = 30 * 60 * 1000L

        /** `analyses/{id}` may 404 for a few seconds after the upload; 10s x 90 is ~15 minutes. */
        private const val POLL_ATTEMPTS = 90
        private const val POLL_INTERVAL_MS = 5_000L

        /** One hop is all VirusTotal's upload chain needs; more than that is a loop. */
        private const val MAX_UPLOAD_REDIRECTS = 2

        private val defaultClient by lazy {
            HttpClient(OkHttp) {
                followRedirects = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 20_000
                }
            }
        }

        internal fun httpFailure(code: Int): VtStatus = when (code) {
            401, 403 -> VtStatus.INVALID_API_KEY
            404 -> VtStatus.NOT_FOUND
            429 -> VtStatus.RATE_LIMITED
            413 -> VtStatus.TOO_LARGE
            else -> VtStatus.ERROR
        }

        /**
         * The pre-signed URL VirusTotal returns is the only host this class will POST an upload to.
         * A tampered or malformed response must never be able to send APK bytes somewhere else.
         */
        internal fun isTrustedUploadUrl(url: String): Boolean {
            val host = runCatching { URL(url).host?.lowercase() }.getOrNull() ?: return false
            return host == "virustotal.com" || host.endsWith(".virustotal.com")
        }

        internal fun parse(attributes: JSONObject, analysis: Boolean = false): VtResult {
            if (analysis && attributes.optString("status") != "completed") return result(VtStatus.QUEUED)
            val stats = attributes.getJSONObject(if (analysis) "stats" else "last_analysis_stats")
            val malicious = stats.optInt("malicious")
            val suspicious = stats.optInt("suspicious")
            val harmless = stats.optInt("harmless")
            val undetected = stats.optInt("undetected")
            val status = when {
                malicious > 0 -> VtStatus.MALICIOUS
                suspicious > 0 -> VtStatus.SUSPICIOUS
                harmless + undetected > 0 -> VtStatus.CLEAN
                else -> VtStatus.ERROR // Missing/all-failed engine results are not a clean verdict.
            }
            val engines = attributes.optJSONObject(if (analysis) "results" else "last_analysis_results")
            return VtResult(malicious, suspicious, harmless, undetected, status,
                engineResults = engines?.keys()?.asSequence()?.map { key ->
                    val engine = engines.getJSONObject(key)
                    VtEngineResult(engine.optString("engine_name", key), engine.optString("category"),
                        if (engine.isNull("result")) null else engine.getString("result"))
                }?.toList().orEmpty(), provider = ScanProvider.VIRUSTOTAL.label)
        }

        private fun result(status: VtStatus) = VtResult(status = status, provider = ScanProvider.VIRUSTOTAL.label)
    }

    suspend fun lookup(hash: String, key: String): VtResult = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext result(VtStatus.NO_API_KEY)
        require(hash.matches(Regex("[a-fA-F0-9]{64}")))
        safe {
            val response = client.get("$BASE/files/$hash") { header("x-apikey", key.trim()) }
            if (response.status.value != 200) result(httpFailure(response.status.value))
            else parse(JSONObject(response.bodyAsText()).getJSONObject("data").getJSONObject("attributes"))
        }
    }

    /**
     * Called only from the explicit scan action, never from automatic hash lookup.
     *
     * Files up to 32 MB go straight to `POST /files`. Anything larger — up to 650 MB — is posted
     * to a one-time pre-signed URL obtained from `GET /files/upload_url`, exactly as VirusTotal
     * documents. Both branches stream the file from disk, so a 600 MB APK is never read into memory.
     */
    suspend fun uploadAndScan(
        file: File,
        key: String,
        progress: (VtResult) -> Unit,
    ): VtResult = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext result(VtStatus.NO_API_KEY)
        val size = file.length()
        if (ScanLimits.exceedsMaximum(size)) {
            return@withContext result(VtStatus.TOO_LARGE).copy(errorMessage = ScanLimits.describeMegabytes(size))
        }
        safe {
            progress(result(VtStatus.UPLOADING))
            val uploadUrl = if (ScanLimits.needsUploadUrl(size)) {
                largeFileUploadUrl(key) ?: return@safe result(VtStatus.RATE_LIMITED)
            } else {
                "$BASE/files"
            }

            val response = postFile(uploadUrl, file, key, progress) ?: return@safe result(VtStatus.ERROR)
            if (response.status.value !in 200..299) {
                return@safe result(httpFailure(response.status.value))
            }
            val analysisId = JSONObject(response.bodyAsText()).getJSONObject("data").getString("id")
            require(analysisId.matches(Regex("[a-zA-Z0-9_=-]+")))
            progress(result(VtStatus.ANALYZING).copy(uploadProgress = 100))

            repeat(POLL_ATTEMPTS) { attempt ->
                // Poll immediately once: small APKs are usually analysed before the first interval,
                // and making the user wait 10s for an answer that already exists feels broken.
                if (attempt > 0) delay(POLL_INTERVAL_MS)
                val poll = client.get("$BASE/analyses/$analysisId") { header("x-apikey", key.trim()) }
                // 404 right after an upload means the analysis is not visible yet — keep polling.
                if (poll.status.value == 404) return@repeat
                if (poll.status.value != 200) return@safe result(httpFailure(poll.status.value))
                val parsed = parse(JSONObject(poll.bodyAsText()).getJSONObject("data").getJSONObject("attributes"), true)
                if (parsed.status != VtStatus.QUEUED) return@safe parsed
                progress(result(VtStatus.QUEUED).copy(uploadProgress = 100))
            }
            result(VtStatus.ERROR).copy(errorMessage = "Analysis did not finish in time")
        }
    }

    /**
     * `GET /files/upload_url` returns `{"data": "<url>"}`. Returns null when the key is valid but
     * the quota is spent, so the caller can surface a rate-limit message instead of a generic error.
     */
    private suspend fun largeFileUploadUrl(key: String): String? {
        val response = client.get("$BASE/files/upload_url") { header("x-apikey", key.trim()) }
        if (response.status.value == 429) return null
        if (response.status.value != 200) error("upload_url rejected with HTTP ${response.status.value}")
        val url = JSONObject(response.bodyAsText()).optString("data")
        if (!isTrustedUploadUrl(url)) error("upload_url pointed at an unexpected host")
        return url
    }

    /**
     * Streams [file] as the multipart `file` part and reports real upload progress.
     *
     * A 650 MB APK must never be materialised in a `ByteArray`, so the part is backed by a
     * [ChannelProvider] that reads from disk. Ktor only auto-follows redirects for GET/HEAD, so a
     * POST redirect from the pre-signed URL is followed here, re-streaming the same file.
     */
    private suspend fun postFile(
        url: String,
        file: File,
        key: String,
        progress: (VtResult) -> Unit,
    ): HttpResponse? {
        var target = url
        repeat(MAX_UPLOAD_REDIRECTS + 1) {
            val response = try {
                client.submitFormWithBinaryData(
                    url = target,
                    formData = formData {
                        append(
                            "file",
                            ChannelProvider(size = file.length()) { file.readChannel() },
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/vnd.android.package-archive")
                                append(HttpHeaders.ContentDisposition, "filename=\"package.apk\"")
                            },
                        )
                    },
                ) {
                    header("x-apikey", key.trim())
                    timeout { requestTimeoutMillis = UPLOAD_TIMEOUT_MS }
                    onUpload { sent, total ->
                        if (total != null && total > 0) {
                            val percent = (sent * 100 / total).toInt().coerceIn(0, 100)
                            progress(result(VtStatus.UPLOADING).copy(uploadProgress = percent))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                return null.also { progress(result(VtStatus.ERROR).copy(errorMessage = "Upload timed out")) }
            } catch (e: SocketTimeoutException) {
                return null.also { progress(result(VtStatus.ERROR).copy(errorMessage = "Connection timed out")) }
            } catch (e: IOException) {
                return null.also {
                    progress(result(VtStatus.ERROR).copy(errorMessage = e.message.orEmpty().ifBlank { "Upload failed" }))
                }
            }

            val redirected = response.status.value in 300..399
            val location = response.headers[HttpHeaders.Location]
            if (redirected && !location.isNullOrBlank() && isTrustedUploadUrl(location)) {
                target = location
                return@repeat
            }
            return response
        }
        return null
    }

    private suspend fun safe(block: suspend () -> VtResult): VtResult = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { result(VtStatus.ERROR).copy(errorMessage = e.message.orEmpty()) }
}
