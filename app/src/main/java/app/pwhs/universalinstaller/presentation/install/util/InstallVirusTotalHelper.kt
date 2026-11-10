package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import androidx.core.os.ConfigurationCompat
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.LookupRequest
import app.pwhs.core.domain.model.ScanLimits
import app.pwhs.core.domain.model.toVtResult
import app.pwhs.core.network.VirusDetecService
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.scanner.ScanProvider
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale

object InstallVirusTotalHelper {

    private fun scanLanguage(context: Context): String = ScanProvider.language(
        ConfigurationCompat.getLocales(context.resources.configuration)[0]?.toLanguageTag().orEmpty())

    /**
     * True only when the user has turned scanning off — this is the switch that hides every scan
     * surface in the UI.
     */
    suspend fun isDisabled(context: Context): Boolean {
        return try {
            context.dataStore.data.first()[PreferencesKeys.DISABLE_VIRUSTOTAL] ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Gate for *automatic* hash lookups, which run on every file the user opens.
     *
     * Those stay off until a provider has actually been chosen, so nothing is sent anywhere just
     * because an APK was opened. This is the documented consent rule and it is deliberately
     * stricter than [isDisabled].
     */
    suspend fun autoLookupDisabled(context: Context): Boolean {
        if (isDisabled(context)) return true
        val stored = runCatchingContext { context.dataStore.data.first()[PreferencesKeys.SCAN_PROVIDER] }
        return ScanProvider.from(stored) == null
    }

    /** The provider an explicit scan should use, honouring an explicit Settings choice. */
    suspend fun provider(context: Context): ScanProvider {
        val stored = runCatchingContext { context.dataStore.data.first()[PreferencesKeys.SCAN_PROVIDER] }
        return ScanProvider.resolve(stored)
    }

    private inline fun <T> runCatchingContext(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    suspend fun computeSha256(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read >= 0) {
                    ensureActive()
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
                digest.digest().toHex()
            } ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun launchHashLookupOnly(
        context: Context,
        originalUri: Uri,
        virusDetecService: VirusDetecService,
        apkInfo: ApkInfo? = null,
        onAutoLookupStart: () -> Unit = {},
    ): Pair<String, VtResult?> = withContext(Dispatchers.IO) {
        if (autoLookupDisabled(context)) return@withContext "" to null
        return@withContext try {
            val sha256 = computeSha256(context, originalUri)
            if (sha256.isBlank()) {
                // Still return the (empty) hash so the caller can show "could not hash" rather than
                // leaving the security row in its pre-scan state forever.
                "" to VtResult(status = VtStatus.ERROR, provider = providerLabel(context))
            } else {
                onAutoLookupStart()
                val prefs = context.dataStore.data.first()
                if (ScanProvider.resolve(prefs[PreferencesKeys.SCAN_PROVIDER]) == ScanProvider.VIRUSTOTAL) {
                    val key = prefs[PreferencesKeys.VIRUSTOTAL_API_KEY].orEmpty()
                    return@withContext sha256 to DirectScanHelper.service.lookup(sha256, key)
                }
                val req = LookupRequest(
                    sha256 = sha256,
                    packageName = apkInfo?.packageName.orEmpty(),
                    appName = apkInfo?.appName.orEmpty(),
                    versionName = apkInfo?.versionName.orEmpty(),
                    versionCode = apkInfo?.versionCode ?: 0L,
                    minSdk = apkInfo?.minSdkVersion ?: 0,
                    targetSdk = apkInfo?.targetSdkVersion ?: 0,
                    certSha256 = apkInfo?.certSha256.orEmpty(),
                    permissions = apkInfo?.permissions.orEmpty(),
                )
                val lookupResult = virusDetecService.lookup(req, scanLanguage(context))
                if (lookupResult.isSuccess) {
                    val resp = lookupResult.getOrThrow()
                    val report = resp.report ?: resp.precheckReport
                    if (report != null) {
                        app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordVirusTotalScan(context)
                        sha256 to report.toVtResult().copy(provider = ScanProvider.VIRUSDETEC.label)
                    } else if (resp.needUpload) {
                        sha256 to VtResult(status = VtStatus.NOT_FOUND, provider = ScanProvider.VIRUSDETEC.label)
                    } else {
                        sha256 to null
                    }
                } else {
                    Timber.w(lookupResult.exceptionOrNull(), "VirusDetec lookup failed")
                    sha256 to null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "VirusDetec lookup error")
            "" to VtResult(status = VtStatus.ERROR, errorMessage = e.message.orEmpty(), provider = providerLabel(context))
        }
    }

    private suspend fun providerLabel(context: Context): String {
        val stored = runCatchingContext { context.dataStore.data.first()[PreferencesKeys.SCAN_PROVIDER] }
        return ScanProvider.resolve(stored).label
    }

    /**
     * Runs an explicit scan and reports every state through [onProgress], including the states that
     * explain why a scan cannot run. There is no silent `return` before the first progress update.
     */
    suspend fun scanVirusTotal(
        context: Context,
        uri: Uri,
        fileName: String,
        current: ApkInfo,
        virusDetecService: VirusDetecService,
        virusTotalNotifier: VirusTotalNotifier,
        onUpdateSha256: (String) -> Unit,
        onProgress: (VtResult) -> Unit,
    ) {
        if (isDisabled(context)) {
            onProgress(VtResult(status = VtStatus.ERROR, provider = "VirusTotal"))
            return
        }

        val provider = provider(context)
        if (provider == ScanProvider.VIRUSTOTAL) {
            val key = runCatchingContext {
                context.dataStore.data.first()[PreferencesKeys.VIRUSTOTAL_API_KEY].orEmpty()
            }.orEmpty()
            if (key.isBlank()) {
                // Never fail silently: the user picked VirusTotal and it needs a key of their own.
                onProgress(VtResult(status = VtStatus.NO_API_KEY, provider = ScanProvider.VIRUSTOTAL.label))
                return
            }
            onProgress(VtResult(status = VtStatus.SCANNING, provider = ScanProvider.VIRUSTOTAL.label))
            val hash = current.sha256.ifBlank { computeSha256(context, uri) }
            if (hash.isBlank()) {
                onProgress(VtResult(status = VtStatus.ERROR, provider = ScanProvider.VIRUSTOTAL.label))
                return
            }
            onUpdateSha256(hash)
            try {
                DirectScanHelper.scan(context, uri, hash, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onProgress(VtResult(status = VtStatus.ERROR, provider = ScanProvider.VIRUSTOTAL.label))
            }
            return
        }

        // ── VirusDetec ──────────────────────────────────────────────────────────
        val sizeBytes = current.fileSizeBytes
        if (ScanLimits.exceedsMaximum(sizeBytes)) {
            onProgress(
                VtResult(
                    status = VtStatus.TOO_LARGE,
                    errorMessage = ScanLimits.describeMegabytes(sizeBytes),
                    provider = ScanProvider.VIRUSDETEC.label,
                )
            )
            return
        }

        val scanNotifId = virusTotalNotifier.notifyHashing(fileName)
        var scanFinished = false

        try {
            onProgress(VtResult(status = VtStatus.SCANNING, provider = ScanProvider.VIRUSDETEC.label))

            val sha256 = current.sha256.ifBlank { computeSha256(context, uri) }
            if (sha256.isBlank()) {
                scanFinished = true
                finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Could not hash file", sha256, onProgress)
                return
            }
            onUpdateSha256(sha256)

            // 1. Try the fast precheck/cache lookup first — no upload needed when it hits.
            val req = LookupRequest(
                sha256 = sha256,
                packageName = current.packageName,
                appName = current.appName,
                versionName = current.versionName,
                versionCode = current.versionCode,
                minSdk = current.minSdkVersion,
                targetSdk = current.targetSdkVersion,
                certSha256 = current.certSha256,
                permissions = current.permissions,
            )
            val lookupResult = virusDetecService.lookup(req, scanLanguage(context))
            if (lookupResult.isSuccess) {
                val resp = lookupResult.getOrThrow()
                val report = resp.report ?: resp.precheckReport
                if (report != null) {
                    scanFinished = true
                    app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordVirusTotalScan(context)
                    finishScan(context, virusTotalNotifier, scanNotifId, fileName, report.toVtResult(), sha256, onProgress)
                    return
                }
            }

            // 2. Unknown file: stage it and hand it to the upload-ticket flow.
            val tempFile = copyToCache(context, uri, fileName)
            if (tempFile == null || !tempFile.exists()) {
                scanFinished = true
                finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Could not read file for upload", sha256, onProgress)
                return
            }

            try {
                onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = 0, provider = ScanProvider.VIRUSDETEC.label))
                virusTotalNotifier.notifyUploading(scanNotifId, fileName, 0)

                val ticketRes = virusDetecService.createUploadTicket(sha256, tempFile.length(), scanLanguage(context))
                if (ticketRes.isFailure) {
                    scanFinished = true
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, ticketRes.exceptionOrNull()?.message ?: "Upload ticket failed", sha256, onProgress)
                    return
                }
                val ticket = ticketRes.getOrThrow()

                val uploadRes = virusDetecService.uploadBinary(ticket.uploadUrl, tempFile) { pct ->
                    onProgress(VtResult(status = VtStatus.UPLOADING, uploadProgress = pct, provider = ScanProvider.VIRUSDETEC.label))
                    virusTotalNotifier.notifyUploading(scanNotifId, fileName, pct)
                }
                if (uploadRes.isFailure) {
                    scanFinished = true
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Upload failed", sha256, onProgress)
                    return
                }

                val startRes = virusDetecService.startScan(ticket.jobId, scanLanguage(context))
                if (startRes.isFailure) {
                    scanFinished = true
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Start scan failed", sha256, onProgress)
                    return
                }

                onProgress(VtResult(status = VtStatus.ANALYZING, uploadProgress = 100, provider = ScanProvider.VIRUSDETEC.label))
                virusTotalNotifier.notifyAnalyzing(scanNotifId, fileName)

                virusDetecService.watchScanStream(ticket.jobId, scanLanguage(context)).collect { update ->
                    when (update.status) {
                        "COMPLETED" -> {
                            val finalResult = update.report?.toVtResult() ?: VtResult(status = VtStatus.ERROR)
                            scanFinished = true
                            app.pwhs.universalinstaller.domain.stats.UserStatsTracker.recordVirusTotalScan(context)
                            finishScan(context, virusTotalNotifier, scanNotifId, fileName, finalResult, sha256, onProgress)
                        }
                        "FAILED" -> {
                            scanFinished = true
                            finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, update.errorCode ?: "Scan failed", sha256, onProgress)
                        }
                        else -> {
                            onProgress(
                                VtResult(
                                    status = VtStatus.ANALYZING,
                                    uploadProgress = update.progressPercent,
                                    errorMessage = update.message,
                                    provider = ScanProvider.VIRUSDETEC.label,
                                )
                            )
                        }
                    }
                }
                // The stream can end without a terminal event (server restart, dropped connection).
                // That must not leave the row spinning forever.
                if (!scanFinished) {
                    scanFinished = true
                    finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, "Scan ended unexpectedly", sha256, onProgress)
                }
            } finally {
                runCatching { tempFile.delete() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "VirusDetec scan error")
            scanFinished = true
            finishScanWithError(context, virusTotalNotifier, scanNotifId, fileName, e.message.orEmpty(), current.sha256, onProgress)
        } finally {
            if (!scanFinished) {
                virusTotalNotifier.cancel(scanNotifId)
            }
        }
    }

    /** Streams the APK into the cache dir; never loads it into memory, so 650 MB is fine. */
    private suspend fun copyToCache(context: Context, uri: Uri, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val ext = fileName.substringAfterLast('.', "apk").ifBlank { "apk" }
            val file = File(context.cacheDir, "sec_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            file.takeIf { it.exists() && it.length() > 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not stage APK for upload")
            null
        }
    }

