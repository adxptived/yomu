package app.yomu.feature.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Text-to-speech queue for the reader. Sentences are queued with their index as utterance id so the
 * reader can highlight the sentence being spoken. Long sentences are split to the engine's input limit and
 * every rejected `speak` call is accounted for, so the reader is never left in a "playing" state that is not real.
 */
class ReaderTts(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var queue: List<String> = emptyList()
    private var index = 0
    private var pendingStart: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())

    /** Voices of the current language; empty until the engine has started (see [prepareVoices]). */
    val voices: StateFlow<List<Voice>> = _voices

    /** Invalidates callbacks of a queue the user has already paused, stopped or replaced. */
    private var generation = 0

    /** Chunks accepted by the engine and not finished or failed yet. */
    private var pending = 0

    private val initWaiters = mutableListOf<(Boolean) -> Unit>()

    var onSentence: ((Int) -> Unit)? = null
    var onQueueFinished: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    var speed = 1f
    var pitch = 1f
    var voiceName: String? = null
    var language: String? = null

    private fun createEngine() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status -> onInit(status == TextToSpeech.SUCCESS) }
    }

    private fun onInit(success: Boolean) {
        ready = success
        main.post {
            try {
                if (success) {
                    tts?.setOnUtteranceProgressListener(listener)
                    _voices.value = queryVoices()
                    pendingStart?.let { runCatching(it) }
                } else {
                    // A paused or stopped start must not speak; a failed engine is dropped so a later attempt can retry.
                    tts?.shutdown()
                    tts = null
                    _playing.value = false
                    onError?.invoke()
                }
            } finally {
                pendingStart = null
                val waiters = initWaiters.toList()
                initWaiters.clear()
                waiters.forEach { waiter -> runCatching { waiter(success) } }
            }
        }
    }

    /** Starts the engine and runs [onReady] once it is available (immediately when it already is). */
    private fun engine(onReady: () -> Unit) {
        if (tts != null && ready) {
            onReady()
            return
        }
        pendingStart = onReady
        createEngine()
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val id = parse(utteranceId) ?: return
            main.post {
                if (id.generation != generation) return@post
                index = id.index
                onSentence?.invoke(id.index)
            }
        }

        override fun onDone(utteranceId: String?) {
            val id = parse(utteranceId) ?: return
            main.post {
                if (id.generation != generation) return@post
                index = id.index
                pending = (pending - 1).coerceAtLeast(0)
                if (pending == 0 && _playing.value) onQueueFinished?.invoke()
            }
        }

        @Suppress("OVERRIDE_DEPRECATION") // abstract in UtteranceProgressListener; its default onError(String, Int) delegates here
        override fun onError(utteranceId: String?) {
            val id = parse(utteranceId) ?: return
            main.post {
                if (id.generation != generation) return@post
                pending = (pending - 1).coerceAtLeast(0)
                // A failed fragment does not necessarily stop the rest of the queue, but the reader must hear about it.
                onError?.invoke()
                if (pending == 0) _playing.value = false
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
    }

    /**
     * Starts the engine for the voice picker: a book screen shows the list before anything is played. The voices
     * arrive through [voices] (and the returned list), also when they were not available yet.
     */
    suspend fun prepareVoices(): List<Voice> {
        if (tts != null && ready) return queryVoices()
        val ok = suspendCancellableCoroutine<Boolean> { cont ->
            initWaiters += { success -> if (cont.isActive) cont.resume(success) }
            createEngine()
        }
        return if (ok) queryVoices() else emptyList()
    }

    private fun queryVoices(): List<Voice> = runCatching {
        val lang = language
        tts?.voices.orEmpty().filter { lang == null || it.locale.language == Locale.forLanguageTag(lang).language }
            .sortedBy { it.name }
    }.getOrDefault(emptyList())

    /** Speaks [sentences] starting at [from]. */
    fun play(sentences: List<String>, from: Int = 0) {
        queue = sentences
        index = from.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        generation++
        _playing.value = true
        engine { speakFrom(index) }
    }

    private fun speakFrom(start: Int) {
        val e = tts ?: run {
            // No engine: do not leave the reader in a "playing" state.
            _playing.value = false
            onError?.invoke()
            return
        }
        e.stop()
        e.setSpeechRate(speed)
        e.setPitch(pitch)
        language?.let { l ->
            val loc = Locale.forLanguageTag(l)
            if (e.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) e.language = loc
        }
        voiceName?.let { v -> e.voices?.firstOrNull { it.name == v }?.let { e.voice = it } }
        if (queue.isEmpty()) {
            _playing.value = false
            main.post { onQueueFinished?.invoke() }
            return
        }
        val gen = generation
        val limit = TextToSpeech.getMaxSpeechInputLength()
        pending = 0
        var rejected = false
        for (i in start until queue.size) {
            chunks(queue[i], limit).forEachIndexed { part, text ->
                if (e.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), utteranceId(gen, i, part)) == TextToSpeech.SUCCESS) {
                    pending++
                } else {
                    rejected = true
                }
            }
        }
        // Nothing was accepted: no callback will come, so the reader must be told right here.
        if (pending == 0 && rejected) {
            _playing.value = false
            onError?.invoke()
        }
    }

    fun pause() {
        generation++
        pendingStart = null
        pending = 0
        _playing.value = false
        tts?.stop()
    }

    fun resume() {
        if (queue.isEmpty()) return
        generation++
        _playing.value = true
        engine { speakFrom(index) }
    }

    /** Re-applies speed/pitch/voice to the rest of the queue. */
    fun restartCurrent() {
        if (!_playing.value) return
        generation++
        engine { speakFrom(index) }
    }

    fun stop() {
        generation++
        pendingStart = null
        pending = 0
        _playing.value = false
        queue = emptyList()
        tts?.stop()
    }

    fun shutdown() {
        generation++
        pendingStart = null
        pending = 0
        queue = emptyList()
        _playing.value = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        _voices.value = emptyList()
    }

    private class Utterance(val generation: Int, val index: Int)

    private fun utteranceId(generation: Int, index: Int, part: Int) = "$generation|$index|$part"

    private fun parse(id: String?): Utterance? {
        if (id.isNullOrBlank()) return null
        val parts = id.split('|')
        if (parts.size != 3) return null
        return Utterance(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
    }

    /** Splits [text] into pieces the engine accepts, preferring a word boundary. */
    private fun chunks(text: String, limit: Int): List<String> {
        if (limit <= 0 || text.length <= limit) return listOf(text)
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end - 1)
                if (space > start + limit / 2) end = space + 1
            }
            out += text.substring(start, end)
            start = end
        }
        return out
    }
}
