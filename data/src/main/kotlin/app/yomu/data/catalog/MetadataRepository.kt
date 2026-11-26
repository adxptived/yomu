package app.yomu.data.catalog

import app.yomu.core.datastore.AppSettings
import app.yomu.core.database.TrackEntity
import app.yomu.data.tracking.AniListTracker
import app.yomu.data.tracking.MyAnimeListTracker
import app.yomu.data.tracking.TrackerManager
import app.yomu.domain.model.ContentType
import app.yomu.domain.model.ExternalMetadata
import app.yomu.domain.model.MetadataAvailability
import app.yomu.domain.model.MetadataProvider
import app.yomu.domain.model.MetadataProviderState
import app.yomu.domain.model.MetadataProviders
import app.yomu.domain.model.MetadataUnavailableException
import app.yomu.domain.model.RelatedTitle
import app.yomu.domain.model.TrackerPage
import app.yomu.domain.model.TrackerPerson
import app.yomu.domain.model.TrackerRecommendation
import app.yomu.domain.model.TrackerSection
import app.yomu.domain.model.TrackerTitleDetails
import app.yomu.domain.tracking.TrackSearchResult
import app.yomu.domain.tracking.TrackService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * External metadata for a title (C1). AniList and MangaBaka are searched without an account; MyAnimeList uses the
 * official API v2 with the client id the tracker is configured with (anonymous requests work when a client id is
 * known). A confirmed match is kept by the entry id and the provider's own id, separately from the source's fields and
 * from any tracker account: removing it restores the source's own cover and description without touching chapters,
 * progress or bookmarks.
 */
