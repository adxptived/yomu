package app.yomu.domain.model

import java.io.IOException

/**
 * Where the card's external information comes from (C1). The identity of a match is the provider plus the provider's
 * own id, so changing the provider never changes the title's source id, chapters, progress or tracker links.
 */
enum class MetadataProvider(val key: String, val displayName: String) {
    ANILIST("anilist", "AniList"),
    MYANIMELIST("mal", "MyAnimeList"),
    MANGABAKA("mangabaka", "MangaBaka"),
    ;

    companion object {
        /** Stored links keep the display name from older versions; both forms resolve. */
        fun of(text: String?): MetadataProvider? = entries.firstOrNull {
            text != null && (it.key.equals(text.trim(), ignoreCase = true) || it.displayName.equals(text.trim(), ignoreCase = true))
        }
    }
}

/** Whether a provider can be asked right now; [NEEDS_CLIENT_ID] is a setup step, not a broken provider. */
enum class MetadataAvailability { READY, NEEDS_CLIENT_ID, NEEDS_SIGN_IN, UNSUPPORTED }

/** A provider and its state, for the selector of the match dialog and the settings. */
data class MetadataProviderState(
    val provider: MetadataProvider,
    val availability: MetadataAvailability,
    /** Translated explanation shown next to a provider that cannot be used yet. */
    val detail: String? = null,
) {
    val usable: Boolean get() = availability == MetadataAvailability.READY
}

/** The provider cannot be asked; [availability] says what is missing. */
class MetadataUnavailableException(
    val provider: MetadataProvider,
    val availability: MetadataAvailability,
    message: String = "${provider.displayName}: $availability",
) : IOException(message)

/** Pure choices of C1: the preferred provider per content type and the state of each provider. */
object MetadataProviders {
    /** Novels may want another provider than manga; books and audio have no external match. */
    fun preferred(type: ContentType, manga: MetadataProvider, novel: MetadataProvider): MetadataProvider =
        when (type) {
            ContentType.NOVEL -> novel
            else -> manga
        }

    /**
     * AniList and MangaBaka answer anonymously. MyAnimeList needs a client id: built into the build or entered by the
     * reader in Settings → Trackers; without it the provider is listed as unavailable, not hidden.
     */
    fun state(provider: MetadataProvider, malConfigured: Boolean): MetadataAvailability = when (provider) {
        MetadataProvider.ANILIST, MetadataProvider.MANGABAKA -> MetadataAvailability.READY
        MetadataProvider.MYANIMELIST -> if (malConfigured) MetadataAvailability.READY else MetadataAvailability.NEEDS_CLIENT_ID
    }
}
