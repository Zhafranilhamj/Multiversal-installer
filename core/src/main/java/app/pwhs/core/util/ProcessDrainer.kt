package app.pwhs.core.util

import java.io.InputStream

/**
 * Reads a child process's output on its own daemon thread while the caller waits for the process
 * to exit.
 *
 * ## Why not just read after `waitFor()`
 * The pipe between a process and its parent holds about 64 KB on Linux. Read only after
 * `waitFor()` and a child that writes more than that blocks inside `write()`, `waitFor()` never
 * returns, and the caller kills a perfectly healthy command and reports a timeout. `pm` on chatty
 * OEM builds — and anything that dumps a package list or an install log — can cross that line.
 *
 * Output is capped at [DEFAULT_MAX_CHARS] with the *tail* kept, because `pm` prints the verdict
 * that matters ("Success: created install session [123]", "Failure [INSTALL_FAILED_…]") last.
 *
 * Android-free so it can be unit-tested on the JVM.
 */
object ProcessDrainer {

    /** Past this many characters only the tail is kept. */
    const val DEFAULT_MAX_CHARS = 256 * 1024

    /** How long [Handle.awaitText] waits for EOF before returning whatever arrived. */
    const val DEFAULT_TIMEOUT_MILLIS = 10_000L

    private const val CHUNK_CHARS = 8192

    /** A running drain. Obtain one from [start], then call [awaitText] once the process exits. */
    class Handle internal constructor(
        private val thread: Thread,
        private val buffer: StringBuilder,
    ) {
        /** Blocks until the stream reaches EOF or [timeoutMillis] elapses, then returns the text. */
        fun awaitText(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String {
            runCatching { thread.join(timeoutMillis) }
            return synchronized(buffer) { buffer.toString() }
        }

        /** Stops the drain early; used when the process is killed for timing out. */
        fun cancel() {
            thread.interrupt()
        }
    }

    /** Starts draining [stream] immediately. The caller owns the process lifecycle. */
    fun start(stream: InputStream, maxChars: Int = DEFAULT_MAX_CHARS): Handle {
        val buffer = StringBuilder()
        val thread = Thread({
            runCatching {
                val reader = stream.bufferedReader()
                val chunk = CharArray(CHUNK_CHARS)
                while (true) {
                    val read = reader.read(chunk)
                    if (read == -1) break
                    synchronized(buffer) {
                        buffer.append(chunk, 0, read)
                        val excess = buffer.length - maxChars
                        if (excess > 0) buffer.delete(0, excess)
                    }
                }
            }
        }, "ProcessDrainer").apply { isDaemon = true }
        thread.start()
        return Handle(thread, buffer)
    }
}
