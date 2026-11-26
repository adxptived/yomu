package app.yomu.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.CoverImage
import app.yomu.core.ui.components.GlassTopBarBackground
import app.yomu.core.ui.components.TopLevelBody
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.glassTopBarColors
import app.yomu.core.ui.components.navBarBottomPadding
import app.yomu.core.ui.components.topLevelContentInsets
import app.yomu.core.ui.theme.Dimens
import app.yomu.domain.model.TrackerPerson
import app.yomu.domain.model.TrackerRecommendation
import app.yomu.domain.model.TrackerSection
import app.yomu.domain.model.TrackerTitleDetails
import app.yomu.domain.tracking.TrackService
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.yomu.core.ui.R as CoreR

/**
 * The public page of a tracker title (C3): what the service shows to everyone, the reader's own entry when signed in,
 * and the provider's characters, staff and recommendations. "Open in Yomu" appears only for a confirmed local link;
 * otherwise the reader can look for a source of the same title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerTitleScreen(
    viewModel: TrackerTitleViewModel,
    onBack: () -> Unit,
    onOpenLocal: (Long) -> Unit,
    onOpenTrackerTitle: (TrackService, Long) -> Unit,
    onFindSource: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenLocalBook: ((Long) -> Unit)? = null,
) {
    val state by viewModel.details.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val staff by viewModel.staff.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backdrop = rememberHazeState()

    Scaffold(
        contentWindowInsets = topLevelContentInsets(),
        topBar = {
            GlassTopBarBackground(backdrop) {
                TopAppBar(
                    colors = glassTopBarColors(),
                    title = { Text(viewModel.service.displayName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreR.string.back)) }
                    },
                )
            }
        },
    ) { padding ->
        TopLevelBody(padding, Modifier.fillMaxSize().hazeSource(backdrop), drawBehindTopBar = true) {
            when (val s = state) {
                TrackerDetailsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is TrackerDetailsState.Failed -> Column(
                    Modifier.fillMaxSize().padding(Dimens.l),
                    verticalArrangement = Arrangement.spacedBy(Dimens.m),
                ) {
                    Text(errorMessage(context, s.error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::load) { Text(stringResource(R.string.tracker_page_retry)) }
                }
                is TrackerDetailsState.Ready -> TrackerTitleContent(
                    state = s,
                    service = viewModel.service,
                    onOpenLocal = onOpenLocal,
                    onOpenLocalBook = onOpenLocalBook,
                    onOpenTrackerTitle = onOpenTrackerTitle,
                    onFindSource = onFindSource,
                    onOpenWeb = onOpenWeb,
                    characters = characters,
                    staff = staff,
                    recommendations = recommendations,
                    onRetrySection = viewModel::loadSection,
                    onMore = viewModel::more,
                    topInset = padding.calculateTopPadding(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackerTitleContent(
    state: TrackerDetailsState.Ready,
    service: TrackService,
    onOpenLocal: (Long) -> Unit,
    onOpenLocalBook: ((Long) -> Unit)?,
    onOpenTrackerTitle: (TrackService, Long) -> Unit,
    onFindSource: (String) -> Unit,
    onOpenWeb: (String) -> Unit,
    characters: TrackerSectionState<TrackerPerson>,
    staff: TrackerSectionState<TrackerPerson>,
    recommendations: TrackerSectionState<TrackerRecommendation>,
    onRetrySection: (TrackerSection, Int) -> Unit,
    onMore: (TrackerSection) -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
) {
    val d = state.details
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, bottom = navBarBottomPadding(Dimens.xl)),
        verticalArrangement = Arrangement.spacedBy(Dimens.s),
    ) {
        item(key = "header") { TrackerHeader(d) }
        item(key = "actions") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Dimens.l),
                horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.localItemId != null) {
                    FilledTonalButton(onClick = { onOpenLocal(state.localItemId) }) {
                        Text(stringResource(R.string.tracker_page_open_local))
                    }
                } else if (state.localBookId != null && onOpenLocalBook != null) {
                    FilledTonalButton(onClick = { onOpenLocalBook(state.localBookId) }) {
                        Text(stringResource(R.string.tracker_page_open_local))
                    }
                } else {
                    FilledTonalButton(onClick = { onFindSource(d.title) }) {
                        Text(stringResource(R.string.tracker_page_find_source))
                    }
                }
                OutlinedButton(onClick = { onOpenWeb(d.url) }) {
                    Icon(Icons.Outlined.Public, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.tracker_page_on_site), Modifier.padding(start = Dimens.s))
                }
            }
        }
        state.track?.let { track ->
            item(key = "personal") {
                // Private fields only with a working account, and read-only here: they are edited in the title sheet.
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.l),
                ) {
                    Column(Modifier.padding(Dimens.l), verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                        Text(stringResource(R.string.tracker_page_my_entry), style = MaterialTheme.typography.titleSmall)
                        val statusText = app.yomu.domain.tracking.TrackStatus.of(track.status).label()
                        val chapters = if (track.totalChapters > 0) {
                            stringResource(R.string.track_chapters_of, track.lastChapterRead.toInt(), track.totalChapters)
                        } else {
                            stringResource(R.string.track_chapters, track.lastChapterRead.toInt())
                        }
                        val score = if (track.score > 0) formatTrackScore(track.score) else null
                        Text(listOfNotNull(statusText, chapters, score).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        d.description?.let { description ->
            item(key = "description") {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.l),
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (d.tags.isNotEmpty()) {
            item(key = "tags") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Dimens.l),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                ) {
                    d.tags.take(14).forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }
            }
        }
        if (TrackerSection.CHARACTERS in d.sections) {
            item(key = "characters") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    SectionTitle(stringResource(R.string.tracker_page_characters))
                    PeopleRow(
                        state = characters,
                        onRetry = { onRetrySection(TrackerSection.CHARACTERS, 1) },
                        onMore = { onMore(TrackerSection.CHARACTERS) },
                    )
                }
            }
        }
        if (TrackerSection.STAFF in d.sections) {
            item(key = "staff") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    SectionTitle(stringResource(R.string.tracker_page_staff))
                    PeopleRow(
                        state = staff,
                        onRetry = { onRetrySection(TrackerSection.STAFF, 1) },
                        onMore = { onMore(TrackerSection.STAFF) },
                    )
                }
            }
        }
        if (TrackerSection.RECOMMENDATIONS in d.sections) {
            item(key = "rec") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    SectionTitle(stringResource(R.string.tracker_page_recommendations))
                    when (val r = recommendations) {
                    TrackerSectionState.Loading -> SectionLoading()
                    is TrackerSectionState.Failed -> SectionError(r.error) { onRetrySection(TrackerSection.RECOMMENDATIONS, 1) }
                    is TrackerSectionState.Ready -> {
                        if (r.items.isEmpty()) {
                            SectionEmpty()
                        } else {
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Dimens.l),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                            ) {
                                r.items.forEach { item ->
                                    Column(
                                        Modifier.width(112.dp).clickable { onOpenTrackerTitle(service, item.id) },
                                        verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
                                    ) {
                                        CoverImage(item.coverUrl, item.title, Modifier.width(112.dp))
                                        Text(item.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        SectionMore(r) { onMore(TrackerSection.RECOMMENDATIONS) }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun TrackerHeader(d: TrackerTitleDetails) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.s),
        horizontalArrangement = Arrangement.spacedBy(Dimens.l),
    ) {
        CoverImage(d.coverUrl, d.title, Modifier.width(120.dp).clip(MaterialTheme.shapes.medium))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Text(d.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            d.altTitles.firstOrNull()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val format = when (d.format?.uppercase()) {
                "MANGA" -> stringResource(R.string.content_manga)
                "NOVEL", "LIGHT_NOVEL" -> stringResource(R.string.tracker_format_novel)
                else -> d.format?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.titlecase() }
            }
            val status = when (d.status?.uppercase()) {
                "RELEASING", "CURRENTLY_PUBLISHING" -> stringResource(R.string.status_ongoing)
                "FINISHED", "FINISHED_PUBLISHING" -> stringResource(R.string.status_completed)
                "HIATUS", "ON_HIATUS" -> stringResource(R.string.status_hiatus)
                "NOT_YET_RELEASED", "NOT_YET_PUBLISHED" -> stringResource(R.string.tracker_status_announced)
                "CANCELLED", "DISCONTINUED" -> stringResource(R.string.tracker_status_discontinued)
                else -> d.status?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.titlecase() }
            }
            val meta = listOfNotNull(format, d.year?.toString(), status)
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            d.score?.let { score ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("${score.toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(start = Dimens.l, end = Dimens.l, top = Dimens.m, bottom = Dimens.xs),
    )
}

@Composable
private fun PeopleRow(
    state: TrackerSectionState<TrackerPerson>,
    onRetry: () -> Unit,
    onMore: () -> Unit,
) {
    when (state) {
        TrackerSectionState.Loading -> SectionLoading()
        is TrackerSectionState.Failed -> SectionError(state.error, onRetry)
        is TrackerSectionState.Ready -> {
            if (state.items.isEmpty()) {
                SectionEmpty()
            } else {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Dimens.l),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.m),
                ) {
                    state.items.forEach { person ->
                        Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                            CoverImage(person.imageUrl, person.name, Modifier.width(84.dp).clip(MaterialTheme.shapes.medium))
                            Text(person.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            person.role?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            SectionMore(state, onMore)
        }
    }
}

@Composable
private fun SectionMore(state: TrackerSectionState.Ready<*>, onMore: () -> Unit) {
    when {
        state.loadingMore -> SectionLoading()
        state.moreError != null -> SectionError(state.moreError, onMore)
        state.hasMore -> TextButton(onClick = onMore, modifier = Modifier.padding(horizontal = Dimens.s)) {
            Text(stringResource(R.string.tracker_page_more))
        }
    }
}

@Composable
private fun SectionLoading() {
    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun SectionEmpty() {
    Text(
        stringResource(R.string.tracker_page_section_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.l),
    )
}

@Composable
private fun SectionError(error: Throwable, onRetry: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s),
    ) {
        Text(
            errorMessage(context, error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.tracker_page_retry)) }
    }
}
