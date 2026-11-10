package app.pwhs.core.util

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Minimal root shell that spawns `su -c "<cmd>"` per command — no libsu dependency, so `:core`
 * stays lean and the mobile `store` flavor (which also consumes `:core`) remains libsu-free.
 *
 * Many Android TV boxes ship rooted, where committing a `pm install` session straight from the
 * uid-0 shell installs **silently** — no system confirmation dialog, which is exactly the
 * D-pad-hostile step we want to skip on a 10-foot UI.
 *
 * stderr is folded into stdout ([ProcessBuilder.redirectErrorStream]) because `pm` prints its
 * `Failure [...]` diagnostics to stdout anyway and merging avoids the two-pipe read deadlock.
 *
 * Output is drained by [ProcessDrainer] on a daemon thread *while* the command runs. Reading it
 * only after [Process.waitFor] deadlocks once the child writes more than the ~64 KB pipe buffer:
 * the child blocks in `write()`, waitFor never returns, and a working command gets killed and
 * reported as a timeout. `pm` on chatty OEM builds can produce that much.
 */
object RootShell {

    data class Result(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    // Cached so the availability probe (which may pop the Magisk grant prompt) runs once.
    @Volatile
    private var cachedAvailable: Boolean? = null

    /** True if a `su` binary exists and yields a uid-0 shell. Result is cached after first probe. */
    suspend fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        return withContext(Dispatchers.IO) {
            val available = runCatching {
                val r = exec("id", timeoutSeconds = 10)
                r.isSuccess && r.output.contains("uid=0")
            }.getOrDefault(false)
            cachedAvailable = available
            available
        }
    }

    /** Forces the next [isAvailable] call to re-probe (e.g. after the user grants root). */
    fun invalidate() {
        cachedAvailable = null
    }

    /**
     * Runs [cmd] in a root shell and returns its exit code + combined output. [timeoutSeconds]
     * guards against vendor `pm`/`su` hangs — on timeout the process is force-killed and a
     * non-zero result with a diagnostic message is returned.
     */
    suspend fun exec(cmd: String, timeoutSeconds: Long = 120): Result = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("su", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        // Started before waitFor on purpose — see the class doc.
        val drainer = ProcessDrainer.start(process.inputStream)

        val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        } else {
            val exit = try {
                withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                    runInterruptible { process.waitFor() }
                }
            } catch (_: InterruptedException) {
                null
            }
            exit != null
        }
        if (!finished) {
            destroyProcess(process)
            drainer.cancel()
            return@withContext Result(-1, "Command timed out after ${timeoutSeconds}s: $cmd")
        }
        // The child has exited, so its stdout is at EOF and the drainer returns promptly; the
        // timeout only covers a wedged read.
        val output = drainer.awaitText()
        Result(process.exitValue(), output)
    }

    private fun destroyProcess(process: Process) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly()
            } else {
                process.destroy()
            }
        }
    }
}
