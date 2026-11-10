package app.pwhs.core.receiver

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream

/**
 * Streaming reader for the `multipart/form-data` bodies the LAN receiver accepts.
 *
 * Bytes go straight from the socket to disk — no in-memory copy of the APK — while [onProgress]
 * reports how much has landed, so the TV can draw a live transfer bar.
 *
 * ## Why this exists
 * The parser this replaced consumed the body to EOF and stripped the closing boundary from a small
 * rolling tail buffer *at the end of the stream*. That silently assumed the APK was the last field
 * in the request. HTTP clients make no such guarantee: with anything after the file part — another
 * field, a CSRF token, a second file — those trailing bytes were written into the APK. The damage
 * only shows up much later, as a package the installer cannot parse, with nothing pointing back at
 * the transfer.
 *
 * This version stops at the first occurrence of the part boundary wherever it appears in the
 * stream, skips any plain form fields that come *before* the file, and reports what it saw so a
 * malformed body is an explicit 400 instead of a corrupt install.
 *
 * Android-free so it can be unit-tested on the JVM against real multipart bodies.
 */
object MultipartStreamReader {

    sealed interface Outcome {
        /**
         * @param file the staged part
         * @param bytesWritten size of the extracted part, boundary excluded
         * @param skippedParts plain form fields discarded before the file part
         * @param trailingParts parts that followed the file part — also discarded, never appended
         */
        data class Stored(
            val file: File,
            val fileName: String,
            val bytesWritten: Long,
            val skippedParts: Int,
            val trailingParts: Int,
        ) : Outcome

        /** The body was malformed or cut short. Nothing was staged. */
        data class Malformed(val reason: String) : Outcome
    }

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_HEADER_BYTES = 8192L
    /** A body with more parts than this in front of the file is not an upload we recognise. */
    private const val MAX_LEADING_PARTS = 16
    private const val CR = '\r'.code
    private const val LF = '\n'.code

    /**
     * Reads the file part out of [input], which must be positioned at the very start of the
     * request body.
     *
     * @param boundary the `boundary=` parameter from the `Content-Type` header
     * @param contentLength declared body length; `<= 0` means unknown and reads to EOF
     * @param onProgress invoked with `(bytesWritten, expectedTotal)` at most every
     *   [progressIntervalMillis], plus once at the end
     */
    fun readSingleFile(
        input: InputStream,
        boundary: String,
        contentLength: Long,
        destDir: File,
        fallbackFileName: String = "received-${System.currentTimeMillis()}.apk",
        onProgress: (bytesWritten: Long, expectedTotal: Long) -> Unit = { _, _ -> },
        progressIntervalMillis: Long = 50L,
        clock: () -> Long = System::currentTimeMillis,
    ): Outcome {
        val marker = "\r\n--$boundary".toByteArray(Charsets.US_ASCII)
        // Finding a boundary means reading further than it, so the reader has to be able to hand
        // bytes back. Without this, one 64 KB read swallows the whole (small) body: the parts after
        // the file are lost and the parts before it take the file part with them.
        val stream = PushbackInputStream(input, BUFFER_SIZE + marker.size)
        var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE

        var fileName: String? = null
        var skippedParts = 0
        var headerBytes = 0L

        // Walk forward to the part that actually carries a file.
        while (remaining > 0 && fileName == null) {
            val headers = readPartHeaders(stream, remaining)
            remaining -= headers.bytesConsumed
            headerBytes += headers.bytesConsumed
            when {
                headers.bytesConsumed == 0L ->
                    return Outcome.Malformed("Request body ended before any part")
                headers.looksLikeEpilogue ->
                    return Outcome.Malformed("Request body contained no file")
                else -> {
                    val name = parseFileName(headers.text)
                    if (name != null) {
                        fileName = FileNameSanitizer.sanitize(name, fallbackFileName)
                    } else {
                        // A plain form field ahead of the file: discard its body.
                        val skipped = transferToBoundary(stream, marker, remaining, sink = null)
                        remaining -= skipped.bytesConsumed
                        skippedParts++
                        if (!skipped.terminated) {
                            return Outcome.Malformed("Request body ended mid-field")
                        }
                        if (skippedParts >= MAX_LEADING_PARTS) {
                            return Outcome.Malformed("Too many form fields before the file")
                        }
                    }
                }
            }
        }

        val resolvedName = fileName ?: return Outcome.Malformed("Request body contained no file")
        val destFile = File(destDir, resolvedName)
        val tempFile = File(destDir, "$resolvedName.tmp")
        val expectedTotal = expectedFileLength(contentLength, headerBytes, marker.size)

        val streamed = FileOutputStream(tempFile).use { fos ->
            transferToBoundary(
                input = stream,
                marker = marker,
                startRemaining = remaining,
                sink = fos,
                expectedTotal = expectedTotal,
                onProgress = onProgress,
                progressIntervalMillis = progressIntervalMillis,
                clock = clock,
            )
        }
        remaining -= streamed.bytesConsumed

        if (!streamed.terminated) {
            tempFile.delete()
            return Outcome.Malformed(
                "Upload ended after ${streamed.bytesWritten} bytes without closing the file part"
            )
        }

        onProgress(streamed.bytesWritten, expectedTotal.coerceAtLeast(streamed.bytesWritten))

        // Consume the rest of the body so a keep-alive connection stays byte-aligned, and count
        // what was in it — those parts are dropped, not appended to the APK.
        val trailingParts = drainAndCount(stream, remaining, marker)

        moveTo(tempFile, destFile)

        return Outcome.Stored(
            file = destFile,
            fileName = destFile.name,
            bytesWritten = streamed.bytesWritten,
            skippedParts = skippedParts,
            trailingParts = trailingParts,
        )
    }

