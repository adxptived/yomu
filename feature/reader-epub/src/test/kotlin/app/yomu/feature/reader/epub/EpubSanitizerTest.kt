package app.yomu.feature.reader.epub

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Removing only `<script>` left `<img onerror=…>`, iframes and `javascript:` links active (audit A03). */
class EpubSanitizerTest {
    private fun sanitized(html: String) = Jsoup.parse(html).also { sanitizeChapter(it) }

    @Test
    fun `handlers, nested documents and plugins are removed`() {
        val doc = sanitized(
            """
            <html><head><script>alert(1)</script></head><body>
              <p onclick="steal()">text</p>
              <img src="cover.png" onerror="alert(2)">
              <iframe src="https://evil.example/"></iframe>
              <object data="x.swf"></object>
              <embed src="x.swf">
              <form action="https://evil.example/"><input name="q"></form>
            </body></html>
            """.trimIndent(),
        )
        assertTrue(doc.select("script, iframe, object, embed, form").isEmpty())
        assertFalse(doc.selectFirst("p")!!.hasAttr("onclick"))
        assertFalse(doc.selectFirst("img")!!.hasAttr("onerror"))
        // The book's own image still points at its packaged file.
        assertEquals("cover.png", doc.selectFirst("img")!!.attr("src"))
        assertTrue(doc.text().contains("text"))
    }

    @Test
    fun `javascript and html data addresses are dropped but normal links stay`() {
        val doc = sanitized(
            """
            <html><body>
              <a href="javascript:alert(3)">bad</a>
              <a href="chapter2.xhtml#n">good</a>
              <img src="data:text/html;base64,PHNjcmlwdD4=">
              <svg xmlns="http://www.w3.org/2000/svg"><a xlink:href="javascript:alert(4)"><text>x</text></a></svg>
            </body></html>
            """.trimIndent(),
        )
        val links = doc.select("a")
        assertFalse(links[0].hasAttr("href"))
        assertEquals("chapter2.xhtml#n", links[1].attr("href"))
        assertFalse(doc.selectFirst("img")!!.hasAttr("src"))
        assertTrue(doc.select("svg a").all { !it.hasAttr("xlink:href") })
    }

    @Test
    fun `meta refresh cannot navigate away`() {
        val doc = sanitized("""<html><head><meta http-equiv="refresh" content="0;url=https://evil.example/"></head><body>x</body></html>""")
        assertTrue(doc.select("meta[http-equiv=refresh]").isEmpty())
    }
}
