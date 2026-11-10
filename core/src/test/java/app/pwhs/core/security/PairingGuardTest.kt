package app.pwhs.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingGuardTest {

    private class FakeClock(var now: Long = 1_000L) {
        fun read(): Long = now
        fun advance(millis: Long) {
            now += millis
        }
    }

    private val token = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"

    private fun guard(clock: FakeClock, maxAttempts: Int = 3) = PairingGuard(
        token = token,
        attemptLimiter = PinAttemptLimiter(
            maxAttempts = maxAttempts,
            windowMillis = 60_000L,
            lockoutMillis = 30_000L,
            clock = clock::read,
        ),
    )

    @Test
    fun `a guard with no token allows everything`() {
        val guard = PairingGuard(token = "")

        assertEquals(false, guard.isEnabled)
        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("10.0.0.1", null, null))
    }

    @Test
    fun `the header is accepted`() {
        val guard = PairingGuard(token)

        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("10.0.0.1", token, null))
    }

    @Test
    fun `the query parameter is accepted`() {
        val guard = PairingGuard(token)

        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("10.0.0.1", null, token))
    }

    @Test
    fun `a request with no token at all is missing, not invalid`() {
        val guard = PairingGuard(token)

        assertEquals(PairingGuard.Verdict.MISSING, guard.evaluate("10.0.0.1", null, null))
        assertEquals(PairingGuard.Verdict.MISSING, guard.evaluate("10.0.0.1", "", "  "))
    }

    @Test
    fun `a wrong token is invalid`() {
        val guard = PairingGuard(token)

        assertEquals(PairingGuard.Verdict.INVALID, guard.evaluate("10.0.0.1", "deadbeef", null))
    }

    @Test
    fun `repeated guesses lock the client out`() {
        val clock = FakeClock()
        val guard = guard(clock, maxAttempts = 3)

        assertEquals(PairingGuard.Verdict.INVALID, guard.evaluate("10.0.0.9", "guess1", null))
        assertEquals(PairingGuard.Verdict.INVALID, guard.evaluate("10.0.0.9", "guess2", null))
        assertEquals(PairingGuard.Verdict.INVALID, guard.evaluate("10.0.0.9", "guess3", null))
        assertEquals(PairingGuard.Verdict.LOCKED_OUT, guard.evaluate("10.0.0.9", token, null))
    }

    @Test
    fun `a lockout does not stop the correct token from a different client`() {
        val clock = FakeClock()
        val guard = guard(clock, maxAttempts = 1)

        guard.evaluate("attacker", "wrong", null)
        assertEquals(PairingGuard.Verdict.LOCKED_OUT, guard.evaluate("attacker", token, null))
        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("phone", token, null))
    }

    @Test
    fun `missing tokens never trigger a lockout`() {
        val clock = FakeClock()
        val guard = guard(clock, maxAttempts = 1)

        // A browser that just lands on the bare address must not lock out the real phone.
        repeat(20) {
            assertEquals(PairingGuard.Verdict.MISSING, guard.evaluate("10.0.0.1", null, null))
        }
        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("10.0.0.1", token, null))
    }

    @Test
    fun `a lockout lapses`() {
        val clock = FakeClock()
        val guard = guard(clock, maxAttempts = 1)

        guard.evaluate("10.0.0.9", "wrong", null)
        assertEquals(PairingGuard.Verdict.LOCKED_OUT, guard.evaluate("10.0.0.9", token, null))

        clock.advance(31_000)
        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("10.0.0.9", token, null))
    }

    @Test
    fun `a successful pairing clears earlier failures`() {
        val clock = FakeClock()
        val guard = guard(clock, maxAttempts = 2)

        guard.evaluate("10.0.0.9", "wrong", null)
        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("10.0.0.9", token, null))
        // The counter restarted, so one more wrong guess is not yet a lockout.
        assertEquals(PairingGuard.Verdict.INVALID, guard.evaluate("10.0.0.9", "wrong", null))
    }

    @Test
    fun `reset clears every throttle`() {
        val clock = FakeClock()
        val guard = guard(clock, maxAttempts = 1)

        guard.evaluate("10.0.0.9", "wrong", null)
        guard.reset()

        assertEquals(PairingGuard.Verdict.ALLOWED, guard.evaluate("10.0.0.9", token, null))
        assertEquals(0L, guard.retryAfterMillis("10.0.0.9"))
    }

    @Test
    fun `retryAfterMillis reports the remaining lockout`() {
        val clock = FakeClock()
        val guard = guard(clock, maxAttempts = 1)

        guard.evaluate("10.0.0.9", "wrong", null)

        assertEquals(30_000L, guard.retryAfterMillis("10.0.0.9"))
        clock.advance(10_000)
        assertEquals(20_000L, guard.retryAfterMillis("10.0.0.9"))
    }
}
