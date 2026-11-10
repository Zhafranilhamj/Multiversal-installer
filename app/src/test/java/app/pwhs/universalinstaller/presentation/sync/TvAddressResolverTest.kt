package app.pwhs.universalinstaller.presentation.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvAddressResolverTest {

    private val noToken: (String) -> String = { "" }

    private fun resolve(
        input: String,
        localIp: String? = "192.168.1.5",
        discoveredTokenFor: (String) -> String = noToken,
    ) = TvAddressResolver.resolve(input, localIp, discoveredTokenFor)

    // ── Scanned QR codes ───────────────────────────────────────────────

    @Test
    fun `a scanned qr yields host, port and pairing token`() {
        val address = resolve("http://192.168.1.20:8787/?token=abc123def456")

        requireNotNull(address)
        assertEquals("192.168.1.20", address.host)
        assertEquals(8787, address.port)
        assertEquals("abc123def456", address.pairingToken)
        assertTrue(address.portWasExplicit)
    }

    @Test
    fun `the token survives a trailing slash and extra query parameters`() {
        assertEquals("t0ken", resolve("http://10.0.0.4:8787/?a=1&token=t0ken")?.pairingToken)
        assertEquals("t0ken", resolve("http://10.0.0.4/?token=t0ken&b=2")?.pairingToken)
    }

    @Test
    fun `a percent encoded token is decoded`() {
        assertEquals("a+b=c", resolve("http://10.0.0.4:8787/?token=a%2Bb%3Dc")?.pairingToken)
    }

    @Test
    fun `https and surrounding whitespace are tolerated`() {
        val address = resolve("  https://192.168.1.20:8787/?token=abc  ")

        assertEquals("192.168.1.20", address?.host)
        assertEquals("abc", address?.pairingToken)
    }

    // ── NSD fallback ───────────────────────────────────────────────────

    @Test
    fun `a typed address borrows the token NSD advertised for that host`() {
        // Without this, manual entry connects but the receiver then refuses the upload.
        val address = resolve("192.168.1.20:8787") { host ->
            if (host == "192.168.1.20") "from-nsd" else ""
        }

        assertEquals("from-nsd", address?.pairingToken)
    }

    @Test
    fun `a scanned token wins over the nsd one`() {
        val address = resolve("http://192.168.1.20:8787/?token=from-qr") { "from-nsd" }

        assertEquals("from-qr", address?.pairingToken)
    }

    @Test
    fun `the token is empty when neither source has one`() {
        assertEquals("", resolve("192.168.1.20:8787")?.pairingToken)
    }

    // ── The TV's IP mnemonic ───────────────────────────────────────────

    @Test
    fun `a four letter ip code expands against this device's subnet`() {
        // IpEncoder.encode("192.168.1.128") == "ERHE"
        val address = resolve("ERHE", localIp = "192.168.1.5")

        assertEquals("192.168.1.128", address?.host)
        assertEquals(8787, address?.port)
        assertFalse(address!!.portWasExplicit)
    }

    @Test
    fun `a mnemonic is lower case insensitive`() {
        assertEquals("192.168.1.128", resolve("erhe", localIp = "192.168.7.9")?.host)
    }

    @Test
    fun `a mnemonic falls back to 192_168 when there is no local ip`() {
        assertEquals("192.168.1.128", resolve("ERHE", localIp = null)?.host)
    }

    @Test
    fun `an undecodable mnemonic is still validated as a host and rejected`() {
        // "QQQQ" is not in the substitution alphabet, so it stays a bare word — not an IP.
        assertNull(resolve("QQQQ"))
    }

    // ── Port probing ───────────────────────────────────────────────────

    @Test
    fun `an explicit port is probed first`() {
        val address = resolve("192.168.1.20:8790")

        requireNotNull(address)
        assertEquals(listOf(8790, 8787, 8788, 8789, 8791, 8792), address.candidatePorts())
    }

    @Test
    fun `without an explicit port the defaults are probed in order`() {
        val address = resolve("192.168.1.20")

        requireNotNull(address)
        assertEquals(TvAddressResolver.CANDIDATE_PORTS, address.candidatePorts())
        assertEquals(8787, address.port)
    }

    @Test
    fun `the default port list is not duplicated when the explicit port is the default`() {
        assertEquals(
            listOf(8787, 8788, 8789, 8790, 8791, 8792),
            resolve("192.168.1.20:8787")?.candidatePorts(),
        )
    }

    // ── Accepted host shapes ───────────────────────────────────────────

    @Test
    fun `localhost and mdns names are accepted`() {
        assertEquals("localhost", resolve("localhost:8787")?.host)
        assertEquals("living-room-tv.local", resolve("living-room-tv.local")?.host)
    }

    @Test
    fun `a bare ip with no port is accepted`() {
        val address = resolve("10.0.0.4")

        assertEquals("10.0.0.4", address?.host)
        assertEquals(8787, address?.port)
    }

    // ── Rejected input ─────────────────────────────────────────────────

    @Test
    fun `hosts that cannot be a tv are rejected`() {
        assertNull(resolve(""))
        assertNull(resolve("   "))
        assertNull(resolve("not a host"))
        assertNull(resolve("192.168.1"))
        assertNull(resolve("192.168.1.5.6"))
        assertNull(resolve("999.1.1.1"))
        assertNull(resolve("192.168.001.5"))
        assertNull(resolve("192.168.1.a"))
        assertNull(resolve("http://"))
        assertNull(resolve("https:///"))
        assertNull(resolve("example.com"))
    }

    @Test
    fun `a non numeric or out of range port is rejected`() {
        assertNull(resolve("192.168.1.20:abc"))
        assertNull(resolve("192.168.1.20:0"))
        assertNull(resolve("192.168.1.20:70000"))
        assertNull(resolve("192.168.1.20:"))
    }

    @Test
    fun `rejection never throws on hostile input`() {
        val hostile = listOf(
            "\u0000", "http://[::1]:8787/?token=", "?token=", ":", "::::", "a".repeat(500),
            "http://192.168.1.20:8787/?token=" + "x".repeat(500),
        )
        hostile.forEach { input ->
            runCatching { resolve(input) }
                .onFailure { throw AssertionError("resolve('$input') threw", it) }
        }
    }
}
