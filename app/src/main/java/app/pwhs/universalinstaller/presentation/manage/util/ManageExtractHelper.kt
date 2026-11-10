package app.pwhs.universalinstaller.presentation.manage.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.model.LookupRequest
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.core.domain.model.toVtResult
import app.pwhs.core.install.ApkExtractor
import app.pwhs.core.network.VirusDetecService
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.domain.scanner.ScanProvider
import app.pwhs.universalinstaller.presentation.install.util.DirectScanHelper
import app.pwhs.universalinstaller.presentation.manage.BatchExtractState
import app.pwhs.universalinstaller.presentation.manage.ExtractMode
import app.pwhs.universalinstaller.presentation.manage.ExtractState
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

object ManageExtractHelper {

    /**
     * Scans an installed app's base APK and returns the verdict for the caller to show.
     *
     * This used to hash the file and then simply open virusdetect.pwhs.app in a browser, so the
     * tap looked like a scan but answered nothing inside the app. It now performs the lookup and
     * reports every outcome, including the ones that explain why no verdict is available.
     */
    suspend fun scanVirusTotal(
        context: Context,
        app: InstalledApp,
        virusDetecService: VirusDetecService,
    ): VtResult = withContext(Dispatchers.IO) {
        try {
            if (app.packageName.isBlank()) return@withContext errorResult("Unknown package")
            val appInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
            val versionCode = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    context.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(app.packageName, 0).versionCode.toLong()
                }
            }.getOrDefault(0L)
            val baseApkFile = File(appInfo.sourceDir)
            if (!baseApkFile.exists()) return@withContext errorResult("APK file is missing")

            val sha256 = sha256Of(baseApkFile)
                ?: return@withContext errorResult("Could not read the installed APK")

            val prefs = context.dataStore.data.first()
            if (ScanProvider.resolve(prefs[PreferencesKeys.SCAN_PROVIDER]) == ScanProvider.VIRUSTOTAL) {
                val key = prefs[PreferencesKeys.VIRUSTOTAL_API_KEY].orEmpty()
                return@withContext if (key.isBlank()) {
                    VtResult(status = VtStatus.NO_API_KEY, provider = ScanProvider.VIRUSTOTAL.label)
                } else {
                    DirectScanHelper.service.lookup(sha256, key)
                }
            }

            val lookup = virusDetecService.lookup(
                LookupRequest(
                    sha256 = sha256,
                    packageName = app.packageName,
                    appName = app.appName,
                    versionName = app.versionName,
                    versionCode = versionCode,
                )
            )
            val response = lookup.getOrNull()
                ?: return@withContext errorResult(lookup.exceptionOrNull()?.message.orEmpty())
            val report = response.report ?: response.precheckReport
            if (report != null) {
                report.toVtResult().copy(provider = ScanProvider.VIRUSDETEC.label)
            } else {
                // Answered, but nothing on file: the caller can offer an upload or a rescan.
                VtResult(
                    status = VtStatus.NOT_FOUND,
                    analysisId = sha256,
                    provider = ScanProvider.VIRUSDETEC.label,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan ${app.packageName}")
            errorResult(e.message.orEmpty())
        }
    }

    private fun errorResult(message: String) = VtResult(
        status = VtStatus.ERROR,
        errorMessage = message.ifBlank { "Scan failed" },
    )

