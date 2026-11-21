package app.yomu.feature.reader

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomu.core.common.AppError
import app.yomu.core.common.ScreenErrors
import app.yomu.core.common.UiState
import app.yomu.core.datastore.AppSettings
import app.yomu.core.datastore.SettingsJson
import app.yomu.core.datastore.stateIn
import app.yomu.core.network.DataSaver
import app.yomu.core.network.TrafficMeter
import app.yomu.data.book.Containers
import app.yomu.data.book.PageSource
import app.yomu.data.book.PageSources
import app.yomu.data.book.PdfPasswordRequired
import app.yomu.data.book.PdfSource
import app.yomu.data.book.pdf.PdfAnnotationStore
import app.yomu.data.book.pdf.PdfAnnotationWorker
import app.yomu.data.catalog.CatalogRepository
import app.yomu.data.download.DownloadRepository
import app.yomu.data.index.FulltextIndexRepository
import app.yomu.data.library.BookFiles
import app.yomu.data.ocr.OcrService
import app.yomu.data.repository.AnnotationRepositoryImpl
import app.yomu.data.repository.BookRepositoryImpl
import app.yomu.data.repository.StatsRepositoryImpl
import app.yomu.data.repository.WordRepositoryImpl
import app.yomu.data.translation.TranslationService
import app.yomu.domain.audio.LinkedEditions
import app.yomu.domain.catalog.ChapterIntent
import app.yomu.domain.catalog.ChapterNavigation
import app.yomu.domain.catalog.ChapterOrder
import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.BookReaderSettings
import app.yomu.domain.model.Bookmark
import app.yomu.domain.model.CatalogEntry
import app.yomu.domain.model.ChapterInfo
import app.yomu.domain.model.CommonReaderSettings
import app.yomu.domain.model.ContentType
import app.yomu.domain.model.EngineType
import app.yomu.domain.model.Highlight
import app.yomu.domain.model.HighlightColor
import app.yomu.domain.model.HighlightStyle
import app.yomu.domain.model.Locator
import app.yomu.domain.model.MangaDirection
import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.Note
import app.yomu.domain.model.PageAction
import app.yomu.domain.model.PdfSettings
import app.yomu.domain.model.QuickActions
import app.yomu.domain.model.QuickMenuStyle
import app.yomu.domain.model.ReadingStatus
import app.yomu.domain.model.SearchHit
import app.yomu.domain.model.TapZoneLayout
import app.yomu.domain.model.TextPreset
import app.yomu.domain.model.TextReaderSettings
import app.yomu.domain.model.TocItem
import app.yomu.domain.reader.PageCapabilitiesOf
import app.yomu.domain.reader.PageContext
import app.yomu.domain.reader.PageEngine
import app.yomu.domain.reader.ReaderPoint
import app.yomu.domain.reader.ReaderWindow
import app.yomu.domain.text.TextTools
import app.yomu.feature.reader.epub.BookHtml
import app.yomu.feature.reader.epub.ChapterText
import app.yomu.feature.reader.epub.EpubController
import app.yomu.feature.reader.epub.EpubLayout
import app.yomu.feature.reader.epub.EpubListener
import app.yomu.feature.reader.epub.EpubProgress
import app.yomu.feature.reader.epub.EpubSelection
import app.yomu.feature.reader.epub.EpubTarget
import app.yomu.feature.reader.epub.EpubTextDocument
import app.yomu.feature.reader.epub.EpubWord
import app.yomu.feature.reader.manga.MangaChapter
import app.yomu.feature.reader.manga.MangaController
import app.yomu.feature.reader.manga.MangaListener
import app.yomu.feature.reader.manga.OverlayBlock
import app.yomu.feature.reader.manga.SessionPageSource
import app.yomu.feature.reader.pdf.PdfController
import app.yomu.feature.reader.pdf.PdfListener
import app.yomu.feature.reader.pdf.PdfSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import timber.log.Timber
import java.io.File

/** What the reader opens: a library book, or an online chapter of a catalog entry. */
data class ReaderArgs(val bookId: Long = -1, val locatorJson: String? = null, val chapterId: Long = -1, val positionReference: String? = null)

sealed interface ReaderContent {
    data class Text(val controller: EpubController) : ReaderContent
    data class Pdf(val controller: PdfController) : ReaderContent
    data class Manga(val controller: MangaController) : ReaderContent
}

/** Current text selection in any engine, in window pixels. */
data class ReaderSelection(
    val text: String,
    val anchor: Rect,
    val chapter: Int,
    val start: Int,
    val end: Int,
    val sentence: String = "",
    val highlightId: Long? = null,
)

data class WordTap(val word: String, val sentence: String, val anchor: Rect?, val locator: Locator)

data class ReaderPosition(
    val chapter: Int = 0,
    val chapterCount: Int = 1,
    val page: Int = 0,
    val pages: Int = 1,
    val progression: Float = 0f,
    val total: Float = 0f,
    val chapterTitle: String? = null,
    val charsLeftInChapter: Int = 0,
    val charsLeftInBook: Long = 0,
    /** 1-based page in the whole book: exact for PDF, comics and a whole-book text document; estimated otherwise. */
    val bookPage: Int = 0,
    val bookPages: Int = 0,
    val approximate: Boolean = false,
    /** Position of the navigation thumb; can differ from the persisted text progress. */
    val seekFraction: Float = total,
    val seekPageCount: Int = bookPages,
    val viewportSeek: Boolean = false,
)

/** Where a slider position would lead: shown in the bubble while dragging. */
data class SeekPreview(
    val page: Int,
    val pages: Int,
    val approximate: Boolean,
    val chapterTitle: String?,
    val minutesLeft: Int? = null,
    val spread: String? = null,
)

/** The place before a jump (slider, contents, search, "go to page"), to return with one tap. */
data class JumpBack(val locator: Locator, val page: Int)

internal data class PageNoteTarget(val locator: Locator, val page: Int)

/** How many jump positions the "back" button remembers; older ones fall off the front. */
private const val JUMP_STACK_MAX = 50

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val done: Boolean = false,
    /** The hit the page is showing now; the compact match bar is built from it. */
    val active: Int? = null,
) {
    val current: SearchHit? get() = active?.let(hits::getOrNull)
}

sealed interface ReaderEvent {
    data class BookmarkAdded(val bookmark: Bookmark) : ReaderEvent
    data class BookmarkRemoved(val bookmark: Bookmark) : ReaderEvent
    data class HighlightDeleted(val highlight: Highlight) : ReaderEvent
    data class WordSaved(val term: String) : ReaderEvent
    data class Message(val res: Int) : ReaderEvent
    data class Error(val error: Throwable) : ReaderEvent
    data class OpenUrl(val url: String) : ReaderEvent
    data class OpenBook(val id: Long) : ReaderEvent
    data object Finished : ReaderEvent

    /** Open the note dialog for the current page (long-press menu). */
    data class PageNote(val locator: Locator, val page: Int) : ReaderEvent

    /** Saving to the gallery is not possible here (Android 9 and older): ask where to save the page. */
    data class SavePageAs(val file: java.io.File, val name: String, val mime: String) : ReaderEvent
}

/** A page menu: the physical page with its engine capabilities, and the window-space anchor of the gesture. */
data class PageMenuRequest(val context: PageContext, val anchor: Offset) {
    val page: Int get() = context.page
}

