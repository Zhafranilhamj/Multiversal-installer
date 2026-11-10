package app.pwhs.universalinstaller.presentation.setting.security.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lazily migrates a legacy app-lock PIN record to PBKDF2.
 *
 * Records written before [PinCryptoHelper] moved off single-round SHA-256 stay verifiable — never
 * lock a user out of their own app — but they are cheap to brute force offline. The PIN itself is
 * only ever known at the instant it is typed correctly, so that moment is the only chance to
 * re-hash it without making the user choose a new PIN.
 *
 * Every gate in the app (app-open lock, install, uninstall, settings) re-reads the hash from
 * DataStore through a Flow, so the write propagates on its own; nothing has to be invalidated
 * here.
 */
object PinRecordUpgrader {

    /** Own scope: the upgrade is a single write and must survive the caller's composition. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Call right after [PinCryptoHelper.verifyPin] returned true. No-op for records that are
     * already in the current format, so it is safe to call on every successful unlock.
     */
    fun upgradeAfterSuccessfulVerify(context: Context, pin: String, storedHash: String) {
        val upgraded = PinCryptoHelper.upgrade(pin, storedHash) ?: return
        val salt = PinCryptoHelper.saltOf(upgraded)
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                appContext.dataStore.edit { prefs ->
                    prefs[PreferencesKeys.PIN_LOCK_HASH] = upgraded
                    prefs[PreferencesKeys.PIN_LOCK_SALT] = salt
                }
            }
        }
    }
}
