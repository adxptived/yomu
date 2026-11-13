package app.yomu.data.audio

import app.yomu.data.download.RangeResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resume decisions of an interrupted audiobook download (HTTP Range). */
class AudiobookResumeRulesTest {
    @Test
    fun contentRangeStartIsTheFirstByte() {
        assertEquals(100L, AudiobookDownloader.contentRangeStart("bytes 100-199/1234"))
        assertEquals(0L, AudiobookDownloader.contentRangeStart("bytes 0-99/1234"))
        assertNull(AudiobookDownloader.contentRangeStart(null))
        assertNull(AudiobookDownloader.contentRangeStart("bytes */1234"))
    }

    @Test
    fun representationIdComesFromTheStreamAddress() {
        assertEquals("251", AudiobookDownloader.itagOf("https://x.googlevideo.com/videoplayback?itag=251&mime=audio%2Fwebm"))
        assertEquals("140", AudiobookDownloader.itagOf("https://x/?a=1&itag=140"))
        assertNull(AudiobookDownloader.itagOf("https://example.com/audio.mp3"))
    }

    @Test
    fun rangesContinueOnlyAtTheSamePosition() {
        assertTrue(RangeResume.canAppend(100, 206, "bytes 100-199/200"))
        assertFalse(RangeResume.canAppend(100, 206, "bytes 0-199/200"))
        assertFalse(RangeResume.canAppend(100, 200, "bytes 100-199/200"))
        assertFalse(RangeResume.canAppend(0, 206, "bytes 0-99/200"))
    }
}
