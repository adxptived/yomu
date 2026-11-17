package app.yomu.domain.reader

import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.PageTurnAnimation
import app.yomu.domain.model.StartZoomAnchor
import app.yomu.domain.model.TextAlignment
import app.yomu.domain.model.TextReaderSettings

/**
 * An accessible/E-ink preset as a set of ordinary settings, not a second reader engine (N03).
 *
 * It makes text larger with more line spacing, turns page animation off, and gives manga pages a little more contrast
 * without a filter that would hide halftones. Because it is a normal change, the existing "Undo the last change" takes
 * it back. A hardware panel refresh is not touched: there is no verified vendor API, so the only flash is the optional
 * software one below.
 */
object EinkPreset {
    /** How long a software flash covers the page; short enough not to feel like a loading state. */
    const val FLASH_MS = 140L

    fun text(current: TextReaderSettings): TextReaderSettings = current.copy(
        fontSizePercent = maxOf(current.fontSizePercent, 125),
        lineHeight = maxOf(current.lineHeight, 1.65f),
        paragraphSpacing = maxOf(current.paragraphSpacing, 0.6f),
        indent = 0f,
        align = TextAlignment.START,
        hyphenation = false,
        letterSpacing = maxOf(current.letterSpacing, 0.02f),
        wordSpacing = maxOf(current.wordSpacing, 0.08f),
        pageAnimation = PageTurnAnimation.NONE,
    )

    fun manga(current: MangaSettings): MangaSettings = current.copy(
        pageAnimation = false,
        smartFit = false,
        startZoom = 1f,
        startZoomAnchor = StartZoomAnchor.CENTER,
        contrast = maxOf(current.contrast, 1.15f),
    )

    /**
     * Whether a software flash belongs after reaching [page] (1-based physical page). It only runs every [every]
     * pages, never on the first page, and never when the option is off.
     */
    fun flashDue(page: Int, every: Int): Boolean = every > 0 && page > 0 && page % every == 0
}
