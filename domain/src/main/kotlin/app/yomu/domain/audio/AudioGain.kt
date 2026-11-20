package app.yomu.domain.audio

import kotlin.math.pow

/** ReplayGain values of a track: its gain in dB and, when the tag has it, the sample peak. */
data class AudioGainTag(val gainDb: Float? = null, val peak: Float? = null)

/**
 * Volume correction from a ReplayGain tag, bounded by the sample peak.
 *
 * The point is to even out tracks, not to make the book loud: a positive gain is only used when the tag's peak
 * leaves room for it, so the corrected signal cannot clip. Without a peak the factor never goes above unity, and a
 * correction never drops a track below [MIN_FACTOR].
 */
object AudioGain {
    const val MIN_FACTOR = 0.1f
    const val MAX_FACTOR = 2f

    fun factor(gainDb: Float?, peak: Float? = null): Float {
        if (gainDb == null || !gainDb.isFinite()) return 1f
        val requested = 10f.pow(gainDb / 20f)
        val headroom = peak?.takeIf { it.isFinite() && it > 0f }?.let { 1f / it } ?: 1f
        return minOf(requested, headroom).coerceIn(MIN_FACTOR, MAX_FACTOR)
    }
}
