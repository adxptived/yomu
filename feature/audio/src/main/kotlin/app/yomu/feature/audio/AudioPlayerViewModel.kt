package app.yomu.feature.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomu.core.datastore.AppSettings
import app.yomu.data.audio.AudioDownloadProgress
import app.yomu.data.audio.AudioDownloadState
import app.yomu.data.audio.AudioOfflineEstimate
import app.yomu.data.audio.AudioTrackState
import app.yomu.data.audio.AudiobookDownloader
import app.yomu.data.audio.AudiobookStore
import app.yomu.domain.audio.AudioOffline
import app.yomu.domain.audio.AudioOfflinePlan
import app.yomu.domain.audio.LinkedEditions
import app.yomu.domain.model.AudioNavEntry
import app.yomu.domain.model.AudioOrigin
import app.yomu.domain.model.AudioPositions
import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.LibraryQuery
import app.yomu.domain.model.LibrarySort
import app.yomu.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayerViewModel(
    val bookId: Long,
    private val books: BookRepository,
    private val store: AudiobookStore,
    private val audio: AudioController,
    private val downloader: AudiobookDownloader,
    private val settings: AppSettings,
) : ViewModel() {
    val book: StateFlow<Book?> = books.observeBook(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The chapter list: tracks, or the chapter marks inside them. */
    private val _chapters = MutableStateFlow<List<AudioNavEntry>>(emptyList())
    val chapters: StateFlow<List<AudioNavEntry>> = _chapters.asStateFlow()

    private val _origin = MutableStateFlow(AudioOrigin.FILES)
    val origin: StateFlow<AudioOrigin> = _origin.asStateFlow()

    private val _loadError = MutableStateFlow<Throwable?>(null)
    val loadError: StateFlow<Throwable?> = _loadError.asStateFlow()

    /** Offline copies of an online book; null for books whose files are already on the device. */
    private val _offline = MutableStateFlow<AudioDownloadState?>(null)
    val offline: StateFlow<AudioDownloadState?> = _offline.asStateFlow()

    /** Every track of the book with its offline state: the sheet lists them and "remove selected" uses the same list. */
    private val _offlineTracks = MutableStateFlow<List<AudioTrackState>>(emptyList())
    val offlineTracks: StateFlow<List<AudioTrackState>> = _offlineTracks.asStateFlow()

    /** Free space where the copies live, 0 when the volume cannot report it. */
    private val _offlineFree = MutableStateFlow(0L)
    val offlineFree: StateFlow<Long> = _offlineFree.asStateFlow()

    /** Size of the current plan; null until it has been measured (unknown lengths are counted, not guessed). */
    private val _offlineEstimate = MutableStateFlow<AudioOfflineEstimate?>(null)
    val offlineEstimate: StateFlow<AudioOfflineEstimate?> = _offlineEstimate.asStateFlow()

    val download: StateFlow<AudioDownloadProgress?> = downloader.progress(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Track lengths known before playback (playlists, M4B headers); 0 where unknown. */
    private var trackDurations: List<Long> = emptyList()

    /** The player, only while it holds this book. */
    val player: StateFlow<PlayerSnapshot?> = audio.state.map { s -> s?.takeIf { it.bookId == bookId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val audioSettings: StateFlow<AudioSettings> = book.map { AudioSettings.of(it?.readerSettingsJson) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioSettings())

    /** The saved offline choice of this book. */
    val offlinePlan: StateFlow<AudioOfflinePlan> = audioSettings.map { it.offlinePlan }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioOfflinePlan.All)

    /** The text edition the reader linked by hand, or null. */
    val linkedEditionId: StateFlow<Long?> = book.map { LinkedEditions.linkedId(it?.readerSettingsJson) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Text and PDF books that can be linked to this audiobook. */
    val linkCandidates: StateFlow<List<Book>> = books.observeBooks(LibraryQuery(sort = LibrarySort.TITLE))
        .map { list -> list.filter { it.id != bookId && (it.format == BookFormat.PDF || it.format.isText) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Length of each entry of [chapters]; 0 where unknown. */
    private val _chapterLengths = MutableStateFlow<List<Long>>(emptyList())
    val chapterLengths: StateFlow<List<Long>> = _chapterLengths.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val b = withContext(Dispatchers.IO) { books.getBook(bookId) } ?: return@launch
                _origin.value = withContext(Dispatchers.IO) { store.origin(b) }
                val tracks = store.tracks(b)
                trackDurations = tracks.map { it.durationMs }
                _chapters.value = AudioPositions.navigation(tracks)
                _chapterLengths.value = PlayerMath.lengths(_chapters.value, trackDurations)
                refreshOffline()
                audio.open(bookId, play = false)
                estimateDownload()
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else _loadError.value = it }
        }
        // Each finished track changes what is on the device.
        viewModelScope.launch {
            var lastTrack = -1
            download.collect { p ->
                if (p == null) return@collect
                if (!p.running || p.track != lastTrack) refreshOffline()
                lastTrack = p.track
            }
        }
    }

    /** Positions move without player events: the screen ticks while it is shown and the book plays. */
    suspend fun tickWhilePlaying() {
        while (true) {
            audio.refresh()
            delay(TICK_MS)
        }
    }

    private suspend fun refreshOffline() {
        val b = withContext(Dispatchers.IO) { books.getBook(bookId) } ?: return
        _offline.value = withContext(Dispatchers.IO) { downloader.state(b) }
        _offlineTracks.value = withContext(Dispatchers.IO) { downloader.trackStates(b) }
        _offlineFree.value = withContext(Dispatchers.IO) { downloader.freeBytes(b) }
    }

    fun playPause() = audio.togglePlay()

    /** A fresh stream after a sign-in: the old one was refused and cached. */
    fun retryPlayback() = viewModelScope.launch {
        _loadError.value = null
        audio.stop()
        audio.open(bookId, play = true)
    }

    /** A failed mid-playback stream: prepare again from the same position instead of reopening the whole book. */
    fun retryNow() {
        val p = audio.player()
        if (p == null) {
            retryPlayback()
            return
        }
        p.prepare()
        p.play()
    }

    fun seekTo(positionMs: Long) { audio.player()?.seekTo(positionMs) }

    fun back() = audio.skipBack()

    fun forward() = audio.skipForward()

    fun setSkipIntervals(backSec: Int, forwardSec: Int) = audio.setSkipIntervals(backSec, forwardSec)

    /** Rewind on resume; the service stores it with the book and applies it on the next resume. */
    fun setRewind(mode: RewindMode, seconds: Int) = audio.setRewind(mode, seconds)

    fun updateMetadata(meta: AudioMetadata) = audio.setMetadata(meta)

    fun setGain(enabled: Boolean) = audio.setGain(enabled)

    /** Links the two editions by hand: each id is saved in the other book's reader settings. */
    fun linkEdition(textBookId: Long) = viewModelScope.launch {
        val audioBook = withContext(Dispatchers.IO) { books.getBook(bookId) } ?: return@launch
        val textBook = withContext(Dispatchers.IO) { books.getBook(textBookId) } ?: return@launch
        withContext(Dispatchers.IO) {
            LinkedEditions.pair(audioBook.id, textBook.id).forEach { (id, linked) ->
                val current = if (id == audioBook.id) audioBook.readerSettingsJson else textBook.readerSettingsJson
                books.saveReaderSettings(id, LinkedEditions.withLink(current, linked))
            }
        }
    }

    fun unlinkEdition() = viewModelScope.launch {
        val audioBook = withContext(Dispatchers.IO) { books.getBook(bookId) } ?: return@launch
        val linked = LinkedEditions.linkedId(audioBook.readerSettingsJson) ?: return@launch
        val textBook = withContext(Dispatchers.IO) { books.getBook(linked) }
        withContext(Dispatchers.IO) {
            books.saveReaderSettings(audioBook.id, LinkedEditions.withLink(audioBook.readerSettingsJson, null))
            textBook?.let { books.saveReaderSettings(it.id, LinkedEditions.withLink(it.readerSettingsJson, null)) }
        }
    }

    /** Where the bookmark button was pressed: the dialog must not move the spot while a note is typed. */
    fun bookmarkPoint(): Pair<Int, Long>? = player.value?.let { it.track to it.positionMs }

    /** Saves a bookmark at the position captured when the button was pressed, not at the live one. */
    fun addBookmark(track: Int, positionMs: Long, note: String) {
        val mark = Bookmark(track, positionMs, note.trim(), System.currentTimeMillis())
        audio.setBookmarks((audioSettings.value.bookmarks + mark).sortedWith(compareBy({ it.track }, { it.positionMs })))
    }

    fun updateBookmark(mark: Bookmark, note: String) {
        val next = audioSettings.value.bookmarks.map { if (it.track == mark.track && it.positionMs == mark.positionMs && it.createdAt == mark.createdAt) it.copy(note = note.trim()) else it }
        audio.setBookmarks(next)
    }

    fun removeBookmark(mark: Bookmark) = audio.setBookmarks(audioSettings.value.bookmarks - mark)

    /** Moves to a bookmark or a journal entry without starting playback: a jump from a pause stays paused. */
    fun jump(mark: Bookmark) {
        audio.player()?.seekTo(mark.track, mark.positionMs)
    }

    fun jumpTo(track: Int, positionMs: Long) {
        audio.player()?.seekTo(track, positionMs)
    }

    fun clearHistory() = audio.clearHistory()

    /** Adds [minutes] to the running sleep timer, or starts one of that length. */
    fun extendSleep(minutes: Int) {
        val at = player.value?.sleepAt?.takeIf { it > 0 }
        audio.sleep(if (at != null) PlayerMath.extendedSleepMinutes(at, System.currentTimeMillis(), minutes) else minutes)
    }

    /** Share of the book heard, or null while some track length is unknown. */
    fun bookProgress(s: PlayerSnapshot): Float? = durationsFor(s)?.let { PlayerMath.bookProgress(it, s.track, s.positionMs) }

    fun jump(entry: AudioNavEntry) {
        val p = audio.player() ?: return
        p.seekTo(entry.track, entry.startMs)
        if (!p.playWhenReady) p.play()
    }

    /** The current chapter's start, or the previous chapter within the first seconds of one. */
    fun previous() {
        val s = player.value ?: return
        val list = chapters.value
        val i = AudioPositions.current(list, s.track, s.positionMs)
        val entry = list.getOrNull(i) ?: return
        val target = if (s.positionMs - entry.startMs > RESTART_WINDOW_MS || i == 0) entry else list[i - 1]
        audio.player()?.seekTo(target.track, target.startMs)
    }

    fun next() {
        val s = player.value ?: return
        val list = chapters.value
        list.getOrNull(AudioPositions.current(list, s.track, s.positionMs) + 1)?.let { audio.player()?.seekTo(it.track, it.startMs) }
    }

    fun setSpeed(speed: Float) {
        // Steps of ±0.05 add float error; keep two decimals.
        val v = Math.round(speed.coerceIn(AudioSettings.MIN_SPEED, AudioSettings.MAX_SPEED) * 100) / 100f
        audio.player()?.setPlaybackSpeed(v)
    }

    /** Time left in the book at the current speed, or null while some track length is unknown. */
    fun timeLeftMs(s: PlayerSnapshot): Long? {
        val durations = durationsFor(s) ?: return null
        return AudioPositions.remaining(durations, s.track, s.positionMs)?.let { (it / s.speed.coerceAtLeast(0.1f)).toLong() }
    }

    private fun durationsFor(s: PlayerSnapshot): List<Long>? {
        val durations = trackDurations.toMutableList().takeIf { it.size == s.trackCount } ?: return null
        if (s.track !in durations.indices) return null
        if (s.durationMs > 0) durations[s.track] = s.durationMs
        return durations
    }

    fun toggleSkipSilence() { player.value?.let { audio.setSkipSilence(!it.skipSilence) } }

    fun sleep(minutes: Int) = audio.sleep(minutes)

    fun startDownload() = viewModelScope.launch {
        val plan = audioSettings.value.offlinePlan
        val from = player.value?.track ?: 0
        downloader.start(bookId, settings.downloadWifiOnly.get(), plan, from)
        estimateDownload()
    }

    fun cancelDownload() = downloader.cancel(bookId)

    /** Saves the offline choice with the other per-book audio settings, then measures what it would cost. */
    fun setOfflinePlan(plan: AudioOfflinePlan) = viewModelScope.launch {
        val b = withContext(Dispatchers.IO) { books.getBook(bookId) } ?: return@launch
        val settings = AudioSettings.of(b.readerSettingsJson).copy(offlinePlan = plan)
        withContext(Dispatchers.IO) { books.saveReaderSettings(bookId, settings.merge(b.readerSettingsJson)) }
        estimateDownload()
    }

    /** Ticks or unticks a track of a selection; the plan becomes an explicit one. */
    fun toggleOfflineTrack(index: Int) {
        val current = audioSettings.value.offlinePlan
        val selected = (current as? AudioOfflinePlan.Selected)?.tracks.orEmpty()
        val next = if (index in selected) selected - index else selected + index
        setOfflinePlan(AudioOfflinePlan.Selected(next))
    }

    fun estimateDownload() = viewModelScope.launch {
        val b = withContext(Dispatchers.IO) { books.getBook(bookId) } ?: return@launch
        val estimate = withContext(Dispatchers.IO) {
            downloader.estimate(b, audioSettings.value.offlinePlan, player.value?.track ?: 0)
        }
        _offlineEstimate.value = estimate
    }

    /** Removes the tracks the current plan covers, keeping the manifest and the listening position. */
    fun removeDownload() = viewModelScope.launch {
        val b = withContext(Dispatchers.IO) { books.getBook(bookId) } ?: return@launch
        val count = _offlineTracks.value.size
        val plan = audioSettings.value.offlinePlan
        val indices = if (count == 0) emptySet() else {
            AudioOffline.indices(count, (0 until count).toSet(), plan, player.value?.track ?: 0).toSet()
        }
        withContext(Dispatchers.IO) {
            if (indices.isEmpty()) downloader.remove(b) else downloader.remove(b, indices)
        }
        refreshOffline()
        estimateDownload()
    }

    private companion object {
        const val TICK_MS = 500L
        const val RESTART_WINDOW_MS = 3_000L
    }
}
