package app.pwhs.universalinstaller.util

import android.content.Context
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuShellExecutor
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.flow.first
import timber.log.Timber

object BatteryOptimizationHelper {
    /** Only default-handler opt-in uses this automatically. The settings switch can opt out. */
    suspend fun allowBackground(context: Context, root: Boolean = false) {
        if (context.dataStore.data.first()[PreferencesKeys.AUTO_BACKGROUND_EXEMPTION] == false) return
        val pkg = context.packageName
        require(pkg.matches(Regex("[A-Za-z0-9._]+")))
        if (root) {
            for (command in listOf("cmd deviceidle whitelist +$pkg",
                "appops set $pkg RUN_IN_BACKGROUND allow", "appops set $pkg RUN_ANY_IN_BACKGROUND allow")) {
                runCatching { RootShell.exec(command, timeoutSeconds = 10) }
                    .onFailure { Timber.w(it, "Background exemption failed") }
                    .onSuccess { if (!it.isSuccess) Timber.w("Background exemption command rejected") }
            }
        } else if (ShizukuShellExecutor.isReady(context)) {
            ShizukuShellExecutor.whitelistBackground(pkg)
                .onFailure { Timber.w(it, "Battery exemption rejected; use Android battery settings") }
            for (op in listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND")) {
                ShizukuShellExecutor.setAppOp(pkg, op, "allow")
                    .onFailure { Timber.w(it, "Background AppOp rejected") }
            }
        }
    }
}
