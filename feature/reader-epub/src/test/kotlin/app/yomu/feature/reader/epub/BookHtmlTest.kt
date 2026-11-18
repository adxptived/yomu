package app.yomu.feature.reader.epub

import app.yomu.domain.model.TocItem
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BookHtmlTest {
    @Test
    fun `chapters become sections of one document with absolute addresses`() = runBlocking {
        val doc = FakeDoc(
            listOf(
                "OEBPS/text/ch1.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml"><head>
                      <title>One</title>
                      <link rel="stylesheet" href="../styles/book.css"/>
                      <style>body{background:url(../img/bg.png)}</style>
                      <script>alert(1)</script>
                    </head><body class="chapter">
                      <p id="note1"><img src="../img/a.png" srcset="../img/a.png 1x, ../img/a@2x.png 2x"/></p>
                      <p><a href="#note1">note</a> <a href="ch2.xhtml#n2">next</a></p>
                    </body></html>
                """.trimIndent(),
                "OEBPS/text/ch2.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml"><head>
                      <link rel="stylesheet" href="../styles/book.css"/>
                    </head><body><p id="n2">Two</p></body></html>
                """.trimIndent(),
            ),
        )

        val html = requireNotNull(BookHtml.build(doc))

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains(BookHtml.MARKER))
        assertEquals(2, Regex("<section data-yomu-chapter=\"").findAll(html).count())
        assertTrue(html.contains("data-yomu-chapter=\"0\" class=\"chapter\""), html)
        // The shared stylesheet is linked once for the whole book, and the inline style is rewritten in place.
        assertEquals(1, Regex("/OEBPS/styles/book.css").findAll(html).count())
        assertTrue(html.contains("url(\"/OEBPS/img/bg.png\")"), html)
        // A chapter is a section now: relative files must not resolve against the book document.
        assertTrue(html.contains("/OEBPS/img/a.png"), html)
        assertTrue(html.contains("/OEBPS/img/a@2x.png"), html)
        assertTrue(html.contains("href=\"/OEBPS/text/ch2.xhtml#n2\""), html)
        // A same-document fragment stays a fragment (the runtime scopes ids to the chapter on screen).
        assertTrue(html.contains("href=\"#note1\""), html)
        assertFalse(html.contains("<script"), html)
    }

    @Test
    fun `chapter styles are scoped and a linked sheet is inlined for its chapter`() = runBlocking {
        val doc = FakeDoc(
            listOf(
                "OEBPS/text/ch1.xhtml" to """
                    <html><head><style>.note { color: red }</style></head><body><p class="note">One</p></body></html>
                """.trimIndent(),
                "OEBPS/text/ch2.xhtml" to """
                    <html><head>
                      <link rel="stylesheet" href="../styles/two.css"/>
                      <style>.note { display: none }</style>
                    </head><body><p class="note">Two</p></body></html>
                """.trimIndent(),
            ),
            resources = mapOf(
                "OEBPS/styles/two.css" to """
                    .note { visibility: hidden }
                    body { margin: 1px }
                    @media (min-width: 600px) { .note { font-size: 120% } }
                """.trimIndent(),
            ),
        )

        val html = requireNotNull(BookHtml.build(doc))

        // The same class keeps two different rules: each is confined to its own chapter.
        assertTrue(html.contains("section[data-yomu-chapter=\"0\"] .note{ color: red }"), html)
        assertTrue(html.contains("section[data-yomu-chapter=\"1\"] .note{ display: none }"), html)
        assertTrue(html.contains("section[data-yomu-chapter=\"1\"] .note{ visibility: hidden }"), html)
        assertTrue(html.contains("section[data-yomu-chapter=\"1\"]{ margin: 1px }"), html)
        assertTrue(html.contains("@media (min-width: 600px)"), html)
        // The sheet was inlined rather than linked unscoped.
        assertFalse(html.contains("href=\"/OEBPS/styles/two.css\""), html)
        assertTrue(Regex("<style>\\s*\\.note").findAll(html).none(), html)
    }

    private class FakeDoc(
        private val files: List<Pair<String, String>>,
        private val resources: Map<String, String> = emptyMap(),
    ) : TextDocument {
        override val title: String = "Test"
        override val language: String = "en"
        override val rtl: Boolean = false
        override val chapterCount: Int = files.size
        override val toc: List<TocItem> = emptyList()
        override fun chapterPath(index: Int): String = files[index].first
        override fun chapterIndexOf(path: String): Int = files.indexOfFirst { it.first == path }
        override fun chapterTitle(index: Int): String = "Chapter ${index + 1}"
        override fun weight(index: Int): Long = files[index].second.length.toLong()
        override suspend fun chapterHtml(index: Int): String = files[index].second
        override fun resource(path: String): ByteArray? = resources[path]?.toByteArray()
        override fun close() = Unit
    }
}
