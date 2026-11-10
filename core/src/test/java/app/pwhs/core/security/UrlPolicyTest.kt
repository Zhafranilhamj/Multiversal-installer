package app.pwhs.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun `plain http and https download links are accepted`() {
        assertEquals(
            "https://github.com/owner/repo/releases/download/v1/app-release.apk",
            UrlPolicy.normalize("https://github.com/owner/repo/releases/download/v1/app-release.apk"),
        )
        assertTrue(UrlPolicy.isAllowed("http://192.168.1.10:8080/app.apk"))
        assertTrue(UrlPolicy.isAllowed("https://example.co.uk/path/to/app.apk?query=1#frag"))
    }

    @Test
    fun `surrounding whitespace is trimmed, not rejected`() {
        assertEquals("https://example.com/a.apk", UrlPolicy.normalize("  https://example.com/a.apk\n"))
    }

    @Test
    fun `non http schemes are refused`() {
        assertFalse(UrlPolicy.isAllowed("file:///sdcard/Download/evil.apk"))
        assertFalse(UrlPolicy.isAllowed("content://downloads/1"))
        assertFalse(UrlPolicy.isAllowed("ftp://example.com/a.apk"))
        assertFalse(UrlPolicy.isAllowed("jar:https://example.com/a.jar!/x"))
        assertFalse(UrlPolicy.isAllowed("javascript:alert(1)"))
        assertFalse(UrlPolicy.isAllowed("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun `embedded credentials are refused`() {
        // The host actually contacted is the part after the last '@', which is not what a person
        // reading the link would assume.
        assertFalse(UrlPolicy.isAllowed("https://github.com@evil.example/a.apk"))
        assertFalse(UrlPolicy.isAllowed("https://user:pass@evil.example/a.apk"))
    }

    @Test
    fun `relative and scheme-less addresses are refused`() {
        assertFalse(UrlPolicy.isAllowed("/sdcard/Download/a.apk"))
        assertFalse(UrlPolicy.isAllowed("example.com/a.apk"))
        assertFalse(UrlPolicy.isAllowed("//example.com/a.apk"))
    }

    @Test
    fun `control characters are refused`() {
        assertFalse(UrlPolicy.isAllowed("https://example.com/a.apk\r\nX-Injected: 1"))
        assertFalse(UrlPolicy.isAllowed("https://example.com/\u0000a.apk"))
    }

    @Test
    fun `malformed urls are refused rather than throwing`() {
        assertFalse(UrlPolicy.isAllowed("https://ex ample.com/a.apk"))
        assertFalse(UrlPolicy.isAllowed("https://"))
        assertFalse(UrlPolicy.isAllowed("http://:8080/a.apk"))
    }

    @Test
    fun `blank and null are refused`() {
        assertNull(UrlPolicy.normalize(null))
        assertNull(UrlPolicy.normalize(""))
        assertNull(UrlPolicy.normalize("   "))
    }

    @Test
    fun `scheme matching is case insensitive`() {
        assertTrue(UrlPolicy.isAllowed("HTTPS://example.com/a.apk"))
        assertTrue(UrlPolicy.isAllowed("Http://example.com/a.apk"))
    }

    @Test
    fun `a local network address is still allowed`() {
        // Downloading from a NAS on the LAN is a legitimate use of the endpoint; blocking private
        // ranges would break it. The pairing token is what keeps strangers out.
        assertTrue(UrlPolicy.isAllowed("http://192.168.0.5/nas/app.apk"))
        assertTrue(UrlPolicy.isAllowed("http://nas.local/app.apk"))
    }
}
