package app.pwhs.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTokenTest {

    @Test
    fun `generated tokens are 128 bits of hex and never repeat`() {
        val tokens = (1..50).map { PairingToken.generate() }.toSet()

        assertEquals(50, tokens.size)
        assertTrue(tokens.all { it.length == PairingToken.TOKEN_BYTES * 2 })
        assertTrue(tokens.all { it.matches(Regex("[0-9a-f]+")) })
    }

    @Test
    fun `a token is appended to a bare url as a query parameter`() {
        assertEquals(
            "http://192.168.1.5:8787/?token=abc123",
            PairingToken.appendToUrl("http://192.168.1.5:8787/", "abc123"),
        )
    }

    @Test
    fun `a token is appended with an ampersand when the url already has a query`() {
        assertEquals(
            "http://10.0.0.2:8787/?a=1&token=abc123",
            PairingToken.appendToUrl("http://10.0.0.2:8787/?a=1", "abc123"),
        )
    }

    @Test
    fun `a blank token leaves the url untouched`() {
        assertEquals("http://10.0.0.2:8787/", PairingToken.appendToUrl("http://10.0.0.2:8787/", ""))
    }

    @Test
    fun `the header wins over the query parameter`() {
        assertEquals("from-header", PairingToken.extract("from-header", "from-query"))
    }

    @Test
    fun `the query parameter is used when there is no header`() {
        assertEquals("from-query", PairingToken.extract(null, "from-query"))
        assertEquals("from-query", PairingToken.extract("", "from-query"))
        assertEquals("from-query", PairingToken.extract("   ", "from-query"))
    }

    @Test
    fun `no token anywhere yields null`() {
        assertNull(PairingToken.extract(null, null))
        assertNull(PairingToken.extract("", ""))
        assertNull(PairingToken.extract("  ", null))
    }

    @Test
    fun `the transport names are the ones the phone client and page script use`() {
        assertEquals("X-UI-Token", PairingToken.HEADER)
        assertEquals("token", PairingToken.QUERY_PARAM)
        // NanoHTTPD lower-cases header names before they reach the handler.
        assertEquals("x-ui-token", PairingToken.HEADER.lowercase())
        assertNotEquals(PairingToken.HEADER, PairingToken.HEADER.lowercase())
    }
}
