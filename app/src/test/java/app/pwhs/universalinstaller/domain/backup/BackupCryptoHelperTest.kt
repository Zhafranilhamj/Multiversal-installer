package app.pwhs.universalinstaller.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoHelperTest {

    @Test
    fun `encrypt and decrypt with correct password produces original content`() {
        val originalText = """{"version":2,"theme":"dark","tokens":["ghp_12345"]}"""
        val password = "SuperSecretPassword123!"

        val envelope = BackupCryptoHelper.encrypt(originalText, password)

        assertTrue(envelope.isEncrypted)
        assertEquals(2, envelope.version)
        assertEquals("AES-256-GCM", envelope.algorithm)
        assertNotEquals(originalText, envelope.ciphertext)

        val decryptedText = BackupCryptoHelper.decrypt(envelope, password)
        assertEquals(originalText, decryptedText)
    }

    @Test
    fun `decrypt with wrong password throws InvalidPasswordException`() {
        val originalText = "confidential backup payload"
        val correctPassword = "CorrectPassword_456"
        val wrongPassword = "WrongPassword_789"

        val envelope = BackupCryptoHelper.encrypt(originalText, correctPassword)

        assertThrows(InvalidPasswordException::class.java) {
            BackupCryptoHelper.decrypt(envelope, wrongPassword)
        }
    }

    /**
     * The envelope is attacker-controlled data: whoever hands us a backup file also hands us its
     * `iterations`. PasswordKdf clamps that into its accepted window, so a hostile value can neither
     * make derivation trivially cheap nor hang the import. Both directions have to end in a plain
     * InvalidPasswordException — the wrong key, not a crash and not a stall.
     */
    @Test
    fun `a hostile iteration count is clamped instead of obeyed`() {
        val envelope = BackupCryptoHelper.encrypt("payload", "CorrectPassword_456")

        for (hostile in intArrayOf(0, 1, -1, Int.MAX_VALUE, 50_000_000)) {
            val started = System.nanoTime()
            assertThrows("iterations=$hostile must not decrypt", InvalidPasswordException::class.java) {
                BackupCryptoHelper.decrypt(envelope.copy(iterations = hostile), "CorrectPassword_456")
            }
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            assertTrue(
                "iterations=$hostile took ${elapsedMillis}ms — the clamp did not apply",
                elapsedMillis < 20_000,
            )
        }
    }

    @Test
    fun `the iteration count the envelope declares drives key derivation`() {
        val envelope = BackupCryptoHelper.encrypt("payload", "CorrectPassword_456")

        // Untouched, it round-trips; off by one, it does not. That is what makes the field above
        // meaningful rather than decorative.
        assertEquals("payload", BackupCryptoHelper.decrypt(envelope, "CorrectPassword_456"))
        assertThrows(InvalidPasswordException::class.java) {
            BackupCryptoHelper.decrypt(envelope.copy(iterations = envelope.iterations + 1), "CorrectPassword_456")
        }
    }

    @Test
    fun `tampered ciphertext fails decryption`() {
        val originalText = "unaltered content"
        val password = "SecurePassword"

        val envelope = BackupCryptoHelper.encrypt(originalText, password)

        val tamperedCiphertext = if (envelope.ciphertext.startsWith("A")) "B" + envelope.ciphertext.substring(1) else "A" + envelope.ciphertext.substring(1)
        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)

        assertThrows(InvalidPasswordException::class.java) {
            BackupCryptoHelper.decrypt(tamperedEnvelope, password)
        }
    }
}
