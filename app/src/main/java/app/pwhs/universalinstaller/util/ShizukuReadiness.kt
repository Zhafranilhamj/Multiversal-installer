package app.pwhs.universalinstaller.util

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

object ShizukuReadiness {
    /** Bounded, cancellable wait for asynchronous provider delivery, never blocks the UI. */
    suspend fun await(context: Context): Boolean = withTimeoutOrNull(2000L) {
        while (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) delay(50)
        !Shizuku.isPreV11() && ShizukuServices.hasShizukuPermission(context)
    } ?: false
}
