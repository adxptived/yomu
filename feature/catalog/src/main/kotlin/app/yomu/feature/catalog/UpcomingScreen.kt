package app.yomu.feature.catalog

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.yomu.core.common.ScreenErrors
import app.yomu.core.common.TimeFormat
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.topLevelContentInsets
import app.yomu.core.ui.theme.Dimens
import app.yomu.data.catalog.CatalogRepository
import app.yomu.data.work.UpdateRunStore
import app.yomu.data.work.UpdateWorker
import app.yomu.domain.catalog.PlannedCheck
import app.yomu.domain.catalog.PlannedDay
import app.yomu.domain.catalog.UpdatePlan
import app.yomu.domain.catalog.UpdateRunReport
import app.yomu.domain.catalog.UpdateSkip
import app.yomu.domain.model.BookCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the check-plan screen shows. */
sealed interface UpcomingState {
    data object Loading : UpcomingState
    data class Ready(val days: List<PlannedDay>, val total: Int) : UpcomingState
    data class Failed(val error: Throwable) : UpcomingState
}

/**
 * The plan of the next automatic checks.
 *
 * It answers "when will this title be asked?" — and says that it is a plan of checks, not a promise about chapters:
 * WorkManager chooses the minute, and a source's own release dates are separate. The result of the last run is
 * persisted, so it is still visible after a restart, and a manual run is awaited instead of being fired blind.
 */
class UpcomingViewModel(
    private val context: Context,
    private val catalog: CatalogRepository,
    private val runs: UpdateRunStore,
) : ViewModel() {
    private val _state = MutableStateFlow<UpcomingState>(UpcomingState.Loading)
    val state: StateFlow<UpcomingState> = _state

    /** The last finished run, persisted by the worker. */
    val lastRun: StateFlow<UpdateRunReport?> = runs.last.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shelves the plan can be filtered by. */
    val shelves = MutableStateFlow<List<BookCollection>>(emptyList())

    /** Selected shelf id; null = every shelf. */
    val shelfFilter = MutableStateFlow<Long?>(null)

    /** A manual check is running (the worker is enqueued or running). */
    val checking = MutableStateFlow(false)

    init {
        load()
        viewModelScope.launch(ScreenErrors) { shelves.value = runCatching { catalog.shelves() }.getOrDefault(emptyList()) }
        observeManualRun()
    }

    fun load() = viewModelScope.launch(ScreenErrors) {
        _state.value = UpcomingState.Loading
        runCatching { catalog.checkPlan() }
            .onSuccess { checks ->
                val week = UpdatePlan.week(checks, LocalDate.now())
                _state.value = UpcomingState.Ready(week, checks.size)
            }
            .onFailure { _state.value = UpcomingState.Failed(it) }
    }

    fun setShelfFilter(id: Long?) {
        shelfFilter.value = id
    }

    /**
     * Runs the same worker the periodic schedule uses and waits for it: the screen reloads the plan and the persisted
     * result when the work finishes, instead of rereading a plan that the worker has not touched yet.
     */
    fun checkAllNow() {
        UpdateWorker.runNow(context)
    }

    private fun observeManualRun() = viewModelScope.launch(ScreenErrors) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("${UpdateWorker.NAME}_now")
            .collect { infos ->
                val running = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
                val was = checking.value
                checking.value = running
                if (was && !running) load()
            }
    }
}

