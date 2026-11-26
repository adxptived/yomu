package app.yomu.domain.model

import kotlinx.serialization.Serializable

/**
 * External information about a title (AniList, MyAnimeList or MangaBaka), kept separately from the source's own fields.
 * The source still owns the id, the chapters and the progress; this only replaces what is shown on the title card until
 * the user removes it. [provenance] names the fields the provider supplied, so a later manual edit can be told apart.
 */
@Serializable
data class ExternalMetadata(
    val provider: String,
    val id: Long,
    val title: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val url: String? = null,
    val year: Int? = null,
    val format: String? = null,
    /** Other name the work is known by (romaji for a localized main title), when the provider has one. */
    val altTitle: String? = null,
    /** The provider's own kind of work: "manga", "novel", "anime" (null for links saved before 2.19). */
    val contentType: String? = null,
    /** Fields that came from the provider: "title", "cover", "description", "year", "format". */
    val provenance: List<String> = emptyList(),
    val country: String? = null,
) {
    /** "AniList · 2021 · MANGA" for the card under the title. */
    val subtitle: String get() = listOfNotNull(provider, year?.toString(), format).joinToString(" · ")

    /** The provider of this match; older links store the display name, which resolves too. */
    val providerKind: MetadataProvider? get() = MetadataProvider.of(provider)
}

/** A title AniList explicitly relates to the matched one: a sequel, a prequel, a side story. */
data class RelatedTitle(
    val relation: String,
    val id: Long,
    val title: String,
    val coverUrl: String? = null,
    val url: String,
)
