package app.pwhs.core.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun `an ordinary package name passes through`() {
        assertEquals("app-release.apk", FileNameSanitizer.sanitize("app-release.apk"))
        assertEquals("split_config.arm64_v8a.apk", FileNameSanitizer.sanitize("split_config.arm64_v8a.apk"))
    }

    @Test
    fun `path separators are reduced to a bare file name`() {
        assertEquals("passwd", FileNameSanitizer.sanitize("../../../../etc/passwd"))
        assertEquals("evil.apk", FileNameSanitizer.sanitize("/sdcard/Download/evil.apk"))
        assertEquals("evil.apk", FileNameSanitizer.sanitize("C:\\Windows\\Temp\\evil.apk"))
    }

    @Test
    fun `a traversal attempt can never escape the staging directory`() {
        val name = FileNameSanitizer.sanitize("../../../../../tmp/evil.apk")

        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
        assertFalse(name.startsWith(".."))
        assertEquals("evil.apk", name)
    }

    @Test
    fun `dots only names fall back instead of resolving to a directory`() {
        assertEquals("received.apk", FileNameSanitizer.sanitize(".."))
        assertEquals("received.apk", FileNameSanitizer.sanitize("."))
        assertEquals("received.apk", FileNameSanitizer.sanitize("..."))
    }

    @Test
    fun `blank and null input fall back`() {
        assertEquals("received.apk", FileNameSanitizer.sanitize(null))
        assertEquals("received.apk", FileNameSanitizer.sanitize(""))
        assertEquals("received.apk", FileNameSanitizer.sanitize("   "))
        assertEquals("custom.apk", FileNameSanitizer.sanitize("", "custom.apk"))
    }

    @Test
    fun `shell and html metacharacters are replaced`() {
        val name = FileNameSanitizer.sanitize("<img src=x onerror=alert(1)>.apk")

        assertFalse(name.contains("<"))
        assertFalse(name.contains(">"))
        assertFalse(name.contains("\""))
        assertFalse(name.contains("'"))
        assertTrue(name.endsWith(".apk"))
    }

    @Test
    fun `control characters are replaced`() {
        val name = FileNameSanitizer.sanitize("a\r\nb\u0000c.apk")

        assertFalse(name.any { it.isISOControl() })
    }

    @Test
    fun `names are capped`() {
        val long = "a".repeat(500) + ".apk"

        val name = FileNameSanitizer.sanitize(long)

        assertEquals(FileNameSanitizer.MAX_LENGTH, name.length)
    }

    @Test
    fun `unicode letters and digits survive`() {
        // isLetterOrDigit() is Unicode-aware, so localized package names stay readable.
        assertEquals("アプリ_v1.apk", FileNameSanitizer.sanitize("アプリ_v1.apk"))
    }
}
