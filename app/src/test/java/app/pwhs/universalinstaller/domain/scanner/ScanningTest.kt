package app.pwhs.universalinstaller.domain.scanner

import app.pwhs.core.domain.model.*
import app.pwhs.universalinstaller.presentation.install.ScanResultCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScanningTest {
    @Test fun providerRequiresExplicitValidSelection() {
        assertNull(ScanProvider.from(null)); assertNull(ScanProvider.from("unknown"))
        assertEquals(ScanProvider.VIRUSTOTAL, ScanProvider.from("VIRUSTOTAL"))
        assertEquals(ScanProvider.VIRUSDETEC, ScanProvider.from("VIRUSDETEC"))
    }
    @Test fun languageUsesVietnameseOnlyWhenRequested() {
        assertEquals("vi", ScanProvider.language("vi-VN"))
        for (language in listOf("en-MY", "ms-MY", "de-DE", "")) assertEquals("en", ScanProvider.language(language))
    }
    @Test fun httpFailuresAreNotCleanResults() {
        assertEquals(VtStatus.INVALID_API_KEY, DirectVirusTotal.httpFailure(401))
        assertEquals(VtStatus.INVALID_API_KEY, DirectVirusTotal.httpFailure(403))
        assertEquals(VtStatus.NOT_FOUND, DirectVirusTotal.httpFailure(404))
        assertEquals(VtStatus.RATE_LIMITED, DirectVirusTotal.httpFailure(429))
        assertEquals(VtStatus.ERROR, DirectVirusTotal.httpFailure(500))
    }
    @Test fun parsesVerdictsWithoutTreatingMissingEnginesAsClean() {
        fun verdict(stats: String) = DirectVirusTotal.parse(JSONObject("""{"last_analysis_stats":$stats}""")).status
        assertEquals(VtStatus.ERROR, verdict("{}"))
        assertEquals(VtStatus.CLEAN, verdict("""{"undetected":4}"""))
        assertEquals(VtStatus.SUSPICIOUS, verdict("""{"suspicious":1}"""))
        assertEquals(VtStatus.MALICIOUS, verdict("""{"malicious":1,"suspicious":2}"""))
    }
    @Test fun fullReportsAndProviderSurvivePersistence() {
        val result = VtResult(status = VtStatus.SUSPICIOUS, suspicious = 1, provider = "VirusDetec",
            engineResults = listOf(VtEngineResult("test", "suspicious", null)),
            report = ScanReport(sha256 = "hash", verdict = Verdict.RISKWARE, riskScore = 12,
                findings = listOf(Finding("test", "R1", SeverityLevel.MEDIUM, 12, "Title", "Details", "Evidence"))))
        assertEquals(result, ScanResultCodec.decode(ScanResultCodec.encode(result)))
    }
    @Test fun interruptedScanRemainsUnknownNotClean() {
        val result = VtResult(status = VtStatus.ANALYZING, provider = "VirusTotal")
        assertEquals(VtStatus.ERROR, ScanResultCodec.decode(ScanResultCodec.encode(result)).status)
    }
}