class MetadataRepository(
    private val trackers: TrackerManager,
    private val settings: AppSettings,
    private val client: () -> OkHttpClient = { OkHttpClient() },
    /** Overridable in tests: the MangaBaka search, so no test has to reach the network. */
    private val mangaBakaSearch: (suspend (String, Int) -> List<ExternalMetadata>)? = null,
) : TrackerPageSource {
    /** Every provider with its state; unavailable ones stay visible with the reason instead of disappearing. */
    fun providers(): List<MetadataProviderState> {
        val mal = trackers.tracker(TrackService.MYANIMELIST).configured()
        return MetadataProvider.entries.map { provider ->
            val availability = MetadataProviders.state(provider, malConfigured = mal)
            MetadataProviderState(provider, availability)
        }
    }

    /** The provider the settings pick for this kind of title, used when the dialog opens. */
    suspend fun preferred(type: ContentType): MetadataProvider = MetadataProviders.preferred(
        type = type,
        manga = settings.metadataProviderManga.get(),
        novel = settings.metadataProviderNovel.get(),
    )

    suspend fun setPreferred(type: ContentType, provider: MetadataProvider) {
        if (type == ContentType.NOVEL) settings.metadataProviderNovel.set(provider) else settings.metadataProviderManga.set(provider)
    }

    /**
     * Search on one provider (null = the preferred one for [type]). A provider that cannot be asked throws
     * [MetadataUnavailableException] so the dialog can name what is missing; a refused or broken answer is thrown too.
     */
    suspend fun search(
        query: String,
        provider: MetadataProvider? = null,
        type: ContentType = ContentType.MANGA,
        limit: Int = 10,
    ): List<ExternalMetadata> {
        val clean = query.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val chosen = provider ?: preferred(type)
        val state = providers().firstOrNull { it.provider == chosen }
        if (state != null && !state.usable) throw MetadataUnavailableException(chosen, state.availability)
        return when (chosen) {
            MetadataProvider.ANILIST -> trackers.tracker(TrackService.ANILIST).search(clean)
                .take(limit).map { it.toExternalMetadata(MetadataProvider.ANILIST) }

            MetadataProvider.MYANIMELIST -> trackers.tracker(TrackService.MYANIMELIST).search(clean)
                .take(limit).map { it.toExternalMetadata(MetadataProvider.MYANIMELIST) }

            MetadataProvider.MANGABAKA -> (mangaBakaSearch ?: { q, n -> MangaBakaClient.search(q, n, client) })(clean, limit)
        }
    }

    fun observeLink(entryId: Long): Flow<ExternalMetadata?> = settings.metadataLinks.flow.map { it[entryId] }

    /**
     * The public page of a tracker title (C3): AniList and MyAnimeList answer without an account when the client is
     * configured. Null when the service does not expose pages or the title is gone.
     */
    override suspend fun trackerTitle(service: TrackService, id: Long): TrackerTitleDetails? = when (service) {
        TrackService.ANILIST -> (trackers.tracker(service) as? AniListTracker)?.details(id)
        TrackService.MYANIMELIST -> (trackers.tracker(service) as? MyAnimeListTracker)?.details(id)
        else -> null
    }

    override suspend fun trackerPeople(service: TrackService, id: Long, section: TrackerSection, page: Int): TrackerPage<TrackerPerson> {
        val tracker = trackers.tracker(service) as? AniListTracker ?: return TrackerPage()
        return when (section) {
            TrackerSection.CHARACTERS -> tracker.characters(id, page)
            TrackerSection.STAFF -> tracker.staff(id, page)
            TrackerSection.RECOMMENDATIONS -> TrackerPage()
        }
    }

    override suspend fun trackerRecommendations(service: TrackService, id: Long, page: Int): TrackerPage<TrackerRecommendation> =
        (trackers.tracker(service) as? AniListTracker)?.recommendations(id, page) ?: TrackerPage()

    /** The reader's own entry on the service, shown on the page only with a working account. */
    override suspend fun trackFor(service: TrackService, id: Long): TrackEntity? = trackers.trackFor(service, id)

    /** The local title linked to this tracker title, if any. */
    override suspend fun localItemId(service: TrackService, id: Long): Long? = trackers.localItemId(service, id)

    override suspend fun localBookId(service: TrackService, id: Long): Long? = trackers.localBookId(service, id)

    override fun signedIn(service: TrackService): Boolean = trackers.tracker(service).loggedIn()

    /**
     * Explicit relations of a confirmed match: only AniList publishes them. A match from another provider has no list
     * here; the card simply shows none, and an offline or failing answer leaves it without the list.
     */
    suspend fun related(metadata: ExternalMetadata): List<RelatedTitle> {
        if (metadata.providerKind != MetadataProvider.ANILIST) return emptyList()
        return runCatching { (trackers.tracker(TrackService.ANILIST) as? AniListTracker)?.relations(metadata.id) }
            .onFailure { Timber.w(it, "Metadata relations") }
            .getOrNull()
            .orEmpty()
    }

    /** A null [metadata] removes the match and brings back the source's own cover and description. */
    suspend fun setLink(entryId: Long, metadata: ExternalMetadata?) {
        settings.metadataLinks.update { links ->
            if (metadata == null) links - entryId else links + (entryId to metadata)
        }
    }
}

/**
 * A tracker search hit as card metadata. The provider is recorded by its display name, which is what older saved
 * AniList links contain; the fields the provider actually supplied are listed for provenance.
 */
internal fun TrackSearchResult.toExternalMetadata(provider: MetadataProvider = MetadataProvider.ANILIST): ExternalMetadata =
    ExternalMetadata(
        provider = provider.displayName,
        id = remoteId,
        title = title,
        altTitle = altTitle?.takeIf { it.isNotBlank() && it != title },
        coverUrl = coverUrl,
        description = description,
        url = url,
        year = year,
        format = format,
        contentType = format?.lowercase(),
        country = country,
        provenance = listOfNotNull(
            "title",
            "cover".takeIf { !coverUrl.isNullOrBlank() },
            "description".takeIf { !description.isNullOrBlank() },
            "year".takeIf { year != null },
            "format".takeIf { !format.isNullOrBlank() },
        ),
    )
