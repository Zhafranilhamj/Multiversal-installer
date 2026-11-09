package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.universalinstaller.R

@Composable
fun BatteryOptimizationPreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun exempt() = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
    var ignored by remember { mutableStateOf(exempt()) }
    LifecycleResumeEffect(Unit) {
        ignored = exempt()
        onPauseOrDispose { }
    }
    SwitchPreference(
        title = stringResource(R.string.setting_ignore_battery_title),
        subtitle = stringResource(R.string.setting_ignore_battery_summary),
        checked = ignored,
        onCheckedChange = { enable ->
            scope.launch {
                context.dataStore.edit { it[PreferencesKeys.AUTO_BACKGROUND_EXEMPTION] = enable }
            }
            val intent = if (enable) Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"))
            else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            runCatching { context.startActivity(intent) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        },
    )
}
