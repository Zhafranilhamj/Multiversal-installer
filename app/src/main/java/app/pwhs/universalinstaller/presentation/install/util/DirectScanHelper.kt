package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.ScanLimits
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.scanner.DirectVirusTotal
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

internal object DirectScanHelper {
    val service = DirectVirusTotal()

    suspend fun scan(context: Context, uri: Uri, hash: String, update: (VtResult) -> Unit) {
        val key = context.dataStore.data.first()[PreferencesKeys.VIRUSTOTAL_API_KEY].orEmpty()
        val lookup = service.lookup(hash, key)
        update(lookup)
        if (lookup.status != VtStatus.NOT_FOUND) return

        withContext(Dispatchers.IO) {
            val temp = File.createTempFile("vt_upload_", ".apk", context.cacheDir)
            try {
                stageForUpload(context, uri, temp, update) ?: return@withContext
                update(service.uploadAndScan(temp, key, update))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update(VtResult(status = VtStatus.ERROR, errorMessage = e.message.orEmpty(), provider = "VirusTotal"))
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * Copies the APK to [temp], refusing anything past VirusTotal's 650 MB ceiling.
     *
     * Returns null when the copy was refused or the source could not be read, after reporting why.
     */
    private suspend fun stageForUpload(
        context: Context,
        uri: Uri,
        temp: File,
        update: (VtResult) -> Unit,
    ): Unit? {
        val input = context.contentResolver.openInputStream(uri) ?: run {
            update(VtResult(status = VtStatus.ERROR, errorMessage = "Cannot read APK", provider = "VirusTotal"))
            return null
        }
        input.use { source ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (ScanLimits.exceedsMaximum(total)) {
                        update(
                            VtResult(
                                status = VtStatus.TOO_LARGE,
                                errorMessage = ScanLimits.describeMegabytes(total),
                                provider = "VirusTotal",
                            )
                        )
                        return null
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return Unit
    }
}
