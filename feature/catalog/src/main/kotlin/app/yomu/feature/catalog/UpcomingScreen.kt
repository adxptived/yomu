package app.yomu.feature.catalog

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.common.ScreenErrors
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.topLevelContentInsets
import app.yomu.core.ui.theme.Dimens
import app.yomu.data.catalog.CatalogRepository
import app.yomu.data.work.UpdateWorker
import app.yomu.domain.catalog.PlannedCheck
import app.yomu.domain.catalog.PlannedDay
import app.yomu.domain.catalog.UpdatePlan
import app.yomu.domain.catalog.UpdateSkip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
 * WorkManager chooses the minute, and a source's own release dates are separate.
 */
class UpcomingViewModel(private val context: Context, private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow<UpcomingState>(UpcomingState.Loading)
    val state: StateFlow<UpcomingState> = _state

    init { load() }

    fun load() = viewModelScope.launch(ScreenErrors) {
        _state.value = UpcomingState.Loading
        runCatching { catalog.checkPlan() }
            .onSuccess { checks ->
                val week = UpdatePlan.week(checks, LocalDate.now())
                _state.value = UpcomingState.Ready(week, checks.size)
            }
            .onFailure { _state.value = UpcomingState.Failed(it) }
    }

    /** Manual check of everything due or not: it bypasses the interval, but not a broken source. */
    fun checkAllNow() {
        UpdateWorker.runNow(context)
        load()
    }
}

/** The plan grouped by day; tapping a title opens it. */
@Composable
fun UpcomingScreen(viewModel: UpcomingViewModel, onBack: () -> Unit, onOpenTitle: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormat = rememberFormat()
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
            Button(onClick = viewModel::checkAllNow, modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.l)) {
                Text(stringResource(R.string.upcoming_check_now))
            }
            when (val s = state) {
                UpcomingState.Loading -> Text(stringResource(R.string.upcoming_loading), modifier = Modifier.padding(Dimens.l))
                is UpcomingState.Failed -> {
                    Text(errorMessage(context, s.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Dimens.l))
                    TextButton(onClick = viewModel::load) { Text(stringResource(R.string.upcoming_retry)) }
                }
                is UpcomingState.Ready -> {
                    if (s.total == 0) {
                        Text(stringResource(R.string.upcoming_empty), modifier = Modifier.padding(Dimens.l), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            s.days.forEach { day ->
                                item(key = "day-${day.date}") {
                                    Text(
                                        day.date.format(dateFormat),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.s),
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
                                    CheckRow(check, dateFormat, onClick = { onOpenTitle(check.titleId) })
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
private fun CheckRow(check: PlannedCheck, format: DateTimeFormatter, onClick: () -> Unit) {
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
            java.time.Instant.ofEpochMilli(check.nextCheckAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm")),
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
        UpdateSkip.NO_SOURCE -> R.string.upcoming_reason_no_source
    },
)

@Composable
private fun rememberFormat(): DateTimeFormatter = androidx.compose.runtime.remember {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault())
}
