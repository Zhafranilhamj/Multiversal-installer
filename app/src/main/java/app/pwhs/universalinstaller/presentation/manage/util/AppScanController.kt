/*
 * (c) 2026 Muhammed Faizul Amali
 * All rights reserved.
 */

package app.pwhs.universalinstaller.presentation.manage.util

import android.content.Context
import app.pwhs.core.network.VirusDetecService
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.manage.ScanUiResult
import app.pwhs.universalinstaller.util.AnalyticsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Runs an install-time-style security scan for an app that is already installed.
 *
 * The previous implementation handed the user off to a browser, so the app never learned the
 * verdict. This keeps the whole round trip in-process and publishes it as [state] for
 * `AppScanResultSheet` to render.
 */
internal class AppScanController(
    private val scope: CoroutineScope,
    private val virusDetecService: VirusDetecService,
) {

    private val _state = MutableStateFlow<ScanUiResult?>(null)

    val state: StateFlow<ScanUiResult?> = _state

    fun scan(context: Context, app: InstalledApp) {
        AnalyticsHelper.logAppManagementAction("scan_virustotal")
        _state.value = ScanUiResult(app.appName, app.packageName, isScanning = true)
        scope.launch {
            // ManageExtractHelper never throws: every outcome, "no verdict" and network failures
            // included, comes back as a VtResult, so the sheet never closes onto nothing.
            val result = ManageExtractHelper.scanVirusTotal(context, app, virusDetecService)
            _state.value =
                ScanUiResult(app.appName, app.packageName, isScanning = false, result = result)
        }
    }

    fun dismiss() {
        _state.value = null
    }
}
