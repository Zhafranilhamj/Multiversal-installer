package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.scanner.ScanProvider
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import kotlinx.coroutines.launch

@Composable
internal fun ScanPreferences() {
    val context = LocalContext.current
    val prefs by context.dataStore.data.collectAsState(initial = emptyPreferences())
    val scope = rememberCoroutineScope()
    val provider = ScanProvider.from(prefs[PreferencesKeys.SCAN_PROVIDER])
    var key by remember(prefs[PreferencesKeys.VIRUSTOTAL_API_KEY]) {
        mutableStateOf(prefs[PreferencesKeys.VIRUSTOTAL_API_KEY].orEmpty())
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.scan_provider_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.scan_privacy), style = MaterialTheme.typography.bodySmall)
        // The scan button already works without an explicit choice (it defaults to VirusDetec),
        // so the copy says what is happening instead of implying scanning is switched off.
        if (provider == null) Text(stringResource(R.string.scan_default_provider))
        ScanProvider.entries.forEach { choice ->
            TextButton(onClick = { scope.launch {
                context.dataStore.edit { it[PreferencesKeys.SCAN_PROVIDER] = choice.name }
            } }) { Text((if (choice == provider) "✓ " else "") + choice.label) }
        }
        Text(stringResource(if (provider == ScanProvider.VIRUSTOTAL) R.string.scan_vt_info else R.string.scan_vd_info),
            style = MaterialTheme.typography.bodySmall)
        if (provider == ScanProvider.VIRUSTOTAL) {
            OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.scan_api_key)) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { scope.launch {
                context.dataStore.edit { it[PreferencesKeys.VIRUSTOTAL_API_KEY] = key.trim() }
            } }) { Text(stringResource(R.string.scan_save_key)) }
        }
        SecurityLevelSelector(
            SecurityLevel.from(prefs[PreferencesKeys.SECURITY_LEVEL], prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] ?: false),
            onChange = { level -> scope.launch {
                context.dataStore.edit {
                    it[PreferencesKeys.SECURITY_LEVEL] = level.name
                    it[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] = level == SecurityLevel.Strict
                }
            } },
        )
    }
}
