package app.pwhs.core.security

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session tokens for the LAN HTTP servers (the phone's sync share and the TV
 * receiver).
 *
 * The sync share used to authenticate with the constant cookie `sync_auth=true`: any client on
 * the same Wi-Fi could send that header and read every shared APK without ever learning the PIN.
 * Tokens issued here are 128-bit [SecureRandom] values that exist only in this map, expire after
 * [ttlMillis], and are capped at [maxSessions] so an attacker cannot grow the map without bound.
 *
 * Nothing is persisted — restarting the server invalidates every session, which is the right
 * behaviour for a share that is meant to live as long as the foreground service.
 *
 * Deliberately Android-free so it can be unit-tested on the JVM.
 */
class LanSessionStore(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** token -> absolute expiry timestamp in [clock] millis. */
    private val expiryByToken = ConcurrentHashMap<String, Long>()

    /** Live session count, for tests and diagnostics. Expired entries are not purged eagerly. */
    val size: Int get() = expiryByToken.size

    /**
     * Mints a fresh token valid for [ttlMillis]. Evicts expired sessions first, then the
     * soonest-expiring ones if the cap is still reached, so issuing never fails.
     */
    fun issue(): String {
        val now = clock()
        purgeExpired(now)
        while (expiryByToken.size >= maxSessions) {
            val oldest = expiryByToken.entries.minByOrNull { it.value } ?: break
            if (!expiryByToken.remove(oldest.key, oldest.value)) break
        }
        val token = newToken()
        expiryByToken[token] = now + ttlMillis
        return token
    }

    /** True when [token] was issued by this store and has not expired. */
    fun isValid(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val now = clock()
        val expiry = expiryByToken[token] ?: return false
        if (expiry <= now) {
            expiryByToken.remove(token, expiry)
            return false
        }
        return true
    }

    /** Drops one session, e.g. when a client logs out. */
    fun revoke(token: String?) {
        if (token.isNullOrBlank()) return
        expiryByToken.remove(token)
    }

    /** Drops every session — call when the server stops or the PIN is rotated. */
    fun revokeAll() {
        expiryByToken.clear()
    }

    private fun purgeExpired(now: Long) {
        val iterator = expiryByToken.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= now) iterator.remove()
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        // Retry on the astronomically unlikely collision rather than silently reusing a session.
        repeat(MAX_MINT_ATTEMPTS) {
            random.nextBytes(bytes)
            val token = bytes.toHex()
            if (expiryByToken.putIfAbsent(token, clock() + ttlMillis) == null) return token
        }
        error("Could not mint a unique LAN session token")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** 128 bits of entropy, rendered as 32 lowercase hex characters. */
        const val TOKEN_BYTES = 16
        const val DEFAULT_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L
        const val DEFAULT_MAX_SESSIONS = 32
        private const val MAX_MINT_ATTEMPTS = 8
    }
}
