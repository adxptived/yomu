package app.yomu.feature.reader

/**
 * The small key/value surface [ChapterWindowState] needs. `SavedStateHandle` is adapted to it by the reader, and unit
 * tests use a map-backed fake, so the snapshot format stays pure and free of Android framework calls.
 */
internal interface SnapshotStore {
    operator fun get(key: String): Any?
    operator fun set(key: String, value: Any?)
}

/**
 * Persists the online chapter window across process death (rotation alone is covered by retaining the ViewModel).
 *
 * The snapshot is stored as three Bundle-friendly values rather than one object, and [restore] checks every one of
 * them before it reaches [ChapterNavigator.restore]. A snapshot written by an older build, a half-written one or one
 * whose chapter no longer belongs to the window is ignored instead of pointing the reader at chapters that are gone.
 */
internal object ChapterWindowState {
    internal const val KEY_CURRENT = "reader_window_current"
    internal const val KEY_ENTRY = "reader_window_entry"
    internal const val KEY_IDS = "reader_window_ids"

    fun save(store: SnapshotStore, snapshot: ChapterNavigator.Snapshot) {
        store[KEY_CURRENT] = snapshot.currentId
        store[KEY_ENTRY] = snapshot.entryId
        // A primitive array has direct Bundle support; a Kotlin list wrapper would not survive the round trip.
        store[KEY_IDS] = snapshot.ids.toLongArray()
    }

    fun clear(store: SnapshotStore) {
        store[KEY_CURRENT] = null
        store[KEY_ENTRY] = null
        store[KEY_IDS] = null
    }

    fun restore(store: SnapshotStore): ChapterNavigator.Snapshot? {
        val current = store[KEY_CURRENT] as? Long ?: return null
        val entry = store[KEY_ENTRY] as? Long ?: return null
        val ids = when (val raw = store[KEY_IDS]) {
            is LongArray -> raw.toList()
            is List<*> -> raw.mapNotNull { it as? Long }
            else -> emptyList()
        }.takeIf { it.isNotEmpty() } ?: return null
        if (current <= 0 || entry <= 0 || current !in ids) return null
        return ChapterNavigator.Snapshot(current, entry, ids)
    }

    /**
     * Only a reader with no content of its own may resume a saved window. A deep link (a book, a locator, a chapter)
     * always wins, so a stale snapshot can never replace what navigation asked to open.
     */
    fun shouldRestore(args: ReaderArgs): Boolean =
        args.bookId <= 0 && args.chapterId <= 0 && args.locatorJson == null && args.positionReference == null
}
