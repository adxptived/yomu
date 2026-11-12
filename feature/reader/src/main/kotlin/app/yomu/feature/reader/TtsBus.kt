package app.yomu.feature.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the foreground service shows and what the notification buttons do. */
data class TtsStatus(
    val active: Boolean = false,
    val playing: Boolean = false,
    val title: String? = null,
    val subtitle: String? = null,
)

/**
 * The read-aloud engine lives in [ReaderViewModel] (it needs the open book to highlight the spoken sentence),
 * while the foreground service owns the media session and the notification. They talk through this bus in one process.
 */
object TtsBus {
    interface Controls {
        fun play()
        fun pause()
        fun stop()
    }

    @Volatile var controls: Controls? = null

    private val _status = MutableStateFlow(TtsStatus())
    val status: StateFlow<TtsStatus> = _status

    fun update(transform: (TtsStatus) -> TtsStatus) = _status.update(transform)
}
