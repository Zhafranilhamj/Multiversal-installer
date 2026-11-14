package app.pwhs.universalinstaller.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import app.pwhs.core.util.openUriSafely

/**
 * Universal Anti-Split Bridge / Protocol Constants.
 * Facilitates 1-click invocation from Multiversal-installer to Universal Anti-Split
 * to merge split APK bundles (.xapk, .apks, .apkm, .zip or installed split apps)
 * into a single standalone APK and receive the output APK back via Activity Result.
 */
object AntiSplitBridge {
    const val PACKAGE_NAME = "app.pwhs.universalantisplit"
    const val ACTION_MERGE_SPLIT = "app.pwhs.universalinstaller.action.MERGE_SPLIT"

    // Input Extras
    const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    const val EXTRA_AUTO_START = "EXTRA_AUTO_START"
    const val EXTRA_AUTO_SIGN = "EXTRA_AUTO_SIGN"

    // Output Result Extras
    const val EXTRA_OUTPUT_PATH = "EXTRA_OUTPUT_PATH"
    const val EXTRA_OUTPUT_URI = "EXTRA_OUTPUT_URI"
    const val EXTRA_RESULT_PACKAGE = "EXTRA_RESULT_PACKAGE"
    const val EXTRA_SUCCESS = "EXTRA_SUCCESS"
    const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"

    const val GITHUB_RELEASES_URL = "https://github.com/pass-with-high-score/universal-antisplit/releases"

    /**
     * Checks if Universal Anti-Split is installed on the device.
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Checks if an intent can be resolved by the system.
     */
    fun canResolveIntent(context: Context, intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }

    /**
     * Creates an Intent to merge an external split bundle file (.xapk, .apks, .apkm, .zip).
     *
     * @param fileUri Content URI of the split bundle archive
     * @param autoStart If true, Universal Anti-Split will immediately trigger merge upon load
     * @param autoSign If true, the output APK is signed (default true)
     * @param newTask If true, adds FLAG_ACTIVITY_NEW_TASK (needed when launching from non-Activity context)
     */
    fun createMergeFileIntent(
        fileUri: Uri,
        autoStart: Boolean = false,
        autoSign: Boolean = true,
        newTask: Boolean = false,
    ): Intent {
        return Intent(ACTION_MERGE_SPLIT).apply {
            setPackage(PACKAGE_NAME)
            setDataAndType(fileUri, "application/octet-stream")
            putExtra(EXTRA_AUTO_START, autoStart)
            putExtra(EXTRA_AUTO_SIGN, autoSign)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Intent to extract & merge an installed split application from the device.
     *
     * @param packageName Target package name of the installed split app
     * @param autoStart If true, Universal Anti-Split will immediately trigger merge upon load
     * @param autoSign If true, the output APK is signed (default true)
     * @param newTask If true, adds FLAG_ACTIVITY_NEW_TASK (needed when launching from non-Activity context)
     */
    fun createMergeInstalledAppIntent(
        packageName: String,
        autoStart: Boolean = false,
        autoSign: Boolean = true,
        newTask: Boolean = false,
    ): Intent {
        return Intent(ACTION_MERGE_SPLIT).apply {
            setPackage(PACKAGE_NAME)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_AUTO_START, autoStart)
            putExtra(EXTRA_AUTO_SIGN, autoSign)
            if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the Universal Anti-Split download / releases page on GitHub.
     */
    fun openDownloadPage(context: Context) {
        context.openUriSafely(GITHUB_RELEASES_URL)
    }

    data class MergeResult(
        val outputUri: Uri?,
        val outputPath: String?,
        val packageName: String?,
        val isSuccess: Boolean,
        val errorMessage: String? = null,
    )

    /**
     * Parses the result intent returned from Universal Anti-Split.
     */
    fun parseResult(resultCode: Int, data: Intent?): MergeResult? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val outputUri: Uri? = data.data
            ?: data.getStringExtra(EXTRA_OUTPUT_URI)?.let { Uri.parse(it) }
        val outputPath = data.getStringExtra(EXTRA_OUTPUT_PATH)
        val packageName = data.getStringExtra(EXTRA_RESULT_PACKAGE)
        val isSuccess = data.getBooleanExtra(EXTRA_SUCCESS, outputUri != null || outputPath != null)
        val errorMessage = data.getStringExtra(EXTRA_ERROR_MESSAGE)

        return MergeResult(
            outputUri = outputUri,
            outputPath = outputPath,
            packageName = packageName,
            isSuccess = isSuccess,
            errorMessage = errorMessage,
        )
    }
}
