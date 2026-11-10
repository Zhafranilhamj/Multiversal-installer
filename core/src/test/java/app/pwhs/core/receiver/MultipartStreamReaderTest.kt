package app.pwhs.core.receiver

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class MultipartStreamReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val boundary = "----uitvboundary123"

    // ── Body builders ──────────────────────────────────────────────────

    private fun filePart(fileName: String, content: ByteArray): ByteArray =
        ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"apk\"; filename=\"$fileName\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "\r\n").toByteArray() + content + "\r\n".toByteArray()

    private fun textPart(name: String, value: String): ByteArray =
        ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"$name\"\r\n" +
            "\r\n").toByteArray() + value.toByteArray() + "\r\n".toByteArray()

    private fun body(vararg parts: ByteArray): ByteArray =
        parts.fold(ByteArray(0)) { acc, part -> acc + part } + "--$boundary--\r\n".toByteArray()

    private fun parse(
        bodyBytes: ByteArray,
        contentLength: Long = bodyBytes.size.toLong(),
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): MultipartStreamReader.Outcome = MultipartStreamReader.readSingleFile(
        input = ByteArrayInputStream(bodyBytes),
        boundary = boundary,
        contentLength = contentLength,
        destDir = folder.root,
        onProgress = onProgress,
        progressIntervalMillis = 0L,
    )

    private fun stored(outcome: MultipartStreamReader.Outcome): MultipartStreamReader.Outcome.Stored {
        assertTrue("expected a stored file but got $outcome", outcome is MultipartStreamReader.Outcome.Stored)
        return outcome as MultipartStreamReader.Outcome.Stored
    }

    private fun malformed(outcome: MultipartStreamReader.Outcome): MultipartStreamReader.Outcome.Malformed {
        assertTrue("expected a malformed body but got $outcome", outcome is MultipartStreamReader.Outcome.Malformed)
        return outcome as MultipartStreamReader.Outcome.Malformed
    }

    /** Deterministic pseudo-random bytes; 251 is prime so the ASCII boundary can never appear. */
    private fun filler(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    // ── The happy path ─────────────────────────────────────────────────

    @Test
    fun `a lone file part is stored byte for byte`() {
        val content = filler(1024)

        val result = stored(parse(body(filePart("base.apk", content))))

        assertEquals("base.apk", result.fileName)
        assertEquals(1024L, result.bytesWritten)
        assertEquals(0, result.skippedParts)
        assertEquals(0, result.trailingParts)
        assertArrayEquals(content, result.file.readBytes())
    }

    @Test
    fun `a large part spanning many read buffers is stored byte for byte`() {
        val content = filler(300_000)

        val result = stored(parse(body(filePart("big.apk", content))))

        assertArrayEquals(content, result.file.readBytes())
        assertEquals(0, result.trailingParts)
    }

    @Test
    fun `an unknown content length still parses to the closing boundary`() {
        val content = filler(4096)

        val result = stored(parse(body(filePart("base.apk", content)), contentLength = 0L))

        assertArrayEquals(content, result.file.readBytes())
    }

    // ── The regression this parser exists for ──────────────────────────

    @Test
    fun `a trailing text field is not appended to the apk`() {
        val content = filler(2048)

        val result = stored(
            parse(body(filePart("base.apk", content), textPart("note", "please install this")))
        )

        assertArrayEquals(
            "the trailing field leaked into the file — the package would be unparseable",
            content,
            result.file.readBytes(),
        )
        assertEquals(2048L, result.bytesWritten)
        assertEquals(1, result.trailingParts)
    }

    @Test
    fun `a second file part is not appended to the first`() {
        val first = filler(1024)
        val second = filler(2048)

        val result = stored(
            parse(body(filePart("base.apk", first), filePart("split_config.arm64.apk", second)))
        )

        assertEquals("base.apk", result.fileName)
        assertArrayEquals(first, result.file.readBytes())
        assertEquals(1, result.trailingParts)
    }

    @Test
    fun `several trailing parts are all discarded and counted`() {
        val content = filler(512)

        val result = stored(
            parse(
                body(
                    filePart("base.apk", content),
                    textPart("a", "1"),
                    textPart("b", "2"),
                    textPart("c", "3"),
                )
            )
        )

        assertArrayEquals(content, result.file.readBytes())
        assertEquals(3, result.trailingParts)
    }

    @Test
    fun `a plain field before the file is skipped, not written as the apk`() {
        val content = filler(1024)

        val result = stored(
            parse(body(textPart("csrf", "abc123"), filePart("base.apk", content)))
        )

        assertEquals(1, result.skippedParts)
        assertEquals("base.apk", result.fileName)
        assertArrayEquals(content, result.file.readBytes())
        assertEquals(0, result.trailingParts)
    }

    // ── Boundary handling ──────────────────────────────────────────────

    @Test
    fun `content that almost contains the boundary is not truncated`() {
        val nearMiss = ("\r\n--" + boundary.dropLast(3)).toByteArray()
        val content = filler(64) + nearMiss + filler(64)

        val result = stored(parse(body(filePart("base.apk", content))))

        assertArrayEquals(content, result.file.readBytes())
    }

    @Test
    fun `content ending in a carriage return keeps it`() {
        val content = filler(100) + "\r".toByteArray()

        val result = stored(parse(body(filePart("base.apk", content))))

        assertArrayEquals(content, result.file.readBytes())
    }

    // ── Malformed bodies ───────────────────────────────────────────────

    @Test
    fun `a truncated upload stages nothing`() {
        val partial = ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"apk\"; filename=\"base.apk\"\r\n" +
            "\r\n").toByteArray() + filler(500)

        val outcome = malformed(parse(partial))

        assertTrue(outcome.reason.isNotBlank())
        assertEquals(
            "a partial file must never be handed to the installer",
            0,
            folder.root.listFiles()?.size ?: 0,
        )
    }

    @Test
    fun `a body with no file part is rejected`() {
        val outcome = malformed(parse(body(textPart("note", "hello"))))

        assertTrue(outcome.reason.contains("no file"))
    }

    @Test
    fun `an empty body is rejected`() {
        val outcome = malformed(parse(ByteArray(0), contentLength = 0L))

        assertTrue(outcome.reason.isNotBlank())
    }

    // ── File naming ────────────────────────────────────────────────────

    @Test
    fun `a traversal filename is neutralised`() {
        val result = stored(parse(body(filePart("../../../../etc/passwd", filler(16)))))

        assertTrue("staged outside the destination: ${result.file}", result.file.parentFile == folder.root)
        assertTrue(result.file.name.none { it == '/' || it == '\\' })
    }

    @Test
    fun `a filename that sanitises to nothing falls back to a generated name`() {
        val result = stored(parse(body(filePart("..", filler(32)))))

        assertEquals("received.apk", result.fileName)
        assertArrayEquals(filler(32), result.file.readBytes())
    }

    @Test
    fun `a part with no filename at all is a form field, not a file`() {
        val bodyBytes = ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"apk\"\r\n" +
            "\r\n").toByteArray() + filler(32) + "\r\n--$boundary--\r\n".toByteArray()

        val outcome = malformed(parse(bodyBytes))

        assertTrue(outcome.reason.contains("no file"))
        assertEquals(0, folder.root.listFiles()?.size ?: 0)
    }

    // ── Progress reporting ─────────────────────────────────────────────

    @Test
    fun `progress rises to the full file size`() {
        val content = filler(200_000)
        val seen = mutableListOf<Pair<Long, Long>>()

        parse(body(filePart("base.apk", content))) { written, total -> seen += written to total }

        assertTrue("no progress was reported", seen.isNotEmpty())
        assertTrue(
            "progress went backwards: $seen",
            seen.zipWithNext().all { (prev, next) -> next.first >= prev.first },
        )
        assertEquals(content.size.toLong(), seen.last().first)
        assertTrue("the final total must cover the file", seen.last().second >= content.size.toLong())
    }
}
