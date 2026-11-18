package app.yomu.domain.reader

import app.yomu.domain.model.ScaleType
import kotlin.math.min
import kotlin.math.sqrt

/** Quarter turn applied to a wide scan so it fills a portrait screen. */
enum class WidePageRotation { NONE, LEFT, RIGHT }

/** Where a page sits after a start zoom. */
enum class StartZoomAnchor { CENTER, START, END }

/** Pixel size of a decoded image or of the viewport that will show it. */
data class ImageSize(val width: Int, val height: Int) {
    val isUsable: Boolean get() = width > 0 && height > 0
}

/**
 * Decisions about how a page is presented that do not need a bitmap, a view or Android.
 *
 * The reader uses this for three settings that used to be absent: rotate-to-fit for a wide scan, a start zoom
 * (including a smart one that fills the width), and a decode budget so a very tall strip is subsampled instead of
 * decoded at full height.
 */
object MangaFit {
    /** Upper bound for one decoded page, in pixels. 12 MP is ~48 MB in ARGB_8888, below a typical phone heap. */
    const val MAX_DECODE_PIXELS: Int = 12_000_000

    /** A page is treated as a wide scan when it is meaningfully wider than tall. */
    private const val WIDE_ASPECT = 1.15f

    /** Smart fit does not scale a page further than this. */
    const val MAX_SMART_ZOOM: Float = 3f

    /**
     * A wide scan — one image holding a two-page spread — is split into halves; a normal portrait page is not.
     * An unknown size is not treated as wide: splitting a portrait page breaks the reading order.
     */
    fun isWide(pageWidth: Int, pageHeight: Int): Boolean =
        pageWidth > 0 && pageHeight > 0 && pageWidth > pageHeight * WIDE_ASPECT

    /**
     * Rotation for a page whose real pixel size is known. A wide scan is turned a quarter turn when the option is on;
     * right-to-left books turn the other way so the reading order of the spread is preserved.
     */
    fun widePageRotation(pageWidth: Int, pageHeight: Int, enabled: Boolean, rightToLeft: Boolean): WidePageRotation {
        if (!enabled || !isWide(pageWidth, pageHeight)) return WidePageRotation.NONE
        return if (rightToLeft) WidePageRotation.RIGHT else WidePageRotation.LEFT
    }

    /**
     * Zoom that fills the width of a page shown with FIT_SCREEN when the fit leaves wide empty margins.
     * A page that already fills the width, or is too small to benefit, keeps 1×.
     */
    fun smartZoom(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        maxZoom: Float = MAX_SMART_ZOOM,
    ): Float {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1f
        val fit = min(viewportWidth.toFloat() / imageWidth, viewportHeight.toFloat() / imageHeight)
        val renderedWidth = imageWidth * fit
        if (renderedWidth <= 0f) return 1f
        val fillWidth = viewportWidth / renderedWidth
        if (!fillWidth.isFinite() || fillWidth <= 1.02f) return 1f
        return fillWidth.coerceAtMost(maxZoom.coerceAtLeast(1f))
    }

    /**
     * Size to decode a page at. The result never upscales, follows [scale] and is always within [maxPixels], so a
     * 30 000 px strip cannot request a full-height bitmap. A caller that does not know the image size passes null and
     * keeps Coil's own sizing.
     */
    fun decodeSize(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        scale: ScaleType,
        maxPixels: Int = MAX_DECODE_PIXELS,
    ): ImageSize? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val budget = maxPixels.coerceAtLeast(1)
        var factor = when {
            scale == ScaleType.ORIGINAL -> 1f
            scale == ScaleType.FIT_WIDTH && viewportWidth > 0 -> viewportWidth.toFloat() / imageWidth
            scale == ScaleType.FIT_HEIGHT && viewportHeight > 0 -> viewportHeight.toFloat() / imageHeight
            viewportWidth > 0 && viewportHeight > 0 ->
                min(viewportWidth.toFloat() / imageWidth, viewportHeight.toFloat() / imageHeight)
            else -> 1f
        }
        if (!factor.isFinite() || factor <= 0f) factor = 1f
        // A decode larger than the original wastes memory; the display can scale up cheaply.
        factor = min(factor, 1f)
        val pixels = imageWidth.toDouble() * imageHeight.toDouble() * factor * factor
        if (pixels > budget) factor *= sqrt(budget / pixels).toFloat()
        val width = kotlin.math.floor(imageWidth * factor).toInt().coerceAtLeast(1)
        val height = kotlin.math.floor(imageHeight * factor).toInt().coerceAtLeast(1)
        return ImageSize(width, height)
    }
}
