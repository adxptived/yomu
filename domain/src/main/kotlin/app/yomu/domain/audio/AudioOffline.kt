package app.yomu.domain.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How much of an online audiobook to keep on the device.
 *
 * The choice belongs to the book, not to the running download: "the whole book" stays the default, "the next few
 * tracks" follows the listening position, and an explicit set is exactly what the user ticked. The downloader turns
 * the choice into track indexes; the UI shows the same list before anything is fetched.
 */
@Serializable
sealed interface AudioOfflinePlan {
    @Serializable
    @SerialName("all")
    data object All : AudioOfflinePlan

    /** The current track and the next [count] - 1; [count] is clamped when the plan is applied. */
    @Serializable
    @SerialName("next")
    data class Next(val count: Int = AudioOffline.NEXT_DEFAULT) : AudioOfflinePlan

    /** The tracks the user selected, by 0-based index in the manifest. */
    @Serializable
    @SerialName("selected")
    data class Selected(val tracks: Set<Int> = emptySet()) : AudioOfflinePlan
}

/** Resolves a plan against the tracks that are actually remote, without touching the network or the files. */
object AudioOffline {
    const val NEXT_DEFAULT = 5
    const val NEXT_MAX = 500

    fun next(count: Int): AudioOfflinePlan.Next = AudioOfflinePlan.Next(count.coerceIn(1, NEXT_MAX))

    /**
     * 0-based indexes to download, in listening order. [remote] is the set of tracks that stream (a local file is
     * already on the device); a selected index outside the book is ignored rather than failing the whole download.
     */
    fun indices(trackCount: Int, remote: Set<Int>, plan: AudioOfflinePlan, fromTrack: Int = 0): List<Int> {
        if (trackCount <= 0) return emptyList()
        val from = fromTrack.coerceIn(0, trackCount - 1)
        return when (plan) {
            AudioOfflinePlan.All -> (0 until trackCount).filter { it in remote }
            is AudioOfflinePlan.Next -> {
                val count = plan.count.coerceIn(1, NEXT_MAX)
                (from until minOf(trackCount, from + count)).filter { it in remote }
            }
            is AudioOfflinePlan.Selected -> plan.tracks.filter { it in 0 until trackCount && it in remote }.sorted()
        }
    }

    /** Tracks still missing from the plan's indexes, given what is already on the device. */
    fun missing(indices: List<Int>, downloaded: Set<Int>): List<Int> = indices.filter { it !in downloaded }
}
