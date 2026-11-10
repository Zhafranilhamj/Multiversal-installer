package app.pwhs.core.receiver

/**
 * Turns an untrusted `filename="..."` from a LAN upload into something safe to write into the
 * staging directory.
 *
 * Two things are defended here: path traversal (`../../etc/foo`, absolute paths, Windows
 * separators) and names that clean down to nothing — an empty or all-dots name would either throw
 * or, worse, resolve to the staging directory itself.
 *
 * Android-free so it can be unit-tested on the JVM.
 */
object FileNameSanitizer {

    /** Long enough for real package names, short enough to stay under filesystem limits. */
    const val MAX_LENGTH = 120

    private const val DEFAULT_NAME = "received.apk"

    fun sanitize(rawName: String?, fallback: String = DEFAULT_NAME): String {
        if (rawName.isNullOrBlank()) return fallback
        val base = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        val cleaned = buildString(base.length) {
            for (ch in base) {
                append(if (ch.isLetterOrDigit() || ch in "-_.") ch else '_')
            }
        }
            // Leading dots would create hidden files, and "." / ".." are directory references.
            .trimStart('.')
            .take(MAX_LENGTH)
            // A trailing dot or space is silently dropped by some filesystems, leaving a
            // different name than the one we validated.
            .trimEnd('.', ' ')
        return cleaned.ifBlank { fallback }
    }
}
