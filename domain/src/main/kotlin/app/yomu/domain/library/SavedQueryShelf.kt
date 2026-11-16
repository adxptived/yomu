package app.yomu.domain.library

import kotlinx.serialization.Serializable

/**
 * A shelf defined by a saved query instead of a manual list.
 *
 * New books that match the query join automatically, so a "Unread fantasy" shelf does not need maintenance. The query
 * is versioned: a version this build does not know falls back to the default instead of failing to decode and losing
 * the whole shelf list.
 */
@Serializable
data class SavedQueryShelf(
    /** Stable identity of the shelf; renaming it does not change which shelf it is. */
    val id: Long,
    val name: String,
    val spec: LibraryQuerySpecData,
    /** A display override for this shelf; null means "use the library default". */
    val grid: Boolean? = null,
    val sortOverride: String? = null,
) {
    companion object {
        /** Bumped when the shape of [spec] changes; older values stay readable through [LibraryQuerySpecData]. */
        const val CURRENT_VERSION = 1
    }
}

/**
 * The persisted form of [LibraryQuerySpec].
 *
 * Enums are stored by name so a value the app no longer knows can be dropped one field at a time instead of making
 * the whole spec unreadable. [toSpec] is the only place that decides what an unknown value means.
 */
@Serializable
data class LibraryQuerySpecData(
    val version: Int = SavedQueryShelf.CURRENT_VERSION,
    val search: String = "",
    val types: List<String> = emptyList(),
    val origins: List<String> = emptyList(),
    val availability: List<String> = emptyList(),
    val read: String = Tri.ANY.name,
    val started: String = Tri.ANY.name,
    val downloaded: String = Tri.ANY.name,
    val bookmarked: String = Tri.ANY.name,
    val tracked: String = Tri.ANY.name,
    val completedPublication: String = Tri.ANY.name,
    val favoritesOnly: Boolean = false,
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val collectionId: Long? = null,
    val sort: String = LibrarySortKey.LAST_OPENED.name,
    val ascending: Boolean = false,
) {
    /** The spec this data describes; unknown enum names become the safe default of that field. */
    fun toSpec(): LibraryQuerySpec = LibraryQuerySpec(
        search = search,
        types = types.mapNotNull { name -> ContentType.entries.firstOrNull { it.name == name } }.toSet(),
        origins = origins.mapNotNull { name -> ContentOrigin.entries.firstOrNull { it.name == name } }.toSet(),
        availability = availability.mapNotNull { name -> Availability.entries.firstOrNull { it.name == name } }.toSet(),
        read = tri(read, Tri.ANY),
        started = tri(started, Tri.ANY),
        downloaded = tri(downloaded, Tri.ANY),
        bookmarked = tri(bookmarked, Tri.ANY),
        tracked = tri(tracked, Tri.ANY),
        completedPublication = tri(completedPublication, Tri.ANY),
        favoritesOnly = favoritesOnly,
        tags = tags.toSet(),
        languages = languages.toSet(),
        collectionId = collectionId,
        sort = LibrarySortKey.entries.firstOrNull { it.name == sort } ?: LibrarySortKey.LAST_OPENED,
        ascending = ascending,
    )

    private fun tri(name: String, fallback: Tri): Tri = Tri.entries.firstOrNull { it.name == name } ?: fallback

    companion object {
        fun from(spec: LibraryQuerySpec): LibraryQuerySpecData = LibraryQuerySpecData(
            search = spec.search,
            types = spec.types.map { it.name },
            origins = spec.origins.map { it.name },
            availability = spec.availability.map { it.name },
            read = spec.read.name,
            started = spec.started.name,
            downloaded = spec.downloaded.name,
            bookmarked = spec.bookmarked.name,
            tracked = spec.tracked.name,
            completedPublication = spec.completedPublication.name,
            favoritesOnly = spec.favoritesOnly,
            tags = spec.tags.toList(),
            languages = spec.languages.toList(),
            collectionId = spec.collectionId,
            sort = spec.sort.name,
            ascending = spec.ascending,
        )
    }
}
