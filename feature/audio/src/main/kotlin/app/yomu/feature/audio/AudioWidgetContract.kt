package app.yomu.feature.audio

import android.content.Context
import android.content.Intent

/** What the home-screen transport widget shows; written by the service, read by the widget provider. */
data class AudioWidgetState(
    val bookId: Long,
    val bookTitle: String,
    val trackTitle: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * The contract between the playback service and the widget.
 *
 * The widget itself lives in the app module (it opens the app), while the state belongs to the service: a small
 * private preference file and a broadcast action keep the two modules independent. The buttons are explicit
 * service intents, so a tap reaches the same player and the same command owner as the notification; starting the
 * service from a widget never starts playback on its own.
 */
object AudioWidgetContract {
    const val ACTION_UPDATE = "app.yomu.audio.WIDGET_UPDATE"
    const val ACTION_TOGGLE = "app.yomu.audio.WIDGET_TOGGLE"
    const val ACTION_BACK = "app.yomu.audio.WIDGET_BACK"
    const val ACTION_FORWARD = "app.yomu.audio.WIDGET_FORWARD"

    private const val PREFS = "audio_widget"
    private const val KEY_BOOK = "book"
    private const val KEY_BOOK_TITLE = "bookTitle"
    private const val KEY_TRACK_TITLE = "trackTitle"
    private const val KEY_PLAYING = "playing"
    private const val KEY_POSITION = "position"
    private const val KEY_DURATION = "duration"

    fun save(context: Context, state: AudioWidgetState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_BOOK, state.bookId)
            .putString(KEY_BOOK_TITLE, state.bookTitle)
            .putString(KEY_TRACK_TITLE, state.trackTitle)
            .putBoolean(KEY_PLAYING, state.playing)
            .putLong(KEY_POSITION, state.positionMs)
            .putLong(KEY_DURATION, state.durationMs)
            .apply()
    }

    fun read(context: Context): AudioWidgetState? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bookId = prefs.getLong(KEY_BOOK, -1)
        if (bookId < 0) return null
        return AudioWidgetState(
            bookId = bookId,
            bookTitle = prefs.getString(KEY_BOOK_TITLE, "").orEmpty(),
            trackTitle = prefs.getString(KEY_TRACK_TITLE, "").orEmpty(),
            playing = prefs.getBoolean(KEY_PLAYING, false),
            positionMs = prefs.getLong(KEY_POSITION, 0),
            durationMs = prefs.getLong(KEY_DURATION, 0),
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** The broadcast that asks the widget to redraw from the saved state. */
    fun updateIntent(context: Context): Intent = Intent(ACTION_UPDATE).setPackage(context.packageName)
}
