package app.yomu.feature.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.ui.components.BookGridItem
import app.yomu.core.ui.components.BookListItem
import app.yomu.core.ui.components.CircleIconButton
import app.yomu.core.ui.components.ConfirmDialog
import app.yomu.core.ui.components.EmptyState
import app.yomu.core.ui.components.GlassTopBarBackground
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.glassTopBarColors
import app.yomu.core.ui.components.PillTab
import app.yomu.core.ui.components.PillTabRow
import app.yomu.core.ui.components.SearchField
import app.yomu.core.ui.components.SheetAction
import app.yomu.core.ui.components.SheetTitle
import app.yomu.core.ui.components.SkeletonList
import app.yomu.core.ui.components.TabActionsSheet
import app.yomu.core.ui.components.TabOption
import app.yomu.core.ui.components.TabsPickerSheet
import app.yomu.core.ui.components.TextInputDialog
import app.yomu.core.ui.components.ToolbarPill
import app.yomu.core.ui.components.TopLevelBody
import app.yomu.core.ui.components.YomuSheet
import app.yomu.core.ui.components.YomuTab
import app.yomu.core.ui.components.motionItem
import app.yomu.core.ui.components.navBarBottomPadding
import app.yomu.core.ui.components.topLevelContentInsets
import app.yomu.core.ui.theme.Dimens
import app.yomu.data.catalog.HistoryEntry
import app.yomu.data.source.SourceHealth
import app.yomu.data.source.SourceInfo
import app.yomu.domain.catalog.LibraryDisplay
import app.yomu.domain.catalog.LibrarySortMode
import app.yomu.domain.catalog.SourceListFilter
import app.yomu.domain.catalog.SourceSortMode
import app.yomu.domain.model.BookCollection
import app.yomu.domain.model.CatalogEntry
import app.yomu.source.SourceContent
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.yomu.core.ui.R as CoreR

private enum class HomeTab { SOURCES, LIBRARY, UPDATES, HISTORY }