    private fun finishScan(
        context: Context,
        notifier: VirusTotalNotifier,
        scanNotifId: Int,
        fileName: String,
        result: VtResult,
        sha256: String,
        onProgress: (VtResult) -> Unit,
    ) {
        onProgress(result.copy(provider = result.provider ?: ScanProvider.VIRUSDETEC.label))
        val (title, text) = resultNotifCopy(context, result)
        notifier.notifyResult(scanNotifId, fileName, title, text, sha256)
    }

    private fun finishScanWithError(
        context: Context,
        notifier: VirusTotalNotifier,
        scanNotifId: Int,
        fileName: String,
        message: String,
        sha256: String,
        onProgress: (VtResult) -> Unit,
    ) {
        finishScan(
            context,
            notifier,
            scanNotifId,
            fileName,
            VtResult(status = VtStatus.ERROR, errorMessage = message, provider = ScanProvider.VIRUSDETEC.label),
            sha256,
            onProgress,
        )
    }

    fun resultNotifCopy(context: Context, result: VtResult): Pair<String, String> {
        return when (result.status) {
            VtStatus.CLEAN -> context.getString(R.string.vt_notif_result_clean) to
                    context.getString(R.string.apk_info_vt_clean)
            VtStatus.MALICIOUS -> context.getString(R.string.vt_notif_result_malicious) to
                    context.getString(R.string.apk_info_vt_malicious, result.malicious)
            VtStatus.SUSPICIOUS -> context.getString(R.string.vt_notif_result_suspicious) to
                    context.getString(R.string.apk_info_vt_suspicious, result.suspicious)
            VtStatus.NOT_FOUND -> context.getString(R.string.vt_notif_result_done) to
                    context.getString(R.string.apk_info_vt_not_found)
            VtStatus.ERROR -> context.getString(R.string.vt_notif_result_error) to
                    (result.errorMessage.ifBlank { "" })
            else -> context.getString(R.string.vt_notif_result_done) to ""
        }
    }

    /**
     * Lower-case hex, explicitly in [Locale.ROOT].
     *
     * `"%02x".format(byte)` uses the default locale, so on a device set to Arabic or Hindi the
     * SHA-256 that gets looked up is not the SHA-256 VirusTotal (or VirusDetec) knows.
     */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(Locale.ROOT, it) }
}
