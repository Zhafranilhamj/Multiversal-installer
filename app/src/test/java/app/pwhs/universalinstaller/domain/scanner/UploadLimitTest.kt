package app.pwhs.universalinstaller.domain.scanner

import app.pwhs.core.domain.model.ScanLimits
import app.pwhs.core.domain.model.VtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the size routing that decides between VirusTotal's direct `POST /files` and the
 * two-step `GET /files/upload_url` flow, plus the host allow-list applied to the returned URL.
 */
class UploadLimitTest {

    private val mb = 1024L * 1024

    @Test
    fun `files at or under 32 MB are sent directly`() {
        assertFalse(ScanLimits.needsUploadUrl(0))
        assertFalse(ScanLimits.needsUploadUrl(1 * mb))
        assertFalse(ScanLimits.needsUploadUrl(32 * mb)) // exactly the documented ceiling
        assertTrue(ScanLimits.needsUploadUrl(32 * mb + 1))
    }

    @Test
    fun `files between 32 MB and 650 MB go through the upload url`() {
        assertTrue(ScanLimits.needsUploadUrl(100 * mb))
        assertTrue(ScanLimits.needsUploadUrl(650 * mb))
        assertFalse(ScanLimits.exceedsMaximum(650 * mb)) // 650 MB is still accepted
        assertTrue(ScanLimits.exceedsMaximum(650 * mb + 1))
    }

    @Test
    fun `the visible limit is reported in megabytes`() {
        assertEquals("700 MB", ScanLimits.describeMegabytes(700 * mb))
        assertEquals("648 MB", ScanLimits.describeMegabytes(648 * mb + 1024))
    }

    @Test
    fun `only virustotal hosts are trusted for the pre-signed upload`() {
        assertTrue(DirectVirusTotal.isTrustedUploadUrl("https://www.virustotal.com/_ah/upload/abc"))
        assertTrue(DirectVirusTotal.isTrustedUploadUrl("https://virustotal.com/api/v3/files"))
        assertTrue(DirectVirusTotal.isTrustedUploadUrl("https://upload.virustotal.com/xyz"))
    }

    @Test
    fun `a tampered upload url can never receive the apk`() {
        assertFalse(DirectVirusTotal.isTrustedUploadUrl("https://evil.example.com/upload"))
        // Suffix confusion must not pass: "notvirustotal.com" ends with "virustotal.com".
        assertFalse(DirectVirusTotal.isTrustedUploadUrl("https://notvirustotal.com/upload"))
        assertFalse(DirectVirusTotal.isTrustedUploadUrl("https://virustotal.com.evil.test/upload"))
        assertFalse(DirectVirusTotal.isTrustedUploadUrl(""))
        assertFalse(DirectVirusTotal.isTrustedUploadUrl("not a url"))
    }

    @Test
    fun `http 413 stays too large and 429 stays rate limited`() {
        assertEquals(VtStatus.TOO_LARGE, DirectVirusTotal.httpFailure(413))
        assertEquals(VtStatus.RATE_LIMITED, DirectVirusTotal.httpFailure(429))
    }

    @Test
    fun `an unset provider resolves to virusdetec but an explicit choice wins`() {
        // Nothing written yet: the scan button must still work, and VirusDetec needs no key.
        assertEquals(ScanProvider.VIRUSDETEC, ScanProvider.resolve(null))
        assertEquals(ScanProvider.VIRUSDETEC, ScanProvider.resolve("garbage"))
        assertEquals(ScanProvider.VIRUSTOTAL, ScanProvider.resolve("VIRUSTOTAL"))
        assertEquals(ScanProvider.VIRUSDETEC, ScanProvider.resolve("VIRUSDETEC"))
    }

    @Test
    fun `automatic lookup consent is stricter than the resolve fallback`() {
        // Automatic hash lookups require a choice; resolve() must not be used to gate them.
        assertEquals(null, ScanProvider.from(null))
        assertEquals(null, ScanProvider.from("garbage"))
    }
}
