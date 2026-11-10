package app.pwhs.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordKdfTest {

    @Test
    fun `derivation is deterministic for the same password and salt`() {
        val salt = "0123456789abcdef".toByteArray()

        val first = PasswordKdf.deriveHex("hunter2", salt, iterations = 50_000)
        val second = PasswordKdf.deriveHex("hunter2", salt, iterations = 50_000)

        assertEquals(first, second)
    }

    @Test
    fun `a different salt yields a different key`() {
        val a = PasswordKdf.deriveHex("1234", "aaaaaaaaaaaaaaaa".toByteArray(), 50_000)
        val b = PasswordKdf.deriveHex("1234", "bbbbbbbbbbbbbbbb".toByteArray(), 50_000)

        assertNotEquals(a, b)
    }

    @Test
    fun `output length follows the requested key length`() {
        val bytes = PasswordKdf.deriveBytes("pw", ByteArray(16), 50_000, keyLengthBits = 256)
        assertEquals(32, bytes.size)

        val hex = PasswordKdf.deriveHex("pw", ByteArray(16), 50_000, keyLengthBits = 128)
        assertEquals(32, hex.length)
    }

    @Test
    fun `iteration counts are clamped into the accepted window`() {
        assertEquals(
            PasswordKdf.MIN_ITERATIONS,
            PasswordKdf.clampIterations(1),
        )
        assertEquals(
            PasswordKdf.MIN_ITERATIONS,
            PasswordKdf.clampIterations(Int.MIN_VALUE),
        )
        assertEquals(
            PasswordKdf.MAX_ITERATIONS,
            PasswordKdf.clampIterations(Int.MAX_VALUE),
        )
        assertEquals(
            PasswordKdf.DEFAULT_ITERATIONS,
            PasswordKdf.clampIterations(PasswordKdf.DEFAULT_ITERATIONS),
        )
    }

    @Test
    fun `an absurd iteration count from a hostile file derives the clamped key, not a hang`() {
        val salt = ByteArray(16) { it.toByte() }
        val started = System.nanoTime()

        val hostile = PasswordKdf.deriveHex("pw", salt, Int.MAX_VALUE, keyLengthBits = 128)
        val clamped = PasswordKdf.deriveHex("pw", salt, PasswordKdf.MAX_ITERATIONS, keyLengthBits = 128)

        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertEquals(hostile, clamped)
        assertTrue("clamped derivation must stay cheap, took ${elapsedMillis}ms", elapsedMillis < 20_000)
    }

    @Test
    fun `hex round trips`() {
        val bytes = byteArrayOf(0, 1, 15, 16, 127, -128, -1)
        val hex = PasswordKdf.bytesToHex(bytes)

        assertEquals("00010f107f80ff", hex)
        assertArrayEquals(bytes, PasswordKdf.hexToBytes(hex))
    }

    @Test
    fun `malformed hex yields an empty array instead of throwing`() {
        assertEquals(0, PasswordKdf.hexToBytes("abc").size)
        assertEquals(0, PasswordKdf.hexToBytes("zz").size)
        assertEquals(0, PasswordKdf.hexToBytes("").size)
    }
}
