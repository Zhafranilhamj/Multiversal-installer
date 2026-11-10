package app.pwhs.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlEscapeTest {

    @Test
    fun `escapes every html significant character`() {
        assertEquals(
            "&amp;&lt;&gt;&quot;&#39;",
            HtmlEscape.escape("&<>\"'"),
        )
    }

    @Test
    fun `hostile apk file name cannot open a script tag`() {
        val fileName = "<img src=x onerror=alert(1)>.apk"
        val escaped = HtmlEscape.escape(fileName)

        assertFalse("raw '<' must not survive", escaped.contains("<"))
        assertFalse("raw '>' must not survive", escaped.contains(">"))
        assertFalse("onerror attribute must be inert", escaped.contains("onerror=alert(1)>"))
        assertTrue(escaped.endsWith("&gt;.apk"))
    }

    @Test
    fun `attribute value breakout is escaped`() {
        val escaped = HtmlEscape.escape("""a" onload="alert(1)""")
        assertEquals("a&quot; onload=&quot;alert(1)", escaped)
    }

    @Test
    fun `plain values pass through untouched`() {
        assertEquals("app-release.apk", HtmlEscape.escape("app-release.apk"))
        assertEquals("Café Münchén 日本語", HtmlEscape.escape("Café Münchén 日本語"))
    }

    @Test
    fun `null and empty input yield empty string`() {
        assertEquals("", HtmlEscape.escape(null))
        assertEquals("", HtmlEscape.escape(""))
    }

    @Test
    fun `header quoting strips crlf and escapes quotes`() {
        assertEquals(
            """evil\".apk""",
            HtmlEscape.headerQuoted("""evil".apk"""),
        )
        assertFalse(
            HtmlEscape.headerQuoted("a\r\nSet-Cookie: x=1").contains("\n"),
        )
    }
}
