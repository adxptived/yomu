package app.yomu.data.catalog

import app.yomu.core.datastore.AppSettings
import app.yomu.data.tracking.AniListTracker
import app.yomu.data.tracking.TrackerManager
import app.yomu.domain.model.ExternalMetadata
import app.yomu.domain.model.RelatedTitle
import app.yomu.domain.tracking.TrackSearchResult
import app.yomu.domain.tracking.TrackService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * External metadata for a title. AniList is searched without an account; a confirmed match is kept by the entry id,
 * separately from the source's fields and from any tracker account, so removing it restores the source's own cover and
 * description without touching chapters, progress or bookmarks.
 */
class MetadataRepository(
    private val trackers: TrackerManager,
    private val settings: AppSettings,
) {
    suspend fun search(query: String, limit: Int = 10): List<ExternalMetadata> {
        val clean = query.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        return runCatching {
            trackers.tracker(TrackService.ANILIST).search(clean).take(limit).map { it.toExternalMetadata() }
        }.getOrDefault(emptyList())
    }

    fun observeLink(entryId: Long): Flow<ExternalMetadata?> = settings.metadataLinks.flow.map { it[entryId] }

    /** Explicit relations of a confirmed match; an offline or failing answer leaves the card without the list. */
    suspend fun related(metadata: ExternalMetadata): List<RelatedTitle> =
        runCatching { (trackers.tracker(TrackService.ANILIST) as? AniListTracker)?.relations(metadata.id) }
            .getOrNull()
            .orEmpty()

    /** A null [metadata] removes the match and brings back the source's own cover and description. */
    suspend fun setLink(entryId: Long, metadata: ExternalMetadata?) {
        settings.metadataLinks.update { links ->
            if (metadata == null) links - entryId else links + (entryId to metadata)
        }
    }
}

internal fun TrackSearchResult.toExternalMetadata(): ExternalMetadata = ExternalMetadata(
    provider = "AniList",
    id = remoteId,
    title = title,
    coverUrl = coverUrl,
    description = description,
    url = url,
    year = year,
    format = format,
)
