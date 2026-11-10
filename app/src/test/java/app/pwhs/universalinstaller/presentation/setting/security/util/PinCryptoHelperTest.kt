package app.pwhs.universalinstaller.presentation.setting.security.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PinCryptoHelperTest {

    /** The pre-PBKDF2 scheme: one salted SHA-256 round, hex encoded, salt stored separately. */
    private fun legacyHash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `generated salt is 16 bytes of hex`() {
        val salt = PinCryptoHelper.generateSalt()

        assertEquals(32, salt.length)
        assertTrue(salt.matches(Regex("[0-9a-f]+")))
        assertNotEquals(salt, PinCryptoHelper.generateSalt())
    }

    @Test
    fun `new records use the versioned pbkdf2 format`() {
        val salt = PinCryptoHelper.generateSalt()

        val record = PinCryptoHelper.hashPin("2468", salt)

        val parts = record.split('$')
        assertEquals(4, parts.size)
        assertEquals(PinCryptoHelper.CURRENT_VERSION, parts[0])
        assertEquals("100000", parts[1])
        assertEquals(salt, parts[2])
        assertTrue(parts[3].matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `correct pin verifies and wrong pin does not`() {
        val salt = PinCryptoHelper.generateSalt()
        val record = PinCryptoHelper.hashPin("246810", salt)

        assertTrue(PinCryptoHelper.verifyPin("246810", record, salt))
        assertFalse(PinCryptoHelper.verifyPin("246811", record, salt))
        assertFalse(PinCryptoHelper.verifyPin("", record, salt))
    }

    @Test
    fun `verification does not depend on the separate salt preference for v2 records`() {
        val record = PinCryptoHelper.hashPin("1357", PinCryptoHelper.generateSalt())

        // The salt is embedded in the record, so a stale/blank salt preference must not matter.
        assertTrue(PinCryptoHelper.verifyPin("1357", record, ""))
        assertTrue(PinCryptoHelper.verifyPin("1357", record, "deadbeef"))
    }

    @Test
    fun `the pin never appears in the stored record`() {
        val record = PinCryptoHelper.hashPin("987654", PinCryptoHelper.generateSalt())

        assertFalse(record.contains("987654"))
    }

    @Test
    fun `same pin with different salts produces different records`() {
        val a = PinCryptoHelper.hashPin("1234", PinCryptoHelper.generateSalt())
        val b = PinCryptoHelper.hashPin("1234", PinCryptoHelper.generateSalt())

        assertNotEquals(a, b)
        assertTrue(PinCryptoHelper.verifyPin("1234", a, ""))
        assertTrue(PinCryptoHelper.verifyPin("1234", b, ""))
    }

    // ── Legacy upgrade path ────────────────────────────────────────────

    @Test
    fun `a legacy single sha256 record still unlocks the app`() {
        val salt = PinCryptoHelper.generateSalt()
        val legacy = legacyHash("4321", salt)

        assertTrue(PinCryptoHelper.verifyPin("4321", legacy, salt))
        assertFalse(PinCryptoHelper.verifyPin("4322", legacy, salt))
    }

    @Test
    fun `a legacy record without its salt is rejected rather than crashing`() {
        val legacy = legacyHash("4321", "00112233445566778899aabbccddeeff")

        assertFalse(PinCryptoHelper.verifyPin("4321", legacy, ""))
    }

    @Test
    fun `needsUpgrade flags legacy records only`() {
        val salt = PinCryptoHelper.generateSalt()

        assertTrue(PinCryptoHelper.needsUpgrade(legacyHash("4321", salt)))
        assertFalse(PinCryptoHelper.needsUpgrade(PinCryptoHelper.hashPin("4321", salt)))
        assertFalse(PinCryptoHelper.needsUpgrade(""))
    }

    @Test
    fun `upgrade converts a verified legacy record and the pin keeps working`() {
        val salt = PinCryptoHelper.generateSalt()
        val legacy = legacyHash("4321", salt)

        val upgraded = PinCryptoHelper.upgrade("4321", legacy)

        requireNotNull(upgraded)
        assertTrue(upgraded.startsWith("${PinCryptoHelper.CURRENT_VERSION}\$"))
        assertFalse(PinCryptoHelper.needsUpgrade(upgraded))
        assertTrue(PinCryptoHelper.verifyPin("4321", upgraded, PinCryptoHelper.saltOf(upgraded)))
        assertFalse(PinCryptoHelper.verifyPin("4322", upgraded, PinCryptoHelper.saltOf(upgraded)))
    }

    @Test
    fun `upgrade is a no-op for a record already in the current format`() {
        val record = PinCryptoHelper.hashPin("4321", PinCryptoHelper.generateSalt())

        assertNull(PinCryptoHelper.upgrade("4321", record))
    }

    @Test
    fun `saltOf reads the embedded salt for v2 and falls back for legacy`() {
        val salt = PinCryptoHelper.generateSalt()
        val record = PinCryptoHelper.hashPin("4321", salt)

        assertEquals(salt, PinCryptoHelper.saltOf(record))
        assertEquals("fallback", PinCryptoHelper.saltOf(legacyHash("4321", salt), "fallback"))
        assertEquals("", PinCryptoHelper.saltOf("garbage"))
    }

    @Test
    fun `a malformed versioned record is rejected instead of throwing`() {
        assertFalse(PinCryptoHelper.verifyPin("1234", "v2\$notanumber\$aa\$bb", ""))
        assertFalse(PinCryptoHelper.verifyPin("1234", "v2\$100000\$zz\$bb", ""))
        assertFalse(PinCryptoHelper.verifyPin("1234", "v2\$100000\$aa", ""))
        assertFalse(PinCryptoHelper.verifyPin("1234", "v2\$", ""))
    }
}
