package app.yomu.domain.reader

import app.yomu.domain.model.MangaDirection
import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.PdfSettings
import app.yomu.domain.model.TextReaderSettings

/** The engine rendering the open publication. */
enum class ReaderEngine { TEXT, PDF, MANGA }

/** The layout the engine is in right now; options that cannot act in it are not offered. */
enum class ReaderMode { PAGED, SCROLL, STRIP, REFLOW, SCAN }

/** The controls the reader can show. */
enum class ReaderOption {
    FONT, FONT_SIZE, THEME, MODE, COLUMNS, DUAL_PAGE, TAP_ZONES, PAGE_ANIMATION, BRIGHTNESS,
    TTS, TTS_SPEED, ZOOM_LOCK,
    MANGA_DIRECTION, MANGA_SPLIT, MANGA_SPREADS, MANGA_WEBTOON_GAPS, MANGA_ROTATE_WIDE,
    PDF_REFLOW, PDF_CROP, PDF_SCAN_FILTERS,
}

/**
 * Which controls make sense for an engine and a mode.
 *
 * The reader keeps the full settings for every engine, but a control that changes nothing in the current mode (dual
 * pages in a scrolling novel, crop margins in a reflowed PDF) is not offered: the user sees the options that act, not
 * a list where half of them are inert. The registry is pure, so the visibility rules are testable without a screen.
 */
object ReaderOptions {
    fun mode(s: TextReaderSettings): ReaderMode = when {
        s.longStrip -> ReaderMode.STRIP
        s.paged -> ReaderMode.PAGED
        else -> ReaderMode.SCROLL
    }

    fun mode(s: MangaSettings): ReaderMode =
        if (s.direction == MangaDirection.WEBTOON) ReaderMode.STRIP else ReaderMode.PAGED

    fun mode(s: PdfSettings): ReaderMode = if (s.reflow) ReaderMode.REFLOW else ReaderMode.SCAN

    private val commonPaged = setOf(
        ReaderOption.MODE, ReaderOption.TAP_ZONES, ReaderOption.PAGE_ANIMATION, ReaderOption.BRIGHTNESS, ReaderOption.ZOOM_LOCK,
    )
    private val commonScroll = setOf(ReaderOption.MODE, ReaderOption.TAP_ZONES, ReaderOption.BRIGHTNESS)
    private val text = setOf(
        ReaderOption.FONT, ReaderOption.FONT_SIZE, ReaderOption.THEME, ReaderOption.TTS, ReaderOption.TTS_SPEED,
    )
    private val mangaPages = setOf(
        ReaderOption.MANGA_DIRECTION, ReaderOption.MANGA_SPLIT, ReaderOption.MANGA_SPREADS, ReaderOption.MANGA_ROTATE_WIDE,
    )
    private val pdfScan = setOf(ReaderOption.PDF_REFLOW, ReaderOption.PDF_CROP, ReaderOption.PDF_SCAN_FILTERS)

    fun visible(engine: ReaderEngine, mode: ReaderMode): Set<ReaderOption> = when (engine) {
        ReaderEngine.TEXT -> text + when (mode) {
            ReaderMode.PAGED -> commonPaged + ReaderOption.COLUMNS + ReaderOption.DUAL_PAGE
            ReaderMode.SCROLL, ReaderMode.STRIP -> commonScroll
            else -> commonScroll
        }
        ReaderEngine.MANGA -> when (mode) {
            ReaderMode.STRIP -> commonScroll + ReaderOption.MANGA_DIRECTION + ReaderOption.MANGA_WEBTOON_GAPS
            else -> commonPaged + mangaPages
        }
        ReaderEngine.PDF -> when (mode) {
            ReaderMode.REFLOW -> setOf(ReaderOption.MODE, ReaderOption.PDF_REFLOW, ReaderOption.BRIGHTNESS, ReaderOption.TAP_ZONES)
            else -> commonPaged + pdfScan
        }
    }

    fun shows(engine: ReaderEngine, mode: ReaderMode, option: ReaderOption): Boolean = option in visible(engine, mode)
}
