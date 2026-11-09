package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.scanner.DirectVirusTotal
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.Dispatchers
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
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > DirectVirusTotal.MAX_BYTES) {
                                update(VtResult(status = VtStatus.TOO_LARGE, provider = "VirusTotal"))
                                return@withContext
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                } ?: error("Cannot read APK")
                update(service.uploadAndScan(temp, key, update))
            } finally { temp.delete() }
        }
    }
}
