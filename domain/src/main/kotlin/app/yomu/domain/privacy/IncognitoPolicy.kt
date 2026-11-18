package app.yomu.domain.privacy

/**
 * The incognito contract as a value. The code had the rule spread over the reader, the audio service, the dictionary
 * and the widgets; this list is the audit those entry points are checked against.
 *
 * Incognito stops *automatic* persistence: progress, read marks, history, session statistics, tracker updates and
 * notification text. Explicit changes the user makes (a manual "mark read", a bookmark, a saved word) are his own
 * edits and stay. Nothing already stored is deleted by turning the mode on or off.
 */
enum class PersistenceEvent {
    /** A position saved while reading (page turn, timer, leaving the reader). */
    AUTO_PROGRESS,

    /** A chapter marked read or finished by reaching its end. */
    AUTO_READ_MARK,

    /** An entry in the reading history or the "recent" feed. */
    HISTORY,

    /** Reading-session statistics (time, pages, speed). */
    SESSION_STATS,

    /** An automatic progress push to a tracker or a server. */
    TRACKER_UPDATE,

    /** A bookmark, note, highlight, word or manual status change. */
    USER_EDIT,

    /** A notification that names a title or a chapter. */
    TITLED_NOTIFICATION,

    /** The title, album, author and artwork handed to Media3 for the lock screen, the shade and Bluetooth. */
    MEDIA_METADATA,
}

object IncognitoPolicy {
    /** True when the event may be persisted (or shown with its real title) under [incognito]. */
    fun persists(event: PersistenceEvent, incognito: Boolean): Boolean = !incognito || event == PersistenceEvent.USER_EDIT

    /** What an incognito notification may show: never a title, chapter or cover description. */
    fun notificationText(title: String?, text: String?, incognito: Boolean): Pair<String?, String?> =
        if (!incognito || (title.isNullOrBlank() && text.isNullOrBlank())) title to text
        else null to null
}
