package app.yomu.feature.reader.manga

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ColorMatrix
import app.yomu.data.book.PageSource
import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.TapZoneLayout
import app.yomu.domain.reader.ImageSize
import app.yomu.domain.reader.MangaFit
import app.yomu.domain.reader.ReaderInput
import app.yomu.domain.reader.ReaderPoint
import app.yomu.domain.reader.ReaderTapAction
import app.yomu.domain.reader.WidePageRotation
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

/** A text block recognised on a page, with coordinates as fractions of the image. */
data class OverlayBlock(val left: Float, val top: Float, val right: Float, val bottom: Float, val original: String, val translated: String?)

/** State of the image based reader. */
class MangaController(
    val pages: PageSource,
    /** The chapters of an online window; null for a local publication, where the pager spans one chapter. */
    val session: SessionPageSource? = pages as? SessionPageSource,
) {
    val count: Int get() = pages.count

    /** The flat page the pager shows: inside the session for online chapters, inside the file for a local book. */
    var page by mutableIntStateOf(0)

    /** Bumped by "fit the page": every page drops its temporary zoom and pan. */
    var zoomReset by mutableIntStateOf(0)
        private set

    fun resetZoom() {
        lockedZoom = 1f
        zoomReset++
    }

    /** The chapter loaded at a flat page, or null for a local publication. */
    fun chapterAt(index: Int): MangaChapter? = session?.chapterAt(index)

    /** The chapter of the current page. */
    val chapter: MangaChapter? get() = chapterAt(page)

    /** Page inside the current chapter; equals [page] when there is no window. */
    val localPage: Int get() = session?.pageAt(page) ?: page

    /** Pages of the current chapter; equals [count] when there is no window. */
    val localCount: Int get() = chapter?.count ?: count

    /** The flat index of [localPage] in [chapterId]; null when that chapter is not loaded. */
    fun globalIndex(chapterId: Long, localPage: Int): Int? =
        if (session != null) session.locate(chapterId, localPage) else localPage.takeIf { it in 0 until count }

    /** The flat index of a page of the current chapter. */
    fun globalOfCurrent(localPage: Int): Int =
        chapter?.let { session?.locate(it.id, localPage) } ?: localPage.coerceIn(0, (count - 1).coerceAtLeast(0))

    /** The page inside its chapter of a flat index (the identity for a local publication). */
    fun localOf(index: Int): Int = session?.pageAt(index) ?: index

    /**
     * True when a chapter follows the last chapter loaded in the window, so the end page leads somewhere. It is not
     * the same as "the current chapter has a next one": the next chapter is already inside the window.
     */
    var hasNextAfterWindow by mutableStateOf(false)

    /**
     * The chapter breaks the pager draws: the last flat page of every loaded chapter except the last, with the title
     * of the chapter that follows. A break is an ordinary page turn, not a reload.
     */
    fun chapterBreaks(): Map<Int, String> {
        val s = session ?: return emptyMap()
        return s.chapters.zipWithNext().associate { (chapter, next) ->
            (s.startOf(chapter.id)!! + chapter.count - 1) to next.title
        }
    }

    var settings by mutableStateOf(MangaSettings())
    val overlays = mutableStateMapOf<Int, List<OverlayBlock>>()
    var overlayVisible by mutableStateOf(true)

    /** Cap on the pages preloaded ahead (the data saver on mobile data); null = the preload setting alone. */
    var preloadLimit by mutableStateOf<Int?>(null)

    /** Pages preloaded ahead of the current one. */
    val preloadAhead: Int get() = preloadLimit?.let { minOf(it, settings.preload) } ?: settings.preload

    /** Zoom kept for the next pages while [MangaSettings.zoomLock] is on. */
    var lockedZoom by mutableFloatStateOf(1f)

    /**
     * Pixel sizes learned from page headers, by physical page. Split mode uses them to cut only wide scans; the map is
     * observable so the slot list follows when a page ahead is measured.
     */
    val pageSizes = androidx.compose.runtime.mutableStateMapOf<Int, ImageSize>()

    /** Records a size seen by the image loader or read from a page; an unusable size is ignored. */
    fun noteSize(index: Int, size: ImageSize?) {
        if (size != null && size.isUsable && pageSizes[index] != size) pageSizes[index] = size
    }

    /** True when the page is a wide scan (the only pages split mode cuts); an unknown page is not wide. */
    fun isWide(index: Int): Boolean = pageSizes[index]?.let { MangaFit.isWide(it.width, it.height) } == true

    /** Reads a page size without materialising the page when the source can provide it. */
    suspend fun size(index: Int): ImageSize? = pageSizes[index] ?: pages.size(index)?.also { noteSize(index, it) }

    /** Incremented to show the tap zone overlay for a few seconds. */
    var tapZonesHint by mutableIntStateOf(0)
        private set

    fun showTapZones() {
        tapZonesHint++
    }
    private val requests = Channel<Int>(Channel.CONFLATED)
    val pageRequests: Flow<Int> = requests.receiveAsFlow()

    suspend fun file(index: Int): File = pages.file(index.coerceIn(0, (count - 1).coerceAtLeast(0)))
    suspend fun previewFile(index: Int): File? = if (index in 0 until count) pages.previewFile(index) else null

    fun goTo(index: Int) {
        requests.trySend(index)
    }

    fun next() = goTo(page + 1)
    fun prev() = goTo(page - 1)
}

