package app.yomu.domain.library

import app.yomu.domain.model.EngineType

/** What the user asked for. A tile tap and a "Continue" button are not the same intent. */
sealed interface LibraryIntent {
    /** A normal tap on a library tile: show the details, not the reader. */
    data object Details : LibraryIntent

    /** "Continue"/Resume: open the saved position of the last read. */
    data object Resume : LibraryIntent

    /** An explicitly chosen chapter. */
    data class Chapter(val chapterId: Long) : LibraryIntent
}

/**
 * The kind of item a tile stands for. Selection keys and routes differ, so the resolver must know which one it has.
 */
enum class LibraryItemKind {
    /** A book in the device library: an EPUB, PDF, comic, audio book or folder. */
    LOCAL_BOOK,

    /** A followed catalogue entry: an online manga, novel or audio title. */
    ONLINE_TITLE,

    /** A result of browsing a source that has not been added to the library yet. */
    SOURCE_RESULT,
}

/** Everything the resolver needs about one item; it holds no Android types and starts no navigation itself. */
data class LibraryItemState(
    val kind: LibraryItemKind,
    val engine: EngineType?,
    /** True when the item has a saved position to resume from. */
    val hasSavedPosition: Boolean,
    /** True when the local content is reachable right now (file present, SAF grant alive). */
    val available: Boolean,
    /** True when the book was finished: resuming restarts it from the beginning. */
    val finished: Boolean = false,
) {
    val isLocal: Boolean get() = kind == LibraryItemKind.LOCAL_BOOK
}

/** Where the tap should go. The caller turns this into a route; the resolver never touches a NavController. */
sealed interface LibraryDestination {
    /** The details screen of a local book. */
    data object BookDetails : LibraryDestination

    /** The details screen of an online title (its chapter list). */
    data object TitleDetails : LibraryDestination

    /** The reader at the saved position, or at the start when nothing is saved. */
    data object Reader : LibraryDestination

    /** The reader of a specific chapter of an online title. */
    data class ReaderChapter(val chapterId: Long) : LibraryDestination

    /** The audio player at the saved track and position. */
    data object AudioPlayer : LibraryDestination

    /**
     * The item cannot be opened: the caller shows a recovery action instead of an empty screen. [reason] is what
     * failed, so the message can name it (a missing file is not a missing source).
     */
    data class Unavailable(val reason: LibraryUnavailable) : LibraryDestination
}

enum class LibraryUnavailable {
    /** A local file or folder that no longer exists, or a SAF grant that was revoked. */
    MISSING_CONTENT,

    /** A catalogue source that is not installed or is broken. */
    SOURCE_UNAVAILABLE,

    /** Nothing to resume: the item has no saved position and its content cannot be started. */
    NOTHING_TO_RESUME,
}

/**
 * The single decision "what does this tap open".
 *
 * The rules are deliberately one-directional:
 *
 * - a normal tap on a tile opens details for both local books and online titles;
 * - Resume opens the saved position, and for a finished book that is the beginning again;
 * - a missing file, a revoked grant or a removed source yields [LibraryDestination.Unavailable] instead of silently
 *   starting a reader that cannot show anything;
 * - an audio book opens the player, never the text reader, and only when the user asked to resume.
 */
object LibraryOpenResolver {
    fun resolve(intent: LibraryIntent, item: LibraryItemState): LibraryDestination = when (intent) {
        LibraryIntent.Details -> when (item.kind) {
            LibraryItemKind.LOCAL_BOOK -> LibraryDestination.BookDetails
            LibraryItemKind.ONLINE_TITLE -> LibraryDestination.TitleDetails
            // A source result is not in the library: the details of a catalogue title is the title screen.
            LibraryItemKind.SOURCE_RESULT -> LibraryDestination.TitleDetails
        }

        is LibraryIntent.Chapter -> when {
            item.kind == LibraryItemKind.LOCAL_BOOK -> LibraryDestination.BookDetails
            !item.available -> LibraryDestination.Unavailable(LibraryUnavailable.SOURCE_UNAVAILABLE)
            else -> LibraryDestination.ReaderChapter(intent.chapterId)
        }

        LibraryIntent.Resume -> when {
            !item.available -> LibraryDestination.Unavailable(
                if (item.isLocal) LibraryUnavailable.MISSING_CONTENT else LibraryUnavailable.SOURCE_UNAVAILABLE,
            )
            item.engine == EngineType.AUDIO -> LibraryDestination.AudioPlayer
            item.isLocal -> LibraryDestination.Reader
            // An online title resumes through its last chapter: the title screen owns which one that is.
            else -> LibraryDestination.TitleDetails
        }
    }

    /**
     * True when a resume is worth offering: the item has a position, or it is finished (resuming restarts it from
     * the beginning, which is what a finished book's "Read again" does).
     */
    fun offersResume(item: LibraryItemState): Boolean = item.available && (item.hasSavedPosition || item.finished)
}
