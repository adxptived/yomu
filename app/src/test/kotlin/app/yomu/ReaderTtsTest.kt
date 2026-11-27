package app.yomu

import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import app.yomu.feature.reader.ReaderTts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderTtsTest {
    @Test
    fun completingAVoiceSampleDoesNotAdvanceTheReaderOrLeaveItPlaying() {
        val tts = ReaderTts(ApplicationProvider.getApplicationContext<Context>())
        val spokenSentences = mutableListOf<Int>()
        var completedChapters = 0
        tts.onSentence = { spokenSentences.add(it) }
        tts.onQueueFinished = { completedChapters++ }
        try {
            tts.speakSample("A short voice sample.")
            val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            engine.onInitListener.onInit(TextToSpeech.SUCCESS)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("A short voice sample.", engine.lastSpokenText)
            assertEquals(emptyList<Int>(), spokenSentences)
            assertEquals(0, completedChapters)
            assertFalse(tts.playing.value)

            tts.play(listOf("The first sentence."))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(0), spokenSentences)
            assertEquals(1, completedChapters)
        } finally {
            tts.shutdown()
        }
    }
}
