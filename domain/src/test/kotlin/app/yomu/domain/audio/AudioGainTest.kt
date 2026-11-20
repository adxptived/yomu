package app.yomu.domain.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A03b: the correction evens out tracks without ever pushing the signal past full scale. */
class AudioGainTest {

    @Test
    fun `a negative gain attenuates and unity changes nothing`() {
        assertEquals(1f, AudioGain.factor(null), 0.001f)
        assertEquals(1f, AudioGain.factor(0f), 0.001f)
        assertEquals(0.501f, AudioGain.factor(-6f), 0.001f)
        assertEquals(0.1f, AudioGain.factor(-60f), 0.001f)
    }

    @Test
    fun `a boost without a peak is not applied`() {
        assertEquals(1f, AudioGain.factor(6f), 0.001f)
        assertEquals(1f, AudioGain.factor(6f, peak = 0f), 0.001f)
    }

    @Test
    fun `a known peak allows only the headroom it leaves`() {
        // +6 dB on a track whose peak is 0.8 would reach 1.59: the headroom caps it at 1.25.
        assertEquals(1.25f, AudioGain.factor(6f, peak = 0.8f), 0.001f)
        // A quiet track (peak 0.25) can take the full +6 dB.
        assertEquals(1.995f, AudioGain.factor(6f, peak = 0.25f), 0.01f)
    }

    @Test
    fun `the correction stays inside the supported range`() {
        assertTrue(AudioGain.factor(-200f) >= AudioGain.MIN_FACTOR)
        assertTrue(AudioGain.factor(60f, peak = 0.01f) <= AudioGain.MAX_FACTOR)
    }
}
