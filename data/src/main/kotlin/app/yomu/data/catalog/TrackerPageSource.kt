package app.yomu.data.catalog

import app.yomu.core.database.TrackEntity
import app.yomu.domain.model.TrackerPage
import app.yomu.domain.model.TrackerPerson
import app.yomu.domain.model.TrackerRecommendation
import app.yomu.domain.model.TrackerSection
import app.yomu.domain.model.TrackerTitleDetails
import app.yomu.domain.tracking.TrackService

/**
 * What the tracker title page (C3) needs: the public page, its pageable sections, the reader's own entry and the local
 * link. [MetadataRepository] implements it; tests provide the same contract without a network or a database.
 */
interface TrackerPageSource {
    suspend fun trackerTitle(service: TrackService, id: Long): TrackerTitleDetails?

    suspend fun trackerPeople(service: TrackService, id: Long, section: TrackerSection, page: Int = 1): TrackerPage<TrackerPerson>

    suspend fun trackerRecommendations(service: TrackService, id: Long, page: Int = 1): TrackerPage<TrackerRecommendation>

    suspend fun trackFor(service: TrackService, id: Long): TrackEntity?

    suspend fun localItemId(service: TrackService, id: Long): Long?

    suspend fun localBookId(service: TrackService, id: Long): Long? = null

    fun signedIn(service: TrackService): Boolean
}
