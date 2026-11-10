package app.pwhs.universalinstaller.presentation.sync

import app.pwhs.core.security.LanSessionStore
import app.pwhs.core.security.PinAttemptLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate in front of the LAN share. Each test maps to a response the server turns it into:
 * [SyncAuthenticator.Decision.Granted] -> 200, [SyncAuthenticator.Decision.ShowPinForm] -> 401,
 * [SyncAuthenticator.Decision.Lockout] -> 429,
 * [SyncAuthenticator.Decision.IssueSession] -> 302 + `Set-Cookie`.
 */
class SyncAuthenticatorTest {

    private class FakeClock(var now: Long = 1_000_000L) {
        fun read(): Long = now
        fun advance(millis: Long) {
            now += millis
        }
    }

    private val pin = "409215"
    private val client = "192.168.1.42"

    private fun authenticator(
        clock: FakeClock = FakeClock(),
        requirePin: Boolean = true,
        ttlMillis: Long = LanSessionStore.DEFAULT_TTL_MILLIS,
        maxAttempts: Int = PinAttemptLimiter.DEFAULT_MAX_ATTEMPTS,
    ) = SyncAuthenticator(
        requirePin = requirePin,
        pinCode = pin,
        sessionStore = LanSessionStore(ttlMillis = ttlMillis, clock = clock::read),
        attemptLimiter = PinAttemptLimiter(maxAttempts = maxAttempts, clock = clock::read),
    )

    private fun get(auth: SyncAuthenticator, cookie: String? = null) =
        auth.decide(cookieToken = cookie, clientKey = client, isPost = false, submittedPin = null)

    private fun post(auth: SyncAuthenticator, submittedPin: String?, cookie: String? = null) =
        auth.decide(cookieToken = cookie, clientKey = client, isPost = true, submittedPin = submittedPin)

    private fun login(auth: SyncAuthenticator): String {
        val decision = post(auth, pin)
        assertTrue("expected a session, got $decision", decision is SyncAuthenticator.Decision.IssueSession)
        return (decision as SyncAuthenticator.Decision.IssueSession).token
    }

    // ── The bypass this class exists to close ──────────────────────────

    @Test
    fun `a forged constant cookie is refused`() {
        val auth = authenticator()

        // Regression: the share used to accept the literal cookie value "true" from anyone on the
        // LAN, without the PIN ever being entered.
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, get(auth, "true"))
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, get(auth, "sync_auth=true"))
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, get(auth, "1"))
    }

    @Test
    fun `an unauthenticated request is shown the pin form`() {
        val auth = authenticator()

        assertEquals(SyncAuthenticator.Decision.ShowPinForm, get(auth, null))
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, get(auth, ""))
    }

    // ── The real login flow ────────────────────────────────────────────

    @Test
    fun `the correct pin buys a session that is then accepted`() {
        val auth = authenticator()

        val token = login(auth)

        assertEquals(SyncAuthenticator.Decision.Granted, get(auth, token))
        assertEquals(1, auth.sessionCount)
    }

    @Test
    fun `session tokens are random, not derived from the pin`() {
        val first = login(authenticator())
        val second = login(authenticator())

        assertNotEquals(first, second)
        assertTrue(first.length >= 32)
        assertTrue(!first.contains(pin))
    }

    @Test
    fun `the wrong pin does not buy a session`() {
        val auth = authenticator()

        assertEquals(SyncAuthenticator.Decision.ShowPinForm, post(auth, "000000"))
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, post(auth, null))
        assertEquals(0, auth.sessionCount)
    }

    @Test
    fun `a session expires`() {
        val clock = FakeClock()
        val auth = authenticator(clock = clock, ttlMillis = 60_000L)
        val token = login(auth)

        clock.advance(59_999)
        assertEquals(SyncAuthenticator.Decision.Granted, get(auth, token))

        clock.advance(2)
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, get(auth, token))
    }

    @Test
    fun `the default session lifetime is at most a day`() {
        assertTrue(SyncAuthenticator.COOKIE_TTL_MILLIS <= 24L * 60 * 60 * 1000)
    }

    @Test
    fun `invalidate drops every session, as on a pin rotation`() {
        val auth = authenticator()
        val token = login(auth)

        auth.invalidate()

        assertEquals(0, auth.sessionCount)
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, get(auth, token))
    }

    // ── Throttling ─────────────────────────────────────────────────────

    @Test
    fun `repeated wrong pins lock the client out instead of showing the form again`() {
        val auth = authenticator(maxAttempts = 3)

        assertEquals(SyncAuthenticator.Decision.ShowPinForm, post(auth, "000000"))
        assertEquals(SyncAuthenticator.Decision.ShowPinForm, post(auth, "000000"))
        val lockout = post(auth, "000000")

        assertTrue("expected a lockout, got $lockout", lockout is SyncAuthenticator.Decision.Lockout)
        assertTrue((lockout as SyncAuthenticator.Decision.Lockout).retryAfterSeconds > 0)
        // Even the right PIN is refused while locked out — that is the point of throttling.
        assertTrue(post(auth, pin) is SyncAuthenticator.Decision.Lockout)
        assertEquals(0, auth.sessionCount)
    }

    @Test
    fun `a lockout is per client`() {
        val auth = authenticator(maxAttempts = 1)

        post(auth, "000000")
        assertTrue(
            auth.decide(null, client, isPost = true, submittedPin = "000000")
                is SyncAuthenticator.Decision.Lockout
        )

        // A different device on the LAN is unaffected.
        val other = auth.decide(null, "192.168.1.77", isPost = true, submittedPin = pin)
        assertTrue(other is SyncAuthenticator.Decision.IssueSession)
    }

    @Test
    fun `a lockout lapses and the right pin works again`() {
        val clock = FakeClock()
        val auth = authenticator(clock = clock, maxAttempts = 1)

        post(auth, "000000")
        assertTrue(post(auth, pin) is SyncAuthenticator.Decision.Lockout)

        clock.advance(PinAttemptLimiter.DEFAULT_LOCKOUT_MILLIS + 1)
        assertTrue(post(auth, pin) is SyncAuthenticator.Decision.IssueSession)
    }

    // ── Gate off ───────────────────────────────────────────────────────

    @Test
    fun `with the gate off every request is granted and no pin is checked`() {
        val auth = authenticator(requirePin = false)

        assertEquals(SyncAuthenticator.Decision.Granted, get(auth, null))
        assertEquals(SyncAuthenticator.Decision.Granted, get(auth, "anything"))
        assertEquals(SyncAuthenticator.Decision.Granted, post(auth, "wrong"))
    }

    // ── The cookie itself ──────────────────────────────────────────────

    @Test
    fun `the session cookie is http-only, scoped and bounded`() {
        val auth = authenticator()
        val token = login(auth)

        val header = auth.cookieHeader(token)

        assertTrue(header.startsWith("${SyncAuthenticator.COOKIE_NAME}=$token;"))
        assertTrue("must not be readable by script", header.contains("HttpOnly"))
        assertTrue("must be scoped to the share", header.contains("Path=/"))
        assertTrue("must not ride along cross-site", header.contains("SameSite="))
        assertTrue("must expire", header.contains("Max-Age="))
        assertTrue(header.contains("Max-Age=${SyncAuthenticator.COOKIE_TTL_MILLIS / 1000}"))
    }

    @Test
    fun `the cookie name is unchanged so existing clients keep working`() {
        assertEquals("sync_auth", SyncAuthenticator.COOKIE_NAME)
    }
}
