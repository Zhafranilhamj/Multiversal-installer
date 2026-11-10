package app.pwhs.core.security

import java.security.SecureRandom

/**
 * The shared secret that pairs a phone with a TV's LAN receiver.
 *
 * The receiver's `/upload` and `/url` endpoints used to accept anything from anywhere on the
 * Wi-Fi: anyone who could see the TV's NSD advertisement could push an arbitrary APK to it, and
 * `/url` was an unauthenticated way to make the TV fetch any URL at all. The token is minted when
 * the receiver starts, published only through the on-screen QR code and the NSD TXT record, and
 * required on every mutating endpoint.
 *
 * The transport is a header rather than a query parameter wherever possible — URLs end up in
 * server logs and browser history, headers do not. The query form is still accepted because the
 * QR code has to carry it (a scanned QR opens a plain `http://` URL) and because
 * `navigator.sendBeacon` cannot set headers.
 *
 * Android-free so it can be unit-tested on the JVM.
 */
object PairingToken {

    /** Request header the phone client sends. */
    const val HEADER = "X-UI-Token"

    /** Query parameter the QR code carries and the browser page falls back to. */
    const val QUERY_PARAM = "token"

    /** 128 bits of entropy, rendered as 32 lowercase hex characters. */
    const val TOKEN_BYTES = 16

    private val defaultRandom = SecureRandom()

    fun generate(random: SecureRandom = defaultRandom): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Appends [token] to [baseUrl] as a query parameter, respecting an existing query string. */
    fun appendToUrl(baseUrl: String, token: String): String {
        if (token.isBlank()) return baseUrl
        val separator = if (baseUrl.contains('?')) "&" else "?"
        return "$baseUrl$separator$QUERY_PARAM=$token"
    }

    /**
     * Pulls the token out of a request: header first, then query parameter. Returns null when the
     * client sent neither.
     */
    fun extract(headerValue: String?, queryValue: String?): String? =
        headerValue?.takeIf { it.isNotBlank() } ?: queryValue?.takeIf { it.isNotBlank() }
}
