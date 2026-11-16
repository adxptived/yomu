package app.yomu.core.ui.components

/**
 * How a wheel with more actions than fit is shown.
 *
 * A ring of 20 tiny sectors cannot be hit with a thumb and cannot be described by a screen reader. Above
 * [MAX_WHEEL_SECTORS] the wheel keeps the first `MAX_WHEEL_SECTORS - 1` actions and adds one "More" sector that opens
 * the remaining ones as a plain list. Every action stays reachable, and the ring keeps its size and geometry.
 */
object WheelLayout {
    /** Sectors on a ring, including "More". */
    const val MAX_WHEEL_SECTORS = 8

    /**
     * The first page of the ring for [count] actions: at most [MAX_WHEEL_SECTORS] indices into the action list.
     * The last one is [moreIndex] when not all actions fit.
     */
    fun ringIndices(count: Int): List<Int> = when {
        count <= 0 -> emptyList()
        count <= MAX_WHEEL_SECTORS -> (0 until count).toList()
        else -> (0 until MAX_WHEEL_SECTORS - 1).toList() + moreIndex(count)
    }

    /** Index of the synthetic "More" entry, or -1 when every action fits on the ring. */
    fun moreIndex(count: Int): Int = if (count > MAX_WHEEL_SECTORS) count else -1

    /** True when [index] of the ring opens the rest of the actions instead of running one. */
    fun isMore(count: Int, ringPosition: Int): Boolean = ringPosition == ringIndices(count).lastIndex && moreIndex(count) >= 0

    /** Actions hidden behind "More", in their configured order. */
    fun overflowIndices(count: Int): List<Int> =
        if (count > MAX_WHEEL_SECTORS) (MAX_WHEEL_SECTORS - 1 until count).toList() else emptyList()
}
