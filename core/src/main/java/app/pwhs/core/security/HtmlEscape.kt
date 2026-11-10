package app.pwhs.core.security

/**
 * Minimal HTML escaping for the two LAN HTTP servers this project ships
 * ([app.pwhs.core.receiver.ApkReceiverServer] and the phone's sync share).
 *
 * Both interpolate values they do not control — file names read off shared storage, exception
 * messages, download URLs — straight into an HTML response. Android filesystems happily allow
 * `<`, `>` and `"` in a file name, so a file called `<img src=x onerror=alert(1)>.apk` dropped
 * into the share folder used to execute script in every browser that opened the page.
 *
 * Escaping `&<>"'` is enough for both text nodes and double-quoted attribute values, which is
 * all these templates use.
 */
object HtmlEscape {

    /** Returns [value] with the five HTML-significant characters replaced by entities. */
    fun escape(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        // Fast path: most interpolated values are plain file names or URLs.
        if (value.none { it == '&' || it == '<' || it == '>' || it == '"' || it == '\'' }) return value
        val out = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * Escapes a value destined for a `Content-Disposition` filename. Quotes and backslashes are
     * the only characters that can break out of the quoted-string, and CR/LF would let a caller
     * inject an extra response header.
     */
    fun headerQuoted(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "")
    }
}
