package app.pwhs.core.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The project's single PBKDF2-HMAC-SHA256 implementation.
 *
 * Two features derive a key from a short human secret: encrypted backups (which already used
 * 100k PBKDF2 rounds) and the app-lock PIN (which used a single salted SHA-256 — milliseconds to
 * brute-force offline for a 4-6 digit PIN). Sharing one KDF keeps the posture consistent and
 * gives both a single place to raise the cost.
 *
 * Iteration counts are always clamped through [clampIterations]: a count read back from a file or
 * a stored record is attacker-controlled, and an absurd value is a cheap CPU denial of service.
 * Clamping changes the derived key for such input, so the GCM tag / hash comparison fails — which
 * is the outcome we want.
 *
 * Android-free for JVM unit tests.
 */
object PasswordKdf {

    const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Cost used when writing new records. ~50 ms on a mid-range phone, ~2 ms on a laptop. */
    const val DEFAULT_ITERATIONS = 100_000

    /** Acceptance window for counts read from untrusted input. */
    const val MIN_ITERATIONS = 50_000
    const val MAX_ITERATIONS = 600_000

    const val DEFAULT_KEY_LENGTH_BITS = 256

    /** Clamps an untrusted iteration count into [MIN_ITERATIONS]..[MAX_ITERATIONS]. */
    fun clampIterations(iterations: Int): Int = iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)

    /** Derives [keyLengthBits] of key material from [password] and [salt]. */
    fun deriveBytes(
        password: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS,
    ): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            clampIterations(iterations),
            keyLengthBits,
        )
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    /** [deriveBytes] rendered as lowercase hex, for records stored as text. */
    fun deriveHex(
        password: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS,
    ): String = deriveBytes(password, salt, iterations, keyLengthBits)
        .joinToString("") { "%02x".format(it) }

    /** Encodes [bytes] as lowercase hex — the format the existing PIN records already use. */
    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Decodes hex back to bytes. Malformed input yields an empty array rather than throwing, so a
     * corrupted record fails verification instead of crashing the unlock dialog.
     */
    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        if (clean.length % 2 != 0) return ByteArray(0)
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val value = clean.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return ByteArray(0)
            out[i] = value.toByte()
        }
        return out
    }
}
