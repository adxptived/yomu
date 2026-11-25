package app.yomu.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import app.yomu.core.common.ScreenErrors
import app.yomu.core.datastore.AppSettings
import app.yomu.core.datastore.stateIn
import app.yomu.core.ui.components.CoverCache
import app.yomu.data.audio.AudiobookStore
import app.yomu.data.audio.YouTubeResult
import app.yomu.data.catalog.CatalogRepository
import app.yomu.data.download.DownloadOverview
import app.yomu.data.download.DownloadSummary
import app.yomu.data.library.ImportOutcome
import app.yomu.data.library.ImportService
import app.yomu.data.library.LibraryProjection
import app.yomu.data.library.LibraryTabs
import app.yomu.data.repository.BookRepositoryImpl
import app.yomu.data.work.LibraryScanWorker
import app.yomu.domain.library.BulkAction
import app.yomu.domain.library.BulkExecutor
import app.yomu.domain.library.BulkOutcome
import app.yomu.domain.library.ContentOrigin
import app.yomu.domain.library.ContentType
import app.yomu.domain.model.ContentType as CatalogContentType
import app.yomu.domain.library.LibraryItemKey
import app.yomu.domain.library.LibraryQuerySpec
import app.yomu.domain.library.LibraryQuerySpecData
import app.yomu.domain.library.LibrarySearchField
import app.yomu.domain.library.LibrarySearchFields
import app.yomu.domain.library.LibrarySearchQuery
import app.yomu.domain.library.LibrarySelection
import app.yomu.domain.library.LibrarySortKey
import app.yomu.domain.library.SavedQueryShelf
import app.yomu.domain.library.matches
import app.yomu.domain.model.BatchMetadata
import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.CatalogEntry
import app.yomu.domain.model.LibraryFilter
import app.yomu.domain.model.LibraryQuery
import app.yomu.domain.model.LibrarySort
import app.yomu.domain.model.Locator
import app.yomu.domain.model.ReadingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** "Type" options of the library filter: groups of book formats and the online titles. */
enum class FormatGroup(val formats: Set<BookFormat>) {
    BOOKS(setOf(BookFormat.EPUB, BookFormat.FB2, BookFormat.TXT, BookFormat.HTML, BookFormat.DOCX, BookFormat.MOBI)),
    PDF(setOf(BookFormat.PDF)),
    COMICS(setOf(BookFormat.CBZ, BookFormat.CBR, BookFormat.CB7, BookFormat.FOLDER)),
    AUDIO(setOf(BookFormat.AUDIO)),
    ONLINE(emptySet()),
}

/** The legacy SQL sort keys and the versioned query's keys, mapped where they mean the same thing. */
private fun LibrarySort.toSpecKey(): LibrarySortKey = when (this) {
    LibrarySort.TITLE -> LibrarySortKey.TITLE
    LibrarySort.AUTHOR -> LibrarySortKey.AUTHOR
    LibrarySort.ADDED -> LibrarySortKey.ADDED
    LibrarySort.LAST_READ -> LibrarySortKey.LAST_OPENED
    LibrarySort.PROGRESS -> LibrarySortKey.PROGRESS
}

private fun LibrarySortKey.toLibrarySort(): LibrarySort = when (this) {
    LibrarySortKey.TITLE -> LibrarySort.TITLE
    LibrarySortKey.AUTHOR -> LibrarySort.AUTHOR
    LibrarySortKey.ADDED, LibrarySortKey.UPDATED -> LibrarySort.ADDED
    LibrarySortKey.LAST_OPENED, LibrarySortKey.UNREAD -> LibrarySort.LAST_READ
    LibrarySortKey.PROGRESS -> LibrarySort.PROGRESS
}

/** The fields of the spec that the local SQL query understands. */
private fun LibraryQuerySpec.toFilter(): LibraryFilter = LibraryFilter(
    formats = types.flatMapTo(HashSet()) { type ->
        when (type) {
            ContentType.TEXT -> FormatGroup.BOOKS.formats
            ContentType.PDF -> FormatGroup.PDF.formats
            ContentType.MANGA -> FormatGroup.COMICS.formats
            ContentType.AUDIO -> FormatGroup.AUDIO.formats
            ContentType.UNKNOWN -> emptySet()
        }
    },
    statuses = statuses,
    tag = tags.firstOrNull(),
    language = languages.firstOrNull(),
    collectionId = collectionId,
    favoritesOnly = favoritesOnly,
)

/** The "Type" chips a spec stands for; online titles are an origin, local content types are not. */
private fun LibraryQuerySpec.toFormatGroups(): Set<FormatGroup> = buildSet {
    if (ContentType.TEXT in types) add(FormatGroup.BOOKS)
    if (ContentType.PDF in types) add(FormatGroup.PDF)
    if (ContentType.MANGA in types) add(FormatGroup.COMICS)
    if (ContentType.AUDIO in types) add(FormatGroup.AUDIO)
    if (ContentOrigin.CATALOG in origins) add(FormatGroup.ONLINE)
}

/** An item of "Continue reading": a book or an online title. */
sealed interface ContinueItem {
    val time: Long

