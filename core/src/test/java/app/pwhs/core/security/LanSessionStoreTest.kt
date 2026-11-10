package app.pwhs.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSessionStoreTest {

    /** Deterministic clock so expiry can be asserted without sleeping. */
    private class FakeClock(var now: Long = 1_000_000L) {
        fun read(): Long = now
        fun advance(millis: Long) {
            now += millis
        }
    }

    @Test
    fun `issued token validates`() {
        val store = LanSessionStore()
        val token = store.issue()

        assertTrue(store.isValid(token))
        assertEquals(1, store.size)
    }

    @Test
    fun `the constant legacy cookie value is not a valid token`() {
        // Regression for the sync_auth=true bypass: a forged constant must never authenticate.
        val store = LanSessionStore()
        store.issue()

        assertFalse(store.isValid("true"))
        assertFalse(store.isValid("sync_auth=true"))
    }

    @Test
    fun `blank and null tokens are rejected`() {
        val store = LanSessionStore()

        assertFalse(store.isValid(null))
        assertFalse(store.isValid(""))
        assertFalse(store.isValid("   "))
    }

    @Test
    fun `tokens are unique and carry 128 bits of entropy`() {
        val store = LanSessionStore()
        val tokens = (1..20).map { store.issue() }.toSet()

        assertEquals(20, tokens.size)
        assertTrue(tokens.all { it.length == LanSessionStore.TOKEN_BYTES * 2 })
        assertTrue(tokens.all { it.matches(Regex("[0-9a-f]+")) })
    }

    @Test
    fun `token expires after the ttl`() {
        val clock = FakeClock()
        val store = LanSessionStore(ttlMillis = 1_000L, clock = clock::read)
        val token = store.issue()

        clock.advance(999)
        assertTrue(store.isValid(token))

        clock.advance(2)
        assertFalse(store.isValid(token))
    }

    @Test
    fun `default ttl is at most 24 hours`() {
        assertTrue(LanSessionStore.DEFAULT_TTL_MILLIS <= 24L * 60 * 60 * 1000)
    }

    @Test
    fun `revoking a single token leaves the others valid`() {
        val store = LanSessionStore()
        val first = store.issue()
        val second = store.issue()

        store.revoke(first)

        assertFalse(store.isValid(first))
        assertTrue(store.isValid(second))
    }

    @Test
    fun `revokeAll invalidates every session on server stop`() {
        val store = LanSessionStore()
        val tokens = (1..5).map { store.issue() }

        store.revokeAll()

        assertEquals(0, store.size)
        assertTrue(tokens.none { store.isValid(it) })
    }

    @Test
    fun `issuing past the cap evicts the soonest expiring session`() {
        val clock = FakeClock()
        val store = LanSessionStore(ttlMillis = 10_000L, maxSessions = 3, clock = clock::read)

        val oldest = store.issue()
        clock.advance(100)
        store.issue()
        clock.advance(100)
        store.issue()
        assertEquals(3, store.size)

        clock.advance(100)
        store.issue()

        assertEquals(3, store.size)
        assertFalse("the oldest session is the one evicted", store.isValid(oldest))
    }

    @Test
    fun `expired sessions are purged on issue so the cap is not exhausted by dead tokens`() {
        val clock = FakeClock()
        val store = LanSessionStore(ttlMillis = 1_000L, maxSessions = 2, clock = clock::read)
        val first = store.issue()
        val second = store.issue()

        clock.advance(5_000)
        val third = store.issue()

        assertEquals(1, store.size)
        assertFalse(store.isValid(first))
        assertFalse(store.isValid(second))
        assertTrue(store.isValid(third))
    }

    @Test
    fun `a token issued by another store is rejected`() {
        val storeA = LanSessionStore()
        val storeB = LanSessionStore()

        assertNotEquals(storeA.issue(), storeB.issue())
        assertFalse(storeB.isValid(storeA.issue()))
    }
}
