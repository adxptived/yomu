package app.yomu.domain.model

import app.yomu.domain.tracking.TrackService

/** Which optional blocks a tracking service can fill on its title page (C3). */
enum class TrackerSection { CHARACTERS, STAFF, RECOMMENDATIONS }

/** A public title page of a tracking service: what anyone sees, without the reader's private list fields. */
data class TrackerTitleDetails(
    val service: TrackService,
    val id: Long,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val description: String? = null,
    val url: String,
    val format: String? = null,
    /** Publication state as the service words it ("RELEASING", "FINISHED"). */
    val status: String? = null,
    val year: Int? = null,
    /** Average score of the service, 0–100 (null when it has none). */
    val score: Float? = null,
    val tags: List<String> = emptyList(),
    /** Sections this provider can answer; a section outside it is hidden, not shown empty. */
    val sections: Set<TrackerSection> = emptySet(),
)

/** A character or a staff member of a title; [role] is the service's own word for the part. */
data class TrackerPerson(
    val id: Long,
    val name: String,
    val imageUrl: String? = null,
    val role: String? = null,
)

/** A title the service recommends next to this one. */
data class TrackerRecommendation(
    val id: Long,
    val title: String,
    val coverUrl: String? = null,
    val url: String,
)

/** A page of public results with the provider’s continuation flag. */
data class TrackerPage<out T>(val items: List<T> = emptyList(), val hasMore: Boolean = false)