enum class SleepTimer(val minutes: Int) { OFF(0), MIN_5(5), MIN_15(15), MIN_30(30), MIN_60(60), END_OF_CHAPTER(-1) }

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    val args: ReaderArgs,
    private val context: Context,
    private val books: BookRepositoryImpl,
    private val annotations: AnnotationRepositoryImpl,
    private val words: WordRepositoryImpl,
    private val stats: StatsRepositoryImpl,
    val settings: AppSettings,
    private val catalog: CatalogRepository,
    private val translation: TranslationService,
    private val ocr: OcrService,
    private val downloads: DownloadRepository,
    private val pdfAnnotations: PdfAnnotationStore,
    private val fulltextIndex: FulltextIndexRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), EpubListener, PdfListener, MangaListener {

    val bookId: Long get() = args.bookId
    var book: Book? = null
        private set
    /** True while an online chapter is open: a process-restored window can supply the chapter when args cannot. */
    val isOnline: Boolean get() = _chapterId.value > 0

    private val _content = MutableStateFlow<UiState<ReaderContent>>(UiState.Loading)
    val content: StateFlow<UiState<ReaderContent>> = _content
    private val _password = MutableStateFlow(false)
    val passwordNeeded: StateFlow<Boolean> = _password
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    /** The audio (or text) edition linked to this book by hand, or null. */
    val linkedEditionId = MutableStateFlow<Long?>(null)

    /** "Chapter 12 of 368" of the online chapter being read, shown under the title; null for books. */
    private var chapterOf: String? = null

    /** Pages are images (PDF, comics, online manga): while loading, show the black page canvas instead of the text theme. */
    private val _imageCanvas = MutableStateFlow(args.chapterId > 0)
    val imageCanvas: StateFlow<Boolean> = _imageCanvas
    private val _toc = MutableStateFlow<List<TocItem>>(emptyList())
    val toc: StateFlow<List<TocItem>> = _toc
    private val _position = MutableStateFlow(ReaderPosition())
    val position: StateFlow<ReaderPosition> = _position

    /** Where the chapters start on the book slider (0..1), drawn as ticks so the slider shows the book's structure. */
    private val _chapterStarts = MutableStateFlow<List<Float>>(emptyList())
    val chapterStarts: StateFlow<List<Float>> = _chapterStarts
    private var chapterStartsFor: Any? = null

    /** First page of every chapter of a whole-book document (index = chapter); drives page → chapter previews. */
    private val _chapterStartPages = MutableStateFlow<List<Int>>(emptyList())

    /** 0..1 while the whole-book document is being built (first open), null when the reader can show the page. */
    private val _preparing = MutableStateFlow<Float?>(null)
    val preparing: StateFlow<Float?> = _preparing

    /** True once the WebView has laid the text out and jumped to the saved position; before that the page is hidden. */
    private val _textReady = MutableStateFlow(false)
    val textReady: StateFlow<Boolean> = _textReady

    val chromeVisible = MutableStateFlow(false)
    val selection = MutableStateFlow<ReaderSelection?>(null)
    val wordTap = MutableStateFlow<WordTap?>(null)
    val footnote = MutableStateFlow<String?>(null)
    val image = MutableStateFlow<Any?>(null)
    val translateText = MutableStateFlow<String?>(null)
    val searchState = MutableStateFlow(SearchState())

    private val events = Channel<ReaderEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ReaderEvent> = events.receiveAsFlow()

    // ------------------------------------------------------------------ settings
    private val bookOverride = MutableStateFlow(BookReaderSettings())
    val textSettings: StateFlow<TextReaderSettings> = combine(settings.textSettings.flow, bookOverride) { g, o -> o.text ?: g }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TextReaderSettings())
    private val appDark = MutableStateFlow(false)
    fun setAppDark(dark: Boolean) { appDark.value = dark }
    private val landscape = MutableStateFlow(false)
    fun setLandscape(value: Boolean) { landscape.value = value }

    private fun textColumns(s: TextReaderSettings, wide: Boolean): Int =
        if (s.dualPageLandscape && wide && s.paged && !s.verticalText && !s.longStrip) 2 else 1

    /** Re-emits once a minute so a night schedule switches without reopening the reader. */
    private val minuteTick = MutableStateFlow(0L)

    /** [textSettings] with the auto night theme applied; what the page, the PDF and the reader colours use. */
    val effectiveText: StateFlow<TextReaderSettings> = combine(textSettings, appDark, minuteTick) { s, dark, _ ->
        val night = when (s.autoNightMode) {
            app.yomu.domain.model.AutoNightMode.SYSTEM -> dark
            app.yomu.domain.model.AutoNightMode.SCHEDULE -> {
                val hour = java.time.LocalTime.now().hour
                if (s.nightFromHour == s.nightToHour) false
                else if (s.nightFromHour < s.nightToHour) hour in s.nightFromHour until s.nightToHour
                else hour >= s.nightFromHour || hour < s.nightToHour
            }
        }
        if (s.autoNight && night) s.copy(theme = s.nightTheme) else s
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TextReaderSettings())
    val mangaSettings: StateFlow<MangaSettings> = combine(settings.mangaSettings.flow, bookOverride) { g, o -> o.manga ?: g }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MangaSettings())
    val pdfSettings: StateFlow<PdfSettings> = combine(settings.pdfSettings.flow, bookOverride) { g, o -> o.pdf ?: g }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PdfSettings())
    val common: StateFlow<CommonReaderSettings> = settings.commonReader.stateIn(viewModelScope)

    /** Global manga toggles (not per-book): Wi-Fi-only preload and the long-strip chapter rail. */
    val readerPreloadWifiOnly: StateFlow<Boolean> = settings.readerPreloadWifiOnly.stateIn(viewModelScope)
    val verticalChapterNavigator: StateFlow<Boolean> = settings.verticalChapterNavigator.stateIn(viewModelScope)

    /** Whether the manga reader may preload pages ahead of the current one on the active network. */
    fun mayPreload(): Boolean = preloadAllowed(readerPreloadWifiOnly.value, TrafficMeter.isMetered())

    fun setReaderPreloadWifiOnly(value: Boolean) {
        viewModelScope.launch(ScreenErrors) { settings.readerPreloadWifiOnly.set(value) }
    }

    fun setVerticalChapterNavigator(value: Boolean) {
        viewModelScope.launch(ScreenErrors) { settings.verticalChapterNavigator.set(value) }
    }

    val selectionActions = settings.selectionActions.stateIn(viewModelScope)
    val selectionMenuStyle = settings.selectionMenuStyle.stateIn(viewModelScope)
    val pageMenuStyle = settings.pageMenuStyle.stateIn(viewModelScope)
    val pageActions = settings.pageActions.stateIn(viewModelScope)
    val pageMenu = MutableStateFlow<PageMenuRequest?>(null)
    val customActions = settings.customActions.stateIn(viewModelScope)
    val perBook: StateFlow<Boolean> = combine(bookOverride, _content) { o, c ->
        when ((c as? UiState.Data)?.value) {
            is ReaderContent.Text -> o.text != null
            is ReaderContent.Pdf -> o.pdf != null
            is ReaderContent.Manga -> o.manga != null
            null -> false
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val highlights: StateFlow<List<Highlight>> = (if (args.bookId > 0) annotations.observeHighlights(args.bookId) else flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = (if (args.bookId > 0) annotations.observeBookmarks(args.bookId) else flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val savedWords: StateFlow<Set<String>> = words.observeSavedNormalized().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ------------------------------------------------------------------ online chapters
    private var chapters: List<ChapterInfo> = emptyList()
    private val _chapterId = MutableStateFlow(args.chapterId)
    val chapterId: StateFlow<Long> = _chapterId
    val hasPrevChapter = MutableStateFlow(false)
    val hasNextChapter = MutableStateFlow(false)
    private var onlineType: ContentType = ContentType.MANGA
    private var onlineEntry: CatalogEntry? = null

    /** Chapters of the online window currently loaded, in reading order (at most current ± 1). */
    private var loadedWindow: List<Long> = emptyList()

    /** Chapter whose end was last reached; its page is written when the reader moves to the next one. */
    private var lastChapterId = -1L
    private var lastChapterPage = 0
    private var lastChapterCount = 0
    private var windowJob: Job? = null
    private val windowMutex = Mutex()
    private var textWindowJob: Job? = null
    private val textWindowMutex = Mutex()

    /** Chapters of the online novel window loaded in the WebView, in reading order. */
    private var loadedTextWindow: List<Long> = emptyList()
    private var lastTextLocal = -1
    private var lastTextGlobal = -1L

    /** Where the reader is in the online novel: global chapter id and offset inside it. */
    private var textAnchor: Pair<Long, Int>? = null
    private var textSaveJob: Job? = null

    // ------------------------------------------------------------------ text engine state
    private var initialTarget: Pair<Int, EpubTarget?>? = null
    private var attachedOnce = false
    private val chapterLengths = HashMap<Int, Int>()

    // ------------------------------------------------------------------ tts / autoscroll / translation
    val tts = ReaderTts(context)
    private val _ttsActive = MutableStateFlow(false)
    val ttsActive: StateFlow<Boolean> = _ttsActive
    val sleepTimer = MutableStateFlow(SleepTimer.OFF)
    val sleepRemainingMs = MutableStateFlow<Long?>(null)
    private var sleepJob: Job? = null
    private var ttsSentences: List<Triple<Int, Int, Int>> = emptyList()
    private var ttsChapter = 0

    val autoScroll = MutableStateFlow(false)
    private var autoScrollJob: Job? = null

    val bilingualProgress = MutableStateFlow<Float?>(null)
    private var bilingualJob: Job? = null
    val ocrRunning = MutableStateFlow(false)

    // ------------------------------------------------------------------ stats
    private var sessionStart = 0L
    private var sessionPages = 0
    private var sessionChars = 0L

    /** Last page turn / scroll / tap: time after it without any activity is not counted as reading. */
    private var lastActivity = 0L
    private var onlineEntryId: Long? = null
    private var lastOffset: Int? = null
    private var lastPage = -1
    private var tapZonesShown = false
    private var saveJob: Job? = null

    /** The position waiting for [scheduleSave]'s debounce; written right away when the reader is left. */
    @Volatile private var pendingLocator: Locator? = null
    private val openBookMutex = Mutex()

    /** One OCR at a time: two large pages decoded together would peak far beyond what one page needs. */
    private val ocrMutex = Mutex()
    private val chapterOpenMutex = Mutex()

    // Q01 owners: input routing, chapter navigation/window and page actions each own their cancellation.
    private val inputRouter = ReaderInputRouter(Dispatchers.Main.immediate) { intent -> executeInputIntent(intent) }
    private val chapterNavigator = ChapterNavigator(Dispatchers.Main.immediate, ScreenErrors)
    private val pageActionExecutor = PageActionExecutor(Dispatchers.Main.immediate, ScreenErrors)

    /** Saved state is a plain key/value map; the adapter keeps the snapshot format independent of the framework. */
    private val windowStore = object : SnapshotStore {
        override fun get(key: String): Any? = savedStateHandle.get<Any?>(key)
        override fun set(key: String, value: Any?) { savedStateHandle[key] = value }
    }
    private val sourceResolver = SourceConnectionResolver(context, catalog)

    private var folderReadSession: BookRepositoryImpl.FolderReadSession? = null

    init {
        tts.onSentence = { i -> onTtsSentence(i) }
        tts.onQueueFinished = { onTtsQueueFinished() }
        // A failing engine must not leave the UI saying "reading" with no sound and a stalled queue.
        tts.onError = {
            events.trySend(ReaderEvent.Message(R.string.reader_tts_error))
            stopTts()
        }
        viewModelScope.launch(ScreenErrors) { settings.dictionaryApp.flow.collect { dictionaryAppCache = it } }
        TtsBus.controls = object : TtsBus.Controls {
            override fun play() = resumeTts()
            override fun pause() = pauseTts()
            override fun stop() = stopTts()
        }
        viewModelScope.launch(ScreenErrors) { tts.playing.collect { playing -> TtsBus.update { it.copy(playing = playing) } } }
        viewModelScope.launch(ScreenErrors) {
            while (true) {
                delay(60_000 - System.currentTimeMillis() % 60_000 + 100)
                minuteTick.value = System.currentTimeMillis()
            }
        }
        viewModelScope.launch(ScreenErrors) {
            combine(effectiveText, landscape) { s, wide -> textColumns(s, wide) }
                .distinctUntilChanged()
                .collect { cols -> (current() as? ReaderContent.Text)?.controller?.updateColumns(cols) }
        }
        // A deep link always wins. Only a reader with nothing to open resumes the window left by a killed process.
        val restored = if (ChapterWindowState.shouldRestore(args)) ChapterWindowState.restore(windowStore) else null
        if (restored != null) {
            chapterNavigator.restore(restored)
            _chapterId.value = restored.currentId
        }
        viewModelScope.launch(ScreenErrors) {
            val chapter = restored?.currentId ?: args.chapterId
            runCatching { if (chapter > 0) openOnline(chapter) else openBook(args.bookId, null) }
                .onFailure { e ->
                    if (e is PdfPasswordRequired) _password.value = true
                    else _content.value = UiState.Error(AppError.from(e))
                }
        }
        viewModelScope.launch(ScreenErrors) { effectiveText.collect { s -> (current() as? ReaderContent.Text)?.controller?.updateSettings(s) } }
        viewModelScope.launch(ScreenErrors) { mangaSettings.collect { s -> (current() as? ReaderContent.Manga)?.controller?.settings = s } }
        viewModelScope.launch(ScreenErrors) { pdfSettings.collect { s -> (current() as? ReaderContent.Pdf)?.controller?.settings = s } }
        viewModelScope.launch(ScreenErrors) { highlights.collect { pushHighlights() } }
        viewModelScope.launch(ScreenErrors) { savedWords.collect { w -> (current() as? ReaderContent.Text)?.controller?.setSavedWords(w.toList()) } }
        viewModelScope.launch(ScreenErrors) { settings.wordTap.flow.collect { g -> (current() as? ReaderContent.Text)?.controller?.let { it.wordTap = g; it.refreshConfig() } } }
        viewModelScope.launch(ScreenErrors) {
            var previous: app.yomu.domain.model.CommonReaderSettings? = null
            common.collect { c ->
                tts.speed = c.ttsSpeed
                tts.pitch = c.ttsPitch
                tts.voiceName = c.ttsVoice
                val voiceChanged = previous?.let { it.ttsSpeed != c.ttsSpeed || it.ttsPitch != c.ttsPitch || it.ttsVoice != c.ttsVoice } == true
                // The whole chapter is queued in advance, so a new rate only reaches the rest of the queue after a restart.
                if (voiceChanged && tts.playing.value) tts.restartCurrent()
                previous = c
                (current() as? ReaderContent.Text)?.controller?.let { t ->
                    if (t.footerSpace != c.footer) { t.footerSpace = c.footer; t.refreshConfig() }
                }
            }
        }
    }

    fun current(): ReaderContent? = (_content.value as? UiState.Data)?.value

    /** Writes the window after every change so process death does not lose the chapter being read. */
    private fun persistWindow() {
        val snapshot = chapterNavigator.snapshot()
        if (snapshot == null) ChapterWindowState.clear(windowStore) else ChapterWindowState.save(windowStore, snapshot)
    }

    /** The one place the window changes: every open and every shift goes through here, so the snapshot cannot lag. */
    private fun openWindow(entryId: Long, order: List<Long>, chapterId: Long): ReaderWindow? {
        val window = chapterNavigator.openWindow(entryId, order, chapterId)
        persistWindow()
        return window
    }

    /** Opens the edition linked to this book; the link only names the other book, which opens at its own position. */
    fun openLinkedEdition() {
        linkedEditionId.value?.let { events.trySend(ReaderEvent.OpenBook(it)) }
    }

    /** App chosen for the "dictionary" selection action (kept in memory: the UI asks synchronously). */
    @Volatile private var dictionaryAppCache = app.yomu.domain.model.DictionaryApp.SYSTEM

    fun dictionaryApp(): app.yomu.domain.model.DictionaryApp = dictionaryAppCache

    // ================================================================== opening
    fun submitPassword(password: String) {
        _password.value = false
        _content.value = UiState.Loading
        viewModelScope.launch(ScreenErrors) {
            runCatching { openBook(args.bookId, password) }.onFailure { e ->
                if (e is PdfPasswordRequired) {
                    _password.value = true
                    events.send(ReaderEvent.Message(R.string.reader_wrong_password))
                } else {
                    _content.value = UiState.Error(AppError.from(e))
                }
            }
        }
    }

    /** True the first time a Word document is opened: the reader then offers the print layout. */
    fun takePageLayoutHint(id: Long): Boolean {
        val prefs = context.getSharedPreferences("docx_layout", Context.MODE_PRIVATE)
        val key = "hinted_$id"
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    /** Whether the error can be fixed by picking the book file again (moved, deleted or permission lost). */
    fun canRelink(error: Throwable): Boolean =
        !isOnline && args.bookId > 0 && (error is AppError.FileMissing || error is AppError.NoPermission)

    fun relink(uri: android.net.Uri) {
        _content.value = UiState.Loading
        viewModelScope.launch(ScreenErrors) {
            runCatching { books.relink(args.bookId, uri) }
            retry()
        }
    }

    fun retry() {
        _content.value = UiState.Loading
        viewModelScope.launch(ScreenErrors) {
            runCatching { if (isOnline) openOnline(_chapterId.value) else openBook(args.bookId, null) }
                .onFailure { e -> if (e is PdfPasswordRequired) _password.value = true else _content.value = UiState.Error(AppError.from(e)) }
        }
    }

    private suspend fun openBook(id: Long, password: String?) = openBookMutex.withLock {
        folderReadSession?.close()
        folderReadSession = null
        var lease = books.beginFolderReading(id)
        try {
            val b = books.getBook(id) ?: throw AppError.CorruptedFile("Book $id not found")
            book = b
            linkedEditionId.value = LinkedEditions.linkedId(b.readerSettingsJson)
            _title.value = b.title
            _imageCanvas.value = !b.format.isText
            bookOverride.value = b.readerSettingsJson?.let { runCatching { SettingsJson.decodeFromString(BookReaderSettings.serializer(), it) }.getOrNull() } ?: BookReaderSettings()
            if (lease != null && args.locatorJson != null && args.positionReference == null)
                throw java.io.IOException("This saved position may be outdated; reopen it from the current list")
            val saved = if (args.positionReference != null) {
                books.resolveSavedPosition(id, args.positionReference) ?: books.getProgress(id)
            } else {
                args.locatorJson?.let { runCatching { SettingsJson.decodeFromString(Locator.serializer(), it) }.getOrNull() } ?: books.getProgress(id)
            }
            // Incognito: opening a book must not change its status (the same rule as for progress and sessions).
            if (b.status == ReadingStatus.NEW && !settings.incognito.get()) books.setStatus(listOf(id), ReadingStatus.READING)
            when (b.engine) {
                // Navigation opens audiobooks in the player; the reader never gets one.
                EngineType.AUDIO -> throw AppError.UnsupportedFormat(b.format.name)
                EngineType.TEXT -> {
                    val uri = BookFiles.readingUri(context, b)
                    val doc =
                        withContext(Dispatchers.IO) { EpubTextDocument.open(Containers.open(context, uri, BookFormat.EPUB)) }
                    // An empty or unreadable book would show a blank page with no explanation.
                    if (doc.chapterCount < 1) throw AppError.CorruptedFile("The book has no readable chapters")
                    // Keep the whole-book layout available when the user switches into pages or the long strip.
                    // Vertical text pages along the other axis and never uses that document, so it is not built.
                    val text = bookOverride.value.text ?: runCatching { settings.textSettings.get() }.getOrDefault(TextReaderSettings())
                    val sourceKey = BookHtml.sourceKey(context, uri)
                    _preparing.value = 0f
                    val wholeBook = if (text.verticalText) {
                        null
                    } else {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                BookHtml.prepare(context, b.id, sourceKey, doc) { p -> _preparing.value = p }
                            }
                        }.onFailure { Timber.w(it, "Whole-book document failed") }.getOrNull()
                    }
                    _preparing.value = null
                    val c = newTextController(doc, wholeBook)
                    _toc.value = doc.toc
                    initialTarget = if (saved != null) {
                        if (saved.chapter !in 0 until doc.chapterCount) {
                            // The file was re-converted (a Word document is one chapter now): the old chapter number
                            // means nothing, so the whole-book progression is the closest place.
                            val (ch, p) = doc.locate(saved.totalProgression)
                            ch to EpubTarget.Progression(p)
                        } else if (saved.offset == null && saved.progression == 0f && saved.totalProgression > 0f) {
                            val (ch, p) = doc.locate(saved.totalProgression)
                            ch to EpubTarget.Progression(p)
                        } else {
                            saved.chapter to (saved.offset?.let { EpubTarget.Offset(it) } ?: EpubTarget.Progression(saved.progression))
                        }
                    } else {
                        0 to null
                    }
                    _position.value = ReaderPosition(chapterCount = doc.chapterCount)
                    _content.value = UiState.Data(ReaderContent.Text(c))
                }
                EngineType.PDF -> {
                    val uri = Uri.parse(b.uri)
                    val src = withContext(Dispatchers.IO) {
                        // Highlights another device wrote into the file come in first; the reader draws them itself, so it
                        // opens the file without Yomu's own annotations.
                        runCatching { pdfAnnotations.importFromFile(b.id) }.onFailure { Timber.w(it, "PDF annotations") }
                        PdfSource.open(context, uri, password, limit = pdfAnnotations.renderLimit(b.id, uri))
                    }
                    if (src.pageCount < 1) {
                        src.close()
                        throw AppError.CorruptedFile("The PDF has no pages")
                    }
                    val c = PdfController(src)
                    c.settings = pdfSettings.value
                    c.page = (saved?.chapter ?: 0).coerceIn(0, (src.pageCount - 1).coerceAtLeast(0))
                    c.highlights = highlights.value
                    _toc.value = runCatching { src.toc() }.getOrDefault(emptyList())
                    _position.value = ReaderPosition(chapter = c.page, chapterCount = src.pageCount, page = c.page, pages = src.pageCount)
                    _content.value = UiState.Data(ReaderContent.Pdf(c))
                    onPageChanged(c.page)
                }
                EngineType.MANGA -> {
                    val uri = BookFiles.readingUri(context, b)
                    val pages =
                        withContext(Dispatchers.IO) { PageSources.open(context, b, uri, folderProofs = lease?.pages) }
                    if (pages.count == 0) throw AppError.CorruptedFile("No pages")
                    openManga(pages, (saved?.chapter ?: 0))
                }
            }
            sessionStart = System.currentTimeMillis()
            lastActivity = sessionStart
            folderReadSession = lease
            lease = null
        } finally { lease?.close() }
    }

    private fun newTextController(doc: app.yomu.feature.reader.epub.TextDocument, wholeBook: File? = null): EpubController {
        val c = EpubController(context, doc, File(context.filesDir, "fonts").apply { mkdirs() }, wholeBook)
        c.listener = this
        _textReady.value = false
        _chapterStartPages.value = emptyList()
        c.columns = textColumns(effectiveText.value, landscape.value)
        c.updateSettings(effectiveText.value, relayout = false)
        c.footerSpace = common.value.footer
        c.updateAnimationSpeed(common.value.animationSpeed)
        c.prevLabel = context.getString(R.string.reader_prev_chapter)
        c.nextLabel = context.getString(R.string.reader_next_chapter)
        viewModelScope.launch(ScreenErrors) {
            c.wordTap = settings.wordTap.get()
            c.reduceMotion = settings.reduceMotion.get()
        }
        attachedOnce = false
        return c
    }

    private fun openManga(pages: PageSource, page: Int, online: Boolean = false) {
        val c = MangaController(pages)
        c.settings = mangaSettings.value
        c.page = page.coerceIn(0, (pages.count - 1).coerceAtLeast(0))
        // "Save mobile data": on mobile data only the next page of a chapter read from the network is preloaded.
        SourceConnectionResolver.preloadLimit(DataSaver.active, online)?.let { c.preloadLimit = it }
        _toc.value = emptyList()
        _position.value = ReaderPosition(chapter = c.page, chapterCount = pages.count, page = c.page, pages = pages.count, chapterTitle = chapterOf)
        _content.value = UiState.Data(ReaderContent.Manga(c))
        lastPage = c.page
        // Where to tap: shown once per opened title (not again for every chapter).
        val m = mangaSettings.value
        if (!tapZonesShown && m.showTapZonesOnOpen && m.tapZones != TapZoneLayout.DISABLED && m.direction != MangaDirection.WEBTOON) {
            tapZonesShown = true
            c.showTapZones()
        }
    }

    /** Shows the tap zone overlay of the open manga (settings preview). */
    fun previewTapZones() {
        (current() as? ReaderContent.Manga)?.controller?.showTapZones()
    }

    private suspend fun openOnline(chapterId: Long, mangaPage: Int? = null) {
        val ch = catalog.chapter(chapterId) ?: throw AppError.CorruptedFile("Chapter not found")
        val entry = catalog.entry(ch.entryId) ?: throw AppError.CorruptedFile("Entry not found")
        _chapterId.value = chapterId
        onlineEntryId = entry.id
        onlineEntry = entry
        onlineType = entry.type
        // Reading order shared with the title page; groups hidden there are skipped (except the chapter being opened).
        val excluded = catalog.chapterDisplay(entry.id).excludedScanlators
        chapters = ChapterOrder.reading(catalog.chapters(entry.id).filter { it.id == chapterId || it.scanlator.isNullOrBlank() || it.scanlator !in excluded })
        applyChapterHeader(ch)
        // "Download ahead": keep the next chapters of library titles downloaded while reading.
        viewModelScope.launch(ScreenErrors) { runCatching { downloads.ensureAhead(entry.id, chapterId) }.onFailure { Timber.w(it, "Download ahead failed") } }
        _imageCanvas.value = SourceConnectionResolver.showImageCanvas(entry.type)
        bookOverride.value = catalog.readerSettings(entry.id)?.let { runCatching { SettingsJson.decodeFromString(BookReaderSettings.serializer(), it) }.getOrNull() } ?: BookReaderSettings()
        when (sourceResolver.windowMode(entry.type)) {
            // A bounded document of the current chapter and its neighbours; not one page for a whole series.
            SourceConnectionResolver.WindowMode.TEXT -> check(openTextWindow(chapterId, null)) { "The chapter has no text" }
            // The reader holds the chapter and its neighbours: crossing an ordinary chapter boundary must not show
            // the loading screen again. A chapter that cannot be opened is a real error only for the one being read.
            SourceConnectionResolver.WindowMode.PAGES -> check(openMangaWindow(chapterId, mangaPage ?: ch.lastPageRead.takeIf { !ch.read } ?: 0)) { "No pages" }
        }
        sessionStart = System.currentTimeMillis()
        lastActivity = sessionStart
    }

    /** Title, "Chapter N of M" and the neighbouring chapters of the chapter being read. */
    private fun applyChapterHeader(ch: ChapterInfo) {
        val entry = onlineEntry ?: return
        _chapterId.value = ch.id
        _title.value = entry.title + " · " + ch.name
        // "Chapter 12 of 368" under the title: the number in reading order, as on the title page.
        val slots = ChapterOrder.slots(chapters)
        // Typed explicitly: lint takes "unknown + 1" for a string and reports the %d of the format.
        val number: Int = slots.indexOfFirst { slot -> slot.any { it.id == ch.id } } + 1
        val total: Int = slots.size
        chapterOf = if (number > 0) context.getString(R.string.reader_chapter_of, number, total) else null
        // One resolver for every way to move between chapters; other releases of the same chapter are skipped only
        // when the numbering can be trusted (see ChapterNavigation).
        hasPrevChapter.value = ChapterNavigation.resolve(chapters, ch.id, ChapterIntent.PREVIOUS) != null
        hasNextChapter.value = ChapterNavigation.resolve(chapters, ch.id, ChapterIntent.NEXT) != null
    }

    /**
     * Reading order of the window: one release per chapter, the current release kept as it is. Other releases of the
     * same chapter are slots, so a neighbour is never a second copy of the chapter being read.
     */
    private fun windowOrder(chapterId: Long): List<Long> = chapterNavigator.readingOrder(chapters, chapterId)

    /** Pages of an online chapter: its downloaded copy when there is one, the source's pages otherwise. */
    private suspend fun openChapterPages(ch: ChapterInfo): PageSource = sourceResolver.openPages(ch)

    /**
     * Opens the window around [chapterId] and swaps the reader onto it without the loading state.
     *
     * Retention is explicit: chapters the next window still needs are handed over by [SessionPageSource.transfer], the
     * far ones are released, and only the missing neighbour is fetched. A neighbour that fails leaves the current
     * chapter readable and is reported instead of failing the open.
     */
    private suspend fun openMangaWindow(chapterId: Long, page: Int): Boolean {
        val ch = chapters.firstOrNull { it.id == chapterId } ?: return false
        val order = windowOrder(chapterId)
        val window = openWindow(onlineEntry?.id ?: ch.entryId, order, chapterId) ?: return false
        val desired = window.ids.toSet()
        val old = (current() as? ReaderContent.Manga)?.controller?.pages as? SessionPageSource
        val reused = old?.chapters?.filter { it.id in desired }.orEmpty()
        val opened = ArrayList<MangaChapter>()
        var neighbourFailure: Throwable? = null
        try {
            for (id in window.ids.filter { candidate -> reused.none { it.id == candidate } }) {
                val info = chapters.firstOrNull { it.id == id }
                if (info != null) {
                    val attempt = runCatching { openChapterPages(info) }
                    val source = attempt.getOrNull()
                    if (source != null) {
                        opened += MangaChapter(id, info.name, source)
                    } else if (id == chapterId) {
                        throw attempt.exceptionOrNull() ?: AppError.CorruptedFile("No pages")
                    } else {
                        neighbourFailure = attempt.exceptionOrNull()
                    }
                }
            }
            if (reused.none { it.id == chapterId } && opened.none { it.id == chapterId }) {
                opened.forEach { runCatching { it.source.close() } }
                return false
            }
        } catch (e: Throwable) {
            opened.forEach { runCatching { it.source.close() } }
            throw e
        }
        // Opening succeeded: the old session now hands over the retained chapters and releases the rest.
        old?.transfer(desired)
        val loaded = (reused + opened).sortedBy { chapter -> window.ids.indexOf(chapter.id) }
        val session = SessionPageSource(loaded)
        val controller = MangaController(session, session)
        controller.settings = mangaSettings.value
        // "Save mobile data": only the next page of a chapter read from the network is preloaded on mobile data.
        SourceConnectionResolver.preloadLimit(DataSaver.active, online = true)?.let { controller.preloadLimit = it }
        val slice = session.slices.firstOrNull { it.chapterId == chapterId } ?: return false
        controller.page = slice.start + page.coerceIn(0, slice.pages - 1)
        // The end page leads outside the window only when a chapter follows the last loaded one.
        val lastLoaded = loaded.last().id
        val lastIndex = order.indexOf(lastLoaded)
        controller.hasNextAfterWindow = lastIndex >= 0 && lastIndex < order.lastIndex
        loadedWindow = loaded.map { it.id }
        // The header follows the content: a window that failed to open must not rename the chapter still on screen.
        applyChapterHeader(ch)
        _toc.value = emptyList()
        lastChapterId = chapterId
        lastChapterPage = controller.localPage
        lastChapterCount = controller.localCount
        lastPage = controller.page
        _position.value = ReaderPosition(
            chapter = controller.localPage,
            chapterCount = controller.localCount,
            page = controller.localPage,
            pages = controller.localCount,
            chapterTitle = chapterOf,
        )
        _imageCanvas.value = true
        _content.value = UiState.Data(ReaderContent.Manga(controller))
        // Where to tap: shown once per opened title (not again for every chapter).
        val m = mangaSettings.value
        if (!tapZonesShown && m.showTapZonesOnOpen && m.tapZones != TapZoneLayout.DISABLED && m.direction != MangaDirection.WEBTOON) {
            tapZonesShown = true
            controller.showTapZones()
        }
        neighbourFailure?.let { events.trySend(ReaderEvent.Message(R.string.reader_chapter_neighbour_failed)) }
        return true
    }

    /**
     * Moves to a chapter already loaded in the window; the reader turns there without fetching it again. False when
     * the chapter is outside the window and must be opened instead.
     */
    private fun moveToLoadedChapter(targetId: Long, page: Int = 0): Boolean {
        when (val content = current()) {
            is ReaderContent.Manga -> {
                val controller = content.controller
                if (controller.session == null) return false
                val index = controller.globalIndex(targetId, page) ?: return false
                chapterNavigator.cancelNavigation()
                controller.goTo(index)
                return true
            }
            is ReaderContent.Text -> {
                val doc = content.controller.document as? WindowHtmlDocument ?: return false
                val index = doc.indexOfChapterId(targetId)
                if (index < 0) return false
                chapterNavigator.cancelNavigation()
                viewModelScope.launch(ScreenErrors) {
                    content.controller.goTo(index, EpubTarget.Progression(0f))
                    syncTextChapter(index)
                }
                return true
            }
            else -> return false
        }
    }

    /** Opens the previous/next chapter of an online entry. */
    fun openAdjacentChapter(next: Boolean) {
        // Other groups' releases of the current chapter are skipped; the current group is preferred.
        val target = ChapterNavigation.resolve(chapters, _chapterId.value, if (next) ChapterIntent.NEXT else ChapterIntent.PREVIOUS) ?: return
        if (moveToLoadedChapter(target.id)) return
        openChapter(target.id, finished = next)
    }

    fun openChapterAt(index: Int) {
        val target = chapters.getOrNull(index) ?: return
        if (moveToLoadedChapter(target.id)) return
        openChapter(target.id, finished = false)
    }

    /**
     * One chapter open at a time: two quick taps must not load two containers, where the loser is never closed.
     * A newer request replaces the older one instead of running beside it. Inside an online window the current
     * content stays on screen until the next one is ready; only a reader with nothing to show gets the loading state.
     */
    private fun openChapter(targetId: Long, finished: Boolean, mangaPage: Int? = null) {
        windowJob?.cancel()
        chapterNavigator.navigate {
            chapterOpenMutex.withLock {
                flushStats()
                // Incognito: chapters read online are not marked read.
                if (finished && !settings.incognito.get()) catalog.saveProgress(_chapterId.value, 0, finished = true)
                val target = chapters.firstOrNull { it.id == targetId }
                val mode = sourceResolver.windowMode(onlineType)
                val windowed = target != null && onlineEntry != null &&
                    SourceConnectionResolver.isWindowed(mode, current() is ReaderContent.Text, current() is ReaderContent.Manga)
                if (!windowed) _content.value = UiState.Loading
                runCatching {
                    when {
                        mode == SourceConnectionResolver.WindowMode.PAGES && windowed ->
                            if (!openMangaWindow(targetId, mangaPage ?: target!!.lastPageRead.takeIf { !target.read } ?: 0)) {
                                throw AppError.CorruptedFile("No pages")
                            }
                        mode == SourceConnectionResolver.WindowMode.TEXT && windowed ->
                            if (!openTextWindow(targetId, null)) {
                                throw AppError.CorruptedFile("The chapter has no text")
                            }
                        else -> openOnline(targetId, mangaPage)
                    }
                }.onFailure { e ->
                    if (windowed) events.send(ReaderEvent.Error(e)) else _content.value = UiState.Error(AppError.from(e))
                }
            }
        }
    }

    /**
     * Keeps the window centred on the chapter being read. The next chapter is already loaded when a boundary is
     * crossed, so this runs after the turn, not before it, and a moved reader cancels the pending rebuild.
     */
    private fun scheduleWindowShift(chapterId: Long) {
        val order = windowOrder(chapterId)
        val window = openWindow(onlineEntry?.id ?: 0, order, chapterId) ?: return
        if (window.ids == loadedWindow) return
        windowJob?.cancel()
        windowJob = viewModelScope.launch(ScreenErrors) {
            delay(350)
            windowMutex.withLock {
                if (_chapterId.value != chapterId) return@withLock
                val controller = (current() as? ReaderContent.Manga)?.controller ?: return@withLock
                if (controller.chapter?.id != chapterId) return@withLock
                runCatching { openMangaWindow(chapterId, controller.localPage) }
                    .onFailure {
                        Timber.w(it, "Cannot shift the chapter window")
                        events.send(ReaderEvent.Message(R.string.reader_chapter_window_failed))
                    }
            }
        }
    }

    /**
     * Opens the bounded document of the online novel: the current chapter and its neighbours, with the identity
     * bridge between section index and global chapter. A neighbour that fails is left out; the chapter being read
     * stays. The document is capped by the window, so a long series is never concatenated into one WebView page.
     */
    private suspend fun openTextWindow(chapterId: Long, offset: Int?): Boolean {
        val ch = chapters.firstOrNull { it.id == chapterId } ?: return false
        val order = windowOrder(chapterId)
        val window = openWindow(onlineEntry?.id ?: ch.entryId, order, chapterId) ?: return false
        val loaded = ArrayList<WindowHtmlDocument.Chapter>()
        var neighbourFailure: Throwable? = null
        for (id in window.ids) {
            val info = chapters.firstOrNull { it.id == id }
            if (info != null) {
                val attempt = runCatching { catalog.novelContent(id) }
                val html = attempt.getOrNull()
                if (html != null) {
                    loaded += WindowHtmlDocument.Chapter(id, info.name, html)
                } else if (id == chapterId) {
                    throw attempt.exceptionOrNull() ?: AppError.CorruptedFile("The chapter has no text")
                } else {
                    neighbourFailure = attempt.exceptionOrNull()
                }
            }
        }
        if (loaded.none { it.id == chapterId }) return false
        val doc = WindowHtmlDocument(onlineEntry?.title ?: ch.name, loaded)
        val local = doc.indexOfChapterId(chapterId).coerceAtLeast(0)
        val controller = newTextController(doc)
        // The contents of an online title are global: a jump still names the library chapter, not a window section.
        _toc.value = chapters.mapIndexed { i, chapter -> TocItem(chapter.name, i) }
        initialTarget = local to (offset?.let { EpubTarget.Offset(it) })
        applyChapterHeader(ch)
        loadedTextWindow = loaded.map { it.id }
        lastTextLocal = local
        lastTextGlobal = chapterId
        textAnchor = offset?.let { chapterId to it }
        _position.value = ReaderPosition(chapter = local, chapterCount = doc.chapterCount, chapterTitle = ch.name)
        _content.value = UiState.Data(ReaderContent.Text(controller))
        neighbourFailure?.let {
            Timber.w(it, "A neighbouring novel chapter could not be loaded")
            events.trySend(ReaderEvent.Message(R.string.reader_chapter_neighbour_failed))
        }
        return true
    }

    /**
     * Keeps the header, the saved chapter and the window in step with the section the WebView shows. Moving forward
     * finishes the chapter left behind; moving back never rewrites a read mark.
     */
    private fun syncTextChapter(local: Int) {
        val controller = (current() as? ReaderContent.Text)?.controller ?: return
        val doc = controller.document as? WindowHtmlDocument ?: return
        val global = doc.chapterId(local) ?: return
        if (global == _chapterId.value) return
        if (lastTextGlobal > 0 && local > lastTextLocal) {
            val left = lastTextGlobal
            viewModelScope.launch(ScreenErrors) { catalog.saveProgress(left, 0, finished = true) }
        }
        lastTextLocal = local
        lastTextGlobal = global
        chapters.firstOrNull { it.id == global }?.let { applyChapterHeader(it) }
        scheduleTextWindowShift(global)
    }

    /** Re-centres the novel window after a chapter turn, keeping the offset inside the chapter being read. */
    private fun scheduleTextWindowShift(chapterId: Long) {
        val order = windowOrder(chapterId)
        val window = openWindow(onlineEntry?.id ?: 0, order, chapterId) ?: return
        if (window.ids == loadedTextWindow) return
        textWindowJob?.cancel()
        textWindowJob = viewModelScope.launch(ScreenErrors) {
            delay(400)
            textWindowMutex.withLock {
                if (_chapterId.value != chapterId) return@withLock
                val controller = (current() as? ReaderContent.Text)?.controller ?: return@withLock
                val doc = controller.document as? WindowHtmlDocument ?: return@withLock
                if (doc.indexOfChapterId(chapterId) < 0) return@withLock
                val offset = textAnchor?.takeIf { it.first == chapterId }?.second
                runCatching { openTextWindow(chapterId, offset) }
                    .onFailure {
                        Timber.w(it, "Cannot shift the novel window")
                        events.send(ReaderEvent.Message(R.string.reader_chapter_window_failed))
                    }
            }
        }
    }

    /** Debounced write of the online novel position: the library needs the chapter, not every scroll tick. */
    private fun scheduleTextSave() {
        if (!isOnline) return
        textSaveJob?.cancel()
        textSaveJob = viewModelScope.launch(ScreenErrors) {
            delay(800)
            val (chapterId, _) = textAnchor ?: return@launch
            if (!settings.incognito.get()) catalog.saveProgress(chapterId, 0, finished = false)
        }
    }

    fun onWebViewAttached(c: EpubController) {
        // The old page must not flash before the new WebView has laid the text out and jumped to the position.
        _textReady.value = false
        if (!attachedOnce) {
            attachedOnce = true
            val (ch, target) = initialTarget ?: (0 to null)
            c.open(ch, target)
        } else {
            c.open(c.chapter, EpubTarget.Offset(c.progress.offset))
        }
    }

    // ------------------------------------------------------------------ fulltext index
    /** One chapter-text indexing at a time: an old chapter must not write over a newer one after a jump. */
    private var indexTextJob: Job? = null

    /**
     * Best-effort indexing of a local text chapter the reader has actually laid out. Runs off the rendering path,
     * never changes reading behaviour and is skipped unless the opt-in index is on and a real book is attached (an
     * online chapter has no book row for the foreign key).
     */
    private fun indexChapterText(chapter: Int) {
        if (isOnline || args.bookId <= 0) return
        indexTextJob?.cancel()
        indexTextJob = viewModelScope.launch(ScreenErrors) {
            runCatching {
                if (settings.fulltextIndex.get()) {
                    val controller = (current() as? ReaderContent.Text)?.controller
                    if (controller != null) {
                        val document = controller.document
                        // chapterHtml already runs on IO; parsing it here keeps the WebView's rendering thread free.
                        val text = ChapterText.of(document.chapterHtml(chapter))
                        fulltextIndex.indexText(args.bookId, "chapter:${document.chapterPath(chapter)}", text)
                    }
                }
            }.onFailure { Timber.w(it, "Fulltext index of chapter $chapter failed") }
        }
    }

    // ================================================================== text listener
    override fun onReady(chapter: Int) {
        _textReady.value = true
        syncTextChapter(chapter)
        indexChapterText(chapter)
        pushHighlights()
        val c = (current() as? ReaderContent.Text)?.controller ?: return
        c.setSavedWords(savedWords.value.toList())
        if (textSettings.value.bilingual) startBilingual()
        if (_ttsActive.value && ttsChapter != chapter) viewModelScope.launch(ScreenErrors) { delay(300); speakTextFrom(c, 0) }
    }

    /** Exact page starts of a whole-book document: the page chip, the chapter ticks and the seek preview. */
    override fun onLayout(layout: EpubLayout) {
        if (layout.pages <= 0) return
        _chapterStartPages.value = layout.starts
        _chapterStarts.value = if (layout.pages <= 1) {
            emptyList()
        } else {
            val marks = layout.starts.filter { it in 1 until layout.pages }
                .map { it.toFloat() / (layout.pages - 1) }
                .filter { it in 0.002f..0.998f }
                .distinct()
            // A novel can have hundreds of chapters: keep the track readable (and the canvas cheap) for the eye.
            val step = ((marks.size + 79) / 80).coerceAtLeast(1)
            marks.filterIndexed { index, _ -> index % step == 0 }
        }
        // The page chip is right before the first scroll report after a reflow.
        val position = _position.value
        _position.value = position.copy(bookPages = layout.pages)
    }

    override fun onProgress(chapter: Int, progress: EpubProgress) {
        val c = (current() as? ReaderContent.Text)?.controller ?: return
        // Reports that belong to a chapter the controller already left would move the saved position backwards.
        if (chapter != c.chapter) return
        syncTextChapter(chapter)
        val doc = c.document
        if (doc is WindowHtmlDocument) {
            doc.chapterId(chapter)?.let { global ->
                textAnchor = global to progress.offset
                scheduleTextSave()
            }
        }
        if (!c.wholeBook) updateChapterStarts(doc)
        chapterLengths[chapter] = progress.total
        markActivity()
        if (lastPage != progress.page || lastOffset == null) sessionPages++
        lastOffset?.let { if (progress.offset > it) sessionChars += (progress.offset - it).coerceAtMost(5000) }
        lastOffset = progress.offset
        lastPage = progress.page
        val whole = c.wholeBook
        val measuredPages = whole && progress.bookPages > 0 && progress.bookPage >= 0
        val scrollFraction = progress.bookScrollProgression?.coerceIn(0f, 1f)
        val exactChars = whole && progress.bookTotal > 0
        val total = when {
            measuredPages && c.pagedBook -> pageSeekFraction(progress.bookPage, progress.bookPages)
            exactChars -> progress.bookOffset.toFloat() / progress.bookTotal.coerceAtLeast(1)
            else -> doc.totalProgression(chapter, progress.progression)
        }.coerceIn(0f, 1f)
        val charsLeft = (progress.total - progress.offset).coerceAtLeast(0)
        val (bookPage, bookPages) = if (measuredPages) progress.bookPage + 1 to progress.bookPages else estimatePages(doc, chapter, progress)
        // The weights of every chapter are only needed for the estimate; a whole-book document knows the exact rest.
        val leftInBook = if (exactChars) {
            (progress.bookTotal - progress.bookOffset).coerceAtLeast(0).toLong()
        } else {
            val totalWeight = (0 until doc.chapterCount).sumOf { doc.weight(it).coerceAtLeast(1) }
            ((1f - total) * totalWeight).toLong().coerceAtLeast(charsLeft.toLong())
        }
        val title = doc.chapterTitle(chapter)
        hereLocator = Locator(chapter = chapter, progression = progress.progression, totalProgression = total, offset = progress.offset)
        _position.value = ReaderPosition(
            bookPage = bookPage,
            bookPages = bookPages,
            approximate = scrollFraction != null || (!whole && doc.chapterCount > 1),
            chapter = chapter,
            chapterCount = doc.chapterCount,
            page = progress.page,
            pages = progress.pages,
            progression = progress.progression,
            total = total,
            seekFraction = scrollFraction ?: total,
            seekPageCount = if (measuredPages && c.pagedBook) bookPages else 0,
            viewportSeek = scrollFraction != null,
            chapterTitle = title,
            charsLeftInChapter = charsLeft,
            charsLeftInBook = leftInBook,
        )
        scheduleSave(Locator(chapter = chapter, progression = progress.progression, totalProgression = total, offset = progress.offset, chapterTitle = title))
    }

    /**
     * A tap from any engine, at fractions 0..1 of the reader viewport. EPUB already sends fractions from its
     * script; the PDF view normalizes its gesture against the reader viewport before calling here.
     */
    override fun onTap(x: Float, y: Float) {
        markActivity()
        if (selection.value != null) return
        val point = ReaderPoint(x, y)
        when (val c = current()) {
            is ReaderContent.Text -> {
                val rtl = textSettings.value.rtl || c.controller.document.rtl
                inputRouter.submit(inputRouter.routeTap(point, common.value.textTapZones, rtl, common.value.tapToTurn))
            }
            is ReaderContent.Pdf -> {
                // A PDF has its own scheme or, by default, the text one.
                inputRouter.submit(inputRouter.routeTap(point, common.value.pdfZones(), false, common.value.tapToTurn))
            }
            else -> toggleChrome()
        }
    }

    /** Executes the one intent the router chose; the router owns when it is delivered. */
    private fun executeInputIntent(intent: ReaderIntent) {
        when (intent) {
            ReaderIntent.Previous -> {
                val c = current()
                if (c is ReaderContent.Pdf && c.controller.page <= 0) return
                prevPage()
            }
            ReaderIntent.Next -> {
                val c = current()
                if (c is ReaderContent.Pdf && c.controller.page >= c.controller.pageCount - 1) return
                nextPage()
            }
            ReaderIntent.Menu -> toggleChrome()
        }
    }

    private fun toggleChrome() {
        chromeVisible.value = !chromeVisible.value
    }

    var density = 1f

    private fun cssRect(controller: EpubController, l: Float, t: Float, r: Float, b: Float): Rect {
        val origin = IntArray(2)
        controller.webView?.getLocationInWindow(origin)
        return Rect(l * density + origin[0], t * density + origin[1], r * density + origin[0], b * density + origin[1])
    }

    override fun onSelection(selection: EpubSelection?) {
        val c = (current() as? ReaderContent.Text)?.controller ?: return
        this.selection.value = selection?.takeIf { it.text.isNotBlank() }?.let {
            // A long strip spans several chapters: the runtime says which one the selection is in.
            val chapter = it.chapter.takeIf { ch -> ch >= 0 } ?: c.chapter
            ReaderSelection(it.text, cssRect(c, it.left, it.top, it.right, it.bottom), chapter, it.start, it.end, it.sentence)
        }
    }

    override fun onWord(word: EpubWord) {
        val c = (current() as? ReaderContent.Text)?.controller ?: return
        val chapter = word.chapter.takeIf { ch -> ch >= 0 } ?: c.chapter
        wordTap.value = WordTap(
            word = word.word,
            sentence = word.sentence,
            anchor = cssRect(c, word.left, word.top, word.right, word.bottom),
            locator = Locator(chapter = chapter, offset = word.start, endOffset = word.end, text = word.word, chapterTitle = c.document.chapterTitle(chapter)),
        )
    }

    override fun onExternalLink(url: String) {
        events.trySend(ReaderEvent.OpenUrl(url))
    }

    override fun onImage(model: Any) {
        image.value = model
    }

    override fun onFootnote(html: String) {
        footnote.value = html
    }

    override fun onHighlightTap(id: Long, x: Float, y: Float) {
        val controller = (current() as? ReaderContent.Text)?.controller ?: return
        val h = highlights.value.firstOrNull { it.id == id } ?: return
        selection.value = ReaderSelection(
            h.text, cssRect(controller, x, y, x, y), h.locator.chapter,
            h.locator.offset ?: 0, h.locator.endOffset ?: 0, highlightId = id,
        )
    }

    override fun onEdgeReached(forward: Boolean) {
        val c = (current() as? ReaderContent.Text)?.controller ?: return
        // A whole-book document has one edge: the end of the book. Chapters inside it are ordinary page turns.
        if (c.wholeBook) {
            if (forward) finishBook()
            return
        }
        if (forward) {
            if (c.chapter < c.document.chapterCount - 1) c.nextChapter()
            else if (isOnline && hasNextChapter.value) openAdjacentChapter(true)
            else finishBook()
        } else {
            if (c.chapter > 0) c.prevChapter(toEnd = true)
            else if (isOnline && hasPrevChapter.value) openAdjacentChapter(false)
        }
    }

    // ================================================================== pdf listener
    override fun onSelection(selection: PdfSelection?, bounds: Rect?) {
        this.selection.value = if (selection == null || bounds == null || selection.text.isBlank()) null
        else ReaderSelection(selection.text, bounds, selection.page, selection.start, selection.end)
    }

    override fun onHighlightTap(highlight: Highlight, bounds: Rect) {
        selection.value = ReaderSelection(highlight.text, bounds, highlight.locator.chapter, highlight.locator.offset ?: 0, highlight.locator.endOffset ?: 0, highlightId = highlight.id)
    }

    override fun onLink(uri: String) {
        events.trySend(ReaderEvent.OpenUrl(uri))
    }

    override fun onPageChanged(page: Int) {
        when (val content = current()) {
            is ReaderContent.Manga -> onMangaPageChanged(content.controller, page)
            is ReaderContent.Pdf -> onPdfPageChanged(content, page)
            else -> Unit
        }
    }

    private fun onPdfPageChanged(content: ReaderContent.Pdf, page: Int) {
        val controller = content.controller
        val count = controller.pageCount
        markActivity()
        if (page != lastPage) sessionPages++
        lastPage = page
        val total = pageSeekFraction(page, count)
        hereLocator = Locator(chapter = page, progression = 0f, totalProgression = total)
        _position.value = ReaderPosition(
            chapter = page, chapterCount = count, page = page, pages = count, progression = total, total = total,
            bookPage = page + 1, bookPages = count,
        )
        if (isOnline) {
            val id = _chapterId.value
            // Incognito: an online chapter is not marked read either.
            viewModelScope.launch(ScreenErrors) {
                if (!settings.incognito.get()) catalog.saveProgress(id, page, finished = page >= count - 1)
            }
        } else {
            scheduleSave(Locator(chapter = page, progression = 0f, totalProgression = total))
        }
        if (controller.visiblePages(page).last >= count - 1 && !isOnline) {
            viewModelScope.launch(ScreenErrors) {
                delay(400)
                if (current() == content && controller.visiblePages(controller.page).last >= count - 1) markFinishedQuietly()
            }
        }
        if (_ttsActive.value && ttsChapter != page) speakPdfPage(page)
    }

    /**
     * A page of the loaded window. The pager counts pages of all held chapters; progress, the page chip and the saved
     * position always describe the physical page inside the chapter that page belongs to. Crossing into the next
     * chapter writes the chapter that was left and re-centres the window after the turn, never before it.
     */
    private fun onMangaPageChanged(controller: MangaController, flat: Int) {
        markActivity()
        if (flat != lastPage) sessionPages++
        lastPage = flat
        val chapter = controller.chapter
        if (chapter != null) {
            val local = controller.localPage
            val count = controller.localCount
            if (chapter.id != lastChapterId) {
                if (lastChapterId > 0) {
                    val left = lastChapterId
                    val page = lastChapterPage
                    val finished = lastChapterCount > 0 && page >= lastChapterCount - 1
                    viewModelScope.launch(ScreenErrors) {
                        if (!settings.incognito.get()) catalog.saveProgress(left, page, finished)
                    }
                }
                lastChapterId = chapter.id
                lastChapterPage = local
                lastChapterCount = count
                chapters.firstOrNull { it.id == chapter.id }?.let { applyChapterHeader(it) }
                scheduleWindowShift(chapter.id)
            } else {
                lastChapterPage = local
                lastChapterCount = count
            }
            val total = if (count <= 1) 1f else local / (count - 1).toFloat()
            hereLocator = Locator(chapter = local, progression = 0f, totalProgression = total, onlineChapterId = chapter.id)
            _position.value = ReaderPosition(
                chapter = local, chapterCount = count, page = local, pages = count, progression = total, total = total,
                bookPage = local + 1, bookPages = count, chapterTitle = chapterOf,
            )
            // Incognito: an online chapter is not marked read either.
            viewModelScope.launch(ScreenErrors) {
                if (!settings.incognito.get()) catalog.saveProgress(chapter.id, local, finished = local >= count - 1)
            }
            return
        }
        // A local publication: the pager spans the whole file.
        val count = controller.count
        val total = if (count <= 1) 1f else flat / (count - 1).toFloat()
        hereLocator = Locator(chapter = flat, progression = 0f, totalProgression = total)
        _position.value = ReaderPosition(
            chapter = flat, chapterCount = count, page = flat, pages = count, progression = total, total = total,
            bookPage = flat + 1, bookPages = count,
        )
        scheduleSave(Locator(chapter = flat, progression = 0f, totalProgression = total))
        if (flat >= count - 1) {
            viewModelScope.launch(ScreenErrors) { delay(400); if (lastPage >= count - 1) markFinishedQuietly() }
        }
    }

    // ================================================================== manga listener
    override fun onMenu() = toggleChrome()

    override fun onLongPress(page: Int) = onLongPressAt(page, Offset.Unspecified)

    /** A long press on a manga page opens the long-press menu; with the menu off, its first action runs at once. */
    override fun onLongPressAt(page: Int, windowPosition: Offset) {
        viewModelScope.launch(ScreenErrors) {
            val context = pageContext(PageEngine.MANGA, page) ?: return@launch
            openPageMenu(context, windowPosition)
        }
    }

    /**
     * A long press on a PDF page that did not land on a glyph. A scanned page has no text to select, so this is how
     * its page menu is reached; a page with a text layer still opens it from the empty margins.
     */
    override fun onPageLongPress(page: Int, windowPosition: Offset) {
        viewModelScope.launch(ScreenErrors) {
            val context = pageContext(PageEngine.PDF, page) ?: return@launch
            openPageMenu(context, windowPosition)
        }
    }

    fun showCurrentPageActions() {
        val content = current()
        val target = when (content) {
            is ReaderContent.Pdf -> PageEngine.PDF to content.controller.page
            is ReaderContent.Manga -> PageEngine.MANGA to content.controller.page
            else -> return
        }
        viewModelScope.launch(ScreenErrors) {
            val context = pageContext(target.first, target.second) ?: return@launch
            if (current() == content) pageMenu.value = PageMenuRequest(context, Offset.Unspecified)
        }
    }

    private fun openPageMenu(context: PageContext, windowPosition: Offset) {
        if (pageMenuStyle.value == QuickMenuStyle.OFF || !windowPosition.isSpecified) {
            runPageAction(context, defaultPageAction(context))
        } else {
            pageMenu.value = PageMenuRequest(context, windowPosition)
        }
    }

    /** The configured first action, restricted to what the engine can actually do. */
    private fun defaultPageAction(context: PageContext): PageAction {
        val enabled = QuickActions.normalize(pageActions.value).filter { it.enabled }.map { it.action }
        return pageActionExecutor.defaultAction(context, enabled)
    }

    /**
     * Identity and available actions of a page of the currently open content. The page number is the physical page
     * of the engine; a PDF is never described as a manga.
     */
    private suspend fun pageContext(engine: PageEngine, page: Int): PageContext? = when (val c = current()) {
        is ReaderContent.Manga -> {
            val controller = c.controller
            val chapter = controller.chapterAt(page)
            PageContext(
                engine = PageEngine.MANGA,
                bookId = args.bookId,
                entryId = 0,
                chapterId = chapter?.id ?: _chapterId.value,
                page = controller.localOf(page),
                pageCount = chapter?.count ?: controller.count,
                capabilities = PageCapabilitiesOf.manga(args.bookId),
            ).takeIf { it.isValid }
        }
        is ReaderContent.Pdf -> {
            val count = c.controller.pageCount
            PageContext(
                engine = PageEngine.PDF,
                bookId = args.bookId,
                entryId = 0,
                chapterId = _chapterId.value,
                page = page,
                pageCount = count,
                capabilities = PageCapabilitiesOf.pdf(args.bookId, hasTextLayer = pdfHasText(c.controller, page)),
            ).takeIf { it.isValid }
        }
        is ReaderContent.Text -> null
        null -> null
    }.takeIf { it?.engine == engine }

    /**
     * True when the page carries selectable text, so export uses the text layer instead of OCR. A scanned page
     * reports an empty string here; a damaged page that cannot be read is treated the same way.
     */
    private suspend fun pdfHasText(controller: PdfController, page: Int): Boolean =
        withContext(Dispatchers.IO) { runCatching { controller.pdf.text(page) }.getOrDefault("").isNotBlank() }

    /** Runs an action against an explicit page identity; an action the engine does not support is not run. */
    fun runPageAction(context: PageContext, action: PageAction) {
        pageMenu.value = null
        if (!isPageActionSupported(action, context)) {
            events.trySend(ReaderEvent.Message(R.string.reader_action_unavailable))
            return
        }
        when (action) {
            PageAction.TRANSLATE -> if (context.engine == PageEngine.MANGA) {
                translateMangaPage(context.page, context.chapterId)
            } else {
                translatePdfPage(context)
            }
            PageAction.SAVE -> savePage(context)
            PageAction.SHARE -> sharePage(context)
            PageAction.COPY_TEXT -> copyPageText(context)
            PageAction.BOOKMARK -> bookmarkPage(context)
            PageAction.SET_COVER -> setPageAsCover(context)
            PageAction.NOTE -> if (context.acceptsNote) {
                events.trySend(ReaderEvent.PageNote(physicalPageLocator(context), context.page))
            } else {
                events.trySend(ReaderEvent.Message(R.string.reader_online_no_annotations))
            }
        }
    }

    fun isPageActionSupported(action: PageAction, context: PageContext): Boolean = pageActionExecutor.isSupported(action, context)

    /**
     * The bitmap of a manga page from its cached file. For a PDF there is no stored page image: it is rendered on
     * demand within the same pixel budget used by OCR, and the original document is never modified.
     */
    private suspend fun pageImage(context: PageContext): android.graphics.Bitmap? = when (context.engine) {
        PageEngine.MANGA -> {
            val file = pageFile(context)
            file?.let { withContext(Dispatchers.IO) { decodeForOcr(it) } }
        }
        PageEngine.PDF -> {
            val c = current() as? ReaderContent.Pdf ?: return null
            withContext(Dispatchers.IO) { runCatching { c.controller.pdf.render(context.page, PDF_EXPORT_WIDTH, annotations = false) }.getOrNull() }
        }
        PageEngine.TEXT -> null
    }

    private suspend fun pageFile(context: PageContext): java.io.File? = (current() as? ReaderContent.Manga)?.controller?.let { c ->
        val index = c.globalIndex(context.chapterId, context.page) ?: return null
        withContext(Dispatchers.IO) { c.file(index) }
    }

    /**
     * Decodes a page for OCR within a pixel budget: a 3000×30000 webtoon strip would need ~343 MiB as an ARGB bitmap
     * before OCR allocates its own buffers. Downsampling keeps the page readable and the peak predictable.
     */
    private fun decodeForOcr(file: java.io.File): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        var pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        while (pixels > MAX_OCR_PIXELS && sample < 32) {
            sample *= 2
            pixels = (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample)
        }
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /** Image type of a page file (pages are cached without a file extension). */
    private fun imageType(file: java.io.File): Pair<String, String> {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, o)
        return when (o.outMimeType) {
            "image/png" -> "image/png" to "png"
            "image/webp" -> "image/webp" to "webp"
            "image/gif" -> "image/gif" to "gif"
            "image/avif", "image/heif", "image/heic" -> (o.outMimeType ?: "image/heif") to "heif"
            else -> "image/jpeg" to "jpg"
        }
    }

    /**
     * The bytes of a page for saving or sharing. Manga copies the cached page file as-is; a PDF page is rendered
     * into a PNG under the share/cache directory, so nothing writes into the opened document.
     */
    private suspend fun pageBytes(context: PageContext, ext: String): Pair<java.io.File, String>? = when (context.engine) {
        PageEngine.MANGA -> {
            val file = pageFile(context) ?: return null
            val mime = withContext(Dispatchers.IO) { imageType(file) }.first
            file to mime
        }
        PageEngine.PDF -> {
            val c = current() as? ReaderContent.Pdf ?: return null
            val out = withContext(Dispatchers.IO) {
                val dir = app.yomu.core.common.Share.shareDir(this@ReaderViewModel.context)
                val target = java.io.File(dir, "pdf-" + pageName(context.page, ext))
                val bmp = runCatching { c.controller.pdf.render(context.page, PDF_EXPORT_WIDTH, annotations = false) }.getOrNull()
                    ?: error("Cannot render the page")
                try {
                    target.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    bmp.recycle()
                }
                target
            }
            out to "image/png"
        }
        PageEngine.TEXT -> null
    }

    private fun pageName(page: Int, ext: String): String {
        val base = listOf(_title.value, chapterOf).filter { !it.isNullOrBlank() }.joinToString(" - ").ifBlank { "Yomu" }
        return app.yomu.core.common.Files.sanitize("$base - ${page + 1}.$ext")
    }

    /** Saves the page to the gallery (Pictures/Yomu). */
    private fun savePage(context: PageContext) = pageActionExecutor.execute {
        runCatching {
            val (file, mime) = pageBytes(context, "png") ?: return@execute
            val name = pageName(context.page, if (mime == "image/png") "png" else "jpg")
            if (android.os.Build.VERSION.SDK_INT < 29) {
                events.send(ReaderEvent.SavePageAs(file, name, mime))
                return@execute
            }
            withContext(Dispatchers.IO) {
                val resolver = this@ReaderViewModel.context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Yomu")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot save the image")
                try {
                    (resolver.openOutputStream(uri) ?: error("Cannot save the image")).use { out -> file.inputStream().use { it.copyTo(out) } }
                    resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            }
            events.send(ReaderEvent.Message(R.string.reader_page_saved))
        }.onFailure { events.send(ReaderEvent.Error(it)) }
    }

    /** Writes a page picked with "Save as" (Android 9 and older). */
    fun savePageTo(file: java.io.File, target: android.net.Uri) = pageActionExecutor.execute {
        runCatching {
            withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(target) ?: error("Cannot save the image")).use { out -> file.inputStream().use { it.copyTo(out) } } }
            events.send(ReaderEvent.Message(R.string.reader_page_saved))
        }.onFailure { events.send(ReaderEvent.Error(it)) }
    }

    private fun sharePage(context: PageContext) = pageActionExecutor.execute {
        runCatching {
            val (file, mime) = pageBytes(context, "png") ?: return@execute
            val shared = withContext(Dispatchers.IO) {
                val ext = if (mime == "image/png") "png" else "jpg"
                val out = java.io.File(app.yomu.core.common.Share.shareDir(this@ReaderViewModel.context), pageName(context.page, ext))
                if (file.absolutePath != out.absolutePath) file.copyTo(out, overwrite = true)
                out to mime
            }
            app.yomu.core.common.Share.shareFile(this@ReaderViewModel.context, shared.first, shared.second)
        }.onFailure { events.send(ReaderEvent.Error(it)) }
    }

    /** Text of a page: the PDF text layer when present, otherwise OCR of the rendered page. */
    private fun copyPageText(context: PageContext) = pageActionExecutor.execute {
        ocrRunning.value = true
        try {
            runCatching {
                val text = when (context.engine) {
                    PageEngine.PDF -> {
                        val c = current() as? ReaderContent.Pdf ?: return@runCatching
                        runCatching { c.controller.pdf.text(context.page) }.getOrDefault("").trim()
                            .ifEmpty { ocrPdfPage(c.controller.pdf, context.page) }
                    }
                    PageEngine.MANGA -> {
                        val file = pageFile(context) ?: return@runCatching
                        ocrMutex.withLock {
                            val bmp = withContext(Dispatchers.IO) { decodeForOcr(file) } ?: error("Cannot decode page")
                            try {
                                ocr.recognize(bmp, settings.ocrLanguage.get(), cacheKey = "${args.bookId}_${_chapterId.value}_${context.page}")
                                    .joinToString("\n\n") { it.text.trim() }.trim()
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    PageEngine.TEXT -> return@runCatching
                }
                if (text.isEmpty()) {
                    events.send(ReaderEvent.Message(R.string.reader_ocr_nothing))
                } else {
                    val cm = this@ReaderViewModel.context.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("yomu", text))
                    events.send(ReaderEvent.Message(R.string.reader_page_text_copied))
                }
            }.onFailure { events.send(ReaderEvent.Error(it)) }
        } finally {
            ocrRunning.value = false
        }
    }

    /** A bookmark at the page (books) or on the chapter (online titles). */
    private fun bookmarkPage(context: PageContext) {
        if (args.bookId > 0) {
            toggleBookmark(physicalPageLocator(context), context.page)
            return
        }
        pageActionExecutor.execute {
            val id = context.chapterId
            val ch = catalog.chapter(id) ?: return@execute
            catalog.setBookmarked(listOf(id), !ch.bookmarked)
            events.send(ReaderEvent.Message(if (ch.bookmarked) R.string.reader_chapter_unbookmarked else R.string.reader_chapter_bookmarked))
        }
    }

    private fun physicalPageLocator(context: PageContext): Locator {
        val fraction = pageSeekFraction(context.page, context.pageCount)
        return Locator(context.page, progression = fraction, totalProgression = fraction)
    }

    private fun setPageAsCover(context: PageContext) {
        if (args.bookId <= 0) {
            events.trySend(ReaderEvent.Message(R.string.reader_cover_books_only))
            return
        }
        pageActionExecutor.execute {
            runCatching {
                val file = when (context.engine) {
                    PageEngine.MANGA -> pageFile(context) ?: return@execute
                    else -> pageBytes(context, "png")?.first ?: return@execute
                }
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                books.setCover(args.bookId, bytes) ?: error("Cannot save the cover")
                events.send(ReaderEvent.Message(R.string.reader_cover_set))
            }.onFailure { events.send(ReaderEvent.Error(it)) }
        }
    }

    /** Translates a PDF page through its text layer, or OCR when the page is a scan. */
    private fun translatePdfPage(context: PageContext) = pageActionExecutor.execute {
        val c = current() as? ReaderContent.Pdf ?: return@execute
        val text = runCatching { c.controller.pdf.text(context.page) }.getOrDefault("").trim()
            .ifEmpty { ocrPdfPage(c.controller.pdf, context.page) }
        if (text.isNotEmpty()) translateText.value = text.take(4000) else events.send(ReaderEvent.Message(R.string.reader_no_text))
    }

    override fun onChapterEdge(next: Boolean) {
        if (isOnline) openAdjacentChapter(next) else if (next) finishBook()
    }

    override fun onFinished() {
        if (isOnline && hasNextChapter.value) return
        finishBook()
    }

    override fun onBlockTap(page: Int, block: OverlayBlock) {
        translateText.value = block.original
    }

    // ================================================================== progress & stats
    private fun scheduleSave(locator: Locator) {
        if (args.bookId <= 0) return
        pendingLocator = locator
        saveJob?.cancel()
        saveJob = viewModelScope.launch(ScreenErrors) {
            delay(800)
            flushProgress()
        }
    }

    /** Writes the debounced position now; it must not be lost when the app goes to the background or is closed. */
    private suspend fun flushProgress() {
        val locator = pendingLocator ?: return
        pendingLocator = null
        // Incognito: the position is kept only while the reader is open.
        if (settings.incognito.get()) return
        withContext(NonCancellable) {
            books.saveProgress(args.bookId, locator)
            // Text books remember every chapter separately: coming back to a chapter restores where it was left.
            if (current() is ReaderContent.Text && locator.offset != null) books.saveChapterProgress(args.bookId, locator.chapter, locator)
        }
    }

    private fun markFinishedQuietly() {
        if (args.bookId <= 0) return
        // App-lifetime: finishing the last page and backing out at once must still mark the book.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            // Incognito: reading a book to the end must not change its status behind the user's back.
            if (settings.incognito.get()) return@launch
            runCatching { books.setStatus(listOf(args.bookId), ReadingStatus.FINISHED) }
        }
    }

    private fun finishBook() {
        markFinishedQuietly()
        events.trySend(ReaderEvent.Finished)
    }

    fun onPause() {
        saveJob?.cancel()
        // In a pocket the auto scroll would keep turning pages and the position would drift while the app is away.
        if (autoScroll.value) stopAutoScroll()
        // Home or the recents screen may be followed by the process being killed: save before that can happen.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { runCatching { flushProgress() } }
        viewModelScope.launch(ScreenErrors) { flushStats() }
    }

    fun onResume() {
        sessionStart = System.currentTimeMillis()
        lastActivity = sessionStart
        sessionPages = 0
        sessionChars = 0
    }

    /** Called on every sign of reading; a long pause closes the session at the last activity and starts a new one. */
    private fun markActivity() {
        val now = System.currentTimeMillis()
        if (sessionStart > 0 && lastActivity > 0 && now - lastActivity > IDLE_MS) {
            val end = lastActivity + IDLE_TAIL_MS
            viewModelScope.launch(ScreenErrors) { flushStats(end) }
        }
        lastActivity = now
    }

    private suspend fun flushStats(endAt: Long? = null) {
        val start = sessionStart
        if (start == 0L) return
        val now = System.currentTimeMillis()
        // Without page turns for a long time the reader was probably left open: stop counting shortly after the last one.
        val end = endAt ?: if (lastActivity > start) minOf(now, lastActivity + IDLE_MS) else now
        val duration = (end - start).coerceAtLeast(0)
        val pages = sessionPages
        val chars = sessionChars
        sessionStart = now
        sessionPages = 0
        sessionChars = 0
        if (duration < 5_000) return
        // Incognito: no reading history or statistics.
        if (settings.incognito.get()) return
        withContext(NonCancellable) {
            stats.recordSession(args.bookId.takeIf { it > 0 }, start, duration, pages, chars, onlineEntryId.takeIf { isOnline })
            if (chars > 500 && duration > 60_000) {
                val cpm = chars * 60_000f / duration
                val old = settings.readingSpeedCpm.get()
                settings.readingSpeedCpm.set(if (old <= 0f) cpm else old * 0.8f + cpm * 0.2f)
            }
        }
    }

    suspend fun readingSpeed(): Float = settings.readingSpeedCpm.get().takeIf { it > 0f } ?: 1000f

    // ================================================================== navigation
    fun goToToc(item: TocItem) {
        rememberJump()
        val anchor = item.anchor
        when (val c = current()) {
            is ReaderContent.Text -> if (isOnline) {
                openChapterAt(item.chapter)
            } else if (anchor != null) {
                c.controller.goTo(item.chapter, EpubTarget.Anchor(anchor))
            } else {
                // A chapter without an anchor opens where it was left last time.
                viewModelScope.launch(ScreenErrors) {
                    c.controller.goTo(item.chapter, savedTextTarget(item.chapter) ?: EpubTarget.Progression(0f))
                }
            }
            is ReaderContent.Pdf -> c.controller.goTo(item.chapter)
            is ReaderContent.Manga -> if (isOnline) openChapterAt(item.chapter) else c.controller.goTo(item.chapter)
            null -> Unit
        }
    }

    fun goToLocator(l: Locator) {
        rememberJump()
        navigate(l)
    }

    private fun navigate(l: Locator) {
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.goTo(l.chapter, l.offset?.let { EpubTarget.Offset(it) } ?: EpubTarget.Progression(l.progression))
            is ReaderContent.Pdf -> c.controller.goTo(l.chapter)
            is ReaderContent.Manga -> {
                val chapterId = l.onlineChapterId
                if (chapterId == null) {
                    c.controller.goTo(c.controller.globalOfCurrent(l.chapter))
                } else if (!moveToLoadedChapter(chapterId, l.chapter)) {
                    openChapter(chapterId, finished = false, mangaPage = l.chapter)
                }
            }
            null -> Unit
        }
    }

    // ================================================================== page numbers & jumps
    private fun updateChapterStarts(doc: app.yomu.feature.reader.epub.TextDocument) {
        if (chapterStartsFor === doc) return
        chapterStartsFor = doc
        _chapterStarts.value = if (doc.chapterCount <= 1) {
            emptyList()
        } else {
            (1 until doc.chapterCount).map { doc.totalProgression(it, 0f) }.filter { it in 0.002f..0.998f }.distinct()
        }
    }

    /** Book weight units per screen page, measured on chapters long enough to be representative. */
    private var weightPerPage = 0f
    private var hereLocator: Locator? = null
    /** Positions the reader jumped away from, newest last: "back" walks the whole chain, not just one step. */
    private val jumpStack = ArrayDeque<JumpBack>()
    private val _jumpBack = MutableStateFlow<JumpBack?>(null)
    val jumpBack: StateFlow<JumpBack?> = _jumpBack

    /** Every remembered place, newest first, for the "recent jumps" list in "More". */
    private val _jumpHistory = MutableStateFlow<List<JumpBack>>(emptyList())
    val jumpHistory: StateFlow<List<JumpBack>> = _jumpHistory

    private fun syncJumpState() {
        _jumpBack.value = jumpStack.lastOrNull()
        _jumpHistory.value = jumpStack.toList().asReversed()
    }

    /** Page of the whole book and page count, from the exact pages of this chapter and the sizes of the others. */
    private fun estimatePages(doc: app.yomu.feature.reader.epub.TextDocument, chapter: Int, progress: app.yomu.feature.reader.epub.EpubProgress): Pair<Int, Int> {
        val pages = progress.pages.coerceAtLeast(1)
        val w = doc.weight(chapter).coerceAtLeast(1).toFloat()
        if (pages >= 3 || weightPerPage <= 0f) weightPerPage = w / pages
        val per = weightPerPage.coerceAtLeast(1f)
        fun pagesOf(i: Int) = if (i == chapter) pages else kotlin.math.ceil(doc.weight(i).coerceAtLeast(1) / per).toInt().coerceAtLeast(1)
        val before = (0 until chapter).sumOf { pagesOf(it) }
        val all = before + (chapter until doc.chapterCount).sumOf { pagesOf(it) }
        return (before + progress.page + 1).coerceAtMost(all) to all
    }

    /** What the slider at [value] (0..1 of the book) points to; [cpm] is the user's reading speed. */
    fun seekPreview(value: Float, cpm: Float = 1000f): SeekPreview {
        val p = _position.value
        // Reading time to the target, from the time left in the book and how far the target is from the end. It is an
        // estimate like the page count itself, so the card says "min left" without a decimal precision it cannot have.
        val minutesLeft = if (p.charsLeftInBook > 0 && p.total < 0.999f) {
            (((1f - value.coerceIn(0f, 1f)) / (1f - p.total).coerceAtLeast(0.001f)) * p.charsLeftInBook / cpm.coerceAtLeast(1f))
                .toInt().takeIf { it in 1..6000 }
        } else {
            null
        }
        return when (val c = current()) {
            is ReaderContent.Text -> {
                val doc = c.controller.document
                val pages = p.bookPages.coerceAtLeast(1)
                val page = pageAtSeekFraction(value, pages) + 1
                val title = if (c.controller.wholeBook) {
                    val starts = _chapterStartPages.value
                    val ch = if (starts.isEmpty()) p.chapter else starts.indexOfLast { it <= page - 1 }.coerceAtLeast(0)
                    doc.chapterTitle(ch) ?: p.chapterTitle
                } else {
                    doc.chapterTitle(doc.locate(value.coerceIn(0f, 1f)).first)
                }
                SeekPreview(page, pages, p.approximate, title, minutesLeft.takeUnless { p.viewportSeek })
            }
            is ReaderContent.Pdf -> {
                val pages = c.controller.pageCount
                val page = pageAtSeekFraction(value, pages)
                val spread = c.controller.visiblePages(page).takeIf { it.first < it.last }
                    ?.let { "${it.first + 1}–${it.last + 1}" }
                SeekPreview(page + 1, pages, false, _toc.value.lastOrNull { it.chapter <= page }?.title, spread = spread)
            }
            else -> {
                val pages = p.pages.coerceAtLeast(1)
                val page = pageAtSeekFraction(value, pages)
                SeekPreview(page + 1, pages, false, p.chapterTitle)
            }
        }
    }

    /** Jumps to the 1-based [page] of the whole book. */
    fun goToPage(page: Int) {
        val p = _position.value
        when (val c = current()) {
            is ReaderContent.Text -> {
                if ((c.controller.pagedBook || p.viewportSeek) && p.bookPages > 0) {
                    rememberJump()
                    c.controller.goToPage((page - 1).coerceIn(0, p.bookPages - 1))
                } else {
                    val pages = p.bookPages.coerceAtLeast(1)
                    seekTotal(((page - 1).toFloat() / (pages - 1).coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            }
            is ReaderContent.Pdf -> { rememberJump(); c.controller.goTo((page - 1).coerceIn(0, c.controller.pageCount - 1)) }
            is ReaderContent.Manga -> {
                rememberJump()
                c.controller.goTo(c.controller.globalOfCurrent((page - 1).coerceIn(0, (c.controller.localCount - 1).coerceAtLeast(0))))
            }
            null -> Unit
        }
    }

    private fun rememberJump() {
        // Before the engine reports anything there is no place to return to: "Back to page 1" from a fresh book is noise.
        val l = hereLocator ?: return
        val page = _position.value.bookPage
        if (page <= 0) return
        val jump = JumpBack(l, page)
        if (jumpStack.lastOrNull() == jump) return
        jumpStack.addLast(jump)
        while (jumpStack.size > JUMP_STACK_MAX) jumpStack.removeFirst()
        syncJumpState()
    }

    /** Returns to where the reader was before the last jump; another tap goes one jump further back. */
    fun jumpBack() {
        val jump = jumpStack.removeLastOrNull() ?: return
        syncJumpState()
        navigate(jump.locator)
    }

    /** Opens one place from the history; the current place stays available through the same history. */
    fun openJump(jump: JumpBack) {
        rememberJump()
        jumpStack.remove(jump)
        syncJumpState()
        navigate(jump.locator)
    }

    fun dismissJumpBack() {
        jumpStack.clear()
        syncJumpState()
    }

    /** Slider: 0..1 over the whole publication. */
    fun seekTotal(value: Float) {
        // A nudge of a percent is not a jump worth a "back" chip; a real move still is.
        if (kotlin.math.abs(value - _position.value.seekFraction) > 0.03f) rememberJump()
        when (val c = current()) {
            is ReaderContent.Text -> {
                val pages = _position.value.bookPages
                when {
                    // A whole book laid out as pages: the thumb is a page, the jump is a page, both exact.
                    c.controller.pagedBook && pages > 0 ->
                        c.controller.goToPage(pageAtSeekFraction(value, pages))
                    // A whole book scrolled as one document: the thumb is the text position.
                    c.controller.wholeBook -> c.controller.goToProgression(value.coerceIn(0f, 1f))
                    else -> {
                        val (ch, p) = c.controller.document.locate(value)
                        c.controller.goTo(ch, EpubTarget.Progression(p))
                    }
                }
            }
            is ReaderContent.Pdf -> c.controller.goTo(pageAtSeekFraction(value, c.controller.pageCount))
            is ReaderContent.Manga -> c.controller.goTo(
                c.controller.globalOfCurrent(pageAtSeekFraction(value, c.controller.localCount)),
            )
            null -> Unit
        }
    }

    fun nextPage() {
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.next()
            is ReaderContent.Pdf -> c.controller.turnPage(forward = true)
            is ReaderContent.Manga -> c.controller.next()
            null -> Unit
        }
    }

    fun prevPage() {
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.prev()
            is ReaderContent.Pdf -> c.controller.turnPage(forward = false)
            is ReaderContent.Manga -> c.controller.prev()
            null -> Unit
        }
    }

    fun nextChapter() {
        when (val c = current()) {
            is ReaderContent.Text -> if (isOnline) openAdjacentChapter(true) else c.controller.nextChapter()
            is ReaderContent.Manga -> if (isOnline) openAdjacentChapter(true) else c.controller.next()
            // For a PDF "chapter" is the page: the bottom bar's chapter buttons must still do something.
            is ReaderContent.Pdf -> c.controller.turnPage(forward = true)
            null -> Unit
        }
    }

    fun prevChapter() {
        when (val c = current()) {
            is ReaderContent.Text -> {
                if (isOnline) {
                    openAdjacentChapter(false)
                } else {
                    val target = c.controller.chapter - 1
                    if (target >= 0) viewModelScope.launch(ScreenErrors) {
                        c.controller.goTo(target, savedTextTarget(target) ?: EpubTarget.Progression(1f))
                    }
                }
            }
            is ReaderContent.Manga -> if (isOnline) openAdjacentChapter(false) else c.controller.prev()
            is ReaderContent.Pdf -> c.controller.turnPage(forward = false)
            null -> Unit
        }
    }

    /** Position saved inside [chapter] of this text book, if it was read before. */
    private suspend fun savedTextTarget(chapter: Int): EpubTarget? {
        if (args.bookId <= 0) return null
        val locator = books.getChapterProgress(args.bookId, chapter) ?: return null
        val offset = locator.offset
        return when {
            offset != null -> EpubTarget.Offset(offset)
            locator.progression > 0.001f -> EpubTarget.Progression(locator.progression)
            else -> null
        }
    }

    // ================================================================== annotations
    private fun pushHighlights() {
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.setHighlights(highlights.value)
            is ReaderContent.Pdf -> c.controller.highlights = highlights.value
            else -> Unit
        }
    }

    private fun selectionLocator(s: ReaderSelection): Locator {
        val p = _position.value
        val title = when (val c = current()) {
            is ReaderContent.Text -> c.controller.document.chapterTitle(s.chapter)
            else -> null
        }
        return Locator(chapter = s.chapter, progression = p.progression, totalProgression = p.total, offset = s.start, endOffset = s.end, text = s.text.take(200), chapterTitle = title)
    }

    fun highlight(color: HighlightColor, style: HighlightStyle, quote: Boolean = false, note: String? = null) {
        val s = selection.value ?: return
        if (args.bookId <= 0) {
            clearSelection()
            events.trySend(ReaderEvent.Message(R.string.reader_online_no_annotations))
            return
        }
        viewModelScope.launch(ScreenErrors) {
            val existing = s.highlightId?.let { id -> highlights.value.firstOrNull { it.id == id } }
            if (existing != null) {
                annotations.saveHighlight(existing.copy(color = color, style = style, isQuote = existing.isQuote || quote, note = note ?: existing.note, tags = TextTools.hashtags(note ?: existing.note).ifEmpty { existing.tags }))
            } else {
                annotations.saveHighlight(Highlight(bookId = args.bookId, locator = selectionLocator(s), text = s.text, color = color, style = style, isQuote = quote, note = note, tags = TextTools.hashtags(note)))
            }
            clearSelection()
        }
    }

    private val drafts by lazy { app.yomu.feature.annotations.NoteDrafts(context) }

    /** A draft is tied to the book and the selection (or page): reopening the dialog finds the typed text again. */
    fun noteDraftKey(sel: ReaderSelection): String = "${args.bookId}:s:${sel.chapter}:${sel.start}"

    fun noteDraft(key: String): String? = drafts.get(key)

    fun saveNoteDraft(sel: ReaderSelection, text: String) {
        drafts.put(noteDraftKey(sel), args.bookId, sel.chapter, sel.start, text)
    }

    /** Removing the annotation also removes its unsaved text: a deleted note must not come back from a draft. */
    fun discardNoteDraft(key: String) = drafts.remove(key)

    /** Writes the note and returns whether the row was really saved; only then is the draft removed. */
    suspend fun saveNote(s: ReaderSelection, text: String): Boolean {
        if (args.bookId <= 0) return false
        val key = noteDraftKey(s)
        val ok = runCatching {
            val existing = s.highlightId?.let { id -> highlights.value.firstOrNull { it.id == id } }
            if (existing != null) {
                annotations.saveHighlight(existing.copy(note = text.ifBlank { null }, tags = TextTools.hashtags(text)))
            } else {
                annotations.saveHighlight(
                    Highlight(
                        bookId = args.bookId,
                        locator = selectionLocator(s),
                        text = s.text,
                        color = HighlightColor.YELLOW,
                        style = HighlightStyle.HIGHLIGHT,
                        note = text.ifBlank { null },
                        tags = TextTools.hashtags(text),
                    ),
                )
            }
            drafts.remove(key)
        }.isSuccess
        if (ok) clearSelection()
        return ok
    }

    fun deleteHighlight(id: Long? = selection.value?.highlightId) {
        if (id == null) return
        val h = highlights.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch(ScreenErrors) {
            annotations.deleteHighlight(id)
            clearSelection()
            events.send(ReaderEvent.HighlightDeleted(h))
        }
    }

    fun restoreHighlight(h: Highlight) = viewModelScope.launch(ScreenErrors) { annotations.restore(highlight = h) }

    fun noteOfSelection(sel: ReaderSelection): String? = sel.highlightId?.let { id -> highlights.value.firstOrNull { it.id == id }?.note }

    fun clearSelection() {
        selection.value = null
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.clearSelection()
            is ReaderContent.Pdf -> c.controller.selection = null
            else -> Unit
        }
    }

    fun expandSelection(paragraph: Boolean) {
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.expand(paragraph)
            is ReaderContent.Pdf -> viewModelScope.launch(ScreenErrors) {
                val original = selection.value ?: return@launch
                val controller = c.controller
                if (controller.selection?.page != original.chapter && controller.select(original.chapter, original.start, original.end) == null) return@launch
                controller.expand(paragraph)
                val expanded = controller.selection ?: return@launch
                if (selection.value === original && current() == c) {
                    selection.value = original.copy(text = expanded.text, start = expanded.start, end = expanded.end, highlightId = null)
                }
            }
            else -> Unit
        }
    }

    /** Current bookmark at this position, if any. */
    fun bookmarkHere(): Bookmark? {
        val p = _position.value
        return bookmarkAt(p.chapter, p.progression)
    }

    private fun bookmarkAt(chapter: Int, progression: Float): Bookmark? {
        return bookmarks.value.firstOrNull { b ->
            b.locator.chapter == chapter && when (current()) {
                is ReaderContent.Text -> kotlin.math.abs(b.locator.progression - progression) < 0.5f / _position.value.pages.coerceAtLeast(1)
                else -> true
            }
        }
    }

    fun toggleBookmark() {
        val target = pageNoteTarget()
        toggleBookmark(target.locator, target.page)
    }

    private fun toggleBookmark(locator: Locator, page: Int) {
        if (args.bookId <= 0) {
            events.trySend(ReaderEvent.Message(R.string.reader_online_no_annotations))
            return
        }
        val existing = bookmarkAt(locator.chapter, locator.progression)
        viewModelScope.launch(ScreenErrors) {
            if (existing != null) {
                annotations.deleteBookmark(existing.id)
                events.send(ReaderEvent.BookmarkRemoved(existing))
            } else {
                val text = (current() as? ReaderContent.Text)?.controller?.let { c -> runCatching { c.visibleText() }.getOrNull()?.take(120) }
                val title = text?.trim()?.ifBlank { null } ?: locator.chapterTitle ?: context.getString(R.string.reader_page_n, page + 1)
                val b = Bookmark(bookId = args.bookId, locator = locator, title = title)
                val id = annotations.saveBookmark(b)
                events.send(ReaderEvent.BookmarkAdded(b.copy(id = id)))
            }
        }
    }

    internal fun pageNoteTarget(): PageNoteTarget {
        val p = _position.value
        val offset = (current() as? ReaderContent.Text)?.controller?.progress?.offset
        return PageNoteTarget(Locator(p.chapter, p.progression, p.total, offset = offset, chapterTitle = p.chapterTitle), p.page)
    }

    private fun pageNoteDraftKey(target: PageNoteTarget): String = "${args.bookId}:p:${target.locator.chapter}:${target.page}"

    internal fun pageNoteDraft(target: PageNoteTarget): String? = drafts.get(pageNoteDraftKey(target))

    internal fun savePageNoteDraft(target: PageNoteTarget, text: String) =
        drafts.put(pageNoteDraftKey(target), args.bookId, target.locator.chapter, 0, text)

    /** Free note attached to the current position (useful for PDF and comics, where there is no text selection). */
    internal suspend fun addPageNote(target: PageNoteTarget, text: String): Boolean {
        if (args.bookId <= 0 || text.isBlank()) return false
        val key = pageNoteDraftKey(target)
        val ok = runCatching {
            annotations.saveNote(Note(bookId = args.bookId, locator = target.locator, text = text.trim(), tags = TextTools.hashtags(text)))
            drafts.remove(key)
        }.isSuccess
        if (ok) events.send(ReaderEvent.Message(R.string.reader_note_saved))
        return ok
    }

    fun undoBookmark(e: ReaderEvent) = viewModelScope.launch(ScreenErrors) {
        when (e) {
            is ReaderEvent.BookmarkAdded -> annotations.deleteBookmark(e.bookmark.id)
            is ReaderEvent.BookmarkRemoved -> annotations.restore(bookmark = e.bookmark)
            else -> Unit
        }
    }

    fun deleteBookmark(b: Bookmark) = viewModelScope.launch(ScreenErrors) {
        annotations.deleteBookmark(b.id)
        events.send(ReaderEvent.BookmarkRemoved(b))
    }

    /** Saves the selected word or phrase to "My words". */
    fun saveSelectionAsWord() {
        val s = selection.value ?: return
        viewModelScope.launch(ScreenErrors) {
            val tr = runCatching { translation.translate(s.text, null, null).translated }.getOrNull()
            words.save(
                app.yomu.domain.model.Word(
                    term = s.text.trim().take(120),
                    translation = tr,
                    context = s.sentence.ifBlank { null },
                    bookId = args.bookId.takeIf { it > 0 },
                    bookTitle = book?.title,
                    locator = selectionLocator(s),
                    lang = book?.language,
                ),
            )
            clearSelection()
            events.send(ReaderEvent.WordSaved(s.text.trim()))
        }
    }

    fun saveWord(term: String, translated: String, lang: String?) = viewModelScope.launch(ScreenErrors) {
        words.save(
            app.yomu.domain.model.Word(
                term = term.trim().take(120),
                translation = translated,
                lang = lang ?: book?.language,
                bookId = args.bookId.takeIf { it > 0 },
                bookTitle = book?.title,
                context = wordTap.value?.sentence ?: selection.value?.sentence,
            ),
        )
        events.send(ReaderEvent.WordSaved(term.trim()))
    }

    // ================================================================== settings
    // A short in-memory journal of changed settings: "Undo the last change" after a wrong tap on a slider. One stack for
    // all four groups, so brightness, typography and the page mode are taken back in the order they were changed.
    private enum class ReaderEngine { TEXT, MANGA, PDF }

    private fun engineOf(content: ReaderContent?): ReaderEngine? = when (content) {
        is ReaderContent.Text -> ReaderEngine.TEXT
        is ReaderContent.Pdf -> ReaderEngine.PDF
        is ReaderContent.Manga -> ReaderEngine.MANGA
        null -> null
    }

    private sealed interface SettingsChange {
        data class Text(val value: TextReaderSettings, val perBook: Boolean) : SettingsChange
        data class Manga(val value: MangaSettings, val perBook: Boolean) : SettingsChange
        data class Pdf(val value: PdfSettings, val perBook: Boolean) : SettingsChange
        data class Common(val value: CommonReaderSettings) : SettingsChange
        data class Scope(val value: BookReaderSettings) : SettingsChange

        /**
         * The E-ink preset changes the text and the manga group at once in the global scope, and only the engine of
         * the open book in the per-book scope. One Undo takes back exactly the groups it wrote.
         */
        data class Eink(
            val text: TextReaderSettings,
            val manga: MangaSettings,
            val perBook: Boolean,
            val engine: ReaderEngine?,
        ) : SettingsChange
    }

    private val undo = ArrayDeque<SettingsChange>()
    private val _canUndo = MutableStateFlow(false)

    /** Whether [undoLastChange] would do something. */
    val canUndo: StateFlow<Boolean> = _canUndo

    private fun push(change: SettingsChange) {
        undo.addLast(change)
        while (undo.size > UNDO_LIMIT) undo.removeFirst()
        _canUndo.value = true
    }

    fun updateText(transform: (TextReaderSettings) -> TextReaderSettings) = viewModelScope.launch(ScreenErrors) {
        val before = textSettings.value
        val after = transform(before)
        if (after == before) return@launch
        push(SettingsChange.Text(before, perBook.value))
        writeText(after, perBook.value)
    }

    private suspend fun writeText(value: TextReaderSettings, local: Boolean = perBook.value) {
        if (local) saveOverride(bookOverride.value.copy(text = value)) else settings.textSettings.set(value)
    }

    private suspend fun writeManga(value: MangaSettings, local: Boolean = perBook.value) {
        if (local) saveOverride(bookOverride.value.copy(manga = value)) else settings.mangaSettings.set(value)
    }

    private suspend fun writePdf(value: PdfSettings, local: Boolean = perBook.value) {
        if (local) saveOverride(bookOverride.value.copy(pdf = value)) else settings.pdfSettings.set(value)
    }

    fun updateManga(transform: (MangaSettings) -> MangaSettings) = viewModelScope.launch(ScreenErrors) {
        val before = mangaSettings.value
        val after = transform(before)
        if (after == before) return@launch
        push(SettingsChange.Manga(before, perBook.value))
        writeManga(after)
        // Changed where taps go: show the new zones right away.
        if (after.tapZones != TapZoneLayout.DISABLED &&
            (after.tapZones != before.tapZones || after.invertTapZones != before.invertTapZones || after.direction != before.direction)
        ) {
            previewTapZones()
        }
    }

    fun updatePdf(transform: (PdfSettings) -> PdfSettings) = viewModelScope.launch(ScreenErrors) {
        val before = pdfSettings.value
        val after = transform(before)
        if (after == before) return@launch
        push(SettingsChange.Pdf(before, perBook.value))
        writePdf(after)
    }

    /**
     * "Fit the page": drops the pinch zoom and pan of the current PDF or comic page. The page itself, its reading
     * position and every format setting stay as they are; a locked page starts again at the fit scale.
     */
    fun fitPage() {
        when (val c = current()) {
            is ReaderContent.Pdf -> c.controller.resetZoom()
            is ReaderContent.Manga -> c.controller.resetZoom()
            else -> Unit
        }
    }

    /**
     * "Apply as the default": writes the settings this book uses into the global reading settings. The book keeps its
     * own copy, so a later global change still does not silently rewrite this book.
     */
    fun applyBookSettingsAsGlobal() = viewModelScope.launch(ScreenErrors) {
        settings.textSettings.set(textSettings.value)
        settings.mangaSettings.set(mangaSettings.value)
        settings.pdfSettings.set(pdfSettings.value)
    }

    /** Takes back the last change made in the reading settings ("Undo the last change"). */
    fun undoLastChange() = viewModelScope.launch(ScreenErrors) {
        when (val change = undo.removeLastOrNull()) {
            is SettingsChange.Text -> writeText(change.value, change.perBook)
            is SettingsChange.Manga -> writeManga(change.value, change.perBook)
            is SettingsChange.Pdf -> writePdf(change.value, change.perBook)
            is SettingsChange.Common -> settings.commonReader.set(change.value)
            is SettingsChange.Scope -> saveOverride(change.value)
            is SettingsChange.Eink -> {
                // The preset never touched the common group; writing it here would revert changes made after it.
                val targets = app.yomu.domain.reader.EinkPreset.targets(
                    change.perBook,
                    textBook = change.engine == ReaderEngine.TEXT,
                    mangaBook = change.engine == ReaderEngine.MANGA,
                )
                if (targets.text) writeText(change.text, change.perBook)
                if (targets.manga) writeManga(change.manga, change.perBook)
            }
            null -> Unit
        }
        _canUndo.value = undo.isNotEmpty()
    }

    /**
     * N03: the E-ink/accessible preset as one ordinary settings change (not a second engine). Everything it touches
     * is undone with a single "Undo the last change".
     */
    fun applyEinkPreset() = viewModelScope.launch(ScreenErrors) {
        val beforeText = textSettings.value
        val beforeManga = mangaSettings.value
        val engine = engineOf(current())
        val local = perBook.value
        // Per book, only the engine the open book actually uses is changed: a text book must not leave a manga
        // override (and the other way round) that another format of the same title would inherit later.
        val targets = app.yomu.domain.reader.EinkPreset.targets(
            local,
            textBook = engine == ReaderEngine.TEXT,
            mangaBook = engine == ReaderEngine.MANGA,
        )
        val afterText = if (targets.text) app.yomu.domain.reader.EinkPreset.text(beforeText) else beforeText
        val afterManga = if (targets.manga) app.yomu.domain.reader.EinkPreset.manga(beforeManga) else beforeManga
        if (afterText == beforeText && afterManga == beforeManga) return@launch
        push(SettingsChange.Eink(beforeText, beforeManga, local, engine))
        if (targets.text) writeText(afterText, local)
        if (targets.manga) writeManga(afterManga, local)
    }

    /** Removes an imported font; the current font and saved presets that use it fall back to the serif face. */
    fun deleteFont(name: String) = viewModelScope.launch(ScreenErrors) {
        val dir = File(context.filesDir, "fonts")
        File(dir, name).delete()
        if (textSettings.value.fontFamily == name) writeText(textSettings.value.copy(fontFamily = "serif"))
        val presets = presets.value.map { p ->
            if (p.settings.fontFamily == name) p.copy(settings = p.settings.copy(fontFamily = "serif")) else p
        }
        settings.readerPresetsJson.set(SettingsJson.encodeToString(ListSerializer(TextPreset.serializer()), presets))
    }

    val wordTapSetting = settings.wordTap.stateIn(viewModelScope)

    // ---------------------------------------------------------------- presets
    /** Text presets saved by the user (the built-in ones live in [TextPresets]). */
    val presets: StateFlow<List<TextPreset>> = settings.readerPresetsJson.flow
        .map { raw -> runCatching { SettingsJson.decodeFromString(ListSerializer(TextPreset.serializer()), raw) }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun applyPreset(preset: TextPreset) = updateText { TextPresets.apply(it, preset.settings, withColors = true) }

    fun savePreset(name: String) = viewModelScope.launch(ScreenErrors) {
        val n = name.trim().take(40).ifEmpty { return@launch }
        val list = presets.value.filterNot { it.name.equals(n, ignoreCase = true) } + TextPreset(n, textSettings.value)
        settings.readerPresetsJson.set(SettingsJson.encodeToString(ListSerializer(TextPreset.serializer()), list.takeLast(MAX_PRESETS)))
    }

    fun deletePreset(name: String) = viewModelScope.launch(ScreenErrors) {
        val list = presets.value.filterNot { it.name == name }
        settings.readerPresetsJson.set(SettingsJson.encodeToString(ListSerializer(TextPreset.serializer()), list))
    }

    /** Typography back to the defaults; theme, page mode and colors stay. */
    fun resetTextLayout() = updateText { TextPresets.apply(it, TextReaderSettings()) }

    fun setWordTap(g: app.yomu.domain.model.WordTapGesture) = viewModelScope.launch(ScreenErrors) { settings.wordTap.set(g) }

    fun setSelectionMenuStyle(style: QuickMenuStyle) = viewModelScope.launch(ScreenErrors) {
        settings.selectionMenuStyle.set(style)
    }

    fun setPageMenuStyle(style: QuickMenuStyle) = viewModelScope.launch(ScreenErrors) {
        settings.pageMenuStyle.set(style)
    }

    fun updateCommon(transform: (CommonReaderSettings) -> CommonReaderSettings) = viewModelScope.launch(ScreenErrors) {
        val before = common.value
        val after = transform(before)
        if (after == before) return@launch
        push(SettingsChange.Common(before))
        settings.commonReader.set(after)
        if (after.animationSpeed != before.animationSpeed) {
            (current() as? ReaderContent.Text)?.controller?.updateAnimationSpeed(after.animationSpeed)
        }
    }

    /** Switches between global settings and settings stored only for this book. */
    fun setPerBook(enabled: Boolean) = viewModelScope.launch(ScreenErrors) {
        val o = bookOverride.value
        val next = when (current()) {
            is ReaderContent.Text -> o.copy(text = if (enabled) textSettings.value else null)
            is ReaderContent.Pdf -> o.copy(pdf = if (enabled) pdfSettings.value else null)
            is ReaderContent.Manga -> o.copy(manga = if (enabled) mangaSettings.value else null)
            null -> o
        }
        if (next != o) {
            saveOverride(next)
            push(SettingsChange.Scope(o))
        }
    }

    private suspend fun saveOverride(o: BookReaderSettings) {
        bookOverride.value = o
        val json = if (o.text == null && o.manga == null && o.pdf == null) null else SettingsJson.encodeToString(BookReaderSettings.serializer(), o)
        if (isOnline) {
            catalog.chapter(_chapterId.value)?.let { catalog.setReaderSettings(it.entryId, json) }
        } else if (args.bookId > 0) {
            books.saveReaderSettings(args.bookId, json)
        }
    }

    // ================================================================== search
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            searchState.value = SearchState(query)
            return
        }
        searchState.value = SearchState(query, loading = true)
        searchJob = pageActionExecutor.execute {
            when (val c = current()) {
                is ReaderContent.Text -> {
                    val doc = c.controller.document
                    val hits = mutableListOf<SearchHit>()
                    withContext(Dispatchers.Default) {
                        for (i in 0 until doc.chapterCount) {
                            if (!isActive || hits.size >= 500) break
                            val text = ChapterText.of(doc.chapterHtml(i))
                            var from = 0
                            var occ = 0
                            while (hits.size < 500) {
                                val at = text.indexOf(q, from, ignoreCase = true)
                                if (at < 0) break
                                val s = (at - 40).coerceAtLeast(0)
                                val e = (at + q.length + 60).coerceAtMost(text.length)
                                hits += SearchHit(i, occ++, text.substring(s, e).replace('\n', ' '), at - s, q.length, doc.chapterTitle(i))
                                from = at + q.length
                            }
                            searchState.update { it.copy(hits = hits.toList()) }
                        }
                    }
                    searchState.value = SearchState(query, hits = hits, done = true)
                }
                is ReaderContent.Pdf -> {
                    val res = runCatching { c.controller.pdf.search(q) }.getOrDefault(emptyList())
                    c.controller.hits = res
                    searchState.value = SearchState(query, hits = res.mapIndexed { i, h -> SearchHit(h.page, i, h.snippet, 0, h.length, null) }, done = true)
                }
                else -> searchState.value = SearchState(query, done = true)
            }
        }
    }

    /**
     * Positions the reader at a hit and marks it. Only the first hit of a search remembers the place to return to:
     * walking "next match" across the results must leave one "back" target, not a stack of every match visited.
     */
    fun openHit(hit: SearchHit) {
        val state = searchState.value
        if (state.active == null) rememberJump()
        val index = state.hits.indexOf(hit)
        if (index >= 0) searchState.value = state.copy(active = index)
        navigateToHit(hit)
    }

    /** The next or previous result of the current search, wrapping at the ends. */
    fun stepHit(delta: Int) {
        val state = searchState.value
        val next = nextHitIndex(state.active, state.hits.size, delta) ?: return
        openHit(state.hits[next])
    }

    private fun navigateToHit(hit: SearchHit) {
        when (val c = current()) {
            is ReaderContent.Text -> pageActionExecutor.execute {
                val doc = c.controller.document
                val text = ChapterText.of(doc.chapterHtml(hit.chapter))
                var from = 0
                var at = -1
                repeat(hit.occurrence + 1) {
                    at = text.indexOf(searchState.value.query.trim(), from, ignoreCase = true)
                    if (at < 0) return@repeat
                    from = at + hit.matchLength
                }
                if (at < 0) return@execute
                c.controller.goTo(hit.chapter, EpubTarget.Offset(at))
                delay(if (c.controller.chapter == hit.chapter) 50 else 600)
                c.controller.mark("search", at, at + hit.matchLength)
            }
            is ReaderContent.Pdf -> {
                val h = c.controller.hits.getOrNull(hit.occurrence) ?: return
                c.controller.currentHit = h
                c.controller.goTo(h.page)
            }
            else -> Unit
        }
    }

    /** A short piece of the page the reader is looking at, for the "Sample" card in the reading settings. */
    suspend fun visibleSnippet(maxChars: Int = 320): String? = when (val c = current()) {
        is ReaderContent.Text -> c.controller.visibleText()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxChars)
            .takeIf { it.length >= 40 }
        else -> null
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchState.value = SearchState()
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.mark("search", null, null)
            is ReaderContent.Pdf -> { c.controller.hits = emptyList(); c.controller.currentHit = null }
            else -> Unit
        }
    }

    // ================================================================== TTS
    fun startTts() {
        when (val c = current()) {
            is ReaderContent.Text -> {
                _ttsActive.value = true
                tts.language = c.controller.document.language ?: book?.language
                viewModelScope.launch(ScreenErrors) { speakTextFrom(c.controller, c.controller.progress.offset) }
            }
            is ReaderContent.Pdf -> {
                _ttsActive.value = true
                tts.language = book?.language
                speakPdfPage(c.controller.page)
            }
            else -> events.trySend(ReaderEvent.Message(R.string.reader_tts_unavailable))
        }
        if (_ttsActive.value) {
            publishTts(true)
            ReaderTtsService.start(context)
        }
    }

    /** Title and chapter for the notification and the lock screen. */
    private fun publishTts(active: Boolean) {
        TtsBus.update {
            it.copy(
                active = active,
                playing = if (active) it.playing else false,
                title = _title.value,
                subtitle = _position.value.chapterTitle ?: chapterOf,
            )
        }
    }

    private suspend fun speakTextFrom(c: EpubController, offset: Int) {
        val list = runCatching { c.sentences(offset) }.getOrDefault(emptyList())
        ttsChapter = c.chapter
        ttsSentences = list.map { Triple(c.chapter, it.start, it.end) }
        if (list.isEmpty()) {
            onTtsQueueFinished()
            return
        }
        tts.play(list.map { it.text })
    }

    private fun speakPdfPage(page: Int) {
        val c = (current() as? ReaderContent.Pdf)?.controller ?: return
        viewModelScope.launch(ScreenErrors) {
            val text = runCatching { c.pdf.text(page) }.getOrDefault("")
            val ranges = sentenceRanges(text)
            ttsChapter = page
            ttsSentences = ranges.map { Triple(page, it.first, it.last + 1) }
            if (ranges.isEmpty()) {
                onTtsQueueFinished()
                return@launch
            }
            tts.play(ranges.map { text.substring(it.first, it.last + 1) })
        }
    }

    private fun onTtsSentence(i: Int) {
        _position.value.chapterTitle?.let { title -> TtsBus.update { it.copy(subtitle = title) } }
        val (ch, s, e) = ttsSentences.getOrNull(i) ?: return
        when (val c = current()) {
            is ReaderContent.Text -> if (c.controller.chapter == ch) c.controller.mark("tts", s, e, scroll = true)
            is ReaderContent.Pdf -> c.controller.ttsMark = Triple(ch, s, e)
            else -> Unit
        }
    }

    private fun onTtsQueueFinished() {
        if (!_ttsActive.value) return
        if (sleepTimer.value == SleepTimer.END_OF_CHAPTER) {
            stopTts()
            return
        }
        when (val c = current()) {
            is ReaderContent.Text -> {
                if (c.controller.chapter < c.controller.document.chapterCount - 1) {
                    ttsChapter = -1
                    c.controller.nextChapter()
                    // A long strip scrolls into the next chapter instead of loading it, so no onReady arrives: read on
                    // from its first sentence once the runtime has reported the new position.
                    if (textSettings.value.longStrip) {
                        viewModelScope.launch(ScreenErrors) {
                            delay(500)
                            if (_ttsActive.value) speakTextFrom(c.controller, 0)
                        }
                    }
                } else {
                    stopTts()
                    finishBook()
                }
            }
            is ReaderContent.Pdf -> {
                if (c.controller.page < c.controller.pageCount - 1) {
                    ttsChapter = -1
                    c.controller.goTo(c.controller.page + 1)
                } else {
                    stopTts()
                }
            }
            else -> stopTts()
        }
    }

    fun pauseTts() = tts.pause()
    fun resumeTts() = tts.resume()

    fun stopTts() {
        _ttsActive.value = false
        publishTts(false)
        ReaderTtsService.stop(context)
        tts.stop()
        sleepJob?.cancel()
        sleepTimer.value = SleepTimer.OFF
        sleepRemainingMs.value = null
        when (val c = current()) {
            is ReaderContent.Text -> c.controller.mark("tts", null, null)
            is ReaderContent.Pdf -> c.controller.ttsMark = null
            else -> Unit
        }
    }

    fun setSleepTimer(t: SleepTimer) {
        sleepJob?.cancel()
        sleepTimer.value = t
        sleepRemainingMs.value = null
        if (t.minutes <= 0) return
        sleepJob = viewModelScope.launch(ScreenErrors) {
            val end = System.currentTimeMillis() + t.minutes * 60_000L
            while (isActive) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                sleepRemainingMs.value = left
                delay(1000)
            }
            stopTts()
            stopAutoScroll()
        }
    }

    /** Voices of the current language; starts the engine, so the picker works before the first playback. */
    suspend fun ttsVoices() = tts.prepareVoices()

    /** N02: a short sample with the current voice, speed and pitch; it never advances the reading queue. */
    fun previewTts() {
        tts.speakSample(context.getString(R.string.reader_tts_sample))
    }

    // ================================================================== auto scroll
    fun toggleAutoScroll() = if (autoScroll.value) stopAutoScroll() else startAutoScroll()

    private fun startAutoScroll() {
        autoScroll.value = true
        chromeVisible.value = false
        autoScrollJob?.cancel()
        autoScrollJob = viewModelScope.launch(ScreenErrors) {
            while (isActive) {
                val speed = common.value.autoScrollSpeed.coerceIn(1, 100)
                when (val c = current()) {
                    is ReaderContent.Text -> if (textSettings.value.paged && !textSettings.value.verticalText && !textSettings.value.longStrip) {
                        delay(pageInterval(speed))
                        c.controller.next()
                    } else {
                        delay(32)
                        c.controller.scrollStep((speed / 12f).coerceAtLeast(1f).toInt().coerceAtLeast(1))
                    }
                    is ReaderContent.Pdf -> { delay(pageInterval(speed)); c.controller.turnPage(forward = true) }
                    is ReaderContent.Manga -> { delay(pageInterval(speed)); c.controller.next() }
                    null -> delay(500)
                }
            }
        }
    }

    private fun pageInterval(speed: Int): Long = ((100 - speed) * 600L + 3_000L)

    fun stopAutoScroll() {
        autoScroll.value = false
        autoScrollJob?.cancel()
    }

    // ================================================================== translation
    fun toggleBilingual() {
        val on = !textSettings.value.bilingual
        updateText { it.copy(bilingual = on) }
        if (on) startBilingual() else {
            bilingualJob?.cancel()
            bilingualProgress.value = null
            (current() as? ReaderContent.Text)?.controller?.removeTranslations()
        }
    }

    private fun startBilingual() {
        val c = (current() as? ReaderContent.Text)?.controller ?: return
        bilingualJob?.cancel()
        bilingualJob = viewModelScope.launch(ScreenErrors) {
            delay(200)
            val blocks = runCatching { c.blocks(c.progress.offset) }.getOrDefault(emptyList()).filter { !it.translated && it.text.isNotBlank() }
            if (blocks.isEmpty()) return@launch
            bilingualProgress.value = 0f
            var failed = false
            blocks.forEachIndexed { i, b ->
                if (!isActive) return@launch
                val r = runCatching { translation.translate(b.text, book?.language, null) }
                r.onSuccess { c.insertTranslation(b.i, it.translated) }.onFailure { if (!failed) { failed = true; events.send(ReaderEvent.Error(it)) } }
                if (failed) return@forEachIndexed
                bilingualProgress.value = (i + 1f) / blocks.size
            }
            c.commitTranslations()
            bilingualProgress.value = null
        }
    }

    /** Translates the text currently visible (page translation). */
    fun translatePage() {
        when (val c = current()) {
            is ReaderContent.Text -> viewModelScope.launch(ScreenErrors) {
                val text = runCatching { c.controller.visibleText() }.getOrDefault("").trim()
                if (text.isNotEmpty()) translateText.value = text.take(4000)
            }
            is ReaderContent.Pdf -> viewModelScope.launch(ScreenErrors) {
                val page = c.controller.page
                val text = runCatching { c.controller.pdf.text(page) }.getOrDefault("").trim().ifEmpty { ocrPdfPage(c.controller.pdf, page) }
                if (text.isNotEmpty()) translateText.value = text.take(4000) else events.send(ReaderEvent.Message(R.string.reader_no_text))
            }
            is ReaderContent.Manga -> translateMangaPage(c.controller.localPage)
            null -> Unit
        }
    }

    /** Scanned PDF pages have no text layer: render the page and recognize it with OCR. */
    private suspend fun ocrPdfPage(pdf: PdfSource, page: Int): String {
        ocrRunning.value = true
        return try {
            runCatching {
                ocrMutex.withLock {
                    val bmp = pdf.render(page, 1600, annotations = false)
                    try {
                        ocr.recognize(bmp, settings.ocrLanguage.get(), cacheKey = "pdf_${args.bookId}_$page").joinToString("\n") { it.text }.trim()
                    } finally {
                        bmp.recycle()
                    }
                }
            }.getOrDefault("")
        } finally {
            ocrRunning.value = false
        }
    }

    /** Runs OCR on a manga page, translates the text blocks and shows them as an overlay. */
    fun translateMangaPage(page: Int, chapterId: Long = _chapterId.value) {
        val c = (current() as? ReaderContent.Manga)?.controller ?: return
        // Overlays belong to the flat page of the window, so a chapter next door never reuses the previous page's
        // blocks; the caller supplies the physical page and its chapter.
        val flat = c.globalIndex(chapterId, page) ?: return
        if (c.overlays.containsKey(flat)) {
            c.overlayVisible = !c.overlayVisible
            return
        }
        pageActionExecutor.execute {
            ocrRunning.value = true
            try {
                runCatching {
                    val file = c.file(flat)
                    val (blocks, w, h) = ocrMutex.withLock {
                        val bmp = withContext(Dispatchers.IO) { decodeForOcr(file) } ?: error("Cannot decode page")
                        try {
                            val width = bmp.width.toFloat()
                            val height = bmp.height.toFloat()
                            val lang = settings.ocrLanguage.get()
                            Triple(ocr.recognize(bmp, lang, cacheKey = "${args.bookId}_${chapterId}_$page"), width, height)
                        } finally {
                            bmp.recycle()
                        }
                    }
                    if (blocks.isEmpty()) {
                        events.send(ReaderEvent.Message(R.string.reader_ocr_nothing))
                        return@runCatching
                    }
                    val lang = settings.ocrLanguage.get()
                    val translated = blocks.map { b ->
                        val t = runCatching { translation.translate(b.text, lang, null).translated }.getOrDefault(b.text)
                        OverlayBlock(b.left / w, b.top / h, b.right / w, b.bottom / h, b.text, t)
                    }
                    c.overlays[flat] = translated
                    c.overlayVisible = true
                }.onFailure { events.send(ReaderEvent.Error(it)) }
            } finally {
                ocrRunning.value = false
            }
        }
    }

    fun sentenceRanges(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        val it = java.text.BreakIterator.getSentenceInstance()
        it.setText(text)
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) {
            val s = text.substring(start, end)
            if (s.isNotBlank()) {
                val lead = s.indexOfFirst { !it.isWhitespace() }
                val trail = s.indexOfLast { !it.isWhitespace() }
                out += (start + lead)..(start + trail)
            }
            start = end
            end = it.next()
        }
        return out
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        inputRouter.dispose()
        chapterNavigator.dispose()
        pageActionExecutor.dispose()
        val folderLease = folderReadSession
        folderReadSession = null
        TtsBus.controls = null
        TtsBus.update { TtsStatus() }
        ReaderTtsService.stop(context)
        tts.shutdown()
        autoScrollJob?.cancel()
        val c = current()
        saveJob?.cancel()
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                runCatching { flushProgress() }
                runCatching { flushStatsBlocking() }
                when (c) {
                    is ReaderContent.Text -> runCatching { c.controller.document.close() }
                    is ReaderContent.Pdf -> {
                        runCatching { c.controller.close() }
                        runCatching { PdfAnnotationWorker.enqueue(context, bookId) }
                    }
                    is ReaderContent.Manga -> runCatching { (c.controller.pages as? java.io.Closeable)?.close() }
                    null -> Unit
                }
            } finally { folderLease?.close() }
        }
    }

    private suspend fun flushStatsBlocking() = flushStats()
}

/** A pause longer than this without page turns ends the counted reading time. */
private const val IDLE_MS = 5 * 60_000L
private const val MAX_PRESETS = 12

/** How many settings changes "Undo the last change" keeps for the open book. */
private const val UNDO_LIMIT = 20

/** Largest page bitmap decoded for OCR (~32 MiB as ARGB); bigger pages are downsampled first. */
private const val MAX_OCR_PIXELS = 8_000_000L

/** Width used to render one PDF page for export, sharing and OCR; the budget in `PdfSource` caps the height. */
private const val PDF_EXPORT_WIDTH = 1600

/** Reading time credited after the last activity when a session is closed for being idle. */
private const val IDLE_TAIL_MS = 60_000L
