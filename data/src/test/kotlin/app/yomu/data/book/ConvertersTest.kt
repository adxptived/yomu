package app.yomu.data.book

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ConvertersTest {
    @Test
    fun `txt chapters are detected by headings`() {
        val text = "Глава 1\n\nПервый абзац\nпродолжение.\n\nВторой абзац.\n\nГлава 2\n\nТекст второй главы, довольно длинный для определения языка.\n"
        val conv = TxtConverter.convert(text.toByteArray(), "Моя книга.txt")
        assertEquals("Моя книга", conv.builder.meta.title)
        assertEquals("ru", conv.builder.meta.language)
        assertEquals(listOf("Глава 1", "Глава 2"), conv.builder.chapters.map { it.title })
        assertTrue(conv.builder.chapters[0].xhtml.contains("<p>Первый абзац продолжение.</p>"), conv.builder.chapters[0].xhtml)
    }

    @Test
    fun `txt without headings becomes one chapter`() {
        val conv = TxtConverter.convert("Just a line.\nAnother line.".toByteArray(), "notes.txt")
        assertEquals(1, conv.builder.chapters.size)
        assertEquals("notes", conv.builder.chapters[0].title)
    }

    @Test
    fun `legacy cyrillic encoding is decoded`() {
        val original = "Привет, мир! Это проверка кодировки."
        assertEquals(original, TextDecoding.decode(original.toByteArray(charset("windows-1251"))))
        assertEquals(original, TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + original.toByteArray()))
        assertEquals(original, TextDecoding.decode(original.toByteArray(Charsets.UTF_16LE).let { byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it }))
    }

    @Test
    fun `fb2 sections become chapters`() {
        val fb2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
              <description>
                <title-info>
                  <author><first-name>Лев</first-name><last-name>Толстой</last-name></author>
                  <book-title>Война и мир</book-title>
                  <lang>ru</lang>
                </title-info>
              </description>
              <body>
                <section><title><p>Часть первая</p></title><p>Текст <emphasis>первой</emphasis> части.</p></section>
                <section><title><p>Часть вторая</p></title><p>Текст второй части.</p></section>
              </body>
            </FictionBook>
        """.trimIndent()
        val conv = Fb2Converter.convert(fb2.toByteArray(), "war.fb2")
        assertEquals("Война и мир", conv.builder.meta.title)
        assertEquals(listOf("Лев Толстой"), conv.builder.meta.authors)
        assertEquals("ru", conv.builder.meta.language)
        val titles = conv.builder.chapters.map { it.title }
        assertTrue(titles.containsAll(listOf("Часть первая", "Часть вторая")), titles.toString())
        assertTrue(conv.builder.chapters.any { it.xhtml.contains("<em>первой</em>") })
    }

    @Test
    fun `html is converted to a chapter`() {
        val conv = HtmlConverter.convert("<html><head><title>Page</title></head><body><h1>Head</h1><p>Body text</p></body></html>".toByteArray(), "page.html")
        assertTrue(conv.builder.chapters.isNotEmpty())
        assertTrue(conv.builder.chapters.joinToString { it.xhtml }.contains("Body text"))
    }

    @Test
    fun `docx is one document with its headings as a contents outline`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter One</w:t></w:r></w:p>
                <w:p><w:r><w:t>First paragraph.</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter Two</w:t></w:r></w:p>
                <w:p><w:r><w:t>Second paragraph.</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val conv = DocxConverter.convert(MapContainer(mapOf("word/document.xml" to xml.toByteArray())), "report.docx")
        // No cut at Heading 1: the whole document is one chapter, so pages never reset between headings.
        assertEquals(1, conv.builder.chapters.size)
        assertEquals(listOf("Chapter One", "Chapter Two"), conv.builder.toc.map { it.title })
        assertEquals(listOf("yomu-h1-1", "yomu-h1-2"), conv.builder.toc.map { it.anchor })
        assertEquals(conv.builder.chapters[0].href, conv.builder.toc[0].href)
        val body = conv.builder.chapters[0].xhtml
        assertTrue(body.contains("First paragraph."), body)
        assertTrue(body.contains("Second paragraph."), body)
        assertTrue(body.contains("id=\"yomu-h1-2\""), body)
        assertNotNull(conv.builder.toc[1].anchor)
    }

    private class MapContainer(private val files: Map<String, ByteArray>) : ResourceContainer {
        override val entries: List<String> = files.keys.toList()
        override val entrySet: Set<String> = files.keys
        override fun open(path: String) = files[path]?.let { ByteArrayInputStream(it) }
        override fun close() = Unit
    }
}
