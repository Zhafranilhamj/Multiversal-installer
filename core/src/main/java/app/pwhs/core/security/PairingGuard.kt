package app.pwhs.core.security

/**
 * Decides whether a LAN request carries a valid pairing token.
 *
 * Split out of the receiver's HTTP handler so the gate itself is testable on the JVM — the handler
 * needs an Android `Context` for its assets, this does not.
 *
 * The verdict distinguishes "no token" from "wrong token" because only the latter is a guess and
 * only guesses should count towards the lockout: a browser that simply lands on the bare IP must
 * not be able to lock out the phone that is about to pair.
 */
class PairingGuard(
    private val token: String,
    private val attemptLimiter: PinAttemptLimiter = PinAttemptLimiter(),
) {

    enum class Verdict {
        /** Paired, or pairing is not configured. */
        ALLOWED,

        /** The client sent no token at all. */
        MISSING,

        /** The client sent a token and it was wrong. Counted as a guess. */
        INVALID,

        /** This client guessed too often and is throttled. */
        LOCKED_OUT,
    }

    /** True when this guard was created without a token, i.e. every request is allowed. */
    val isEnabled: Boolean get() = token.isNotBlank()

    /**
     * @param clientKey identifies the client for throttling — both servers pass the remote IP
     * @param headerValue the `X-UI-Token` request header, if present
     * @param queryValue the `?token=` query parameter, if present
     */
    fun evaluate(clientKey: String, headerValue: String?, queryValue: String?): Verdict {
        if (!isEnabled) return Verdict.ALLOWED
        if (attemptLimiter.isLockedOut(clientKey)) return Verdict.LOCKED_OUT

        val presented = PairingToken.extract(headerValue, queryValue) ?: return Verdict.MISSING
        if (SecretCompare.equals(token, presented)) {
            attemptLimiter.recordSuccess(clientKey)
            return Verdict.ALLOWED
        }
        attemptLimiter.recordFailure(clientKey)
        return Verdict.INVALID
    }

    /** Milliseconds the client still has to wait; 0 when it is not locked out. */
    fun retryAfterMillis(clientKey: String): Long = attemptLimiter.remainingLockoutMillis(clientKey)

    /** Clears every client's throttle state — used when the receiver restarts. */
    fun reset() = attemptLimiter.reset()
}
