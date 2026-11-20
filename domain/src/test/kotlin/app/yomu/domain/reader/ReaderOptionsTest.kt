package app.yomu.domain.reader

import app.yomu.domain.model.MangaDirection
import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.PdfSettings
import app.yomu.domain.model.TextReaderSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** N02: a control is offered only where it acts. */
class ReaderOptionsTest {

    @Test
    fun `a paged novel offers columns and dual pages, a scrolling one does not`() {
        val paged = ReaderOptions.mode(TextReaderSettings(paged = true))
        val scroll = ReaderOptions.mode(TextReaderSettings(paged = false))
        assertTrue(ReaderOptions.shows(ReaderEngine.TEXT, paged, ReaderOption.COLUMNS))
        assertTrue(ReaderOptions.shows(ReaderEngine.TEXT, paged, ReaderOption.DUAL_PAGE))
        assertFalse(ReaderOptions.shows(ReaderEngine.TEXT, scroll, ReaderOption.COLUMNS))
        assertFalse(ReaderOptions.shows(ReaderEngine.TEXT, scroll, ReaderOption.DUAL_PAGE))
        // Typography and speech work in both.
        assertTrue(ReaderOptions.shows(ReaderEngine.TEXT, scroll, ReaderOption.FONT_SIZE))
        assertTrue(ReaderOptions.shows(ReaderEngine.TEXT, scroll, ReaderOption.TTS))
    }

    @Test
    fun `a webtoon offers gaps and not spread controls`() {
        val mode = ReaderOptions.mode(MangaSettings(direction = MangaDirection.WEBTOON))
        assertTrue(ReaderOptions.shows(ReaderEngine.MANGA, mode, ReaderOption.MANGA_WEBTOON_GAPS))
        assertFalse(ReaderOptions.shows(ReaderEngine.MANGA, mode, ReaderOption.MANGA_SPREADS))
        assertFalse(ReaderOptions.shows(ReaderEngine.MANGA, mode, ReaderOption.MANGA_SPLIT))
    }

    @Test
    fun `manga pages offer split, spreads and direction`() {
        val mode = ReaderOptions.mode(MangaSettings(direction = MangaDirection.RTL))
        assertTrue(ReaderOptions.shows(ReaderEngine.MANGA, mode, ReaderOption.MANGA_SPREADS))
        assertTrue(ReaderOptions.shows(ReaderEngine.MANGA, mode, ReaderOption.MANGA_SPLIT))
        assertTrue(ReaderOptions.shows(ReaderEngine.MANGA, mode, ReaderOption.MANGA_DIRECTION))
    }

    @Test
    fun `a reflowed PDF does not offer crop margins, a scanned one does`() {
        val reflow = ReaderOptions.mode(PdfSettings(reflow = true))
        val scan = ReaderOptions.mode(PdfSettings(reflow = false))
        assertFalse(ReaderOptions.shows(ReaderEngine.PDF, reflow, ReaderOption.PDF_CROP))
        assertTrue(ReaderOptions.shows(ReaderEngine.PDF, reflow, ReaderOption.PDF_REFLOW))
        assertTrue(ReaderOptions.shows(ReaderEngine.PDF, scan, ReaderOption.PDF_CROP))
        assertTrue(ReaderOptions.shows(ReaderEngine.PDF, scan, ReaderOption.PDF_SCAN_FILTERS))
    }

    @Test
    fun `an engine never offers another engine's controls`() {
        val text = ReaderOptions.visible(ReaderEngine.TEXT, ReaderMode.PAGED)
        val pdf = ReaderOptions.visible(ReaderEngine.PDF, ReaderMode.SCAN)
        assertFalse(ReaderOption.PDF_CROP in text)
        assertFalse(ReaderOption.FONT_SIZE in pdf)
        assertFalse(ReaderOption.TTS in pdf)
    }
}
