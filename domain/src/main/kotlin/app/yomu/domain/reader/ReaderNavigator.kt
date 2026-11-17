package app.yomu.domain.reader

import kotlin.math.roundToInt

/** Side of the reader that carries the optional vertical page slider. */
enum class NavigatorSide { OFF, LEFT, RIGHT }

/**
 * Drag state of the vertical page slider.
 *
 * The thumb follows the finger rather than the incoming reading progress: while dragging, a position
 * reported by the engine (a slow image, a chapter boundary) must not pull the thumb back. Only the release
 * produces the value to commit, and releasing without dragging commits nothing.
 */
data class NavigatorSeek(val dragging: Boolean = false, val draft: Float = 0f) {
    /** What the thumb shows for an incoming [progress]. */
    fun thumb(progress: Float): Float = if (dragging) draft else progress.clampFraction()

    /** Starts a drag from the current position. */
    fun start(progress: Float): NavigatorSeek = copy(dragging = true, draft = progress.clampFraction())

    /** Moves the draft while dragging; a move outside a drag is ignored. */
    fun drag(fraction: Float): NavigatorSeek = if (dragging) copy(draft = fraction.clampFraction()) else this

    /** Ends a drag. The returned commit is the fraction to apply, or null when nothing was dragged. */
    fun release(): NavigatorRelease = if (!dragging) NavigatorRelease(this, null) else NavigatorRelease(copy(dragging = false), draft)

    /** A cancelled drag keeps the last reading progress. */
    fun cancel(): NavigatorSeek = copy(dragging = false)
}

data class NavigatorRelease(val state: NavigatorSeek, val commit: Float?)

object ReaderNavigator {
    /** 1-based page for a 0..1 fraction of the book. */
    fun pageAt(fraction: Float, pages: Int): Int {
        if (pages <= 0) return 1
        if (pages == 1) return 1
        return (fraction.clampFraction() * (pages - 1)).roundToInt() + 1
    }

    /** 0..1 position of a 1-based page. */
    fun fractionOf(page: Int, pages: Int): Float {
        if (pages <= 1) return 0f
        return ((page - 1).toFloat() / (pages - 1)).clampFraction()
    }
}

private fun Float.clampFraction(): Float = if (isNaN()) 0f else coerceIn(0f, 1f)
