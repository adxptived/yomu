package app.yomu.domain.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageActionContextTest {
    @Test
    fun `a manga page of a library book can be exported covered and recognised`() {
        val c = PageCapabilitiesOf.manga(bookId = 42)
        assertTrue(c.exportable)
        assertTrue(c.coverable)
        assertTrue(c.ocr)
        assertTrue(c.translatable)
        assertFalse(c.hasTextLayer)
    }

    @Test
    fun `an online manga page cannot become a cover`() {
        val c = PageCapabilitiesOf.manga(bookId = 0)
        assertTrue(c.exportable)
        assertFalse(c.coverable)
        assertFalse(c.notable)
    }

    @Test
    fun `a PDF page with a text layer is not treated as a scan`() {
        val c = PageCapabilitiesOf.pdf(bookId = 7, hasTextLayer = true)
        assertTrue(c.exportable)
        assertTrue(c.hasTextLayer)
        assertFalse(c.ocr)
    }

    @Test
    fun `a scanned PDF page falls back to OCR`() {
        val c = PageCapabilitiesOf.pdf(bookId = 7, hasTextLayer = false)
        assertFalse(c.hasTextLayer)
        assertTrue(c.ocr)
    }

    @Test
    fun `a text page is never exported as an image`() {
        val c = PageCapabilitiesOf.text(bookId = 7, hasTextLayer = true)
        assertFalse(c.exportable)
        assertFalse(c.coverable)
        assertFalse(c.ocr)
        assertTrue(c.translatable)
    }

    @Test
    fun `page identity is physical and validated against the page count`() {
        val c = PageContext(PageEngine.PDF, 7, 0, 0, page = 3, pageCount = 10, PageCapabilitiesOf.pdf(7, true))
        assertTrue(c.isValid)
        assertFalse(c.copy(page = 10).isValid)
        assertFalse(c.copy(page = -1).isValid)
        assertFalse(c.copy(pageCount = 0).isValid)
    }

    @Test
    fun `notes belong to library books only`() {
        val book = PageContext(PageEngine.MANGA, 7, 0, 0, 0, 5, PageCapabilitiesOf.manga(7))
        val online = PageContext(PageEngine.MANGA, 0, entryId = 99, chapterId = 1, page = 0, pageCount = 5, PageCapabilitiesOf.manga(0))
        assertTrue(book.acceptsNote)
        assertFalse(online.acceptsNote)
    }
}
