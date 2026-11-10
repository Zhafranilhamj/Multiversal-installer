package app.pwhs.universalinstaller.presentation.sync

import java.security.SecureRandom

/**
 * Generates the per-session PIN for the LAN share.
 *
 * Used when the user turns the PIN gate on without having configured a PIN. That path used to
 * fall back to the constant `"1234"` — the single most common LAN-gate PIN — which made the gate
 * decorative: anyone who has ever seen a router admin page could walk in. A fresh random PIN per
 * session, shown in the sync screen and the foreground notification, is strictly better and costs
 * the user nothing.
 *
 * Android-free so it can be unit-tested on the JVM.
 */
object SyncPinFactory {

    /** Digits in a generated PIN. Longer than the old default, still thumb-typable on a phone. */
    const val PIN_LENGTH = 6

    private val defaultRandom = SecureRandom()

    /** Smallest and count of the [PIN_LENGTH]-digit space with no leading zero. */
    private const val LOWEST = 100_000
    private const val SPAN = 900_000

    /** A PIN in `100000..999999`. Never constant, never leading-zero. */
    fun generatePin(random: SecureRandom = defaultRandom): String =
        (LOWEST + random.nextInt(SPAN)).toString()
}
