package app.pwhs.universalinstaller.presentation.sync

import app.pwhs.core.security.LanSessionStore
import app.pwhs.core.security.PinAttemptLimiter
import app.pwhs.core.security.SecretCompare

/**
 * Decides whether a request to the LAN sync share is authenticated, and mints the session a
 * correct PIN buys.
 *
 * Split out of [ApkHttpServer] so the gate is testable on the JVM: the server needs an Android
 * `Context` for its HTML assets and `Environment` for the share folder, this needs neither.
 *
 * ## What it replaces
 * The share used to authenticate with the constant cookie `sync_auth=true`. Any client on the same
 * Wi-Fi could send that header and download every shared APK without ever learning the PIN. Tokens
 * are now 128-bit random values held only in [sessionStore], expire after at most 24 h, and a
 * forged constant matches nothing.
 */
class SyncAuthenticator(
    private val requirePin: Boolean,
    private val pinCode: String,
    private val sessionStore: LanSessionStore = LanSessionStore(ttlMillis = COOKIE_TTL_MILLIS),
    private val attemptLimiter: PinAttemptLimiter = PinAttemptLimiter(),
) {

    sealed interface Decision {
        /** The request may be served. */
        data object Granted : Decision

        /** The PIN was right — hand the client this session cookie and redirect it back. */
        data class IssueSession(val token: String) : Decision

        /** No session yet and nothing usable was submitted: render the PIN form (401). */
        data object ShowPinForm : Decision

        /** Too many wrong PINs from this client: back off (429). */
        data class Lockout(val retryAfterSeconds: Long) : Decision
    }

    /**
     * @param cookieToken value of the `sync_auth` cookie, or null
     * @param clientKey identifies the client for throttling — the remote IP
     * @param isPost whether the request carried a form body
     * @param submittedPin value of the `pin` form field, or null
     */
    fun decide(cookieToken: String?, clientKey: String, isPost: Boolean, submittedPin: String?): Decision {
        if (!requirePin) return Decision.Granted
        if (sessionStore.isValid(cookieToken)) return Decision.Granted
        if (attemptLimiter.isLockedOut(clientKey)) return lockout(clientKey)

        if (!isPost || submittedPin == null) return Decision.ShowPinForm

        // Constant-time: String.equals would leak how much of a guess was right through timing.
        if (SecretCompare.equals(pinCode, submittedPin)) {
            attemptLimiter.recordSuccess(clientKey)
            return Decision.IssueSession(sessionStore.issue())
        }
        if (attemptLimiter.recordFailure(clientKey)) return lockout(clientKey)
        return Decision.ShowPinForm
    }

    /** The `Set-Cookie` value for a freshly issued session token. */
    fun cookieHeader(token: String): String =
        "$COOKIE_NAME=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=${COOKIE_TTL_MILLIS / 1000L}"

    /**
     * Drops every live session. Called when the share stops and when the PIN is rotated, so a
     * cookie minted under an old PIN cannot outlive it.
     */
    fun invalidate() {
        sessionStore.revokeAll()
        attemptLimiter.reset()
    }

    /** Live session count, for diagnostics and tests. */
    val sessionCount: Int get() = sessionStore.size

    private fun lockout(clientKey: String): Decision.Lockout = Decision.Lockout(
        retryAfterSeconds = (attemptLimiter.remainingLockoutMillis(clientKey) / 1000L) + 1L
    )

    companion object {
        const val COOKIE_NAME = "sync_auth"

        /** Cookie and token lifetimes are the same number so they die together. */
        const val COOKIE_TTL_MILLIS: Long = LanSessionStore.DEFAULT_TTL_MILLIS
    }
}
