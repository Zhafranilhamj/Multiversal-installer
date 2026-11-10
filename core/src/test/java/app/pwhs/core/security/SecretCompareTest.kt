package app.pwhs.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretCompareTest {

    @Test
    fun `identical secrets match`() {
        assertTrue(SecretCompare.equals("1234", "1234"))
        assertTrue(SecretCompare.equals("a1b2c3", "a1b2c3"))
    }

    @Test
    fun `different secrets do not match`() {
        assertFalse(SecretCompare.equals("1234", "1235"))
        assertFalse(SecretCompare.equals("1234", "12345"))
    }

    @Test
    fun `null and blank never match, even against each other`() {
        assertFalse(SecretCompare.equals(null, null))
        assertFalse(SecretCompare.equals("", ""))
        assertFalse(SecretCompare.equals("1234", null))
        assertFalse(SecretCompare.equals(null, "1234"))
        assertFalse(SecretCompare.equals("1234", ""))
        assertFalse(SecretCompare.equals("", "1234"))
    }

    @Test
    fun `comparison is case sensitive`() {
        assertFalse(SecretCompare.equals("ABC123", "abc123"))
    }
}
