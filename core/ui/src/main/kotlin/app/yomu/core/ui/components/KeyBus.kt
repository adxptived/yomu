package app.yomu.core.ui.components

/**
 * Hardware key routing from the activity to the active screen (volume keys and page-turner keys in readers).
 *
 * The activity is the only place that sees every key; a screen registers itself while it is on screen. No handler means
 * "nobody wants this key", so the platform keeps its default — that is what keeps the reader from taking the volume
 * keys away from an audiobook or another app. The handler returns true when it consumed the key.
 */
object KeyBus {
    @Volatile
    var volumeHandler: ((up: Boolean) -> Boolean)? = null

    /** Turns a page: true = forward. Registered by the reader only. */
    @Volatile
    var pageHandler: ((forward: Boolean) -> Boolean)? = null

    /** Whether the open reader reads right to left; the activity maps a key to a direction with it. */
    @Volatile
    var rtl: Boolean = false

    fun onVolume(up: Boolean): Boolean = volumeHandler?.invoke(up) ?: false

    fun onPage(forward: Boolean): Boolean = pageHandler?.invoke(forward) ?: false
}
