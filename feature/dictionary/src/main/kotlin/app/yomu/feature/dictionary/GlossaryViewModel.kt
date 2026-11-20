package app.yomu.feature.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomu.core.common.ScreenErrors
import app.yomu.core.datastore.AppSettings
import app.yomu.core.datastore.stateIn
import app.yomu.data.glossary.GlossaryRepository
import app.yomu.data.index.FulltextIndexRepository
import app.yomu.domain.glossary.GlossaryEntry
import app.yomu.domain.glossary.GlossaryIndex
import app.yomu.domain.index.IndexedHit
import app.yomu.domain.index.IndexPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Glossary notes plus the opt-in text index, both owned by the Dictionary destination. */
class GlossaryViewModel(
    private val glossary: GlossaryRepository,
    private val index: FulltextIndexRepository,
    settings: AppSettings,
) : ViewModel() {
    val query = MutableStateFlow("")
    val bookFilter = MutableStateFlow<Long?>(null)
    val searchQuery = MutableStateFlow("")

    private val _books = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val books: StateFlow<List<Pair<Long, String>>> = _books
    private val _booksWithEntries = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val booksWithEntries: StateFlow<List<Pair<Long, String>>> = _booksWithEntries

    val entries: StateFlow<List<GlossaryEntry>> = combine(glossary.observe(), bookFilter, query) { all, book, q ->
        all.filter { book == null || it.bookId == book }.filter { GlossaryIndex.matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hitsList = MutableStateFlow<List<IndexedHit>>(emptyList())

    val plan: StateFlow<IndexPlan> = index.plan
    val estimate = index.estimate
    val indexEnabled = settings.fulltextIndex.stateIn(viewModelScope)

    init {
        reloadBooks()
    }

    private fun reloadBooks() = viewModelScope.launch(ScreenErrors) {
        _books.value = glossary.books()
        _booksWithEntries.value = glossary.booksWithEntries()
    }

    fun save(entry: GlossaryEntry) = viewModelScope.launch(ScreenErrors) {
        val now = System.currentTimeMillis()
        glossary.save(if (entry.id == 0L) entry.copy(createdAt = now, updatedAt = now) else entry.copy(updatedAt = now))
        reloadBooks()
    }

    fun delete(id: Long) = viewModelScope.launch(ScreenErrors) {
        glossary.delete(id)
        reloadBooks()
    }

    fun estimate() = viewModelScope.launch(ScreenErrors) { index.estimate() }

    fun rebuild() = viewModelScope.launch(ScreenErrors) { index.rebuild() }

    fun cancel() = index.cancel()

    fun searchIndexed() = viewModelScope.launch(ScreenErrors) {
        hitsList.value = index.search(searchQuery.value, bookFilter.value)
    }

    fun clearIndex() = viewModelScope.launch(ScreenErrors) { index.clear() }
}
