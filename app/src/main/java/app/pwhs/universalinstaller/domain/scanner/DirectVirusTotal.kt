package app.pwhs.universalinstaller.domain.scanner

import app.pwhs.core.domain.model.VtEngineResult
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Direct VirusTotal v3 only. No proxy or cross-provider fallback; never logs credentials. */
class DirectVirusTotal(private val client: HttpClient = defaultClient) {
    companion object {
        const val MAX_BYTES = 32L * 1024 * 1024
        private const val BASE = "https://www.virustotal.com/api/v3"
        private val defaultClient by lazy {
            HttpClient(OkHttp) {
                followRedirects = false
                install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 15_000 }
            }
        }
        internal fun httpFailure(code: Int): VtStatus = when (code) {
            401, 403 -> VtStatus.INVALID_API_KEY
            404 -> VtStatus.NOT_FOUND
            429 -> VtStatus.RATE_LIMITED
            413 -> VtStatus.TOO_LARGE
            else -> VtStatus.ERROR
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

    /** Called only from the explicit scan action, never from automatic hash lookup. */
    suspend fun uploadAndScan(file: File, key: String, progress: (VtResult) -> Unit): VtResult = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext result(VtStatus.NO_API_KEY)
        if (file.length() > MAX_BYTES) return@withContext result(VtStatus.TOO_LARGE)
        safe {
            progress(result(VtStatus.UPLOADING))
            val response = client.submitFormWithBinaryData("$BASE/files", formData {
                append("file", file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "application/vnd.android.package-archive")
                    append(HttpHeaders.ContentDisposition, "filename=\"package.apk\"")
                })
            }) { header("x-apikey", key.trim()) }
            if (response.status.value !in 200..299) return@safe result(httpFailure(response.status.value))
            val id = JSONObject(response.bodyAsText()).getJSONObject("data").getString("id")
            require(id.matches(Regex("[a-zA-Z0-9_=-]+")))
            repeat(20) {
                progress(result(VtStatus.ANALYZING))
                delay(30_000) // Respect public API rate limits; stop on 429 rather than retrying.
                val poll = client.get("$BASE/analyses/$id") { header("x-apikey", key.trim()) }
                if (poll.status.value != 200) return@safe result(httpFailure(poll.status.value))
                val parsed = parse(JSONObject(poll.bodyAsText()).getJSONObject("data").getJSONObject("attributes"), true)
                if (parsed.status != VtStatus.QUEUED) return@safe parsed
            }
            result(VtStatus.ERROR)
        }
    }

    private suspend fun safe(block: suspend () -> VtResult): VtResult = try { block() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { result(VtStatus.ERROR) }
}