enum class TapAction { PREV, NEXT, MENU }

/**
 * Manga-facing adapter over [ReaderInput]: the zone logic itself lives in `domain` so the PDF and text
 * readers resolve taps exactly like this one. New code should call [ReaderInput] directly.
 */
object TapZones {
    /** Action for a tap at fractions [x], [y] of the reader. */
    fun action(layout: TapZoneLayout, inverted: Boolean, rtl: Boolean, x: Float, y: Float, sideFraction: Float = 0.4f): TapAction =
        when (ReaderInput.action(layout, inverted, rtl, ReaderPoint(x, y), sideFraction)) {
            ReaderTapAction.PREV -> TapAction.PREV
            ReaderTapAction.NEXT -> TapAction.NEXT
            ReaderTapAction.MENU -> TapAction.MENU
        }
}

object ImageFilters {
    /** Colour matrix for brightness (-1..1), contrast (0.5..2), grayscale and invert. */
    fun matrix(s: MangaSettings): ColorMatrix? {
        if (s.brightness == 0f && s.contrast == 1f && !s.grayscale && !s.invert) return null
        val m = ColorMatrix()
        if (s.grayscale) m.setToSaturation(0f)
        val c = s.contrast
        val t = (1f - c) * 128f + s.brightness * 255f
        val contrast =
            ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
        m.timesAssign(contrast)
        if (s.invert) m.timesAssign(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
        return m
    }
}

/** Removes uniform white or black borders around a scanned page. */
class CropBordersTransformation(private val threshold: Int = 28) : Transformation() {
    override val cacheKey: String = "crop-borders-$threshold"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 32 || h < 32) return input
        val step = maxOf(1, minOf(w, h) / 300)
        val ref = input.getPixel(0, 0)
        fun differs(c: Int): Boolean {
            val dr = ((c shr 16) and 0xFF) - ((ref shr 16) and 0xFF)
            val dg = ((c shr 8) and 0xFF) - ((ref shr 8) and 0xFF)
            val db = (c and 0xFF) - (ref and 0xFF)
            return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db) > threshold * 3
        }
        fun rowInk(y: Int) = (0 until w step step).any { differs(input.getPixel(it, y)) }
        fun colInk(x: Int) = (0 until h step step).any { differs(input.getPixel(x, it)) }
        val top = (0 until h step step).firstOrNull { rowInk(it) } ?: 0
        val bottom = (h - 1 downTo 0 step step).firstOrNull { rowInk(it) } ?: (h - 1)
        val left = (0 until w step step).firstOrNull { colInk(it) } ?: 0
        val right = (w - 1 downTo 0 step step).firstOrNull { colInk(it) } ?: (w - 1)
        val cw = right - left + 1
        val ch = bottom - top + 1
        if (cw < w / 3 || ch < h / 3 || (cw >= w - 2 && ch >= h - 2)) return input
        return Bitmap.createBitmap(input, left, top, cw, ch)
    }
}

/**
 * Turns a landscape scan a quarter turn so it fills a portrait screen. Portrait pages and an option turned off
 * return the bitmap untouched, so the same cached request can be reused when the page is not wide.
 */
class RotateWideTransformation(private val rotation: WidePageRotation) : Transformation() {
    override val cacheKey: String = "rotate-wide-${rotation.name.lowercase()}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (rotation == WidePageRotation.NONE) return input
        if (input.width <= input.height * 1.15f) return input
        val degrees = if (rotation == WidePageRotation.LEFT) -90f else 90f
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
    }
}

/**
 * One half of a wide scan, for split pages. The bitmap is cut, not scaled: each half then fits the screen as a whole
 * page would, and the physical page identity stays with the reader (see MangaPaging).
 */
class SplitPageTransformation(private val secondHalf: Boolean) : Transformation() {
    override val cacheKey: String = if (secondHalf) "split-right" else "split-left"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val half = input.width / 2
        if (half < 2) return input
        val x = if (secondHalf) input.width - half else 0
        return Bitmap.createBitmap(input, x, 0, half, input.height)
    }
}