    // ── Internals ──────────────────────────────────────────────────────

    /**
     * @param bytesConsumed bytes removed from the stream; excludes anything pushed back
     * @param bytesWritten bytes of part content handed to the sink
     */
    private class Transfer(val bytesConsumed: Long, val bytesWritten: Long, val terminated: Boolean)

    /**
     * Copies bytes to [sink] (or discards them when null) until [marker] is found or the body runs
     * out. The last `marker.size - 1` bytes are always held back, so a marker split across two
     * socket reads is still recognised.
     *
     * Bytes read past the marker are pushed back onto [input], which must be a
     * [PushbackInputStream] with room for a whole read buffer.
     */
    private fun transferToBoundary(
        input: PushbackInputStream,
        marker: ByteArray,
        startRemaining: Long,
        sink: OutputStream?,
        expectedTotal: Long = 0L,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        progressIntervalMillis: Long = 50L,
        clock: () -> Long = System::currentTimeMillis,
    ): Transfer {
        val holdBack = marker.size - 1
        val pending = ByteArray(holdBack)
        var pendingLen = 0
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = startRemaining
        var bytesRead = 0L
        var bytesWritten = 0L
        var pushedBack = 0L
        var terminated = false
        var lastProgress = 0L

        while (remaining > 0 && !terminated) {
            val read = input.read(buffer, 0, minOf(BUFFER_SIZE.toLong(), remaining).toInt())
            if (read == -1) break
            remaining -= read
            bytesRead += read

            val window = if (pendingLen == 0) {
                buffer.copyOf(read)
            } else {
                ByteArray(pendingLen + read).also {
                    System.arraycopy(pending, 0, it, 0, pendingLen)
                    System.arraycopy(buffer, 0, it, pendingLen, read)
                }
            }

            val markerIndex = indexOf(window, marker)
            val flushLength = if (markerIndex >= 0) {
                markerIndex
            } else {
                (window.size - holdBack).coerceAtLeast(0)
            }
            if (flushLength > 0) {
                sink?.write(window, 0, flushLength)
                bytesWritten += flushLength
            }
            if (markerIndex >= 0) {
                terminated = true
                // Everything after the marker belongs to the next part.
                val overshoot = window.size - (markerIndex + marker.size)
                if (overshoot > 0) {
                    input.unread(window, markerIndex + marker.size, overshoot)
                    pushedBack += overshoot
                }
            } else {
                pendingLen = window.size - flushLength
                System.arraycopy(window, flushLength, pending, 0, pendingLen)
            }

            val now = clock()
            if (now - lastProgress >= progressIntervalMillis) {
                lastProgress = now
                onProgress(bytesWritten, expectedTotal)
            }
        }

        sink?.flush()
        return Transfer(bytesRead - pushedBack, bytesWritten, terminated)
    }

