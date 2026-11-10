package app.pwhs.core.security

import java.security.MessageDigest

/**
 * Constant-time comparison for the short shared secrets the LAN features check: the sync PIN,
 * the TV pairing token and the app-lock PIN.
 *
 * `String.equals` returns on the first differing character, which leaks how much of a guess was
 * right through response timing. [MessageDigest.isEqual] compares every byte regardless, so the
 * cost of a wrong guess is the same as a nearly-right one.
 */
object SecretCompare {

    /** True when both sides are non-null and byte-identical. Null/blank never matches. */
    fun equals(expected: String?, actual: String?): Boolean {
        if (expected.isNullOrEmpty() || actual.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }
}
