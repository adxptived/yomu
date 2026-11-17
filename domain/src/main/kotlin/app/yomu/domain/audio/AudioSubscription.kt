package app.yomu.domain.audio

import app.yomu.domain.model.AudioTrack
import kotlinx.serialization.Serializable

/**
 * A feed the user subscribed to, kept apart from the episodes it produced.
 *
 * The book holds what is playable *now*; this row holds what makes the feed updatable: its identity, the conditional
 * request state (`ETag`/`Last-Modified`) and the settings of the subscription. Storing the subscription only as the
 * current track list would lose the address as soon as the feed changed.
 */
@Serializable
data class AudioSubscription(
    val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val bookId: Long,
    val etag: String? = null,
    val lastModified: String? = null,
    val lastCheckedAt: Long = 0,
    val createdAt: Long = 0,
    val enabled: Boolean = true,
)

/**
 * One episode of a feed as the feed reports it, before it is matched with what the player already has.
 *
 * [guid] is the feed's own identity when it has one; [EpisodeIdentity] fills in a deterministic fallback otherwise.
 */
data class SubscriptionEpisode(
    val guid: String,
    val title: String,
    val url: String,
    val durationMs: Long = 0,
    val publishedAt: Long = 0,
)

/**
 * Identity of an episode.
 *
 * The feed's GUID wins: a podcast may re-encode an episode and change its enclosure address, and a position saved
 * against the old address must still be the same episode. Without a GUID the address, the title and the publication
 * date together are stable enough — they only differ when the episode itself is different.
 */
object EpisodeIdentity {
    fun of(guid: String?, url: String, title: String, publishedAt: Long): String =
        guid?.trim()?.takeIf { it.isNotEmpty() } ?: fallback(url, title, publishedAt)

    private fun fallback(url: String, title: String, publishedAt: Long): String =
        "$url\u001f${title.trim()}\u001f$publishedAt"

    /** The identity of an existing track, including the fallback for manifests written before GUIDs were stored. */
    fun of(track: AudioTrack): String = of(track.guid, track.uri, track.title, track.publishedAt)
}

/**
 * What one refresh changed, in the same order the feed lists its episodes.
 *
 * [retainedFiles] are offline copies of episodes the feed no longer lists: they are **not** deleted, because the user
 * downloaded them, but the number is reported so the library can offer to remove them.
 */
data class EpisodeReconciliation(
    val tracks: List<AudioTrack>,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val retainedFiles: List<String> = emptyList(),
) {
    val changed: Boolean get() = added > 0 || updated > 0 || removed > 0
}

/**
 * Matches a freshly fetched feed against the tracks the book already has.
 *
 * Rules that keep a listener's place:
 * - an episode is the same episode when its identity matches, whatever its address or title is now;
 * - a downloaded copy ([AudioTrack.file]) and the chapter marks are kept for a matched episode;
 * - a changed address is applied to the track, so the next play uses the new one;
 * - an episode missing from the feed leaves the list, but its downloaded file is reported, not deleted.
 */
object AudioSubscriptionReconciler {
    fun reconcile(existing: List<AudioTrack>, fetched: List<SubscriptionEpisode>): EpisodeReconciliation {
        val byIdentity = existing.associateBy(EpisodeIdentity::of)
        var added = 0
        var updated = 0
        var removed = 0
        val retained = mutableListOf<String>()

        val tracks = fetched.map { episode ->
            val old = byIdentity[episode.guid]
            if (old == null) {
                added++
                AudioTrack(
                    title = episode.title,
                    uri = episode.url,
                    durationMs = episode.durationMs,
                    publishedAt = episode.publishedAt,
                    guid = episode.guid,
                )
            } else {
                val next = old.copy(
                    title = episode.title,
                    uri = episode.url,
                    // A feed that stops reporting a length must not erase the one the player measured.
                    durationMs = episode.durationMs.takeIf { it > 0 } ?: old.durationMs,
                    publishedAt = episode.publishedAt,
                    guid = episode.guid,
                )
                if (next != old) updated++
                next
            }
        }

        val present = fetched.mapTo(HashSet()) { it.guid }
        existing.forEach { old ->
            if (EpisodeIdentity.of(old) !in present) {
                removed++
                old.file?.let { retained += it }
            }
        }
        return EpisodeReconciliation(tracks, added, updated, removed, retained)
    }
}
