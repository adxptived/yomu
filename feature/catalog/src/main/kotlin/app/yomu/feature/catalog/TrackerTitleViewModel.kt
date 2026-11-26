package app.yomu.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yomu.core.common.ScreenErrors
import app.yomu.core.database.TrackEntity
import app.yomu.data.catalog.TrackerPageSource
import app.yomu.domain.model.TrackerPage
import app.yomu.domain.model.TrackerPerson
import app.yomu.domain.model.TrackerRecommendation
import app.yomu.domain.model.TrackerSection
import app.yomu.domain.model.TrackerTitleDetails
import app.yomu.domain.tracking.TrackService
import app.yomu.domain.tracking.TrackStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One pageable block of the tracker title page: loading, a page of items, or an error with a retry. */
sealed interface TrackerSectionState<out T> {
    data object Loading : TrackerSectionState<Nothing>
    data class Ready<T>(
        val items: List<T>,
        val page: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val moreError: Throwable? = null,
    ) : TrackerSectionState<T>
    data class Failed(val error: Throwable) : TrackerSectionState<Nothing>
}

sealed interface TrackerDetailsState {
    data object Loading : TrackerDetailsState
    data class Ready(
        val details: TrackerTitleDetails,
        /** The local online title linked to this tracker title, if any. */
        val localItemId: Long?,
        /** The reader's own entry: shown only with a working account. */
        val track: TrackEntity?,
        val localBookId: Long? = null,
    ) : TrackerDetailsState

    data class Failed(val error: Throwable) : TrackerDetailsState
}

/**
 * The tracker title page (C3): public details first, then the provider's own characters, staff and recommendations,
 * one page at a time. Private fields (status, progress, score) come from the reader's link and never from the public
 * page. A section error is kept per section, so "Retry" reloads only what failed.
 */
class TrackerTitleViewModel(
    val service: TrackService,
    val remoteId: Long,
    private val metadata: TrackerPageSource,
) : ViewModel() {
    private val _details = MutableStateFlow<TrackerDetailsState>(TrackerDetailsState.Loading)
    val details: StateFlow<TrackerDetailsState> = _details.asStateFlow()

    private val _characters = MutableStateFlow<TrackerSectionState<TrackerPerson>>(TrackerSectionState.Loading)
    val characters: StateFlow<TrackerSectionState<TrackerPerson>> = _characters.asStateFlow()

    private val _staff = MutableStateFlow<TrackerSectionState<TrackerPerson>>(TrackerSectionState.Loading)
    val staff: StateFlow<TrackerSectionState<TrackerPerson>> = _staff.asStateFlow()

    private val _recommendations = MutableStateFlow<TrackerSectionState<TrackerRecommendation>>(TrackerSectionState.Loading)
    val recommendations: StateFlow<TrackerSectionState<TrackerRecommendation>> = _recommendations.asStateFlow()

    val signedIn: Boolean get() = metadata.signedIn(service)

    init {
        load()
    }

    private var loadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true) return
        _details.value = TrackerDetailsState.Loading
        loadJob = viewModelScope.launch(ScreenErrors) {
            runCatching { metadata.trackerTitle(service, remoteId) }
                .onSuccess { title ->
                    if (title == null) {
                        _details.value = TrackerDetailsState.Failed(IllegalStateException("${service.displayName}: title not found"))
                        return@launch
                    }
                    val local = runCatching { metadata.localItemId(service, remoteId) }.getOrNull()
                    val track = if (signedIn) runCatching { metadata.trackFor(service, remoteId) }.getOrNull() else null
                    val book = if (local == null) runCatching { metadata.localBookId(service, remoteId) }.getOrNull() else null
                    _details.value = TrackerDetailsState.Ready(title, local, track, book)
                    title.sections.forEach { section -> loadSection(section, page = 1) }
                }
                .onFailure { _details.value = TrackerDetailsState.Failed(it) }
        }
    }

    /** The sections a provider can answer, for the screen to show only those. */
    fun sections(): Set<TrackerSection> = (_details.value as? TrackerDetailsState.Ready)?.details?.sections.orEmpty()

    fun loadSection(section: TrackerSection, page: Int = 1) = fetchSection(section, append = page > 1)

    fun more(section: TrackerSection) = fetchSection(section, append = true)

    private fun fetchSection(section: TrackerSection, append: Boolean) {
        when (section) {
            TrackerSection.CHARACTERS -> fetch(_characters, append) { metadata.trackerPeople(service, remoteId, section, it) }
            TrackerSection.STAFF -> fetch(_staff, append) { metadata.trackerPeople(service, remoteId, section, it) }
            TrackerSection.RECOMMENDATIONS -> fetch(_recommendations, append) { metadata.trackerRecommendations(service, remoteId, it) }
        }
    }

    private fun <T> fetch(
        state: MutableStateFlow<TrackerSectionState<T>>,
        append: Boolean,
        request: suspend (Int) -> TrackerPage<T>,
    ) {
        val shown = if (append) state.value as? TrackerSectionState.Ready ?: return else null
        if (shown != null && (!shown.hasMore || shown.loadingMore)) return
        val page = (shown?.page ?: 0) + 1
        state.value = shown?.copy(loadingMore = true, moreError = null) ?: TrackerSectionState.Loading
        viewModelScope.launch(ScreenErrors) {
            state.value = try {
                val result = request(page)
                TrackerSectionState.Ready(shown?.items.orEmpty() + result.items, page, result.hasMore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                shown?.copy(loadingMore = false, moreError = e) ?: TrackerSectionState.Failed(e)
            }
        }
    }
}