    data class BookItem(val book: Book) : ContinueItem {
        override val time: Long get() = book.lastReadAt ?: 0
    }

    data class OnlineItem(val entry: CatalogEntry) : ContinueItem {
        override val time: Long get() = entry.lastReadAt
    }
}

/** A YouTube search started from "Audiobook by link". */
data class AudioSearch(val query: String, val loading: Boolean = true, val results: List<YouTubeResult> = emptyList(), val error: Throwable? = null)

/**
 * The tab picker of the library: the [books] or the online title [online] it is for, and how many of them each tab
 * already holds.
 */
data class TabPick(val books: Set<Long> = emptySet(), val online: Long? = null, val counts: Map<Long, Int> = emptyMap(), val title: String? = null) {
    val size: Int get() = if (online != null) 1 else books.size
}

/** Reading state of an online title in the library's status filter. */
internal fun CatalogEntry.readingStatus(): ReadingStatus = when {
    lastReadAt <= 0 -> ReadingStatus.NEW
    unread > 0 -> ReadingStatus.READING
    else -> ReadingStatus.FINISHED
}

/** The text as typed and the tree it parsed to; the two always change together. */
private data class SearchSnapshot(val raw: String, val query: LibrarySearchQuery)

/** The debounced text and what it parsed to, carried together so a filter never sees a mismatched pair. */
internal data class LibrarySearchAndSort(
    val raw: String,
    val parsed: LibrarySearchQuery,
    val sort: LibrarySort,
    val ascending: Boolean,
)

/** A local book's view for the advanced search expression. */
internal fun Book.toSearchFields(): LibrarySearchFields = object : LibrarySearchFields {
    override val searchText: String
        get() = buildList {
            add(title)
            add(authorsText)
            series?.let { add(it) }
            description?.let { add(it) }
            publisher?.let { add(it) }
            language?.let { add(it) }
            addAll(tags)
        }.joinToString(" ")

    override fun text(field: LibrarySearchField): String? = when (field) {
        LibrarySearchField.TITLE -> title
        LibrarySearchField.AUTHOR -> authorsText.takeIf { it.isNotBlank() }
        LibrarySearchField.DESCRIPTION -> description
        LibrarySearchField.GENRE -> tags.joinToString(" ").takeIf { it.isNotBlank() }
        LibrarySearchField.LANGUAGE -> language
        else -> null
    }

    override fun number(field: LibrarySearchField): Long? = when (field) {
        LibrarySearchField.ADDED -> addedAt
        LibrarySearchField.PROGRESS -> (progress * 100).toLong()
        LibrarySearchField.RATING -> rating.toLong()
        else -> null
    }
}

/** An online title's view: it has artist/source/update fields that a local book does not. */
internal fun CatalogEntry.toSearchFields(): LibrarySearchFields = object : LibrarySearchFields {
    override val searchText: String
        get() = buildList {
            add(title)
            author?.let { add(it) }
            artist?.let { add(it) }
            description?.let { add(it) }
            genres.joinToString(" ").takeIf { it.isNotBlank() }?.let { add(it) }
            add(sourceId.toString())
        }.joinToString(" ")

    override fun text(field: LibrarySearchField): String? = when (field) {
        LibrarySearchField.TITLE -> title
        LibrarySearchField.AUTHOR -> author
        LibrarySearchField.ARTIST -> artist
        LibrarySearchField.DESCRIPTION -> description
        LibrarySearchField.GENRE -> genres.joinToString(" ").takeIf { it.isNotBlank() }
        LibrarySearchField.SOURCE -> sourceId.toString()
        else -> null
    }

    override fun number(field: LibrarySearchField): Long? = when (field) {
        LibrarySearchField.UNREAD -> unread.toLong()
        LibrarySearchField.ADDED -> addedAt
        LibrarySearchField.NEXT_UPDATE -> lastUpdateAt
        LibrarySearchField.PROGRESS -> if (chapterCount > 0) ((chapterCount - unread).toFloat() / chapterCount * 100).toLong() else null
        else -> null
    }
}

/** The "Type" chip an online title belongs to: the same content groups the local books use. */
internal fun CatalogContentType.formatGroup(): FormatGroup = when (this) {
    CatalogContentType.MANGA -> FormatGroup.COMICS
    CatalogContentType.AUDIO -> FormatGroup.AUDIO
    CatalogContentType.NOVEL, CatalogContentType.BOOK -> FormatGroup.BOOKS
}

