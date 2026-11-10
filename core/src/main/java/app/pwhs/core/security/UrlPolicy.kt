package app.pwhs.core.security

import java.net.URI

/**
 * Validates a URL the LAN receiver was asked to download from.
 *
 * `POST /url` makes the device fetch an arbitrary address, which is a textbook SSRF primitive: an
 * unauthenticated client on the Wi-Fi could use the TV to probe the internal network or to hit a
 * `file:`/`content:` style handler. The endpoint is now behind the pairing token, and on top of
 * that only well-formed absolute `http`/`https` URLs are accepted.
 *
 * Android-free so it can be unit-tested on the JVM.
 */
object UrlPolicy {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /**
     * Returns [raw] when it is a safe download target, or null with the reason in [Reject].
     * Callers that only need a boolean can use [isAllowed].
     */
    fun normalize(raw: String?): String? {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        // Control characters allow request smuggling and log forgery further down the stack.
        if (candidate.any { it.isISOControl() }) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (!uri.isAbsolute) return null
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return null
        // `http://trusted.host@evil.example/a.apk` — the authority that actually gets contacted is
        // the part after the last '@', which is not what a human reading the URL would assume.
        if (!uri.userInfo.isNullOrEmpty()) return null
        if (uri.host.isNullOrBlank()) return null
        return candidate
    }

    fun isAllowed(raw: String?): Boolean = normalize(raw) != null
}
