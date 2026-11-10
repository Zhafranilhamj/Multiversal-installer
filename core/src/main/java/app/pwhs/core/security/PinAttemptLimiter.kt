package app.pwhs.core.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-client lockout for the LAN secret checks (sync PIN, TV pairing token).
 *
 * A 4-6 digit PIN over an open Wi-Fi is brute-forceable in minutes without throttling, and the
 * TV pairing token is only as strong as the QR code hiding it. Both servers therefore report a
 * failure here and refuse to even compare the secret once a client is locked out.
 *
 * Keyed by whatever identifies the client (the remote IP is what both servers pass). Entries are
 * immutable and replaced via [ConcurrentHashMap.compute], so no extra locking is needed, and the
 * map is bounded by [maxTrackedClients].
 *
 * Android-free for JVM unit tests.
 */
class PinAttemptLimiter(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val lockoutMillis: Long = DEFAULT_LOCKOUT_MILLIS,
    private val maxTrackedClients: Int = DEFAULT_MAX_TRACKED_CLIENTS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class Bucket(
        val failures: Int,
        val windowStart: Long,
        val lockedUntil: Long,
        val lastSeen: Long,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /** True when [clientKey] is currently locked out and the secret should not be compared. */
    fun isLockedOut(clientKey: String): Boolean {
        val bucket = buckets[clientKey] ?: return false
        val now = clock()
        if (bucket.lockedUntil > now) return true
        if (bucket.lockedUntil != 0L || now - bucket.lastSeen > windowMillis) {
            buckets.remove(clientKey, bucket)
        }
        return false
    }

    /**
     * Records a failed check. Returns true when this failure triggered a lockout, so callers can
     tell the client to back off instead of just "wrong PIN".
     */
    fun recordFailure(clientKey: String): Boolean {
        val now = clock()
        prune(now)
        val updated = buckets.compute(clientKey) { _, existing ->
            val freshWindow = existing == null ||
                existing.lockedUntil != 0L ||
                now - existing.windowStart > windowMillis
            val failures = if (freshWindow) 1 else existing!!.failures + 1
            val windowStart = if (freshWindow) now else existing!!.windowStart
            val lockedUntil = if (failures >= maxAttempts) now + lockoutMillis else 0L
            Bucket(
                failures = if (lockedUntil != 0L) 0 else failures,
                windowStart = if (lockedUntil != 0L) 0L else windowStart,
                lockedUntil = lockedUntil,
                lastSeen = now,
            )
        }
        return updated != null && updated.lockedUntil > now
    }

    /** Clears a client's history after a successful check. */
    fun recordSuccess(clientKey: String) {
        buckets.remove(clientKey)
    }

    /** Clears every client, e.g. when the server restarts. */
    fun reset() {
        buckets.clear()
    }

    /** How long [clientKey] still has to wait, in millis; 0 when not locked out. */
    fun remainingLockoutMillis(clientKey: String): Long {
        val bucket = buckets[clientKey] ?: return 0L
        return (bucket.lockedUntil - clock()).coerceAtLeast(0L)
    }

    private fun prune(now: Long) {
        if (buckets.size < maxTrackedClients) return
        val iterator = buckets.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeen > windowMillis && entry.value.lockedUntil <= now) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 8
        const val DEFAULT_WINDOW_MILLIS: Long = 60_000L
        const val DEFAULT_LOCKOUT_MILLIS: Long = 60_000L
        const val DEFAULT_MAX_TRACKED_CLIENTS = 256
    }
}