/** Online titles of the current tab that pass the search and filters, in the library's order. */
internal fun List<CatalogEntry>.forLibrary(
    filter: LibraryFilter,
    types: Set<FormatGroup>,
    query: LibrarySearchAndSort,
    tabsOf: Map<Long, Set<Long>>,
): List<CatalogEntry> {
    // "Online" is an origin, not a content type: it widens the selection to every network title. Without it, the
    // remaining chips still match by the title's own kind (manga → comics, novel → books), so the manga filter no
    // longer drops the online manga the user added to the library.
    val onlineOnly = FormatGroup.ONLINE in types
    val contentTypes = types - FormatGroup.ONLINE
    // Titles of the sources carry no language: a language filter is about the books.
    if (filter.language != null) return emptyList()
    val q = query.raw.trim()
    val shown = filter { e ->
        (types.isEmpty() || onlineOnly || e.type.formatGroup() in contentTypes) &&
            (filter.collectionId == null || tabsOf[e.id]?.contains(filter.collectionId) == true) &&
            (if (query.parsed.usesAdvanced) {
                query.parsed.expression.matches(e.toSearchFields())
            } else {
                q.isEmpty() || e.title.contains(q, ignoreCase = true) || e.author?.contains(q, ignoreCase = true) == true
            }) &&
            (filter.statuses.isEmpty() || e.readingStatus() in filter.statuses) &&
            (filter.tag == null || e.genres.any { it.equals(filter.tag, ignoreCase = true) })
    }
    // The same comparator the local books use: one sort definition for both kinds, with the namespace as the
    // tie-breaker, so a reload cannot swap two titles with equal keys.
    return LibraryProjection.orderOnline(shown, query.sort, query.ascending)
}

/** Share of the known chapters that are read. */
internal fun CatalogEntry.readFraction(): Float = if (chapterCount <= 0) 0f else ((chapterCount - unread).coerceAtLeast(0).toFloat() / chapterCount)

sealed interface LibraryEvent {
    data class Imported(val added: Int, val duplicates: Int, val failed: Int, val firstError: Throwable?, val singleBookId: Long?) : LibraryEvent

