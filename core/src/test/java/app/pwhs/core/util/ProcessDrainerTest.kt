package app.pwhs.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

class ProcessDrainerTest {

    @Test
    fun `small output is captured in full`() {
        val text = "Success: created install session [1234567]\n"
        val handle = ProcessDrainer.start(ByteArrayInputStream(text.toByteArray()))

        assertEquals(text, handle.awaitText())
    }

    @Test
    fun `output past the cap keeps the tail, where pm prints its verdict`() {
        val payload = ("abcdefghij".repeat(1_000)).toByteArray() // 10_000 chars
        val handle = ProcessDrainer.start(ByteArrayInputStream(payload), maxChars = 100)

        val text = handle.awaitText()

        assertEquals(100, text.length)
        assertEquals("abcdefghij".repeat(10), text)
    }

    @Test
    fun `an empty stream yields an empty string`() {
        val handle = ProcessDrainer.start(ByteArrayInputStream(ByteArray(0)))

        assertEquals("", handle.awaitText())
    }

    @Test
    fun `cancel returns without hanging`() {
        val handle = ProcessDrainer.start(ByteArrayInputStream("x".repeat(10).toByteArray()))
        handle.cancel()

        // Must return rather than block forever; content is not asserted after a cancel.
        handle.awaitText(timeoutMillis = 2_000L)
    }

    /**
     * The regression this whole class exists for: a child that writes far more than the ~64 KB
     * pipe buffer. Reading only after waitFor() deadlocks here; concurrent draining does not.
     */
    @Test
    fun `a child writing more than the pipe buffer does not deadlock`() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").canExecute())

        val payloadChars = 512 * 1024
        val process = ProcessBuilder(
            "/bin/sh", "-c",
            "head -c $payloadChars /dev/zero | tr '\\000' 'x'",
        ).redirectErrorStream(true).start()

        val handle = ProcessDrainer.start(process.inputStream, maxChars = payloadChars)
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        val text = handle.awaitText(timeoutMillis = 30_000L)

        assertTrue("the child must exit instead of blocking on a full pipe", finished)
        assertEquals(0, process.exitValue())
        assertEquals(payloadChars, text.length)
        assertTrue("drained bytes must be the child's output, not gaps", text.all { it == 'x' })
    }
}
