package app.yomu.domain.reader

import app.yomu.domain.model.TapZoneLayout

/**
 * A position inside the reader viewport as fractions of its width and height.
 *
 * This is the only coordinate space tap zones are resolved in. Pixels from a gesture (in root or page
 * coordinates) must go through [ReaderInput.normalize] first; nothing downstream may compare raw pixels
 * with the fractions here.
 */
data class ReaderPoint(val x: Float, val y: Float) {
    /** True when the point lies inside the viewport, inclusive of the right and bottom edges. */
    val isInside: Boolean get() = x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f
}

/** What a tap on the reader does. */
enum class ReaderTapAction { PREV, NEXT, MENU }

/**
 * Converts gesture positions into [ReaderPoint]s and resolves the tap zone contract.
 *
 * The conversion needs the viewport rectangle in the same coordinate space as the incoming point —
 * the reader area, never the whole activity, a single page or a scrollable document, so scrolling,
 * crop, zoom and dual-page layouts keep the zones where the user sees them.
 */
object ReaderInput {
    /**
     * Converts a root point in pixels into viewport fractions.
     *
     * Returns null when the point or the viewport cannot be used: a zero, negative, NaN or infinite
     * width/height, a non-finite offset or point, and any touch that lands outside the viewport.
     * Callers must ignore the gesture on null instead of guessing a zone.
     */
    fun normalize(rootX: Float, rootY: Float, left: Float, top: Float, width: Float, height: Float): ReaderPoint? {
        if (!rootX.isFinite() || !rootY.isFinite()) return null
        if (!left.isFinite() || !top.isFinite()) return null
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
        val point = ReaderPoint((rootX - left) / width, (rootY - top) / height)
        return point.takeIf { it.isInside }
    }

    /** Converts a point that is already local to a viewport of the given size in pixels. */
    fun normalizeLocal(x: Float, y: Float, width: Float, height: Float): ReaderPoint? =
        normalize(x, y, 0f, 0f, width, height)

    /**
     * Action of a tap at [point] for [layout].
     *
     * [inverted] swaps the previous and next zones by the user's request; [rtl] additionally swaps them
     * for right-to-left reading. Both are ignored by [TapZoneLayout.EDGES], which is not a left/right
     * layout. A non-finite point is not a tap: it resolves to [ReaderTapAction.MENU].
     */
    fun action(
        layout: TapZoneLayout,
        inverted: Boolean,
        rtl: Boolean,
        point: ReaderPoint,
        sideFraction: Float = 0.4f,
    ): ReaderTapAction {
        val x = point.x
        val y = point.y
        if (!x.isFinite() || !y.isFinite()) return ReaderTapAction.MENU
        val raw = when (layout) {
            TapZoneLayout.DISABLED -> ReaderTapAction.MENU
            TapZoneLayout.LEFT_RIGHT -> when {
                x < sideFraction -> ReaderTapAction.PREV
                x > 1f - sideFraction -> ReaderTapAction.NEXT
                else -> ReaderTapAction.MENU
            }
            TapZoneLayout.EDGES -> when {
                x < 0.2f || x > 0.8f -> ReaderTapAction.NEXT
                y < 0.2f -> ReaderTapAction.PREV
                else -> ReaderTapAction.MENU
            }
            TapZoneLayout.KINDLE -> when {
                y < 0.2f -> ReaderTapAction.MENU
                x < 0.33f -> ReaderTapAction.PREV
                else -> ReaderTapAction.NEXT
            }
            TapZoneLayout.L_SHAPE -> when {
                y < 0.33f && x < 0.66f -> ReaderTapAction.PREV
                x < 0.33f && y < 0.66f -> ReaderTapAction.PREV
                y > 0.66f || x > 0.66f -> ReaderTapAction.NEXT
                else -> ReaderTapAction.MENU
            }
        }
        val flipped = inverted xor (rtl && layout != TapZoneLayout.EDGES)
        if (!flipped || raw == ReaderTapAction.MENU) return raw
        return if (raw == ReaderTapAction.NEXT) ReaderTapAction.PREV else ReaderTapAction.NEXT
    }
}
