package app.yomu.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.yomu.data.source.SourceManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.BookGridItem
import app.yomu.core.ui.components.ChipItem
import app.yomu.core.ui.components.EmptyState
import app.yomu.core.ui.components.FilterChipsRow
import app.yomu.core.ui.components.SearchField
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.navBarBottomPadding
import app.yomu.core.ui.theme.Dimens
import app.yomu.data.catalog.MigrationCategory
import app.yomu.data.catalog.MigrationPlan
import app.yomu.source.SourceContent
import app.yomu.core.ui.R as CoreR

/** Searches every enabled source at once; in migration mode picking a result moves the reading state there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    onBack: () -> Unit,
    onOpenTitle: (Long) -> Unit,
    onOpenSource: (sourceId: Long, query: String) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val activeQuery by viewModel.activeQuery.collectAsStateWithLifecycle()
    val languages by viewModel.searchLanguages.collectAsStateWithLifecycle()
    val languageCounts by viewModel.languageCounts.collectAsStateWithLifecycle()
    var showLanguages by remember { mutableStateOf(false) }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val migrating by viewModel.migrating.collectAsStateWithLifecycle()
    val migrationPlan by viewModel.migrationPlan.collectAsStateWithLifecycle()
    val migrationCategories by viewModel.migrationCategories.collectAsStateWithLifecycle()
    val infos by viewModel.sourceInfos.collectAsStateWithLifecycle()
    val onlyWithResults by viewModel.onlyWithResults.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }
    CatalogEventsEffect(viewModel.events, snackbar, onOpenTitle)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(if (viewModel.migrateFrom != null) R.string.migrate_title else R.string.global_search)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreR.string.back)) } },
                )
                SearchField(
                    query = query,
                    onQueryChange = { viewModel.query.value = it },
                    placeholder = stringResource(R.string.global_search_hint),
                    modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
                    onSearch = { viewModel.submit() },
                    focusRequester = focus,
                )
                FilterChipsRow(
                    listOf(
                        ChipItem(stringResource(R.string.content_all), content == null, { viewModel.setContent(null) }),
                        ChipItem(stringResource(R.string.content_manga), content == SourceContent.MANGA, { viewModel.setContent(SourceContent.MANGA) }),
                        ChipItem(stringResource(R.string.content_novel), content == SourceContent.NOVEL, { viewModel.setContent(SourceContent.NOVEL) }),
                        ChipItem(stringResource(R.string.content_book), content == SourceContent.BOOK, { viewModel.setContent(SourceContent.BOOK) }),
                        ChipItem(stringResource(R.string.content_audiobooks), content == SourceContent.AUDIO, { viewModel.setContent(SourceContent.AUDIO) }),
                        ChipItem(stringResource(R.string.global_search_only_results), onlyWithResults, { viewModel.setOnlyWithResults(!onlyWithResults) }, Icons.Filled.FilterList),
                    ),
                    modifier = Modifier.padding(vertical = Dimens.xs),
                )
                TextButton(
                    onClick = { showLanguages = true },
                    modifier = Modifier.padding(horizontal = Dimens.s),
                ) {
                    Text(stringResource(R.string.global_search_languages, languagesSummary(languages)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val all = results.orEmpty()
                val answered = all.count { !it.loading }
                when {
                    migrating -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    all.isNotEmpty() && answered < all.size -> LinearProgressIndicator(progress = { answered.toFloat() / all.size }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = results
        if (list == null) {
            LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
            EmptyState(
                title = stringResource(R.string.global_search_empty),
                message = stringResource(if (viewModel.migrateFrom != null) R.string.migrate_hint else R.string.global_search_empty_hint),
                icon = Icons.Outlined.TravelExplore,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            return@Scaffold
        }
        val shown = if (onlyWithResults) list.filter { it.loading || it.items.isNotEmpty() } else list
        val answered = list.count { !it.loading }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = navBarBottomPadding(Dimens.xl))) {
            if (list.isEmpty()) item(key = "no-sources") { EmptyState(title = stringResource(R.string.sources_empty), modifier = Modifier.fillMaxWidth()) }
            if (list.isNotEmpty()) {
                item(key = "summary") {
                    Text(
                        stringResource(R.string.global_search_progress, answered, list.size, list.sumOf { it.items.size }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
                    )
                }
            }
            if (list.isNotEmpty() && shown.isEmpty()) {
                item(key = "nothing") { EmptyState(title = stringResource(R.string.global_search_nothing), modifier = Modifier.fillMaxWidth().padding(top = Dimens.xl)) }
            }
            items(shown, key = { it.sourceId }) { r ->
                Column(Modifier.fillMaxWidth().padding(vertical = Dimens.s)) {
                    Row(Modifier.fillMaxWidth().padding(start = Dimens.l), verticalAlignment = Alignment.CenterVertically) {
                        infos[r.sourceId]?.let { info -> SourceIcon(info, Modifier.padding(end = Dimens.m), size = 32.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(r.sourceName, style = MaterialTheme.typography.titleSmall)
                            val base = when {
                                r.loading -> stringResource(R.string.global_search_loading)
                                r.error != null -> errorMessage(r.error!!)
                                r.items.isEmpty() -> stringResource(R.string.browse_nothing)
                                else -> stringResource(R.string.global_search_found, r.items.size)
                            }
                            val unstable = !r.loading && health[r.sourceId]?.unstable == true
                            val status = if (unstable) base + " · " + stringResource(R.string.source_unstable) else base
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (r.loading) CircularProgressIndicator(Modifier.size(20.dp).padding(end = Dimens.s), strokeWidth = 2.dp)
                        if (!r.loading && r.error != null) {
                            IconButton(onClick = { viewModel.retry(r.sourceId) }) { Icon(Icons.Filled.Refresh, stringResource(CoreR.string.retry)) }
                        }
                        IconButton(onClick = { onOpenSource(r.sourceId, activeQuery) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.global_search_open_source))
                        }
                    }
                    if (r.items.isNotEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = Dimens.l), horizontalArrangement = Arrangement.spacedBy(Dimens.m)) {
                            items(r.items, key = { it.url }) { item ->
                                Box(Modifier.width(112.dp)) {
                                    BookGridItem(
                                        title = item.title,
                                        subtitle = item.author,
                                        cover = item.thumbnailUrl,
                                        progress = 0f,
                                        onClick = { if (!migrating) viewModel.open(r.sourceId, item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLanguages) {
        SourceLanguagesSheet(
            all = remember(languageCounts) {
                languageCounts.keys.filter { it.isNotBlank() && it != "all" }.sortedBy(::languageName)
            },
            counts = languageCounts,
            selected = languages,
            onToggle = { viewModel.toggleSearchLanguage(it) },
            onAll = { viewModel.setSearchLanguages(setOf(SourceManager.ALL_LANGUAGES)) },
            onDefault = { viewModel.setSearchLanguages(emptySet()) },
            onDismiss = { showLanguages = false },
        )
    }

    migrationPlan?.let { plan ->
        MigrationPreviewDialog(
            plan = plan,
            categories = migrationCategories,
            busy = migrating,
            onToggle = viewModel::toggleMigrationCategory,
            onApply = viewModel::applyMigration,
            onCancel = viewModel::cancelMigration,
        )
    }
}

/** The preview of a source migration: what will move, how sure the matching is, and what stays behind. */
@Composable
private fun MigrationPreviewDialog(
    plan: MigrationPlan,
    categories: Set<MigrationCategory>,
    busy: Boolean,
    onToggle: (MigrationCategory) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(stringResource(R.string.migrate_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
                Text(stringResource(R.string.migrate_preview_target, plan.fromTitle, plan.targetTitle), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.migrate_preview_counts, plan.exact.size, plan.likely.size, plan.ambiguous.size, plan.missing.size),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (plan.matched.any { !it.pagePositionSafe }) {
                    Text(
                        stringResource(R.string.migrate_preview_page_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.migrate_preview_old_kept),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MigrationCategory.entries.forEach { category ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(category) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = category in categories, onCheckedChange = { onToggle(category) })
                        Text(migrationCategoryLabel(category))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply, enabled = !busy) { Text(stringResource(R.string.migrate_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.migrate_cancel)) }
        },
    )
}

@Composable
private fun migrationCategoryLabel(category: MigrationCategory): String = when (category) {
    MigrationCategory.PROGRESS -> stringResource(R.string.migrate_category_progress)
    MigrationCategory.BOOKMARKS -> stringResource(R.string.migrate_category_bookmarks)
    MigrationCategory.TABS -> stringResource(R.string.migrate_category_tabs)
    MigrationCategory.FAVORITE -> stringResource(R.string.migrate_category_favorite)
}