    /**
     * Consumes whatever follows the file part and counts the parts in it. Nothing is retained — a
     * trailing part could be another whole APK.
     */
    private fun drainAndCount(input: PushbackInputStream, remaining: Long, marker: ByteArray): Int {
        if (remaining <= 0) return 0
        val buffer = ByteArray(BUFFER_SIZE)
        var left = remaining
        var count = 0
        // A marker can straddle two reads, so the last marker.size - 1 bytes are carried over.
        // Shorter than a marker, so a fully-consumed one can never be counted twice.
        var carry = ByteArray(0)
        while (left > 0) {
            val read = input.read(buffer, 0, minOf(BUFFER_SIZE.toLong(), left).toInt())
            if (read == -1) break
            left -= read
            val window = if (carry.isEmpty()) buffer.copyOf(read) else carry + buffer.copyOf(read)
            count += countOccurrences(window, marker)
            val keep = (marker.size - 1).coerceAtMost(window.size)
            carry = window.copyOfRange(window.size - keep, window.size)
        }
        return count
    }

    private fun countOccurrences(haystack: ByteArray, needle: ByteArray): Int {
        var count = 0
        var from = 0
        while (true) {
            val index = indexOf(haystack, needle, from)
            if (index < 0) return count
            count++
            from = index + needle.size
        }
    }

    /** First index of [needle] in [haystack] at or after [from], or -1. */
    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || haystack.size - from < needle.size) return -1
        outer@ for (i in from..haystack.size - needle.size) {
            if (haystack[i] != needle[0]) continue
            for (j in 1 until needle.size) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private class PartHeaders(
        val text: String,
        val bytesConsumed: Long,
        val looksLikeEpilogue: Boolean,
    )

    /** Reads MIME part headers, stopping at the blank line that separates them from the body. */
    private fun readPartHeaders(input: PushbackInputStream, remaining: Long): PartHeaders {
        if (remaining <= 0) return PartHeaders("", 0L, false)
        val buffer = ByteArrayOutputStream()
        var prev3 = -1
        var prev2 = -1
        var prev1 = -1
        var consumed = 0L
        while (consumed < remaining && consumed < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b == -1) break
            consumed++
            buffer.write(b)
            if (prev3 == CR && prev2 == LF && prev1 == CR && b == LF) break
            if (prev1 == LF && b == LF) break
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        val text = buffer.toString("UTF-8")
        // Two shapes reach this reader: the very first block ("--boundary\r\nHeaders…\r\n\r\n")
        // and the block after a part's terminator ("\r\nHeaders…\r\n\r\n", or just "--\r\n"
        // when the body is over). The closing "--boundary--" therefore shows up as a block with no
        // headers at all, and MIME headers always contain a colon.
        val looksLikeEpilogue = consumed > 0 && !text.contains(':') && text.trim().startsWith("--")
        return PartHeaders(text, consumed, looksLikeEpilogue)
    }

    /** Extracts `filename="…"`, tolerating the unquoted form some clients emit. */
    internal fun parseFileName(headerText: String): String? {
        val match = Regex("""filename="([^"]+)"""").find(headerText)
            ?: Regex("""filename=([^;\r\n]+)""").find(headerText)
            ?: return null
        return match.groupValues[1].trim().ifBlank { null }
    }

    /**
     * Best-effort total for the progress bar: declared body length minus part headers and the
     * trailing boundary. Display only, so an approximation is fine.
     */
    private fun expectedFileLength(contentLength: Long, headerBytes: Long, markerBytes: Int): Long {
        if (contentLength <= 0) return 1L
        return (contentLength - headerBytes - markerBytes - 4).coerceAtLeast(1L)
    }

    private fun moveTo(tempFile: File, destFile: File) {
        if (destFile.exists() && !destFile.delete()) {
            // Could not remove the previous copy (an open handle on some OEM filesystems) —
            // overwrite it in place rather than losing the upload.
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        } else if (!tempFile.renameTo(destFile)) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }
    }
}
