package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import timber.log.Timber

/** Recovery runs only after explicit default-handler opt-in; it cannot undo a true force-stop. */
object InstallerRecovery {
    private val mutex = Mutex()

    fun register(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        fun recover() { scope.launch(Dispatchers.IO) { restore(app) } }
        Shizuku.addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener { recover() })
        Shizuku.addRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) recover()
        })
    }

    private suspend fun restore(context: Context) = mutex.withLock {
        try {
            if (!ShizukuReadiness.await(context)) return@withLock
            val prefs = context.dataStore.data.first()
            if (prefs[PreferencesKeys.PREFERRED_DEFAULT_INSTALLER] == true ||
                prefs[PreferencesKeys.PREFERRED_DEFAULT_UNINSTALLER] == true) {
                BatteryOptimizationHelper.allowBackground(context)
            }
            if (prefs[PreferencesKeys.PREFERRED_DEFAULT_INSTALLER] == true) {
                ShizukuDefaultInstaller.setDefaultInstaller(ComponentName(context,
                    "app.pwhs.universalinstaller.presentation.install.DialogInstallActivity"), true)
                    .onFailure { Timber.w(it, "Could not restore installer preference") }
            }
            if (prefs[PreferencesKeys.PREFERRED_DEFAULT_UNINSTALLER] == true) {
                ShizukuDefaultInstaller.setDefaultUninstaller(ComponentName(context,
                    "app.pwhs.universalinstaller.presentation.manage.uninstall.DialogUninstallActivity"), true)
                    .onFailure { Timber.w(it, "Could not restore uninstaller preference") }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Default handler recovery failed")
        }
    }
}