/** The plan grouped by day; tapping a title opens it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpcomingScreen(viewModel: UpcomingViewModel, onBack: () -> Unit, onOpenTitle: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastRun by viewModel.lastRun.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val filter by viewModel.shelfFilter.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings: AppSettings = koinInject()
    val timeFormat by settings.timeFormat.flow.collectAsStateWithLifecycle(initialValue = settings.timeFormat.default)
    val dateFormat = rememberFormat()
    val today = LocalDate.now()
    Scaffold(
        contentWindowInsets = topLevelContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upcoming_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(app.yomu.core.ui.R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.upcoming_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.s),
            )
            Button(
                onClick = viewModel::checkAllNow,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.l),
            ) {
                if (checking) {
                    CircularProgressIndicator(Modifier.padding(end = Dimens.s).size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.upcoming_checking))
                } else {
                    Text(stringResource(R.string.upcoming_check_now))
                }
            }
            // The last run is persisted, so "checked 12, 3 new, 1 failed" survives a restart.
            lastRun?.takeIf { it.hasAnything }?.let { report ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.s),
                ) {
                    Column(Modifier.padding(Dimens.m)) {
                        Text(
                            stringResource(
                                R.string.upcoming_last_run,
                                TimeFormat.format(context, timeFormat, report.finishedAt),
                                report.checked,
                                report.newChapters,
                                report.failed,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (report.queuedDownloads > 0) {
                            Text(
                                stringResource(R.string.upcoming_last_run_downloads, report.queuedDownloads),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        report.titles.filter { it.failed }.take(3).forEach { title ->
                            Text(
                                stringResource(R.string.upcoming_last_run_failed, title.title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (shelves.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = Dimens.l),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = filter == null,
                            onClick = { viewModel.setShelfFilter(null) },
                            label = { Text(stringResource(R.string.upcoming_filter_all)) },
                        )
                    }
                    items(shelves, key = { "shelf-${it.id}" }) { shelf ->
                        FilterChip(
                            selected = filter == shelf.id,
                            onClick = { viewModel.setShelfFilter(if (filter == shelf.id) null else shelf.id) },
                            label = { Text(shelf.name) },
                        )
                    }
                }
            }
            when (val s = state) {
                UpcomingState.Loading -> Text(stringResource(R.string.upcoming_loading), modifier = Modifier.padding(Dimens.l))
                is UpcomingState.Failed -> {
                    Text(errorMessage(context, s.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Dimens.l))
                    TextButton(onClick = viewModel::load) { Text(stringResource(R.string.upcoming_retry)) }
                }
                is UpcomingState.Ready -> {
                    val days = if (filter == null) s.days else s.days.map { day ->
                        day.copy(checks = day.checks.filter { filter in it.shelfIds })
                    }
                    val shown = days.sumOf { it.checks.size }
                    if (shown == 0) {
                        Text(
                            if (filter == null) stringResource(R.string.upcoming_empty) else stringResource(R.string.upcoming_filter_empty),
                            modifier = Modifier.padding(Dimens.l),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        if (filter != null) {
                            Text(
                                stringResource(R.string.upcoming_filter_count, shown, s.total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
                            )
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            days.forEachIndexed { index, day ->
                                // A day boundary you can see: a heavier line before every day but the first, and the
                                // day header stays on screen while its titles scroll.
                                if (index > 0) {
                                    item(key = "boundary-${day.date}") {
                                        HorizontalDivider(Modifier.padding(top = Dimens.s, bottom = Dimens.xs), thickness = 2.dp)
                                    }
                                }
                                stickyHeader(key = "day-${day.date}") {
                                    Text(
                                        when (day.date) {
                                            today -> stringResource(R.string.upcoming_today, day.date.format(dateFormat))
                                            today.plusDays(1) -> stringResource(R.string.upcoming_tomorrow, day.date.format(dateFormat))
                                            else -> day.date.format(dateFormat)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = Dimens.l, vertical = Dimens.s),
                                    )
                                }
                                if (day.checks.isEmpty()) {
                                    item(key = "empty-${day.date}") {
                                        Text(
                                            stringResource(R.string.upcoming_nothing_today),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
                                        )
                                    }
                                }
                                items(day.checks, key = { "t-${it.titleId}-${it.nextCheckAt}" }) { check ->
                                    CheckRow(check, timeFormat, onClick = { onOpenTitle(check.titleId) })
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(check: PlannedCheck, timeFormat: String, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dimens.l, vertical = Dimens.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s),
    ) {
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            check.reason?.let {
                Text(reasonLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            TimeFormat.format(context, timeFormat, check.nextCheckAt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun reasonLabel(reason: UpdateSkip): String = stringResource(
    when (reason) {
        UpdateSkip.INTERVAL -> R.string.upcoming_reason_interval
        UpdateSkip.RESTRICTION -> R.string.upcoming_reason_restriction
        UpdateSkip.EXCLUDED_SHELF -> R.string.upcoming_reason_shelf
        UpdateSkip.EXCLUDED_TITLE -> R.string.upcoming_reason_title
        UpdateSkip.NO_SOURCE -> R.string.upcoming_reason_no_source
    },
)

@Composable
private fun rememberFormat(): DateTimeFormatter = androidx.compose.runtime.remember {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault())
}
