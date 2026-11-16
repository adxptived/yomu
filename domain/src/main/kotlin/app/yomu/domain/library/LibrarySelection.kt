package app.yomu.domain.library

/**
 * Identity of one selectable library item.
 *
 * A local book with id 7 and a catalogue entry with id 7 are different items: sharing one numeric id space is what
 * lets a bulk action reach the wrong record. The type is part of the key, so the two can never collide.
 */
sealed interface LibraryItemKey {
    data class LocalBook(val id: Long) : LibraryItemKey

    data class OnlineEntry(val id: Long) : LibraryItemKey

    /** A result of a source browser, before it is added: its identity is the source and the item URL. */
    data class SourceResult(val sourceId: Long, val url: String) : LibraryItemKey
}

/**
 * What the user selected, and the snapshot the selection belongs to.
 *
 * [querySnapshot] is the identity of the filter/sort that produced the list when "select all" was used. New rows that
 * arrive later under a changed query are not silently selected; a range anchor is dropped when the order changes.
 */
data class LibrarySelection(
    val keys: Set<LibraryItemKey> = emptySet(),
    val anchor: LibraryItemKey? = null,
    val querySnapshot: String? = null,
) {
    val isActive: Boolean get() = keys.isNotEmpty() || querySnapshot != null
    val count: Int get() = keys.size

    fun contains(key: LibraryItemKey): Boolean = key in keys

    /** Adds or removes one item; the first selection becomes the range anchor. */
    fun toggle(key: LibraryItemKey): LibrarySelection = copy(
        keys = if (key in keys) keys - key else keys + key,
        anchor = anchor ?: key.takeIf { it !in keys },
    )

    fun select(key: LibraryItemKey): LibrarySelection = copy(keys = keys + key, anchor = anchor ?: key)

    fun deselect(key: LibraryItemKey): LibrarySelection = copy(keys = keys - key)

    fun clear(): LibrarySelection = LibrarySelection()

    /** Replaces the whole set, as "select all of this query" does. */
    fun replaceAll(newKeys: Set<LibraryItemKey>, snapshot: String? = querySnapshot): LibrarySelection =
        copy(keys = newKeys, anchor = null, querySnapshot = snapshot)

    fun invert(visible: List<LibraryItemKey>): LibrarySelection =
        copy(keys = keys - visible.toSet() + visible.filter { it !in keys }, anchor = null)

    /**
     * Selects the range between the anchor and [key] in the order the list currently shows. Without an anchor this is
     * the same as selecting [key] alone. The order is passed in, so the range never depends on load completion.
     */
    fun rangeTo(key: LibraryItemKey, visible: List<LibraryItemKey>): LibrarySelection {
        val from = anchor ?: return select(key)
        val start = visible.indexOf(from)
        val end = visible.indexOf(key)
        if (start < 0 || end < 0) return select(key)
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        return copy(keys = keys + visible.subList(lo, hi + 1), anchor = from)
    }

    /** The same query was refreshed: the selection is kept, new rows are not added. */
    fun retainOnRefresh(): LibrarySelection = this

    /** A new query ran: only the part of the selection that still matches it survives. */
    fun intersectWith(newQueryKeys: Set<LibraryItemKey>): LibrarySelection = copy(
        keys = keys intersect newQueryKeys,
        anchor = anchor?.takeIf { it in newQueryKeys },
        querySnapshot = null,
    )

    /** Only the order changed: keys are kept, the range anchor is dropped because the order it described is gone. */
    fun onSortChanged(): LibrarySelection = copy(anchor = null)

    /** Drops keys that no longer exist (deleted elsewhere), without touching the rest. */
    fun retainExisting(existing: Set<LibraryItemKey>): LibrarySelection = copy(
        keys = keys intersect existing,
        anchor = anchor?.takeIf { it in existing },
    )
}
