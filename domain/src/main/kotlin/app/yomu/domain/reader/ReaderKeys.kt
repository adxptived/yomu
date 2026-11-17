package app.yomu.domain.reader

/** What a physical page-turner key means inside the reader. */
enum class ReaderKeyAction { NEXT, PREVIOUS, IGNORE }

/**
 * Physical keys the reader acts on: hardware page-turners and D-pad arrows.
 *
 * The key codes are Android's public constants, passed as plain integers so the decision can be tested without the
 * platform class. Right-to-left reading swaps the direction: "page down" or "right" goes forward in a left-to-right
 * book and back in a manga read right to left.
 *
 * Media keys (play/pause/next track) are deliberately **not** mapped: they belong to whatever is playing, and a
 * reader must not take them from an audiobook or another app.
 */
object ReaderKeys {
    // android.view.KeyEvent
    const val PAGE_UP = 92
    const val PAGE_DOWN = 93
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22

    fun action(keyCode: Int, rtl: Boolean): ReaderKeyAction = when (keyCode) {
        PAGE_UP, DPAD_LEFT -> if (rtl) ReaderKeyAction.NEXT else ReaderKeyAction.PREVIOUS
        PAGE_DOWN, DPAD_RIGHT -> if (rtl) ReaderKeyAction.PREVIOUS else ReaderKeyAction.NEXT
        else -> ReaderKeyAction.IGNORE
    }
}
