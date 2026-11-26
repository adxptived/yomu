package app.yomu.feature.reader

import kotlin.math.roundToInt

internal fun pageAtSeekFraction(fraction: Float, count: Int): Int =
    (fraction.coerceIn(0f, 1f) * (count - 1).coerceAtLeast(0)).roundToInt()

internal fun pageSeekFraction(page: Int, count: Int): Float =
    if (count <= 1) 1f else page.coerceIn(0, count - 1).toFloat() / (count - 1)

internal fun seekTrackPoint(start: Float, end: Float, fraction: Float, reversed: Boolean): Float {
    val progress = fraction.coerceIn(0f, 1f)
    return start + (end - start) * if (reversed) 1f - progress else progress
}

internal fun seekPreviewOffset(anchor: Int, contentSize: Int, viewportSize: Int, margin: Int): Int {
    val available = (viewportSize - contentSize).coerceAtLeast(0)
    val inset = margin.coerceIn(0, available / 2)
    return (anchor - contentSize / 2).coerceIn(inset, available - inset)
}

/**
 * The next search result around [active], wrapping at both ends. A search that was only listed but not opened starts
 * at the first or the last result, depending on the direction.
 */
internal fun nextHitIndex(active: Int?, count: Int, delta: Int): Int? {
    if (count <= 0) return null
    if (active == null) return if (delta >= 0) 0 else count - 1
    return ((active + delta) % count + count) % count
}
