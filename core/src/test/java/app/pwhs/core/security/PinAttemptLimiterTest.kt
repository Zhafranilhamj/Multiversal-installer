package app.pwhs.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAttemptLimiterTest {

    private class FakeClock(var now: Long = 5_000_000L) {
        fun read(): Long = now
        fun advance(millis: Long) {
            now += millis
        }
    }

    private fun limiter(
        clock: FakeClock,
        maxAttempts: Int = 3,
        windowMillis: Long = 60_000L,
        lockoutMillis: Long = 30_000L,
    ) = PinAttemptLimiter(
        maxAttempts = maxAttempts,
        windowMillis = windowMillis,
        lockoutMillis = lockoutMillis,
        clock = clock::read,
    )

    @Test
    fun `an unknown client is never locked out`() {
        val clock = FakeClock()
        assertFalse(limiter(clock).isLockedOut("192.168.1.20"))
    }

    @Test
    fun `failures below the threshold do not lock out`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 3)

        limiter.recordFailure("10.0.0.5")
        limiter.recordFailure("10.0.0.5")

        assertFalse(limiter.isLockedOut("10.0.0.5"))
    }

    @Test
    fun `reaching the threshold locks the client out and reports it`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 3)

        assertFalse(limiter.recordFailure("10.0.0.5"))
        assertFalse(limiter.recordFailure("10.0.0.5"))
        assertTrue("the third failure must trigger the lockout", limiter.recordFailure("10.0.0.5"))

        assertTrue(limiter.isLockedOut("10.0.0.5"))
    }

    @Test
    fun `lockout is per client`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 2)

        limiter.recordFailure("attacker")
        limiter.recordFailure("attacker")

        assertTrue(limiter.isLockedOut("attacker"))
        assertFalse(limiter.isLockedOut("honest-phone"))
    }

    @Test
    fun `lockout lapses after the configured duration`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 1, lockoutMillis = 30_000L)

        limiter.recordFailure("10.0.0.9")
        assertTrue(limiter.isLockedOut("10.0.0.9"))

        clock.advance(29_999)
        assertTrue(limiter.isLockedOut("10.0.0.9"))

        clock.advance(2)
        assertFalse(limiter.isLockedOut("10.0.0.9"))
    }

    @Test
    fun `remaining lockout counts down`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 1, lockoutMillis = 10_000L)

        limiter.recordFailure("10.0.0.9")
        assertEquals(10_000L, limiter.remainingLockoutMillis("10.0.0.9"))

        clock.advance(4_000)
        assertEquals(6_000L, limiter.remainingLockoutMillis("10.0.0.9"))

        clock.advance(20_000)
        assertEquals(0L, limiter.remainingLockoutMillis("10.0.0.9"))
    }

    @Test
    fun `a success clears the failure history`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 3)

        limiter.recordFailure("10.0.0.5")
        limiter.recordFailure("10.0.0.5")
        limiter.recordSuccess("10.0.0.5")

        // Two more failures are needed again, so the counter really restarted.
        assertFalse(limiter.recordFailure("10.0.0.5"))
        assertFalse(limiter.isLockedOut("10.0.0.5"))
    }

    @Test
    fun `failures spread outside the window do not accumulate`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 3, windowMillis = 1_000L)

        limiter.recordFailure("10.0.0.5")
        clock.advance(1_500)
        limiter.recordFailure("10.0.0.5")
        clock.advance(1_500)
        assertFalse(limiter.recordFailure("10.0.0.5"))
        assertFalse(limiter.isLockedOut("10.0.0.5"))
    }

    @Test
    fun `failures inside the window do accumulate`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 3, windowMillis = 10_000L)

        limiter.recordFailure("10.0.0.5")
        clock.advance(1_000)
        limiter.recordFailure("10.0.0.5")
        clock.advance(1_000)
        assertTrue(limiter.recordFailure("10.0.0.5"))
    }

    @Test
    fun `reset clears every client`() {
        val clock = FakeClock()
        val limiter = limiter(clock, maxAttempts = 1)

        limiter.recordFailure("a")
        limiter.recordFailure("b")
        limiter.reset()

        assertFalse(limiter.isLockedOut("a"))
        assertFalse(limiter.isLockedOut("b"))
    }
}
