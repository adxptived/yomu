package app.yomu.domain.library

/** A filter that needs "yes", "no" and "any" — a boolean cannot express "I don't care". */
enum class Tri {
    ANY, YES, NO;

    fun matches(value: Boolean): Boolean = when (this) {
        ANY -> true
        YES -> value
        NO -> !value
    }

    /** Cycles any → yes → no → any, which is what one tap on a filter chip does. */
    fun next(): Tri = when (this) {
        ANY -> YES
        YES -> NO
        NO -> ANY
    }
}

/** What the content is. Independent of where it came from and whether it is available offline. */
enum class ContentType { TEXT, PDF, MANGA, AUDIO, UNKNOWN }

/** Where the item lives. */
enum class ContentOrigin { LOCAL, CATALOG, SERVER }

/**
 * How much of the content is on the device.
 *
 * "Downloaded only" must not be a single flag: a partially downloaded title is neither fully offline nor online, and
 * treating it as offline sends the reader into a chapter that needs the network.
 */
enum class Availability { FULLY_OFFLINE, PARTIAL, ONLINE, MISSING }

/** The sort keys. A key that does not apply to an item (unread chapters of a local PDF) leaves it in place. */
enum class LibrarySortKey { TITLE, AUTHOR, ADDED, LAST_OPENED, PROGRESS, UPDATED, UNREAD }

/**
 * The one query model of the library.
 *
 * Type, origin and availability are independent axes: an item has exactly one of each, and a filter on one never
 * implies a value for another. Read/started/downloaded/bookmarked/tracked are tri-state, so "any" is not the same as
 * "no" — the previous booleans could not express the difference.
 *
 * The spec is a plain value: it can be saved as a shelf, sent to a repository and compared in a test without Android.
 */
data class LibraryQuerySpec(
    val search: String = "",
    val types: Set<ContentType> = emptySet(),
    val origins: Set<ContentOrigin> = emptySet(),
    val availability: Set<Availability> = emptySet(),
    val read: Tri = Tri.ANY,
    val started: Tri = Tri.ANY,
    val downloaded: Tri = Tri.ANY,
    val bookmarked: Tri = Tri.ANY,
    val tracked: Tri = Tri.ANY,
    val completedPublication: Tri = Tri.ANY,
    val favoritesOnly: Boolean = false,
    val tags: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val collectionId: Long? = null,
    val sort: LibrarySortKey = LibrarySortKey.LAST_OPENED,
    val ascending: Boolean = false,
    /** Fixed for a session or a shelf: re-randomising on every recomposition would make paging jump. */
    val randomSeed: Long? = null,
) {
    /** True when anything narrows the list, for the active-filter badge and the "clear" affordance. */
    val isFiltered: Boolean
        get() = search.isNotBlank() || types.isNotEmpty() || origins.isNotEmpty() || availability.isNotEmpty() ||
            read != Tri.ANY || started != Tri.ANY || downloaded != Tri.ANY || bookmarked != Tri.ANY ||
            tracked != Tri.ANY || completedPublication != Tri.ANY || favoritesOnly ||
            tags.isNotEmpty() || languages.isNotEmpty() || collectionId != null

    /** Number of active narrowing conditions, independent of how many options each one holds. */
    val activeFilterCount: Int
        get() = listOf(
            types.isNotEmpty(), origins.isNotEmpty(), availability.isNotEmpty(),
            read != Tri.ANY, started != Tri.ANY, downloaded != Tri.ANY, bookmarked != Tri.ANY,
            tracked != Tri.ANY, completedPublication != Tri.ANY, favoritesOnly,
            tags.isNotEmpty(), languages.isNotEmpty(), collectionId != null,
        ).count { it }

    /** A stable identity of the query, used to snapshot a selection against it. */
    val identity: String
        get() = listOf(
            search, types.sortedBy { it.name }, origins.sortedBy { it.name }, availability.sortedBy { it.name },
            read, started, downloaded, bookmarked, tracked, completedPublication, favoritesOnly,
            tags.sorted(), languages.sorted(), collectionId, sort, ascending,
        ).joinToString("|")

    fun matches(item: LibraryQueryItem): Boolean {
        if (search.isNotBlank() && !item.searchText.contains(search.trim(), ignoreCase = true)) return false
        if (types.isNotEmpty() && item.type !in types) return false
        if (origins.isNotEmpty() && item.origin !in origins) return false
        if (availability.isNotEmpty() && item.availability !in availability) return false
        if (!read.matches(item.read)) return false
        if (!started.matches(item.started)) return false
        if (!downloaded.matches(item.downloaded)) return false
        if (!bookmarked.matches(item.bookmarked)) return false
        if (!tracked.matches(item.tracked)) return false
        if (!completedPublication.matches(item.completedPublication)) return false
        if (favoritesOnly && !item.favorite) return false
        if (tags.isNotEmpty() && item.tags.none { it in tags }) return false
        if (languages.isNotEmpty() && item.language !in languages) return false
        if (collectionId != null && collectionId !in item.collections) return false
        return true
    }

    /** True when the sort key means something for this item; the UI explains it instead of silently reordering. */
    fun sortAppliesTo(item: LibraryQueryItem): Boolean = when (sort) {
        LibrarySortKey.UNREAD -> item.unreadChapters != null
        else -> true
    }

    companion object {
        /** The default of a new installation; a saved shelf with a version the app does not know falls back to it. */
        val DEFAULT = LibraryQuerySpec()
    }
}

/**
 * The fields of one library item a query can see. Built from a `Book` or a `CatalogEntry`, never by the query itself.
 */
data class LibraryQueryItem(
    val searchText: String,
    val type: ContentType,
    val origin: ContentOrigin,
    val availability: Availability,
    val read: Boolean = false,
    val started: Boolean = false,
    val downloaded: Boolean = false,
    val bookmarked: Boolean = false,
    val tracked: Boolean = false,
    val completedPublication: Boolean = false,
    val favorite: Boolean = false,
    val tags: Set<String> = emptySet(),
    val language: String? = null,
    val collections: Set<Long> = emptySet(),
    /** Chapters still unread; null when the item has no chapter list (a local PDF has pages, not chapters). */
    val unreadChapters: Int? = null,
)
