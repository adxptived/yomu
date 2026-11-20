package app.yomu.feature.audio

import app.yomu.domain.audio.AudioOffline
import app.yomu.domain.audio.AudioOfflinePlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioSettingsTest {
    @Test
    fun settingsRoundTripAndKeepOtherKeys() {
        val json = AudioSettings(1.35f, skipSilence = true).merge("""{"fontSize":18,"audio":{"speed":2.0}}""")
        assertEquals(AudioSettings(1.35f, true), AudioSettings.of(json))
        assertTrue("fontSize" in Json.parseToJsonElement(json).jsonObject)
    }

    @Test
    fun oldAndBrokenSettingsFallBack() {
        assertEquals(AudioSettings(2f, false), AudioSettings.of("""{"audio":{"speed":2.0}}"""))
        assertEquals(AudioSettings(3f, false), AudioSettings.of("""{"audio":{"speed":9.0}}"""))
        assertEquals(AudioSettings(), AudioSettings.of("not json"))
        assertEquals(AudioSettings(), AudioSettings.of(null))
    }

    @Test
    fun theOfflineChoiceRoundTripsWithTheOtherAudioSettings() {
        listOf(
            AudioOfflinePlan.All,
            AudioOffline.next(7),
            AudioOfflinePlan.Selected(setOf(4, 1)),
        ).forEach { plan ->
            val json = AudioSettings(offlinePlan = plan).merge("""{"fontSize":18,"audio":{"speed":2.0}}""")
            assertEquals(plan, AudioSettings.of(json).offlinePlan)
            assertTrue("fontSize" in Json.parseToJsonElement(json).jsonObject)
        }
    }

    @Test
    fun anUnknownOfflineChoiceFallsBackToTheWholeBook() {
        assertEquals(AudioOfflinePlan.All, AudioSettings.of("""{"audio":{"offline":{"mode":"everything"}}}""").offlinePlan)
        assertEquals(AudioOfflinePlan.All, AudioSettings.of("""{"audio":{"offline":3}}""").offlinePlan)
        // A selection with unusable entries keeps the usable ones.
        assertEquals(
            AudioOfflinePlan.Selected(setOf(2)),
            AudioSettings.of("""{"audio":{"offline":{"mode":"selected","tracks":[2,-1,"x"]}}}""").offlinePlan,
        )
    }

    @Test
    fun metadataAndGainArePerBookAndBlankOverridesAreDropped() {
        val meta = AudioMetadata(narrator = "A Reader", series = "The Series", language = "en", description = "A story.")
        val restored = AudioSettings.of(AudioSettings(metadata = meta, gainEnabled = true).merge(null))
        assertEquals(meta, restored.metadata)
        assertTrue(restored.gainEnabled)

        val blank = AudioSettings(metadata = AudioMetadata(narrator = "  ", series = "")).merge(null)
        assertEquals(AudioMetadata(), AudioSettings.of(blank).metadata)
        // Old books have gain off: normalisation is opt-in.
        assertFalse(AudioSettings.of("""{"audio":{"speed":1.0}}""").gainEnabled)
    }

    @Test
    fun networkRetriesBackOffUpToAMinute() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), (0..6).map(AudioPlaybackService::networkBackoff))
    }

    @Test
    fun rewindGrowsWithThePause() {
        assertEquals(0L, AudioPlaybackService.rewindAfter(30_000))
        assertEquals(3_000L, AudioPlaybackService.rewindAfter(5 * 60_000L))
        assertEquals(10_000L, AudioPlaybackService.rewindAfter(30 * 60_000L))
        assertEquals(20_000L, AudioPlaybackService.rewindAfter(5 * 60 * 60_000L))
        assertEquals(30_000L, AudioPlaybackService.rewindAfter(3 * 24 * 60 * 60_000L))
    }

    @Test
    fun rewindModesDecideWhetherAndHowFarToStepBack() {
        // Off never moves, however long the pause.
        assertEquals(0L, AudioPlaybackService.rewindAfter(30 * 60_000L, RewindMode.OFF, 15))
        // Fixed uses the chosen seconds after a real pause; a blip is not a pause.
        assertEquals(15_000L, AudioPlaybackService.rewindAfter(10 * 60_000L, RewindMode.CUSTOM, 15))
        assertEquals(0L, AudioPlaybackService.rewindAfter(1_000L, RewindMode.CUSTOM, 15))
        // An out-of-range custom value is clamped, not applied as zero.
        assertEquals(1_000L, AudioPlaybackService.rewindAfter(10 * 60_000L, RewindMode.CUSTOM, 0))
        assertEquals(120_000L, AudioPlaybackService.rewindAfter(10 * 60_000L, RewindMode.CUSTOM, 10_000))
    }

    @Test
    fun olderBooksKeepAdaptiveRewindAndNewChoicesRoundTrip() {
        val old = AudioSettings.of("""{"audio":{"speed":1.2}}""")
        assertEquals(RewindMode.ADAPTIVE, old.rewindMode)
        assertEquals(AudioSettings.DEFAULT_REWIND_SECONDS, old.rewindSeconds)

        val custom = AudioSettings(rewindMode = RewindMode.CUSTOM, rewindSeconds = 7)
        val restored = AudioSettings.of(custom.merge(null))
        assertEquals(RewindMode.CUSTOM, restored.rewindMode)
        assertEquals(7, restored.rewindSeconds)
        // An unknown mode falls back to the adaptive behaviour rather than to a random one.
        assertEquals(RewindMode.ADAPTIVE, AudioSettings.of("""{"audio":{"rewindMode":"sometimes"}}""").rewindMode)
    }
}
