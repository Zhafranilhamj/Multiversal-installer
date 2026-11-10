package app.pwhs.universalinstaller.presentation.setting.security.util

import app.pwhs.core.security.PasswordKdf
import app.pwhs.core.security.SecretCompare
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hashes and verifies the app-lock PIN.
 *
 * ## Format
 * New records are written as a self-describing string so the cost can be raised later without
 * another migration:
 *
 * ```
 * v2$<iterations>$<hex salt>$<hex hash>
 * ```
 *
 * `v2` is PBKDF2-HMAC-SHA256 via [PasswordKdf]. `v1` — a bare hex SHA-256 of `"<salt>:<pin>"`
 * stored next to a separate salt preference — is what every existing install has. It is still
 * *verified*, because locking a user out of their own app is worse than the weakness being fixed,
 * but a single SHA-256 round over a 4-6 digit PIN is milliseconds of offline brute force, so
 * [needsUpgrade] flags those records and callers re-hash on the next successful unlock.
 */
object PinCryptoHelper {

    private const val SALT_LENGTH_BYTES = 16

    /** Marker for PBKDF2 records. Anything without it is a legacy single-round SHA-256 hash. */
    const val CURRENT_VERSION = "v2"

    private const val SEPARATOR = '$'
    private const val FIELD_COUNT = 4

    /**
     * Generates a cryptographically secure random salt encoded as a hexadecimal string.
     */
    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(saltBytes)
        return PasswordKdf.bytesToHex(saltBytes)
    }

    /**
     * Hashes [pin] into a `v2` PBKDF2 record. [salt] is embedded in the result, so callers only
     * have to persist the returned string (the separate salt preference is kept in sync for
     * backwards compatibility with readers that still look at it).
     */
    fun hashPin(pin: String, salt: String): String {
        val saltHex = salt.ifBlank { generateSalt() }
        val hash = PasswordKdf.deriveHex(
            password = pin,
            salt = PasswordKdf.hexToBytes(saltHex),
            iterations = PasswordKdf.DEFAULT_ITERATIONS,
        )
        return listOf(
            CURRENT_VERSION,
            PasswordKdf.DEFAULT_ITERATIONS.toString(),
            saltHex,
            hash,
        ).joinToString(SEPARATOR.toString())
    }

    /**
     * Verifies whether the provided [inputPin] matches the [storedHash] using constant-time
     * comparison. Accepts both the current `v2` format and legacy bare SHA-256 hashes.
     */
    fun verifyPin(inputPin: String, storedHash: String, storedSalt: String): Boolean {
        if (inputPin.isBlank() || storedHash.isBlank()) return false
        return if (isVersioned(storedHash)) {
            verifyVersioned(inputPin, storedHash)
        } else {
            // Legacy record: the salt lives in its own preference.
            if (storedSalt.isBlank()) return false
            SecretCompare.equals(legacySha256(inputPin, storedSalt), storedHash)
        }
    }

    /** True when [storedHash] predates PBKDF2 and should be re-hashed on the next unlock. */
    fun needsUpgrade(storedHash: String): Boolean =
        storedHash.isNotBlank() && !isVersioned(storedHash)

    /**
     * Re-hashes a verified PIN into the current format. Returns null when [storedHash] is already
     * a `v2` record, so callers can skip the DataStore write.
     */
    fun upgrade(pin: String, storedHash: String): String? {
        if (!needsUpgrade(storedHash)) return null
        return hashPin(pin, generateSalt())
    }

    /**
     * The salt a `v2` record carries, for callers that still persist a separate salt preference.
     * Returns [fallback] for legacy records, whose salt is not embedded.
     */
    fun saltOf(storedHash: String, fallback: String = ""): String {
        val parts = storedHash.split(SEPARATOR)
        return if (parts.size == FIELD_COUNT && parts[0] == CURRENT_VERSION) parts[2] else fallback
    }

    private fun isVersioned(storedHash: String): Boolean =
        storedHash.startsWith("$CURRENT_VERSION$SEPARATOR")

    private fun verifyVersioned(inputPin: String, storedHash: String): Boolean {
        val parts = storedHash.split(SEPARATOR)
        if (parts.size != FIELD_COUNT) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = PasswordKdf.hexToBytes(parts[2])
        if (salt.isEmpty()) return false

        val computed = PasswordKdf.deriveHex(inputPin, salt, iterations)
        return SecretCompare.equals(parts[3], computed)
    }

    /** The original single-round scheme, kept only so existing installs can still unlock. */
    private fun legacySha256(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return PasswordKdf.bytesToHex(digest)
    }
}
