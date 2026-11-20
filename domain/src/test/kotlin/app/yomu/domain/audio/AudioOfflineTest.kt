package app.yomu.domain.audio

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A02: the offline choice is resolved in one place, before anything is fetched. */
class AudioOfflineTest {

    private val all = setOf(0, 1, 2, 3, 4, 5, 6, 7)

    @Test
    fun `the whole book selects every remote track`() {
        assertEquals(listOf(0, 1, 2, 3), AudioOffline.indices(4, setOf(0, 1, 2, 3), AudioOfflinePlan.All))
        // Tracks already local are not downloaded again.
        assertEquals(listOf(0, 2, 3), AudioOffline.indices(4, setOf(0, 2, 3), AudioOfflinePlan.All))
    }

    @Test
    fun `the next few tracks start at the listening position`() {
        val plan = AudioOffline.next(3)
        assertEquals(listOf(4, 5, 6), AudioOffline.indices(8, all, plan, fromTrack = 4))
        // The end of the book is clamped, not padded.
        assertEquals(listOf(6, 7), AudioOffline.indices(8, all, plan, fromTrack = 6))
        assertEquals(listOf(0, 1, 2), AudioOffline.indices(8, all, plan, fromTrack = -5))
    }

    @Test
    fun `a selected plan keeps the ticked tracks and ignores the rest`() {
        val plan = AudioOfflinePlan.Selected(setOf(5, 1, 99))
        assertEquals(listOf(1, 5), AudioOffline.indices(6, all, plan))
        assertEquals(emptyList<Int>(), AudioOffline.indices(0, all, plan))
    }

    @Test
    fun `an out of range next count is clamped`() {
        assertEquals(1, (AudioOffline.next(0) as AudioOfflinePlan.Next).count)
        assertEquals(AudioOffline.NEXT_MAX, (AudioOffline.next(10_000) as AudioOfflinePlan.Next).count)
        assertEquals(listOf(3), AudioOffline.indices(4, all, AudioOfflinePlan.Next(0), fromTrack = 3))
    }

    @Test
    fun `missing tracks are what the plan still has to fetch`() {
        assertEquals(listOf(2, 3), AudioOffline.missing(listOf(0, 1, 2, 3), setOf(0, 1)))
        assertEquals(emptyList<Int>(), AudioOffline.missing(emptyList(), emptySet()))
    }

    @Test
    fun `plans survive the worker's JSON`() {
        val json = Json
        val plans = listOf(AudioOfflinePlan.All, AudioOffline.next(4), AudioOfflinePlan.Selected(setOf(2, 7)))
        plans.forEach { plan ->
            val text = json.encodeToString(AudioOfflinePlan.serializer(), plan)
            assertEquals(plan, json.decodeFromString(AudioOfflinePlan.serializer(), text))
        }
        assertTrue(json.encodeToString(AudioOfflinePlan.serializer(), AudioOfflinePlan.All).contains("all"))
    }
}