    /** A bulk command reached only part of the selection; the UI names both numbers instead of saying "done". */
    data class BulkPartial(val action: String, val succeeded: Int, val failed: Int, val unsupported: Int) : LibraryEvent
    data class Message(val text: Int) : LibraryEvent
    data class OpenBook(val book: app.yomu.domain.model.Book) : LibraryEvent
    data class ReadChapter(val chapterId: Long) : LibraryEvent
    data class OpenTitle(val itemId: Long) : LibraryEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class LibraryViewModel(
    private val context: Context,
    private val books: BookRepositoryImpl,
    private val importer: ImportService,
    private val settings: AppSettings,
    private val tabs: LibraryTabs,
    private val catalog: CatalogRepository,
    private val audiobooks: AudiobookStore,
    private val history: app.yomu.data.repository.ReadingHistoryRepository,
    downloads: DownloadOverview,
    private val reports: app.yomu.data.library.ImportReportStore,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter())
    private val types = MutableStateFlow<Set<FormatGroup>>(emptySet())
    private val _pick = MutableStateFlow<TabPick?>(null)
    private val pendingDelete = MutableStateFlow<Set<Long>>(emptySet())
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)

    init {
        // Filters survive restarts; the search text is transient on purpose.
        viewModelScope.launch(ScreenErrors) {
            val storedSpec = runCatching { settings.libraryQuerySpec.get() }.getOrDefault(LibraryQuerySpecData())
            if (storedSpec != LibraryQuerySpecData()) {
                // L05: the versioned spec is the source of truth once it holds something; the old keys are for upgrades.
                applySpec(storedSpec.toSpec())
            } else {
                filter.value = runCatching { settings.libraryFilter.get() }.getOrDefault(LibraryFilter())
                types.value = runCatching { settings.libraryTypes.get() }.getOrDefault(emptySet())
                    .mapNotNull { name -> FormatGroup.entries.firstOrNull { it.name == name } }.toSet()
            }
            launch { filter.collect { settings.libraryFilter.set(it) } }
            launch { types.collect { settings.libraryTypes.set(it.map { group -> group.name }.toSet()) } }
        }
        // Every change of the query is written as one versioned spec, which is what a smart shelf saves.
        viewModelScope.launch(ScreenErrors) {
            combine(search.debounce(200), filter, types, settings.librarySort.flow, settings.libraryAscending.flow) { _, _, _, _, _ ->
                currentSpec()
            }.collect { settings.libraryQuerySpec.set(LibraryQuerySpecData.from(it)) }
        }
    }

    /** Smart shelves: saved queries that new matching books join automatically. */
    val shelves: StateFlow<List<SavedQueryShelf>> = settings.libraryShelves.stateIn(viewModelScope)

    fun saveShelf(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(ScreenErrors) {
            val shelf = SavedQueryShelf(
                id = System.currentTimeMillis(),
                name = trimmed,
                spec = LibraryQuerySpecData.from(currentSpec()),
            )
            settings.libraryShelves.set(settings.libraryShelves.get().filterNot { it.name.equals(trimmed, ignoreCase = true) } + shelf)
        }
    }

    fun applyShelf(shelf: SavedQueryShelf) = viewModelScope.launch(ScreenErrors) { applySpec(shelf.spec.toSpec()) }

    fun deleteShelf(id: Long) = viewModelScope.launch(ScreenErrors) {
        settings.libraryShelves.set(settings.libraryShelves.get().filterNot { it.id == id })
    }

    /** The filters, types, sort and search as the one query model; also what "save shelf" stores. */
    fun currentSpec(): LibraryQuerySpec {
        val groups = types.value
        val contentTypes = buildSet {
            if (FormatGroup.BOOKS in groups) add(ContentType.TEXT)
            if (FormatGroup.PDF in groups) add(ContentType.PDF)
            if (FormatGroup.COMICS in groups) add(ContentType.MANGA)
            if (FormatGroup.AUDIO in groups) add(ContentType.AUDIO)
        }
        val f = filter.value
        return LibraryQuerySpec(
            search = search.value,
            types = contentTypes,
            origins = if (FormatGroup.ONLINE in groups) setOf(ContentOrigin.CATALOG) else emptySet(),
            statuses = f.statuses,
            favoritesOnly = f.favoritesOnly,
            tags = setOfNotNull(f.tag),
            languages = setOfNotNull(f.language),
            collectionId = f.collectionId,
            sort = sort.value.toSpecKey(),
            ascending = ascending.value,
        )
    }

    private suspend fun applySpec(spec: LibraryQuerySpec) {
        search.value = spec.search
        filter.value = spec.toFilter()
        types.value = spec.toFormatGroups()
        settings.librarySort.set(spec.sort.toLibrarySort())
        settings.libraryAscending.set(spec.ascending)
    }

    val events: Flow<LibraryEvent> = _events.receiveAsFlow()
    val searchText: StateFlow<String> = search
    val filterState: StateFlow<LibraryFilter> = filter
    val selection: StateFlow<Set<Long>> = _selection
    val sort = settings.librarySort.stateIn(viewModelScope)
    val ascending = settings.libraryAscending.stateIn(viewModelScope)
    val grid = settings.libraryGrid.stateIn(viewModelScope)
    val columns = settings.gridColumns.stateIn(viewModelScope)
    /** The long-press wheel: drag to an action and lift (a game wheel), or tap an action. */
    val radialDrag = settings.radialDrag.stateIn(viewModelScope)
    val showContinue = settings.showContinueReading.stateIn(viewModelScope)

    /** The "From the catalog" row: hidden by the user, and whether it sits under the books. */
    val shelfHidden = settings.libraryShelfHidden.stateIn(viewModelScope)
    val shelfBelow = settings.libraryShelfBelow.stateIn(viewModelScope)

    fun setShelfHidden(value: Boolean) = viewModelScope.launch(ScreenErrors) { settings.libraryShelfHidden.set(value) }
    fun setShelfBelow(value: Boolean) = viewModelScope.launch(ScreenErrors) { settings.libraryShelfBelow.set(value) }

    /** A one-line confirmation, e.g. after hiding the catalog row. */
    fun message(text: Int) { _events.trySend(LibraryEvent.Message(text)) }
    val incognito = settings.incognito.stateIn(viewModelScope)

    fun setIncognito(on: Boolean) = viewModelScope.launch(ScreenErrors) { settings.incognito.set(on) }
    val importProgress = importer.progress
    val downloadSummary: StateFlow<DownloadSummary> = downloads.summary.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadSummary())

    private val parsedSearch: StateFlow<SearchSnapshot> = search.debounce(200)
        .map { SearchSnapshot(it, LibrarySearchQuery.parse(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchSnapshot("", LibrarySearchQuery.parse("")))
    private val repositorySearch: Flow<String> = parsedSearch.map { snapshot ->
        // The SQL LIKE/FTS path would read operators as literals; advanced syntax is evaluated on the loaded items.
        if (snapshot.query.usesAdvanced) "" else snapshot.raw
    }

    private val query: Flow<LibraryQuery> =
        combine(repositorySearch, settings.librarySort.flow, settings.libraryAscending.flow, filter) { s, so, asc, f ->
            LibraryQuery(s, so, asc, f)
        }

    val paged: Flow<PagingData<Book>> = query.combine(pendingDelete) { query, pending -> query to pending }
        .flatMapLatest { (query, pending) ->
            books.pagedBooks(query).map { data -> data.filter { it.id !in pending } }
        }
        .cachedIn(viewModelScope)
        .combine(parsedSearch) { data, snapshot ->
            if (snapshot.query.usesAdvanced) data.filter { snapshot.query.expression.matches(it.toSearchFields()) } else data
        }

    val continueReading = books.observeContinueReading(12)
        .combine(pendingDelete) { l, p -> l.filter { it.id !in p } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = books.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = books.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val languages = books.observeLanguages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val total: StateFlow<Int> = books.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /** What the "All" tab counts: the books on the screen (a deletion pending its undo is out of the list). */
    val visibleTotal: StateFlow<Int> = books.observeCount()
        .combine(pendingDelete) { count, pending -> count - pending.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /** The "Type" filter (book formats and online titles); empty shows everything. */
    val typeFilter: StateFlow<Set<FormatGroup>> = types

    /** False when the type filter leaves only the online titles. */
    val showBooks: StateFlow<Boolean> = types.map { t -> t.isEmpty() || t.any { it != FormatGroup.ONLINE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Number of active filters (type, status, tag, language), for the badge of the filter button. */
    val activeFilters: StateFlow<Int> = combine(filter, types) { f, t ->
        t.size + f.statuses.size + (if (f.tag != null) 1 else 0) + (if (f.language != null) 1 else 0) + (if (f.favoritesOnly) 1 else 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ---- online titles (added with the heart in the catalog) ----
    private val onlineAll: StateFlow<List<CatalogEntry>> = catalog.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tabs of every online title: title id → tab ids. */
    val onlineTabs: StateFlow<Map<Long, Set<Long>>> = tabs.observeOnline().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Number of online titles; -1 until they are loaded. */
    val onlineCount: StateFlow<Int> = catalog.observeFavorites().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /** Online titles of the current tab that pass the search and the filters. */
    val onlineShown: StateFlow<List<CatalogEntry>> = combine(
        onlineAll, onlineTabs, filter, types,
        combine(parsedSearch, settings.librarySort.flow, settings.libraryAscending.flow) { snapshot, so, asc ->
            LibrarySearchAndSort(snapshot.raw, snapshot.query, so, asc)
        },
    ) { list, tabsOf, f, t, q -> list.forLibrary(f, t, q, tabsOf) }
        // Filtering and ordering are a pass over the user's online library: it runs on a worker, never on the thread
        // that composes the grid.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The saved place of every "Continue" book, so the card can say "Chapter 12 · 34%" instead of only a percentage.
     * The query is small (a handful of books) and reads only the progress table.
     */
    val continuePositions: StateFlow<Map<Long, Locator>> = continueReading
        .map { list ->
            list.mapNotNull { book ->
                runCatching { books.getProgress(book.id) }.getOrNull()?.let { book.id to it }
            }.toMap()
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Books and online titles in progress, the most recent first. */
    val continueItems: StateFlow<List<ContinueItem>> = combine(continueReading, onlineAll) { b, o ->
        (b.map { ContinueItem.BookItem(it) } + o.filter { it.lastReadAt > 0 && it.unread > 0 }.map { ContinueItem.OnlineItem(it) })
            .sortedByDescending { it.time }
            .take(CONTINUE_MAX)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The open tab picker, if any. */
    val tabPick: StateFlow<TabPick?> = _pick

    fun setSearch(text: String) { search.value = text }

    fun setSort(value: LibrarySort) = viewModelScope.launch(ScreenErrors) {
        if (settings.librarySort.get() == value) settings.libraryAscending.update { !it } else settings.librarySort.set(value)
    }

    /** Selects the sort without touching the direction: the display sheet has its own direction row. */
    fun chooseSort(value: LibrarySort) = viewModelScope.launch(ScreenErrors) { settings.librarySort.set(value) }

    fun setGrid(value: Boolean) = viewModelScope.launch(ScreenErrors) { settings.libraryGrid.set(value) }
    fun setColumns(value: Int) = viewModelScope.launch(ScreenErrors) { settings.gridColumns.set(value) }

    /** Explicit order direction, for the display sheet's own control (not tied to re-picking the sort). */
    fun setAscending(value: Boolean) = viewModelScope.launch(ScreenErrors) { settings.libraryAscending.set(value) }

    fun toggleGroup(group: FormatGroup) {
        val next = types.updateAndGet { if (group in it) it - group else it + group }
        filter.update { f -> f.copy(formats = next.flatMap { it.formats }.toSet()) }
    }

    fun toggleStatus(status: ReadingStatus) = filter.update { f ->
        f.copy(statuses = if (status in f.statuses) f.statuses - status else f.statuses + status)
    }

    fun toggleFavorites() = filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    fun setCollection(id: Long?) = filter.update { it.copy(collectionId = id) }
    fun setTag(tag: String?) = filter.update { it.copy(tag = tag) }
    fun setLanguage(lang: String?) = filter.update { it.copy(language = lang) }
    fun clearFilters() {
        types.value = emptySet()
        filter.update { LibraryFilter(collectionId = it.collectionId) }
    }

    // ---- selection ----
    /**
     * The selection in the shared typed form: a local book and an online entry with the same numeric id are separate
     * items, so a bulk action over a mixed set can never reach the wrong record.
     */
    private val _typedSelection = MutableStateFlow(LibrarySelection())

    /** The same selection, for the UI and for actions that need to know which kind each item is. */
    val typedSelection: StateFlow<LibrarySelection> = _typedSelection

    /** Local book ids of the selection (kept as the shape the existing bulk actions use). */
    fun toggleSelect(id: Long) {
        _typedSelection.update { it.toggle(LibraryItemKey.LocalBook(id)) }
        syncLegacySelection()
    }

    /** Toggles an online entry of the selection; the online grid uses this while selecting. */
    fun toggleSelectOnline(id: Long) {
        _typedSelection.update { it.toggle(LibraryItemKey.OnlineEntry(id)) }
        syncLegacySelection()
    }

    fun clearSelection() {
        _typedSelection.value = LibrarySelection()
        _selection.value = emptySet()
    }

    /** True when [id] of an online entry is selected. */
    fun isOnlineSelected(id: Long): Boolean = _typedSelection.value.contains(LibraryItemKey.OnlineEntry(id))

    /** Keeps [_selection] (the local ids the existing actions consume) aligned with the typed state. */
    private fun syncLegacySelection() {
        _selection.value = _typedSelection.value.keys.filterIsInstance<LibraryItemKey.LocalBook>().mapTo(mutableSetOf()) { it.id }
    }

    fun selectAll() = viewModelScope.launch(ScreenErrors) {
        val q = query.first()
        val parsed = parsedSearch.value.query
        // One predicate for "select all", the page and the count: the ids come from the same query the grid reads.
        val pending = pendingDelete.value
        val (ids, snapshot) = kotlinx.coroutines.withContext(Dispatchers.IO) {
            books.observeBooks(q).first()
                .filter { !parsed.usesAdvanced || parsed.expression.matches(it.toSearchFields()) }
                .map { it.id }.filter { it !in pending } to q.toString()
        }
        _selection.value = ids.toSet()
        // The online tiles visible under the same query belong to the same "all": a mixed selection keeps its typed keys.
        val online = onlineShown.value.map { LibraryItemKey.OnlineEntry(it.id) }
        val keys = mutableSetOf<LibraryItemKey>()
        ids.mapTo(keys) { LibraryItemKey.LocalBook(it) }
        keys.addAll(online)
        // "Select all" is a snapshot of the query, not of the loaded tiles: the same set feeds the typed state.
        _typedSelection.value = LibrarySelection().replaceAll(keys, snapshot)
    }

    /** "Continue" on an online title opens the chapter to read next; the title page only when it has no chapters. */
    fun continueOnline(itemId: Long) = viewModelScope.launch(ScreenErrors) {
        val chapter = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { history.resumeChapter(itemId, null) }
        _events.send(if (chapter != null) LibraryEvent.ReadChapter(chapter) else LibraryEvent.OpenTitle(itemId))
    }

    // ---- import ----
    /** The last batch: its per-file result, kept after a restart, and the retry of the files that can succeed. */
    val importReport = reports.latest

    fun import(uris: List<Uri>, link: Boolean? = null) = viewModelScope.launch(ScreenErrors) {
        if (uris.isEmpty()) return@launch
        val started = System.currentTimeMillis()
        // The picker may grant a persisted read permission; taking it is what makes a retry after a restart possible.
        uris.forEach { persistRead(it) }
        val outcomes = importer.import(uris, link)
        val asLink = link ?: (runCatching { settings.importMode.get() }.getOrDefault("COPY") == "LINK")
        reports.save(
            app.yomu.data.library.ImportReportStore.report(
                id = app.yomu.data.library.ImportReportStore.newId(),
                startedAt = started,
                finishedAt = System.currentTimeMillis(),
                link = asLink,
                uris = uris.map { it.toString() },
                outcomes = outcomes,
            ),
        )
        report(outcomes)
    }

    /** Retries only the files of the last batch that failed for a reason that can change. */
    fun retryFailedImport() = viewModelScope.launch(ScreenErrors) {
        val retry = reports.latest.value?.retryable?.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }.orEmpty()
        if (retry.isEmpty()) return@launch
        val started = System.currentTimeMillis()
        retry.forEach { persistRead(it) }
        val outcomes = importer.import(retry)
        reports.save(
            app.yomu.data.library.ImportReportStore.report(
                id = app.yomu.data.library.ImportReportStore.newId(),
                startedAt = started,
                finishedAt = System.currentTimeMillis(),
                link = false,
                uris = retry.map { it.toString() },
                outcomes = outcomes,
            ),
        )
        report(outcomes)
    }

    fun dismissImportReport() = reports.dismiss()

    private fun persistRead(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** A YouTube video or playlist, a podcast feed or an audio link becomes an audiobook, opened right away; words search YouTube. */
    fun addAudioLink(text: String) = viewModelScope.launch(ScreenErrors) {
        if (text.isBlank()) return@launch
        if (AudiobookStore.extractUrl(text) == null) {
            searchYouTube(text.trim())
            return@launch
        }
        _events.send(LibraryEvent.Message(R.string.library_audio_link_adding))
        val outcome = runCatching { audiobooks.addLink(text) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrElse { ImportOutcome.Failed(text, it) }
        openOrReport(outcome)
    }

    private val _audioSearch = MutableStateFlow<AudioSearch?>(null)
    val audioSearch: StateFlow<AudioSearch?> = _audioSearch
    private var audioSearchJob: kotlinx.coroutines.Job? = null

    fun searchYouTube(query: String) {
        audioSearchJob?.cancel()
        _audioSearch.value = AudioSearch(query)
        audioSearchJob = viewModelScope.launch {
            val result = runCatching { audiobooks.searchYouTube(query) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            _audioSearch.value = AudioSearch(query, loading = false, results = result.getOrDefault(emptyList()), error = result.exceptionOrNull())
        }
    }

    fun closeAudioSearch() {
        audioSearchJob?.cancel()
        _audioSearch.value = null
    }

    fun addYouTubeResult(result: YouTubeResult) = viewModelScope.launch(ScreenErrors) {
        closeAudioSearch()
        _events.send(LibraryEvent.Message(R.string.library_audio_link_adding))
        val outcome = runCatching { audiobooks.addYouTube(result) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrElse { ImportOutcome.Failed(result.title, it) }
        openOrReport(outcome)
    }

    private suspend fun openOrReport(outcome: ImportOutcome) {
        val id = (outcome as? ImportOutcome.Added)?.bookId ?: (outcome as? ImportOutcome.Duplicate)?.bookId
        val book = id?.let { books.getBook(it) }
        if (book != null) _events.send(LibraryEvent.OpenBook(book)) else report(listOf(outcome))
    }

    fun importFolderAsBook(tree: Uri) = viewModelScope.launch(ScreenErrors) {
        persist(tree)
        persistRead(tree)
        val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree)?.name ?: "Folder"
        val started = System.currentTimeMillis()
        val outcome = importer.importFolder(tree, name)
        reports.save(
            app.yomu.data.library.ImportReportStore.report(
                id = app.yomu.data.library.ImportReportStore.newId(),
                startedAt = started,
                finishedAt = System.currentTimeMillis(),
                link = true,
                uris = listOf(tree.toString()),
                outcomes = listOf(outcome),
            ),
        )
        report(listOf(outcome))
    }

    fun addScanFolder(tree: Uri) = viewModelScope.launch(ScreenErrors) {
        persist(tree)
        settings.scanFolders.update { it + tree.toString() }
        report(importer.scanTree(tree))
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Pull-to-refresh: scans the scan folders in place and reports what was found. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch(ScreenErrors) {
            _refreshing.value = true
            try {
                // The online shelf is the only part of the library with covers from the internet: a pull-down asks
                // for those pictures again, so new artwork is not hidden behind the old cache.
                val online = runCatching { catalog.observeFavorites().first() }.getOrDefault(emptyList())
                CoverCache.refresh(context, online.map { it.coverUrl })
                val folders = settings.scanFolders.get()
                if (folders.isEmpty()) {
                    _events.send(LibraryEvent.Message(R.string.library_no_scan_folders))
                    return@launch
                }
                val outcomes = folders.flatMap { runCatching { importer.scanTree(Uri.parse(it)) }.getOrDefault(emptyList()) }
                if (outcomes.any { it is ImportOutcome.Added || it is ImportOutcome.Failed }) report(outcomes)
                else _events.send(LibraryEvent.Message(R.string.library_up_to_date))
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun rescan() = LibraryScanWorker.runNow(context).let { _events.trySend(LibraryEvent.Message(R.string.library_scan_started)) }

    private fun persist(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private suspend fun report(result: List<ImportOutcome>) {
        val added = result.filterIsInstance<ImportOutcome.Added>()
        val dup = result.count { it is ImportOutcome.Duplicate }
        val failed = result.filterIsInstance<ImportOutcome.Failed>()
        val single = if (result.size == 1) (result.first() as? ImportOutcome.Added)?.bookId ?: (result.first() as? ImportOutcome.Duplicate)?.bookId else null
        _events.send(LibraryEvent.Imported(added.size, dup, failed.size, failed.firstOrNull()?.error, single))
    }

    // ---- bulk actions ----
    /**
     * Runs one bulk command over the current typed selection.
     *
     * Local books and online titles go to their own repositories, and the report keeps a result per key: a mixed
     * selection reports "applied to 8 of 12" instead of claiming success, and an item the command cannot reach is
     * recorded as unsupported rather than silently dropped.
     */
    fun runBulk(action: BulkAction, run: suspend (LibraryItemKey) -> BulkOutcome) = viewModelScope.launch(ScreenErrors) {
        val keys = _typedSelection.value.keys
        if (keys.isEmpty()) return@launch
        val report = BulkExecutor.run(action, keys, run)
        if (report.isPartial) {
            _events.send(LibraryEvent.BulkPartial(action.name, report.succeeded, report.failed, report.unsupported))
        }
        clearSelection()
    }

    /** The numbers shown before a bulk command runs ("apply to 8 of 12"). */
    fun bulkPreview(action: BulkAction): String = BulkExecutor.preview(action, _typedSelection.value.keys)

    fun setStatus(ids: Set<Long>, status: ReadingStatus) = viewModelScope.launch(ScreenErrors) { books.setStatus(ids, status); clearSelection() }

    fun setFavorite(ids: Set<Long>, favorite: Boolean) = viewModelScope.launch(ScreenErrors) { books.setFavorite(ids, favorite); clearSelection() }

    /** The reader's own score, 0 (not rated) to 5. */
    fun setRating(ids: Set<Long>, rating: Int) = viewModelScope.launch(ScreenErrors) { books.setRating(ids, rating); clearSelection() }

    fun addToCollection(ids: Set<Long>, collectionId: Long) = viewModelScope.launch(ScreenErrors) { books.addToCollection(ids, collectionId); clearSelection() }

    fun removeFromCollection(ids: Set<Long>, collectionId: Long) = viewModelScope.launch(ScreenErrors) { books.removeFromCollection(ids, collectionId); clearSelection() }

    /** Creates a tab at the end of the row (with the [add] books in it). */
    fun createCollection(name: String, add: Set<Long> = emptySet()) = viewModelScope.launch(ScreenErrors) {
        if (name.isBlank()) return@launch
        val id = tabs.create(name)
        if (add.isNotEmpty()) books.addToCollection(add, id)
        clearSelection()
    }

    fun renameCollection(id: Long, name: String) = viewModelScope.launch(ScreenErrors) { if (name.isNotBlank()) tabs.rename(id, name) }

    fun deleteCollection(id: Long) = viewModelScope.launch(ScreenErrors) {
        if (filter.value.collectionId == id) setCollection(null)
        tabs.delete(id)
    }

    /** Moves a tab one place left ([delta] < 0) or right. */
    fun moveCollection(id: Long, delta: Int) = viewModelScope.launch(ScreenErrors) { tabs.move(id, delta) }

    // ---- tab picker (books of the selection, or one online title) ----
    fun pickTabsForBooks(ids: Set<Long>) = viewModelScope.launch(ScreenErrors) {
        if (ids.isNotEmpty()) _pick.value = TabPick(books = ids, counts = bookCounts(ids))
    }

    fun pickTabsForOnline(itemId: Long, title: String? = null) = viewModelScope.launch(ScreenErrors) {
        _pick.value = TabPick(online = itemId, counts = tabs.onlineTabsOf(itemId).associateWith { 1 }, title = title)
    }

    /** Puts every item of the picker in the tab, or takes them all out when all of them are already there. */
    fun togglePickTab(tabId: Long) = viewModelScope.launch(ScreenErrors) {
        val p = _pick.value ?: return@launch
        val join = (p.counts[tabId] ?: 0) < p.size
        applyPick(p, tabId, join)
    }

    fun createTabInPick(name: String) = viewModelScope.launch(ScreenErrors) {
        if (name.isBlank()) return@launch
        val id = tabs.create(name)
        _pick.value?.let { applyPick(it, id, true) }
    }

    fun closeTabPick() {
        val p = _pick.value
        _pick.value = null
        if (p != null && p.books.isNotEmpty()) clearSelection()
    }

    private suspend fun applyPick(p: TabPick, tabId: Long, join: Boolean) {
        if (p.online != null) tabs.setOnline(listOf(p.online), tabId, join) else tabs.setBooks(p.books, tabId, join)
        val counts = if (p.online != null) tabs.onlineTabsOf(p.online).associateWith { 1 } else bookCounts(p.books)
        // The picker may have been closed meanwhile.
        _pick.update { current -> if (current == null) null else p.copy(counts = counts) }
    }

    private suspend fun bookCounts(ids: Set<Long>): Map<Long, Int> = ids.flatMap { tabs.bookTabsOf(it) }.groupingBy { it }.eachCount()

    /** Removes an online title from the library (and from its tabs). */
    fun removeOnline(itemId: Long) = viewModelScope.launch(ScreenErrors) { catalog.setFavorite(itemId, false) }

    fun addTag(ids: Set<Long>, tag: String) = viewModelScope.launch(ScreenErrors) { books.addTag(ids, tag); clearSelection() }

    // ---- batch metadata editor ----
    private val _batch = MutableStateFlow<List<Book>?>(null)

    /** The books the batch metadata editor is open for (null: closed). */
    val batch: StateFlow<List<Book>?> = _batch
    private var batchOrder: List<Long> = emptyList()

    fun openBatchEdit(ids: Set<Long>) = viewModelScope.launch(ScreenErrors) {
        if (ids.isEmpty()) return@launch
        batchOrder = ids.toList()
        _batch.value = ids.mapNotNull { books.getBook(it) }
    }

    fun closeBatchEdit() { _batch.value = null }

    fun applyBatchEdit(edit: BatchMetadata) = viewModelScope.launch(ScreenErrors) {
        val list = _batch.value ?: return@launch
        _batch.value = null
        if (!edit.isEmpty) {
            books.batchEdit(edit.ordered(list, batchOrder), edit)
            _events.send(LibraryEvent.Message(R.string.library_batch_saved))
        }
        clearSelection()
    }

    /** Hides the books at once; they are removed for real only when the user dismisses the undo snackbar. */
    fun requestDelete(ids: Set<Long>) {
        pendingDelete.update { it + ids }
        clearSelection()
    }

    /** Books hidden by [requestDelete] whose undo snackbar is still pending (shown again when the screen returns). */
    val pendingDeletions: StateFlow<Set<Long>> = pendingDelete

    fun undoDelete(ids: Set<Long>) {
        pendingDelete.update { it - ids }
    }

    fun commitDelete(ids: Set<Long>, deleteFiles: Boolean = false) = viewModelScope.launch(ScreenErrors) {
        try {
            books.delete(ids, deleteFiles)
            if (deleteFiles) clearSelection()
        } finally {
            pendingDelete.update { it - ids }
        }
    }

    override fun onCleared() {
        // Pending deletions are dropped, not executed: a screen that went away without the user answering the
        // undo prompt must not delete books silently. The rows were never touched, so they simply appear again.
        pendingDelete.value = emptySet()
        super.onCleared()
    }
}

/** Items of "Continue reading" (the large card and the row after it). */
private const val CONTINUE_MAX = 12