    private fun sha256Of(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var bytes = input.read(buffer)
            while (bytes >= 0) {
                digest.update(buffer, 0, bytes)
                bytes = input.read(buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    suspend fun extractApp(
        context: Context,
        packageName: String,
        appName: String,
        mode: ExtractMode,
        outputDir: File?,
        onProgress: (Long, Long) -> Unit,
    ): ExtractState {
        val useConfiguredPath = mode == ExtractMode.Backup
        val result = performExtract(
            context = context,
            packageName = packageName,
            cacheOutputDir = outputDir,
            useConfiguredPath = useConfiguredPath,
            onProgress = onProgress,
        )
        return when (result) {
            is ApkExtractor.Result.Success -> ExtractState.Done(appName, result.uri, mode)
            is ApkExtractor.Result.Failure -> ExtractState.Error(appName, result.message, mode)
        }
    }

    suspend fun extractBatch(
        context: Context,
        packages: List<String>,
        apps: List<InstalledApp>,
        onProgress: (BatchExtractState.Running) -> Unit,
    ): BatchExtractState.Done {
        val lookup = apps.associateBy { it.packageName }
        var success = 0
        var failed = 0
        val total = packages.size
        packages.forEachIndexed { index, pkg ->
            val name = lookup[pkg]?.appName ?: pkg
            onProgress(
                BatchExtractState.Running(
                    completed = index,
                    total = total,
                    currentName = name,
                    bytesCopied = 0L,
                    totalBytes = 1L,
                )
            )
            val result = performExtract(
                context = context,
                packageName = pkg,
                cacheOutputDir = null,
                useConfiguredPath = true,
            ) { bytes, totalBytes ->
                onProgress(
                    BatchExtractState.Running(
                        completed = index,
                        total = total,
                        currentName = name,
                        bytesCopied = bytes,
                        totalBytes = totalBytes,
                    )
                )
            }
            when (result) {
                is ApkExtractor.Result.Success -> success++
                is ApkExtractor.Result.Failure -> {
                    failed++
                    Timber.w("Bulk extract failed for $pkg: ${result.message}")
                }
            }
        }
        return BatchExtractState.Done(success = success, failed = failed)
    }

    suspend fun performExtract(
        context: Context,
        packageName: String,
        cacheOutputDir: File?,
        useConfiguredPath: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): ApkExtractor.Result {
        val prefs = context.dataStore.data.first()
        val customPathUri = prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH]
        val template = prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] ?: "{name}-{version}"
        val splitFormat = if (prefs[PreferencesKeys.APK_EXTRACTOR_SPLIT_FORMAT] == "xapk") {
            ApkExtractor.SplitFormat.XAPK
        } else {
            ApkExtractor.SplitFormat.APKS
        }
        val includeObb = prefs[PreferencesKeys.APK_EXTRACTOR_INCLUDE_OBB] ?: false
        return ApkExtractor.extract(
            context = context,
            packageName = packageName,
            outputDir = resolveOutputDir(
                context = context,
                cacheOutputDir = cacheOutputDir,
                customPathUri = customPathUri,
                useConfiguredPath = useConfiguredPath,
            ),
            filenameTemplate = template,
            splitFormat = splitFormat,
            includeObb = includeObb,
            onProgress = onProgress,
        )
    }

    private fun resolveOutputDir(
        context: Context,
        cacheOutputDir: File?,
        customPathUri: String?,
        useConfiguredPath: Boolean,
    ): DocumentFile? {
        if (!useConfiguredPath) {
            return cacheOutputDir?.apply { mkdirs() }?.let { DocumentFile.fromFile(it) }
        }

        val configured = resolveConfiguredOutputDir(context, customPathUri)
        if (
            configured != null &&
            configured.exists() &&
            configured.isDirectory &&
            configured.canWrite()
        ) {
            return configured
        }

        val fallback = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DEFAULT_EXTRACTED_SUBFOLDER,
        ).apply { mkdirs() }
        return DocumentFile.fromFile(fallback)
    }

    fun resolveConfiguredOutputDir(context: Context, path: String?): DocumentFile? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("content://")) {
            DocumentFile.fromTreeUri(context, Uri.parse(path))
        } else {
            val dir = File(path).apply { if (!exists()) mkdirs() }
            if (dir.isDirectory) DocumentFile.fromFile(dir) else null
        }
    }

    private const val DEFAULT_EXTRACTED_SUBFOLDER = "UniversalInstaller/Extracted"
}