/** Catalog home: sources, the online library (favourites), updates feed and reading history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogHomeViewModel,
    onOpenSource: (Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
    onReadChapter: (Long) -> Unit,
    onGlobalSearch: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenWebView: (String) -> Unit = {},
    onOpenSourceSettings: (Long) -> Unit = {},
    /** The connection wizard: what "Add" leads to (S01). */
    onAddSource: () -> Unit = {},
    /** The plan of the next automatic checks (U02). */
    onOpenUpdatesPlan: () -> Unit = {},
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val sourceListing by viewModel.sourceListing.collectAsStateWithLifecycle()
    val langs by viewModel.languages.collectAsStateWithLifecycle()
    val allLangs by viewModel.allLanguages.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val libraryTotal by viewModel.libraryTotal.collectAsStateWithLifecycle()
    val libraryDisplay by viewModel.libraryDisplay.collectAsStateWithLifecycle()
    val sourceQuery by viewModel.sourceQuery.collectAsStateWithLifecycle()
    val sourceSort by viewModel.sourceSort.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()
    val languageCounts by viewModel.languageCounts.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val tabMembers by viewModel.tabMembers.collectAsStateWithLifecycle()
    val updates by viewModel.updates.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val newCount by viewModel.newCount.collectAsStateWithLifecycle()
    val extensionUpdates by viewModel.extensionUpdates.collectAsStateWithLifecycle()
    val incognito by viewModel.incognito.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    // Pencil of the Sources tab: pick many sources and pin, enable or hide them at once.
    var editingSources by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tab) { if (HomeTab.entries[tab] != HomeTab.SOURCES) editingSources = false }
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::configureLocal) }

    CatalogEventsEffect(viewModel.events, snackbar, onOpenTitle, onOpenWebView = onOpenWebView)

    val topBarBackdrop = rememberHazeState()
    Scaffold(
        contentWindowInsets = topLevelContentInsets(),
        topBar = {
            GlassTopBarBackground(topBarBackdrop) { Column {
                TopAppBar(
                    colors = glassTopBarColors(),
                    title = { Text(stringResource(R.string.catalog_title)) },
                    actions = {
                        IconButton(onClick = onGlobalSearch) {
                            Icon(Icons.Filled.Search, stringResource(R.string.global_search))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(CoreR.string.more)) }
                            DropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(CoreR.string.incognito_mode)) },
                                    leadingIcon = { Icon(if (incognito) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility, null) },
                                    trailingIcon = {
                                        Switch(checked = incognito, onCheckedChange = null)
                                    },
                                    onClick = { showMenu = false; viewModel.setIncognito(!incognito) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.catalog_downloads)) },
                                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                                    onClick = { showMenu = false; onOpenDownloads() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.catalog_local_folder)) },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                                    onClick = { showMenu = false; pickFolder.launch(null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.catalog_check_updates)) },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                    onClick = { showMenu = false; viewModel.checkUpdates() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.upcoming_title)) },
                                    leadingIcon = { Icon(Icons.Outlined.NewReleases, null) },
                                    onClick = { showMenu = false; onOpenUpdatesPlan() },
                                )
                            }
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, divider = {}) {
                    HomeTab.entries.forEachIndexed { i, t ->
                        YomuTab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = {
                                when (t) {
                                    HomeTab.SOURCES -> Text(stringResource(R.string.catalog_tab_sources))
                                    HomeTab.LIBRARY -> Text(stringResource(R.string.catalog_tab_library))
                                    HomeTab.UPDATES -> BadgedBox(
                                        badge = {
                                            if (newCount > 0) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                                    Text(newCount.coerceAtMost(99).toString())
                                                }
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.catalog_tab_updates))
                                    }
                                    HomeTab.HISTORY -> Text(stringResource(R.string.catalog_tab_history))
                                }
                            },
                        )
                    }
                }
            } }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        TopLevelBody(padding, Modifier.fillMaxSize().hazeSource(topBarBackdrop), drawBehindTopBar = true) {
            when (HomeTab.entries[tab]) {
                HomeTab.SOURCES -> SourcesTab(
                    topInset = padding.calculateTopPadding(),
                    ready = ready,
                    listing = sourceListing,
                    query = sourceQuery,
                    sort = sourceSort,
                    health = health,
                    langs = langs,
                    allLangs = allLangs,
                    filter = sourceFilter,
                    languageCounts = languageCounts,
                    viewModel = viewModel,
                    onOpenSource = { s ->
                        if (s.kind == SourceInfo.Kind.LOCAL && !viewModel.localConfigured) pickFolder.launch(null) else onOpenSource(s.id)
                    },
                    onAddSource = onAddSource,
                    onPickFolder = { pickFolder.launch(null) },
                    onOpenExtensions = onOpenExtensions,
                    onOpenWebView = onOpenWebView,
                    onOpenSourceSettings = onOpenSourceSettings,
                    editing = editingSources,
                    onToggleEditing = { editingSources = !editingSources },
                    extensionUpdates = extensionUpdates,
                )
                HomeTab.LIBRARY -> LibraryTab(favorites, libraryTotal, libraryDisplay, tabs, tabMembers, viewModel, onOpenTitle, onBrowse = { tab = 0 }, topInset = padding.calculateTopPadding())
                HomeTab.UPDATES -> {
                    var filter by remember { mutableStateOf(app.yomu.domain.catalog.HistoryFilter()) }
                    val shown = remember(updates, filter) { updates.filter { filter.matches(it.toViewItem()) } }
                    FeedTab(
                        entries = shown,
                        emptyTitle = stringResource(R.string.updates_empty),
                        emptyIcon = Icons.Outlined.NewReleases,
                        topInset = padding.calculateTopPadding(),
                        header = { hasEntries ->
                            OutlinedButton(onClick = viewModel::checkUpdates, modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.s)) {
                                Icon(Icons.Filled.Refresh, null)
                                Text(stringResource(R.string.catalog_check_updates), Modifier.padding(start = Dimens.s))
                            }
                            if (hasEntries) HistoryFilterRow(filter) { filter = it }
                        },
                        onClick = { onReadChapter(it.chapter.id) },
                        onOpenTitle = { onOpenTitle(it.chapter.entryId) },
                        onRemove = null,
                        onMarkRead = viewModel::setChaptersRead,
                        onDownload = viewModel::downloadChapters,
                        onRemoveSelected = null,
                    )
                }
                HomeTab.HISTORY -> {
                    var confirmClear by remember { mutableStateOf(false) }
                    var filter by remember { mutableStateOf(app.yomu.domain.catalog.HistoryFilter()) }
                    val shown = remember(history, filter) { history.filter { filter.matches(it.toViewItem()) } }
                    FeedTab(
                        entries = shown,
                        emptyTitle = stringResource(R.string.history_empty),
                        emptyIcon = Icons.Outlined.History,
                        topInset = padding.calculateTopPadding(),
                        header = { hasEntries ->
                            if (history.isNotEmpty()) {
                                TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = Dimens.s)) {
                                    Icon(Icons.Filled.DeleteSweep, null)
                                    Text(stringResource(R.string.history_clear), Modifier.padding(start = Dimens.s))
                                }
                            }
                            if (hasEntries) HistoryFilterRow(filter) { filter = it }
                        },
                        onClick = { onReadChapter(it.chapter.id) },
                        onOpenTitle = { onOpenTitle(it.chapter.entryId) },
                        onRemove = { viewModel.removeHistory(it.chapter.id) },
                        onMarkRead = viewModel::setChaptersRead,
                        onDownload = viewModel::downloadChapters,
                        onRemoveSelected = viewModel::removeHistory,
                    )
                    if (confirmClear) {
                        ConfirmDialog(
                            title = stringResource(R.string.history_clear),
                            text = stringResource(R.string.history_clear_confirm),
                            confirmLabel = stringResource(CoreR.string.clear),
                            onConfirm = viewModel::clearHistory,
                            onDismiss = { confirmClear = false },
                        )
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SourcesTab(
    topInset: androidx.compose.ui.unit.Dp,
    ready: Boolean,
    listing: CatalogSourceListing,
    query: String,
    sort: SourceSortMode,
    health: Map<Long, SourceHealth>,
    langs: Set<String>,
    allLangs: List<String>,
    filter: SourceListFilter,
    languageCounts: Map<String, Int>,
    viewModel: CatalogHomeViewModel,
    onOpenSource: (SourceInfo) -> Unit,
    onAddSource: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenWebView: (String) -> Unit,
    onOpenSourceSettings: (Long) -> Unit,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    extensionUpdates: Int,
) {
    val sources = listing.sources
    var menuFor by remember { mutableStateOf<SourceInfo?>(null) }
    var removeFor by remember { mutableStateOf<SourceInfo?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulk by remember { mutableStateOf(false) }
    val refreshing by viewModel.refreshingSources.collectAsStateWithLifecycle()
    val checkingSources by viewModel.checkingSources.collectAsStateWithLifecycle()
    val installedAdultSourcesHidden by viewModel.installedAdultSourcesHidden.collectAsStateWithLifecycle()
    val visibleIds = remember(sources) { sources.mapTo(HashSet()) { it.id } }
    val selectedVisible = remember(selected, visibleIds) { selected.intersect(visibleIds) }
    BackHandler(enabled = editing && !showBulk) { onToggleEditing() }
    LaunchedEffect(visibleIds) {
        selected = selected.intersect(visibleIds)
        if (selected.isEmpty()) showBulk = false
    }
    LaunchedEffect(editing) { if (!editing) { selected = emptySet(); showBulk = false } }
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    if (!ready) {
        SkeletonList(Modifier.fillMaxSize())
        return
    }
    // Split once per list, not on every recomposition of the tab.
    val (external, remaining) = remember(sources, editing, query) {
        if (editing || query.isNotBlank()) emptyList<SourceInfo>() to sources
        else sources.partition { it.externalExtension }
    }
    val (pinned, others) = remember(remaining) { remaining.partition { it.pinned } }
    val filtered = !listing.showingExcluded && listing.excludedCount > 0
    // One section per language while the list mixes many of them (an extension ships a source per language, and a
    // flat multilingual list reads as random duplicates); a search and the bulk editor stay flat.
    val universalLabel = stringResource(R.string.catalog_section_universal)
    val otherLabel = stringResource(R.string.catalog_section_other)
    val grouping = !editing && query.isEmpty() && !filter.isActive &&
        others.map { it.lang }.distinct().size > 1
    val hasLeadingSection = pinned.isNotEmpty() || external.isNotEmpty()
    val sections: List<Triple<String, String?, List<SourceInfo>>> = remember(others, grouping, allLangs, universalLabel, otherLabel, hasLeadingSection) {
        if (!grouping) {
            val flatHeader = if (hasLeadingSection && others.isNotEmpty()) otherLabel else null
            listOf(Triple("all", flatHeader, others))
        } else {
            val universal = others.filter { SourceVariants.isMultiLang(it.lang) || it.kind == SourceInfo.Kind.LOCAL || it.kind == SourceInfo.Kind.OPDS }
            val byLang = (others - universal.toSet()).groupBy { it.lang }
            val ordered = byLang.entries
                .sortedBy { (lang, _) -> allLangs.indexOf(lang).takeIf { it >= 0 } ?: Int.MAX_VALUE }
                .map { (lang, list) -> Triple(lang, languageName(lang), list) }
            buildList {
                if (universal.isNotEmpty()) add(Triple("universal", universalLabel, universal))
                addAll(ordered)
            }
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (!editing) viewModel.refreshSources() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset, bottom = navBarBottomPadding(Dimens.xl)),
        ) {
            item(key = "source-actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!editing) {
                        FilledTonalButton(onClick = onAddSource, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.connection_title), Modifier.padding(start = Dimens.xs))
                        }
                        TextButton(onClick = onOpenExtensions) {
                            Icon(Icons.Filled.Extension, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.ext_title), Modifier.padding(start = Dimens.xs))
                            if (extensionUpdates > 0) {
                                Badge(Modifier.padding(start = Dimens.xs)) { Text(extensionUpdates.coerceAtMost(99).toString()) }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (editing || sources.isNotEmpty()) {
                        // The bulk editor as a tonal circle: the same look as the other header buttons, so a bare icon
                        // does not hide a mode switch among two labelled buttons.
                        CircleIconButton(
                            if (editing) Icons.Filled.Check else Icons.Outlined.Edit,
                            stringResource(if (editing) R.string.source_bulk_done else R.string.source_bulk_edit),
                            onToggleEditing,
                        )
                    }
                }
            }
            if (sources.isNotEmpty() || filtered || query.isNotEmpty() || listing.showingExcluded) {
                item(key = "tools") {
                    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.xs)) {
                        SearchField(
                            query = query,
                            onQueryChange = { viewModel.sourceQuery.value = it },
                            placeholder = stringResource(R.string.sources_search_hint),
                            modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
                        )
                        SourceFilterBar(
                            filter = filter,
                            langs = langs,
                            sort = sort,
                            listing = listing,
                            searching = query.isNotBlank(),
                            onToggleExcluded = { viewModel.showExcludedSources(!listing.showingExcluded) },
                            onOpenFilters = { viewModel.showExcludedSources(false); showFilters = true },
                            onOpenLanguages = { viewModel.showExcludedSources(false); showLanguages = true },
                            onUpdate = { viewModel.updateSourceFilter(it) },
                            modifier = Modifier.padding(top = Dimens.xs),
                        )
                    }
                }
            }
            if (!editing && installedAdultSourcesHidden) {
                item(key = "safe-content") {
                    TextButton(
                        onClick = { viewModel.showInstalledAdultSources() },
                        modifier = Modifier.padding(horizontal = Dimens.l),
                    ) {
                        Text(stringResource(R.string.catalog_show_installed_adult))
                    }
                }
            }
            if (sources.isEmpty() && query.isNotEmpty()) {
                item(key = "not-found") {
                    EmptyState(
                        title = stringResource(R.string.sources_not_found, query),
                        icon = Icons.Filled.Search,
                        actionLabel = stringResource(CoreR.string.clear),
                        onAction = { viewModel.sourceQuery.value = "" },
                        message = if (filtered) stringResource(R.string.source_filter_empty) else null,
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.xl),
                    )
                }
            } else if (sources.isEmpty() && filtered) {
                item(key = "filtered-empty") {
                    EmptyState(
                        title = stringResource(R.string.source_filter_empty),
                        icon = Icons.Filled.FilterList,
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.xl),
                    )
                }
            } else if (sources.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.sources_empty),
                        message = stringResource(R.string.sources_empty_hint),
                        icon = Icons.Outlined.Public,
                        actionLabel = stringResource(R.string.ext_title),
                        onAction = onOpenExtensions,
                    )
                }
            }
            if (editing && sources.isNotEmpty()) {
                item(key = "bulk-bar") { SourceBulkBar(selectedVisible.size, onSelectAll = { selected = visibleIds }, onClear = { selected = emptySet() }, onActions = { showBulk = true }, modifier = Modifier.animateItem()) }
            }
            val row: @Composable LazyItemScope.(SourceInfo) -> Unit = { s ->
                SourceRow(
                    s = s,
                    health = health[s.id],
                    checking = s.id in checkingSources,
                    editing = editing,
                    selected = s.id in selectedVisible,
                    onOpen = { if (editing) toggle(s.id) else onOpenSource(s) },
                    onMenu = { menuFor = s },
                    onPin = { viewModel.setPinned(s.id, !s.pinned) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (external.isNotEmpty()) {
                item(key = "h-external") { ListHeader(stringResource(R.string.catalog_section_external), Modifier.animateItem()) }
                items(external, key = { it.id }) { row(it) }
            }
            if (pinned.isNotEmpty()) {
                item(key = "h-pinned") { ListHeader(stringResource(R.string.catalog_section_pinned), Modifier.animateItem()) }
                items(pinned, key = { it.id }) { row(it) }
            }
            sections.forEach { (key, label, list) ->
                if (label != null && list.isNotEmpty()) {
                    item(key = "h-$key") { ListHeader("$label · ${list.size}", Modifier.animateItem()) }
                }
                items(list, key = { it.id }) { row(it) }
            }
        }
    }

    if (showFilters) {
        SourceFilterSheet(
            filter = filter,
            sort = sort,
            onSort = { viewModel.setSourceSort(it) },
            onUpdate = { viewModel.updateSourceFilter(it) },
            onReset = {
                viewModel.resetSourceFilters()
                showFilters = false
            },
            onUnhideAll = {
                viewModel.unhideAllSources()
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
    if (showLanguages) {
        SourceLanguagesSheet(
            all = allLangs,
            counts = languageCounts,
            selected = langs,
            onToggle = { viewModel.toggleLanguage(it) },
            onAll = { viewModel.clearLanguages() },
            onDefault = { viewModel.defaultLanguages() },
            onDismiss = { showLanguages = false },
        )
    }
    menuFor?.let { s ->
        YomuSheet(onDismiss = { menuFor = null }) {
            SheetTitle(s.name)
            if (s.kind != SourceInfo.Kind.LOCAL && (s.capabilities.popular || s.capabilities.latest) && s.id !in checkingSources) {
                SheetAction(Icons.Filled.Refresh, stringResource(R.string.source_check_availability)) {
                    menuFor = null
                    viewModel.checkSourceAvailability(s)
                }
            }
            s.webUrl?.let { url ->
                SheetAction(Icons.Filled.OpenInBrowser, stringResource(R.string.source_open_browser)) {
                    menuFor = null
                    onOpenWebView(url)
                }
            }
            if (s.hasSettings) {
                SheetAction(Icons.Outlined.Tune, stringResource(R.string.source_settings)) {
                    menuFor = null
                    onOpenSourceSettings(s.id)
                }
            }
            SheetAction(if (s.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, stringResource(if (s.pinned) R.string.source_unpin else R.string.source_pin)) {
                viewModel.setPinned(s.id, !s.pinned); menuFor = null
            }
            SheetAction(if (s.enabled) Icons.Outlined.Block else Icons.Outlined.CheckCircle, stringResource(if (s.enabled) R.string.source_disable else R.string.source_enable)) {
                viewModel.setEnabled(s.id, !s.enabled); menuFor = null
            }
            if (!s.extensionHidden || s.individuallyHidden) {
                SheetAction(
                    if (s.individuallyHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    stringResource(if (s.individuallyHidden) R.string.source_unhide else R.string.source_hide),
                ) {
                    viewModel.setHidden(s.id, !s.individuallyHidden)
                    menuFor = null
                }
            }
            s.extensionPkg?.let { pkg ->
                SheetAction(
                    if (s.extensionHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    stringResource(
                        if (s.extensionHidden) R.string.source_unhide_extension else R.string.source_hide_extension,
                        s.extensionName ?: pkg,
                    ),
                ) {
                    viewModel.setExtensionHidden(pkg, !s.extensionHidden)
                    menuFor = null
                }
            }
            if (s.kind == SourceInfo.Kind.LOCAL) {
                SheetAction(Icons.Filled.Folder, stringResource(R.string.catalog_local_folder)) { menuFor = null; onPickFolder() }
            }
            if (s.kind == SourceInfo.Kind.OPDS) {
                SheetAction(Icons.Filled.Delete, stringResource(R.string.source_remove), destructive = true) { removeFor = s; menuFor = null }
            }
        }
    }
    removeFor?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.source_remove),
            text = stringResource(R.string.source_remove_confirm, s.name),
            confirmLabel = stringResource(CoreR.string.delete),
            onConfirm = { viewModel.removeOpds(s.id) },
            onDismiss = { removeFor = null },
        )
    }
    if (showBulk && selectedVisible.isNotEmpty()) {
        YomuSheet(onDismiss = { showBulk = false }) {
            SheetTitle(stringResource(R.string.source_bulk_title, selectedVisible.size))
            fun applyToSelection(block: (Set<Long>) -> Unit) { block(selectedVisible); selected = emptySet(); showBulk = false }
            SheetAction(Icons.Filled.PushPin, stringResource(R.string.source_bulk_pin)) {
                applyToSelection { viewModel.setPinned(it, true) }
            }
            SheetAction(Icons.Outlined.PushPin, stringResource(R.string.source_bulk_unpin)) {
                applyToSelection { viewModel.setPinned(it, false) }
            }
            SheetAction(Icons.Outlined.CheckCircle, stringResource(R.string.source_bulk_enable)) {
                applyToSelection { viewModel.setEnabled(it, true) }
            }
            SheetAction(Icons.Outlined.Block, stringResource(R.string.source_bulk_disable)) {
                applyToSelection { viewModel.setEnabled(it, false) }
            }
            SheetAction(Icons.Outlined.VisibilityOff, stringResource(R.string.source_bulk_hide)) {
                applyToSelection { viewModel.setHidden(it, true) }
            }
            val selectedSources = sources.filter { it.id in selectedVisible }
            val individuallyHidden = selectedSources.filter { it.individuallyHidden }.map { it.id }
            if (individuallyHidden.isNotEmpty()) {
                SheetAction(Icons.Outlined.Visibility, stringResource(R.string.source_bulk_show)) {
                    applyToSelection { viewModel.setHidden(individuallyHidden, false) }
                }
            }
            val hiddenPackages = selectedSources.filter { it.extensionHidden }.mapNotNull { it.extensionPkg }.toSet()
            if (hiddenPackages.isNotEmpty()) {
                SheetAction(Icons.Outlined.Visibility, stringResource(R.string.source_bulk_show_extensions)) {
                    applyToSelection { viewModel.setExtensionsHidden(hiddenPackages, false) }
                }
            }
            val extensionsToHide = selectedSources.filterNot { it.extensionHidden }.mapNotNull { it.extensionPkg }.toSet()
            if (extensionsToHide.isNotEmpty()) {
                SheetAction(Icons.Outlined.VisibilityOff, stringResource(R.string.source_bulk_hide_extensions)) {
                    applyToSelection { viewModel.setExtensionsHidden(extensionsToHide, true) }
                }
            }
        }
    }
}

/** "Select all / Actions" row of the source bulk editor. */
@Composable
private fun SourceBulkBar(selected: Int, onSelectAll: () -> Unit, onClear: () -> Unit, onActions: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
    ) {
        Text(stringResource(R.string.source_bulk_selected, selected), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.source_bulk_select_all)) }
        TextButton(onClick = onClear) { Text(stringResource(R.string.source_bulk_clear)) }
        TextButton(onClick = onActions, enabled = selected > 0) { Text(stringResource(R.string.source_bulk_actions)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(
    s: SourceInfo,
    health: SourceHealth?,
    checking: Boolean,
    editing: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = listOfNotNull(
        s.lang.takeIf { it.isNotBlank() && s.kind != SourceInfo.Kind.LOCAL }?.let { langLabel(it, single = true) },
        if (!s.enabled) stringResource(R.string.source_disabled) else null,
        if (s.hidden) stringResource(R.string.source_hidden) else null,
    )
    val lastError = health?.lastError
    val warning = when {
        checking -> null
        lastError != null -> errorMessage(lastError)
        health?.slow == true -> stringResource(R.string.source_slow)
        else -> null
    }
    val availability = when {
        checking -> stringResource(R.string.source_check_running)
        s.kind != SourceInfo.Kind.LOCAL && warning == null && (health?.lastSuccessAt ?: 0L) > 0L -> stringResource(R.string.source_responding)
        else -> null
    }
    val subtitle = (details + listOfNotNull(availability)).joinToString(" · ")
    val errorColor = MaterialTheme.colorScheme.error
    ListItem(
        headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                // The badges are labels, not buttons: filtering by content and kind lives in the filters sheet,
                // and a tappable badge was far below the 48 dp touch target.
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                    SourceContentBadge(s)
                    SourceKindBadge(s.kind)
                    if (s.isNsfw) {
                        SourceBadge(stringResource(R.string.ext_nsfw), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (subtitle.isNotEmpty() || warning != null) {
                    Text(
                        buildAnnotatedString {
                            append(subtitle)
                            if (warning != null) {
                                if (subtitle.isNotEmpty()) append(" · ")
                                withStyle(SpanStyle(color = errorColor)) { append(warning) }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Language, kind and the availability of long names wrap instead of hiding behind an ellipsis.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingContent = { SourceIcon(s) },
        trailingContent = {
            if (editing) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPin) {
                        Icon(
                            if (s.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            stringResource(if (s.pinned) R.string.source_unpin else R.string.source_pin),
                            tint = if (s.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, stringResource(CoreR.string.more)) }
                }
            }
        },
        modifier = modifier
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else Color.Transparent)
            .alpha(if (s.enabled && !s.hidden) 1f else 0.55f)
            .combinedClickable(onClick = onOpen, onLongClick = if (editing) onOpen else onMenu),
    )
}

/** "Manga" / "Novels" / "Books" / "Audiobooks" in a colour of its own: the kind of a source is seen at once. */
@Composable
private fun SourceContentBadge(s: SourceInfo) {
    val (label, icon) = when {
        s.audio -> stringResource(R.string.content_audiobooks) to Icons.Filled.Headphones
        s.content == SourceContent.MANGA -> contentLabel(LocalContext.current, s.content) to Icons.Outlined.AutoStories
        s.content == SourceContent.NOVEL -> contentLabel(LocalContext.current, s.content) to Icons.AutoMirrored.Outlined.Article
        else -> contentLabel(LocalContext.current, s.content) to Icons.AutoMirrored.Outlined.MenuBook
    }
    val (container, content) = when {
        s.audio -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        s.content == SourceContent.MANGA -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        s.content == SourceContent.NOVEL -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    SourceBadge(label, container, content, icon)
}

@Composable
private fun SourceKindBadge(kind: SourceInfo.Kind) {
    val label = when (kind) {
        SourceInfo.Kind.BUILT_IN -> stringResource(R.string.source_kind_builtin)
        SourceInfo.Kind.EXTENSION -> stringResource(R.string.source_kind_extension)
        SourceInfo.Kind.OPDS -> "OPDS"
        SourceInfo.Kind.LOCAL -> stringResource(R.string.source_kind_local)
    }
    SourceBadge(label, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SourceBadge(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(container)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint = content)
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

@Composable
private fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(start = Dimens.l, end = Dimens.l, top = Dimens.m, bottom = Dimens.xs),
    )
}

@Composable
internal fun LibraryTab(
    favorites: List<CatalogEntry>,
    total: Int,
    display: LibraryDisplay,
    tabs: List<BookCollection>,
    tabMembers: Map<Long, Set<Long>>,
    viewModel: CatalogHomeViewModel,
    onOpenTitle: (Long) -> Unit,
    onBrowse: () -> Unit,
    query: String = "",
    showTabs: Boolean = true,
    topInset: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var tabsFor by remember { mutableStateOf<CatalogEntry?>(null) }
    var createTab by remember { mutableStateOf(false) }
    var editTab by remember { mutableStateOf<BookCollection?>(null) }
    var showDisplay by remember { mutableStateOf(false) }
    // A deleted tab falls back to "All".
    if (tab != null && tabs.none { it.id == tab }) tab = null

    if (total == 0) {
        EmptyState(
            title = stringResource(R.string.online_library_empty),
            message = stringResource(R.string.online_library_empty_hint),
            icon = Icons.Outlined.CollectionsBookmark,
            actionLabel = stringResource(R.string.catalog_tab_sources),
            onAction = onBrowse,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val q = query.trim()
    val activeTab = tab.takeIf { showTabs }
    val shown = remember(favorites, activeTab, tabMembers, q) {
        favorites.filter { e ->
            (activeTab == null || tabMembers[e.id]?.contains(activeTab) == true) &&
                (q.isEmpty() || e.title.contains(q, ignoreCase = true) || e.author?.contains(q, ignoreCase = true) == true)
        }
    }
    Column(Modifier.fillMaxSize().padding(top = topInset)) {
        if (showTabs) {
            PillTabRow(
                tabs = listOf(PillTab("all", stringResource(R.string.category_all), total)) +
                    tabs.map { t -> PillTab(t.id.toString(), t.name, tabMembers.values.count { t.id in it }) },
                selected = activeTab?.toString() ?: "all",
                onSelect = { key -> tab = key.toLongOrNull() },
                onLongPress = { key -> key.toLongOrNull()?.let { id -> editTab = tabs.firstOrNull { it.id == id } } },
                addLabel = stringResource(CoreR.string.tabs_new),
                onAdd = { createTab = true },
                modifier = Modifier.padding(top = Dimens.s),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.l, vertical = Dimens.s),
            horizontalArrangement = Arrangement.spacedBy(Dimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val active = listOf(display.unreadOnly, display.downloadedOnly, display.completedOnly).count { it }
            ToolbarPill(Icons.Filled.FilterList, stringResource(R.string.library_filter_short), onClick = { showDisplay = true }, badge = active)
            ToolbarPill(
                if (display.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                librarySortLabel(display.sort),
                onClick = { showDisplay = true },
            )
        }
        if (shown.isEmpty() && display.isFiltered) {
            EmptyState(
                title = stringResource(R.string.library_filtered_empty),
                actionLabel = stringResource(R.string.chapter_filters_reset),
                onAction = { viewModel.updateLibraryDisplay { it.copy(unreadOnly = false, downloadedOnly = false, completedOnly = false) } },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (shown.isEmpty() && q.isNotEmpty()) {
            EmptyState(title = stringResource(R.string.online_library_nothing_found), icon = Icons.Filled.Search, modifier = Modifier.fillMaxSize())
        } else if (shown.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.tab_empty_online),
                message = stringResource(R.string.tab_empty_online_hint),
                icon = Icons.Outlined.CollectionsBookmark,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(Dimens.gridMin),
                contentPadding = PaddingValues(start = Dimens.m, end = Dimens.m, top = Dimens.xs, bottom = navBarBottomPadding(96.dp)),
                horizontalArrangement = Arrangement.spacedBy(Dimens.m),
                verticalArrangement = Arrangement.spacedBy(Dimens.m),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { e ->
                    BookGridItem(
                        title = e.title,
                        subtitle = e.author ?: contentLabel(context, e.type),
                        cover = e.coverUrl,
                        progress = 0f,
                        badge = if (e.unread > 0) e.unread.toString() else null,
                        modifier = motionItem(),
                        onClick = { onOpenTitle(e.id) },
                        onLongClick = { menuFor = e },
                    )
                }
            }
        }
    }

    if (showDisplay) {
        LibraryDisplaySheet(display, viewModel, onDismiss = { showDisplay = false })
    }
    menuFor?.let { e ->
        YomuSheet(onDismiss = { menuFor = null }) {
            SheetTitle(e.title)
            SheetAction(Icons.Filled.Bookmarks, stringResource(R.string.tabs_action)) { tabsFor = e; menuFor = null }
            if (e.unread > 0) {
                SheetAction(Icons.Filled.Download, stringResource(R.string.library_download_unread)) { viewModel.downloadUnread(listOf(e.id)); menuFor = null }
            }
            SheetAction(Icons.Filled.Delete, stringResource(R.string.title_remove_from_library), destructive = true) {
                viewModel.removeFromLibrary(listOf(e.id)); menuFor = null
            }
        }
    }
    tabsFor?.let { e ->
        val member = tabMembers[e.id].orEmpty()
        TabsPickerSheet(
            title = stringResource(R.string.tabs_pick_title),
            subtitle = e.title,
            tabs = tabs.map { TabOption(it.id, it.name, if (it.id in member) ToggleableState.On else ToggleableState.Off) },
            onToggle = { id -> viewModel.setTab(listOf(e.id), id, id !in member) },
            onCreate = { name -> viewModel.createTab(name, listOf(e.id)) },
            onDismiss = { tabsFor = null },
        )
    }
    if (createTab) {
        TextInputDialog(
            title = stringResource(CoreR.string.tabs_new),
            label = stringResource(CoreR.string.tabs_name),
            onConfirm = { viewModel.createTab(it) },
            onDismiss = { createTab = false },
        )
    }
    editTab?.let { t ->
        val index = tabs.indexOfFirst { it.id == t.id }
        TabActionsSheet(
            name = t.name,
            canMoveLeft = index > 0,
            canMoveRight = index in 0 until tabs.lastIndex,
            onRename = { viewModel.renameTab(t.id, it) },
            onMove = { viewModel.moveTab(t.id, it) },
            onDelete = { viewModel.deleteTab(t.id); if (tab == t.id) tab = null },
            onDismiss = { editTab = null },
        )
    }
}


@Composable
internal fun sourceSortLabel(mode: SourceSortMode): String = stringResource(
    when (mode) {
        SourceSortMode.RECENT -> R.string.source_sort_recent
        SourceSortMode.NAME -> R.string.source_sort_name
        SourceSortMode.LANGUAGE -> R.string.source_sort_language
    },
)

@Composable
private fun librarySortLabel(mode: LibrarySortMode): String = stringResource(
    when (mode) {
        LibrarySortMode.LAST_UPDATE -> R.string.library_sort_last_update
        LibrarySortMode.TITLE -> R.string.library_sort_title
        LibrarySortMode.UNREAD -> R.string.library_sort_unread
        LibrarySortMode.LAST_READ -> R.string.library_sort_last_read
        LibrarySortMode.DATE_ADDED -> R.string.library_sort_date_added
        LibrarySortMode.CHAPTERS -> R.string.library_sort_chapters
    },
)

/** Sort and filters of the online library. */
@Composable
private fun LibraryDisplaySheet(display: LibraryDisplay, viewModel: CatalogHomeViewModel, onDismiss: () -> Unit) {
    YomuSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetTitle(stringResource(R.string.sort_and_filter))
            ListHeader(stringResource(R.string.chapter_sort), Modifier.padding(start = Dimens.s))
            LibrarySortMode.entries.forEach { mode ->
                val selected = display.sort == mode
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.touch)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.setLibrarySort(mode) })
                        .padding(horizontal = Dimens.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(librarySortLabel(mode), Modifier.weight(1f).padding(start = Dimens.m))
                    if (selected) {
                        Icon(
                            if (display.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                            stringResource(if (display.descending) R.string.sort_descending else R.string.sort_ascending),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            ListHeader(stringResource(R.string.chapter_filter), Modifier.padding(start = Dimens.s))
            listOf(
                Triple(R.string.library_filter_unread, display.unreadOnly) { v: Boolean -> viewModel.updateLibraryDisplay { it.copy(unreadOnly = v) } },
                Triple(R.string.library_filter_downloaded, display.downloadedOnly) { v: Boolean -> viewModel.updateLibraryDisplay { it.copy(downloadedOnly = v) } },
                Triple(R.string.library_filter_completed, display.completedOnly) { v: Boolean -> viewModel.updateLibraryDisplay { it.copy(completedOnly = v) } },
            ).forEach { (label, checked, onChange) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.touch)
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onChange(it) })
                        .padding(horizontal = Dimens.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(stringResource(label), Modifier.padding(start = Dimens.m))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedTab(
    entries: List<HistoryEntry>,
    emptyTitle: String,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    /** [hasEntries] lets the header hide its filters over an empty list. */
    header: @Composable (hasEntries: Boolean) -> Unit,
    onClick: (HistoryEntry) -> Unit,
    onOpenTitle: (HistoryEntry) -> Unit,
    onRemove: ((HistoryEntry) -> Unit)?,
    onMarkRead: (Collection<Long>, Boolean) -> Unit,
    onDownload: (Collection<Long>) -> Unit,
    onRemoveSelected: ((Collection<Long>) -> Unit)?,
    topInset: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f),
) {
    // Chapter ids are the record identity: the same chapter read again is a second history row, one selection.
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var grouping by rememberSaveable { mutableStateOf(app.yomu.domain.catalog.HistoryGrouping.DATE) }
    LaunchedEffect(entries) {
        val present = entries.map { it.chapter.id }.toSet()
        selection = selection.intersect(present)
    }
    val selecting = selection.isNotEmpty()
    val sections = remember(entries, grouping) {
        val byId = entries.associateBy { it.chapter.id }
        app.yomu.domain.catalog.HistoryView.group(entries.map { it.toViewItem() }, grouping)
            .map { section -> section.label to section.items.mapNotNull { byId[it.chapterId] } }
    }
    fun toggle(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, bottom = navBarBottomPadding(Dimens.xl)),
    ) {
        item(key = "header") { header(entries.isNotEmpty()) }
        // The grouping and the filters mean nothing over an empty list: they only pushed the empty state down.
        if (entries.isNotEmpty()) {
            item(key = "grouping") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.l, vertical = Dimens.xs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.hist_group_by), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    app.yomu.domain.catalog.HistoryGrouping.entries.forEach { mode ->
                        FilterChip(
                            selected = grouping == mode,
                            onClick = { grouping = mode },
                            label = { Text(stringResource(if (mode == app.yomu.domain.catalog.HistoryGrouping.DATE) R.string.hist_group_date else R.string.hist_group_title)) },
                        )
                    }
                }
            }
        }
        if (selecting) {
            item(key = "selection") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = Dimens.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, stringResource(CoreR.string.close)) }
                    Text(
                        stringResource(R.string.feed_selected, selection.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onMarkRead(selection, true); selection = emptySet() }) {
                        Icon(Icons.Filled.Check, stringResource(R.string.feed_mark_read))
                    }
                    IconButton(onClick = { onDownload(selection); selection = emptySet() }) {
                        Icon(Icons.Filled.Download, stringResource(R.string.feed_download))
                    }
                    if (onRemoveSelected != null) {
                        IconButton(onClick = { onRemoveSelected(selection); selection = emptySet() }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.feed_remove_history))
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") { EmptyState(title = emptyTitle, icon = emptyIcon, modifier = Modifier.fillMaxWidth().padding(top = Dimens.xl)) }
        }
        sections.forEach { (label, sectionEntries) ->
            item(key = "section-$label") {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.s),
                )
            }
            items(sectionEntries, key = { "${it.chapter.id}-${it.readAt}" }) { h ->
                BookListItem(
                    title = h.entryTitle,
                    subtitle = h.chapter.name,
                    cover = h.coverUrl,
                    progress = 0f,
                    modifier = motionItem(),
                    selected = h.chapter.id in selection,
                    selectionMode = selecting,
                    meta = listOfNotNull(
                        relativeTime(h.readAt).ifBlank { null },
                        if (h.chapter.downloaded) stringResource(R.string.chapter_downloaded) else null,
                        if (h.chapter.read) stringResource(R.string.chapter_read) else null,
                    ).joinToString(" · "),
                    onClick = { if (selecting) toggle(h.chapter.id) else onClick(h) },
                    onLongClick = { toggle(h.chapter.id) },
                    trailing = if (selecting) null else {
                        {
                            Row {
                                IconButton(onClick = { onOpenTitle(h) }) { Icon(Icons.Outlined.CollectionsBookmark, stringResource(R.string.title_open)) }
                                if (onRemove != null) IconButton(onClick = { onRemove(h) }) { Icon(Icons.Filled.Delete, stringResource(CoreR.string.delete)) }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** One feed row in the filters' terms: read state, download and bookmark are independent questions. */
private fun app.yomu.data.catalog.HistoryEntry.toViewItem() = app.yomu.domain.catalog.HistoryViewItem(
    chapterId = chapter.id,
    titleId = chapter.entryId,
    title = entryTitle,
    chapter = chapter.name,
    at = readAt,
    read = chapter.read,
    downloaded = chapter.downloaded,
    bookmarked = chapter.bookmarked,
)

/** Read/unread, downloaded and bookmarked filters of the updates and history feeds. */
@Composable
private fun HistoryFilterRow(filter: app.yomu.domain.catalog.HistoryFilter, onChange: (app.yomu.domain.catalog.HistoryFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.l, vertical = Dimens.xs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            app.yomu.domain.catalog.HistoryReadFilter.ALL to R.string.hist_filter_all,
            app.yomu.domain.catalog.HistoryReadFilter.READ to R.string.hist_filter_read,
            app.yomu.domain.catalog.HistoryReadFilter.UNREAD to R.string.hist_filter_unread,
        ).forEach { (value, label) ->
            FilterChip(
                selected = filter.read == value,
                onClick = { onChange(filter.copy(read = value)) },
                label = { Text(stringResource(label)) },
            )
        }
        FilterChip(
            selected = filter.downloadedOnly,
            onClick = { onChange(filter.copy(downloadedOnly = !filter.downloadedOnly)) },
            label = { Text(stringResource(R.string.hist_filter_downloaded)) },
        )
        FilterChip(
            selected = filter.bookmarkedOnly,
            onClick = { onChange(filter.copy(bookmarkedOnly = !filter.bookmarkedOnly)) },
            label = { Text(stringResource(R.string.hist_filter_bookmarked)) },
        )
    }
}
