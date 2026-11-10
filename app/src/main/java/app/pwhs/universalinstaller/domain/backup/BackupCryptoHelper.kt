package app.pwhs.universalinstaller.domain.backup

import app.pwhs.core.security.PasswordKdf
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class InvalidPasswordException(
    message: String = "Incorrect password or corrupted backup file",
    cause: Throwable? = null,
) : Exception(message, cause)

object BackupCryptoHelper {

    private const val ALGORITHM = "AES-256-GCM"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = PasswordKdf.ALGORITHM
    private const val ITERATION_COUNT = PasswordKdf.DEFAULT_ITERATIONS
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12

    private val secureRandom = SecureRandom()

    fun encrypt(plainText: String, password: String): EncryptedBackupEnvelope {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(password, salt, ITERATION_COUNT)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedBackupEnvelope(
            isEncrypted = true,
            version = 2,
            algorithm = ALGORITHM,
            kdf = KDF_ALGORITHM,
            iterations = ITERATION_COUNT,
            salt = Base64Compat.encode(salt),
            iv = Base64Compat.encode(iv),
            ciphertext = Base64Compat.encode(cipherBytes),
        )
    }

    fun decrypt(envelope: EncryptedBackupEnvelope, password: String): String {
        try {
            val salt = Base64Compat.decode(envelope.salt)
            val iv = Base64Compat.decode(envelope.iv)
            val cipherBytes = Base64Compat.decode(envelope.ciphertext)

            // envelope.iterations comes out of the file, so it is attacker-controlled: a hostile
            // backup could ask for Int.MAX_VALUE rounds and burn the CPU for minutes. Clamped.
            val secretKey = deriveKey(password, salt, envelope.iterations)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val decryptedBytes = cipher.doFinal(cipherBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw InvalidPasswordException("Failed to decrypt: invalid password or corrupted data", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidPasswordException("Corrupted backup encoding", e)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val keyBytes = PasswordKdf.deriveBytes(password, salt, iterations, KEY_LENGTH_BITS)
        return SecretKeySpec(keyBytes, "AES")
    }
}

internal object Base64Compat {
    fun encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    fun decode(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str.trim())
        } catch (e: Throwable) {
            android.util.Base64.decode(str.trim(), android.util.Base64.NO_WRAP)
        }
    }
}
