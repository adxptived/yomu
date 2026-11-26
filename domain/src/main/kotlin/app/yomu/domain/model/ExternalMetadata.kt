package app.yomu.domain.model

import kotlinx.serialization.Serializable

/**
 * External information about a title (AniList), kept separately from the source's own fields. The source still owns
 * the id, the chapters and the progress; this only replaces what is shown on the title card until the user removes it.
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
) {
    /** "AniList · 2021 · MANGA" for the card under the title. */
    val subtitle: String get() = listOfNotNull(provider, year?.toString(), format).joinToString(" · ")
}
