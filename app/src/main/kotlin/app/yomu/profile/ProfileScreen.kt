package app.yomu.profile

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.SendToMobile
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.R
import app.yomu.core.common.ErrorLog
import app.yomu.core.common.Share
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.components.CollapsibleSection
import app.yomu.core.ui.components.CoverImage
import app.yomu.core.ui.components.GlassTopBarBackground
import app.yomu.core.ui.components.LocalReduceMotion
import app.yomu.core.ui.components.PreferenceItem
import app.yomu.core.ui.components.TopLevelBody
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.glassTopBarColors
import app.yomu.core.ui.components.navBarBottomPadding
import app.yomu.core.ui.components.topLevelContentInsets
import app.yomu.data.download.DownloadRepository
import app.yomu.data.repository.ReadingHistoryItem
import app.yomu.domain.model.DownloadStatus
import app.yomu.domain.model.EngineType
import app.yomu.domain.repository.BookRepository
import app.yomu.feature.library.HistoryActions
import app.yomu.feature.library.HistoryPane
import app.yomu.feature.library.HistoryViewModel
import app.yomu.feature.reader.ReadingStatsActions
import app.yomu.feature.reader.ReadingStatsPane
import app.yomu.feature.reader.ReadingStatsViewModel
import app.yomu.feature.settings.SettingsSection
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.yomu.core.ui.theme.Dimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import app.yomu.core.ui.R as CoreR
import app.yomu.feature.library.R as LibraryR

/**
 * The pages of the journal, in the order they are entered: the journal itself, the full history, the statistics and
 * the trackers. A single state replaces the old Overview/History tabs and the pages on top of them, so the history is
 * one list with one search and one scroll position, and Back always returns to the screen it was opened from.
 */
private enum class ProfilePage(@StringRes val title: Int) {
    JOURNAL(R.string.profile_title),
    HISTORY(R.string.profile_tab_history),
    STATS(R.string.profile_tab_stats),
    TRACKING(R.string.profile_tab_tracking),
}

/** Emoji avatars of the profile; "" (the first one in the picker) is the first letter of the name. */
private val AVATARS =
    listOf("📚", "📖", "🦊", "🐱", "🐼", "🦉", "🐉", "🌸", "🌙", "⭐", "🔥", "🍵", "🎧", "🎮", "👾", "🍙")

/**
 * The reading journal (2.20.0, formerly the "Profile" tab): the last book first, today's goal and streak, the tools
 * (downloads, notes, tracking), then sharing. The full history, the statistics and the trackers are pages of the same
 * screen; the history is one list with one search, and Back returns to the page it was opened from (statistics opened
 * from the history come back to the same query and scroll position).
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    historyViewModel: HistoryViewModel,
    statsViewModel: ReadingStatsViewModel,
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    onReadChapter: (Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenNotes: (() -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
    onOpenCatalog: (() -> Unit)? = null,
    onOpenTrackerPage: ((app.yomu.domain.tracking.TrackService, Long) -> Unit)? = null,
) {
    var page by rememberSaveable { mutableStateOf(ProfilePage.JOURNAL) }
    // The page the statistics were opened from (the journal or the history).
    var statsReturn by rememberSaveable { mutableStateOf(ProfilePage.JOURNAL) }
    // Created when the trackers page is first opened.
    val trackingViewModel: ProfileTrackingViewModel? = if (page == ProfilePage.TRACKING) koinViewModel() else null
    var searching by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by historyViewModel.recent.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current
    // The scroll position of the journal and of the history survives the statistics and the trackers.
    val journalList = rememberLazyListState()
    val historyList = rememberLazyListState()
    val settings: AppSettings = koinInject()
    val showStats by settings.profileShowStats.flow.collectAsStateWithLifecycle(initialValue = true)
    val showMilestones by settings.profileShowMilestones.flow.collectAsStateWithLifecycle(initialValue = true)
    val showTop by settings.profileShowTop.flow.collectAsStateWithLifecycle(initialValue = true)
    val showRecent by settings.profileShowRecent.flow.collectAsStateWithLifecycle(initialValue = true)
    val showShare by settings.profileShowShare.flow.collectAsStateWithLifecycle(initialValue = true)
    // Only the number of waiting chapters, for the one-line note under "Downloads".
    val downloads: DownloadRepository = koinInject()
    val queued by produceState(0) {
        downloads.observe().collect { list ->
            value = list.count { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
        }
    }

    fun open(target: ProfilePage) {
        if (target == ProfilePage.STATS) statsReturn = page
        page = target
    }

    fun back() {
        page = when (page) {
            ProfilePage.STATS -> statsReturn
            else -> ProfilePage.JOURNAL
        }
    }

    BackHandler(enabled = page != ProfilePage.JOURNAL, onBack = ::back)
    val topBarBackdrop = rememberHazeState()

    Scaffold(
        contentWindowInsets = topLevelContentInsets(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            GlassTopBarBackground(topBarBackdrop) { TopAppBar(
                colors = glassTopBarColors(),
                title = {
                    if (reduce) {
                        Text(stringResource(page.title))
                    } else {
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = { fadeIn(tween(110, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                            label = "journal-title",
                        ) { p -> Text(stringResource(p.title)) }
                    }
                },
                navigationIcon = {
                    if (page != ProfilePage.JOURNAL) {
                        IconButton(onClick = ::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreR.string.back))
                        }
                    }
                },
                actions = {
                    when (page) {
                        ProfilePage.STATS -> ReadingStatsActions(statsViewModel)
                        ProfilePage.TRACKING -> trackingViewModel?.let { vm -> TrackingActions(vm, onOpenSettings = { onOpenSettings(SettingsSection.TRACKING) }) }
                        ProfilePage.HISTORY -> {
                            IconButton(onClick = { open(ProfilePage.STATS) }) {
                                Icon(Icons.Outlined.Insights, stringResource(R.string.profile_open_stats))
                            }
                            HistoryActions(historyViewModel, searching, onSearching = { searching = it })
                        }
                        // The journal's top row: its one other page, the full history.
                        ProfilePage.JOURNAL -> IconButton(onClick = { open(ProfilePage.HISTORY) }) {
                            Icon(Icons.Outlined.History, stringResource(R.string.profile_tab_history))
                        }
                    }
                },
            ) }
        },
    ) { padding ->
        TopLevelBody(padding, Modifier.fillMaxSize().hazeSource(topBarBackdrop)) {
            val content: @Composable (ProfilePage) -> Unit = { p ->
                when (p) {
                    ProfilePage.JOURNAL -> JournalPane(
                        state = state,
                        recent = recent,
                        listState = journalList,
                        snackbar = snackbar,
                        viewModel = viewModel,
                        historyViewModel = historyViewModel,
                        showStats = showStats,
                        showMilestones = showMilestones,
                        showTop = showTop,
                        showRecent = showRecent,
                        showShare = showShare,
                        queued = queued,
                        onEdit = { editing = true },
                        onOpenStats = { open(ProfilePage.STATS) },
                        onOpenHistory = { open(ProfilePage.HISTORY) },
                        onOpenTracking = { open(ProfilePage.TRACKING) },
                        onOpenDownloads = onOpenDownloads,
                        onOpenNotes = onOpenNotes,
                        onOpenLibrary = onOpenLibrary,
                        onOpenCatalog = onOpenCatalog,
                        onOpenBook = onOpenBook,
                        onReadChapter = onReadChapter,
                        onOpenTitle = onOpenTitle,
                        onOpenBackup = { onOpenSettings(SettingsSection.BACKUP) },
                    )
                    ProfilePage.HISTORY -> HistoryPane(
                        viewModel = historyViewModel,
                        searching = searching,
                        onSearching = { searching = it },
                        snackbar = snackbar,
                        onOpenBook = onOpenBook,
                        onBookDetails = onBookDetails,
                        onReadChapter = onReadChapter,
                        onOpenTitle = onOpenTitle,
                        onOpenStats = { open(ProfilePage.STATS) },
                        modifier = Modifier.fillMaxSize(),
                        showToday = false,
                        listState = historyList,
                    )
                    ProfilePage.STATS -> {
                        val ui = state
                        ReadingStatsPane(
                            statsViewModel,
                            Modifier.fillMaxSize(),
                            showTopTitles = showTop,
                            achievements = if (showMilestones && ui != null) {
                                { MilestonesCard(ui.milestones) }
                            } else {
                                null
                            },
                        )
                    }
                    ProfilePage.TRACKING -> trackingViewModel?.let { vm ->
                        TrackingPane(
                            viewModel = vm,
                            snackbar = snackbar,
                            onOpenTitle = onOpenTitle,
                            onBookDetails = onBookDetails,
                            onOpenTrackerPage = onOpenTrackerPage,
                            onOpenSettings = { onOpenSettings(SettingsSection.TRACKING) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (reduce) {
                content(page)
            } else {
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(200)) { it / 10 * direction } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(160)) { -it / 10 * direction } + fadeOut(tween(120)))
                    },
                    label = "journal-page",
                ) { p -> content(p) }
            }
        }
    }

    if (editing) {
        EditProfileDialog(
            name = state?.name.orEmpty(),
            avatar = state?.avatar.orEmpty(),
            onDismiss = { editing = false },
            onSave = { name, avatar ->
                viewModel.saveProfile(name, avatar)
                editing = false
            },
        )
    }
}

/**
 * The journal itself, in the order of the plan: the book to continue, today's goal and streak, the tools, then the
 * rarely used sharing block. The name and avatar are not a header any more: they live in "Name, avatar and card".
 */
@Composable
private fun JournalPane(
    state: ProfileUi?,
    recent: List<ReadingHistoryItem>,
    listState: LazyListState,
    snackbar: SnackbarHostState,
    viewModel: ProfileViewModel,
    historyViewModel: HistoryViewModel,
    showStats: Boolean,
    showMilestones: Boolean,
    showTop: Boolean,
    showRecent: Boolean,
    showShare: Boolean,
    queued: Int,
    onEdit: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenDownloads: (() -> Unit)?,
    onOpenNotes: (() -> Unit)?,
    onOpenLibrary: (() -> Unit)?,
    onOpenCatalog: (() -> Unit)?,
    onOpenBook: (Long) -> Unit,
    onReadChapter: (Long) -> Unit,
    onOpenTitle: (Long) -> Unit,
    onOpenBackup: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val palette = ProfileCard.Palette(colors.primary.toArgb(), colors.tertiary.toArgb(), colors.onPrimary.toArgb())
    val books: BookRepository = koinInject()
    // "Chapter 3" or "Page 12" when the saved position is known: the same words the library shelf uses.
    val firstBook = recent.firstOrNull()
    val place by key(firstBook?.bookId) {
        produceState<String?>(null, firstBook?.readAt) {
            val id = firstBook?.bookId ?: return@produceState
            val book = runCatching { books.getBook(id) }.getOrNull() ?: return@produceState
            val locator = runCatching { books.getProgress(id) }.getOrNull()
            value = locator?.let {
                when (book.engine) {
                    EngineType.TEXT, EngineType.AUDIO -> it.chapterTitle?.takeIf { title -> title.isNotBlank() }
                        ?: context.getString(LibraryR.string.library_continue_chapter, it.chapter + 1)
                    else -> context.getString(LibraryR.string.library_continue_page, it.chapter + 1)
                }
            }
        }
    }

    /** Prepares a file off the main thread and opens the share sheet for it; failures go to the snackbar. */
    fun shareFile(mime: String, make: suspend (File) -> File) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { make(Share.shareDir(context)) }
                Share.shareFile(context, file, mime)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ErrorLog.handled(e)
                snackbar.showSnackbar(errorMessage(context, e))
            } finally {
                busy = false
            }
        }
    }

    fun openRecent(item: ReadingHistoryItem) {
        val bookId = item.bookId
        val itemId = item.catalogItemId
        when {
            bookId != null -> onOpenBook(bookId)
            itemId != null -> scope.launch {
                val chapter = runCatching { historyViewModel.resumeChapter(item) }.getOrNull()
                if (chapter != null) onReadChapter(chapter) else onOpenTitle(itemId)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = Dimens.l, end = Dimens.l, top = Dimens.m, bottom = navBarBottomPadding(Dimens.xl)),
        verticalArrangement = Arrangement.spacedBy(Dimens.m),
    ) {
        // The first useful thing: the book the reader left, one tap away (or the two ways to start).
        if (showRecent) {
            item(key = "continue") {
                when {
                    recent.isNotEmpty() -> ContinueCard(recent.first(), place = place, onOpen = ::openRecent)
                    state == null -> ContinuePlaceholder()
                    // Sessions exist but the history is empty (cleared or incognito): do not claim "start reading".
                    state.sessions > 0 -> ChooseBookCard(
                        hint = stringResource(R.string.profile_choose_book_hint),
                        onOpenLibrary = onOpenLibrary,
                        onOpenCatalog = onOpenCatalog,
                    )
                    else -> StartReadingCard(onOpenLibrary, onOpenCatalog)
                }
            }
        }
        if (state != null && showStats) {
            item(key = "today") { TodayCard(state, onOpenStats) }
        }
        item(key = "tools") {
            ToolsCard(
                queued = queued,
                showStatsEntry = !showStats,
                onOpenStats = onOpenStats,
                onOpenTracking = onOpenTracking,
                onOpenDownloads = onOpenDownloads,
                onOpenNotes = onOpenNotes,
            )
        }
        if (state != null && showShare) {
            item(key = "share") {
                CollapsibleSection(
                    title = stringResource(R.string.profile_share_section),
                    summary = stringResource(R.string.profile_share_summary),
                    icon = Icons.Outlined.Share,
                    horizontalPadding = 0.dp,
                ) {
                    ShareSection(
                        busy = busy,
                        showHeader = false,
                        onEdit = onEdit,
                        onShareCard = {
                            val content = cardContent(context, state)
                            shareFile("image/png") { dir -> withContext(Dispatchers.Default) { ProfileCard.render(File(dir, "yomu_profile.png"), content, palette) } }
                        },
                        onShareText = { Share.shareText(context, shareText(context, state)) },
                        onShareHistory = { shareFile("text/csv") { dir -> viewModel.exportHistoryCsv(dir) } },
                        onShareBackup = { shareFile("application/octet-stream") { dir -> viewModel.createBackup(dir) } },
                        onOpenBackup = onOpenBackup,
                    )
                }
            }
        }
        // Milestones and most-read titles are not repeated here: they are part of the statistics page.
        if (state != null && !showStats && (showMilestones || showTop)) {
            item(key = "stats-hint") {
                PreferenceItem(
                    stringResource(R.string.profile_tool_statistics),
                    stringResource(R.string.profile_tool_statistics_sum),
                    icon = Icons.Outlined.Insights,
                    onClick = onOpenStats,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ journal cards

/**
 * One row: cover, title and the chapter (online) or the author and progress (local), then the arrow. The whole row is
 * one action with one TalkBack focus; the label names the actual destination (book, chapter or title).
 */
@Composable
private fun ContinueCard(item: ReadingHistoryItem, place: String? = null, onOpen: (ReadingHistoryItem) -> Unit) {
    val heading = stringResource(if (item.isAudio) LibraryR.string.details_continue_listening else R.string.profile_continue_reading)
    val actionLabel = stringResource(
        when {
            item.isAudio -> LibraryR.string.details_continue_listening
            item.bookId != null -> R.string.profile_continue_reading
            item.chapterId != null -> R.string.profile_continue_chapter
            else -> R.string.profile_open_title
        },
    )
    val meta = buildList {
        // The saved chapter or page first: it is the one thing the progress bar cannot tell.
        place?.takeIf { it.isNotBlank() }?.let(::add)
        item.subtitle?.takeIf { it.isNotBlank() && it != place }?.let(::add)
        if (item.bookId != null && !item.finished && (item.progress ?: 0f) > 0f) add("${((item.progress ?: 0f) * 100).toInt()}%")
        if (item.bookId == null) item.sourceName?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = actionLabel, role = Role.Button) { onOpen(item) },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = Dimens.l, vertical = Dimens.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.m),
        ) {
            CoverImage(item.cover?.let { c -> if (item.bookId != null) File(c) else c }, item.title, Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(heading, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The height of [ContinueCard]: the brief block keeps its place while the data loads. */
@Composable
private fun ContinuePlaceholder() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun StartReadingCard(onOpenLibrary: (() -> Unit)?, onOpenCatalog: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(Dimens.l)) {
            Text(stringResource(R.string.profile_start_reading), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.profile_no_reading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimens.xs),
            )
            FlowRow(Modifier.padding(top = Dimens.s)) {
                // On the lavender card the default purple text would disappear: use the card's own content colour.
                val text = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)
                if (onOpenLibrary != null) TextButton(onClick = onOpenLibrary, colors = text) { Text(stringResource(R.string.profile_open_library)) }
                if (onOpenCatalog != null) TextButton(onClick = onOpenCatalog, colors = text) { Text(stringResource(R.string.profile_open_catalog)) }
            }
        }
    }
}

/** Past sessions but no history entry: offer the library and the catalog without pretending it is the first launch. */
@Composable
private fun ChooseBookCard(hint: String, onOpenLibrary: (() -> Unit)?, onOpenCatalog: (() -> Unit)?) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(Dimens.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Dimens.s))
                Text(stringResource(R.string.profile_choose_book), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.xs),
            )
            FlowRow(Modifier.padding(top = Dimens.xs)) {
                if (onOpenLibrary != null) TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.profile_open_library)) }
                if (onOpenCatalog != null) TextButton(onClick = onOpenCatalog) { Text(stringResource(R.string.profile_open_catalog)) }
            }
        }
    }
}

@Composable
private fun ToolsCard(
    queued: Int,
    showStatsEntry: Boolean,
    onOpenStats: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenDownloads: (() -> Unit)?,
    onOpenNotes: (() -> Unit)?,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = Dimens.l, end = Dimens.l, top = Dimens.m), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_tools), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            if (onOpenDownloads != null) {
                PreferenceItem(
                    stringResource(R.string.profile_tab_downloads),
                    if (queued > 0) pluralStringResource(R.plurals.profile_downloads_queue, queued, queued)
                    else stringResource(R.string.profile_downloads_entry),
                    icon = Icons.Outlined.Download,
                    onClick = onOpenDownloads,
                )
            }
            if (onOpenNotes != null) {
                PreferenceItem(
                    stringResource(R.string.profile_notes),
                    stringResource(R.string.profile_notes_entry),
                    icon = Icons.Outlined.EditNote,
                    onClick = onOpenNotes,
                )
            }
            PreferenceItem(
                stringResource(R.string.profile_tab_tracking),
                stringResource(R.string.profile_tracking_entry),
                icon = Icons.Outlined.Sync,
                onClick = onOpenTracking,
            )
            // With the today block hidden, the statistics stay reachable from here.
            if (showStatsEntry) {
                PreferenceItem(
                    stringResource(R.string.profile_tool_statistics),
                    stringResource(R.string.profile_tool_statistics_sum),
                    icon = Icons.Outlined.Insights,
                    onClick = onOpenStats,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ avatar

@Composable
private fun Avatar(avatar: String, name: String, modifier: Modifier = Modifier) {
    val text = avatar.ifBlank { initial(name) }
    Box(
        modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = if (avatar.isBlank()) 30.sp else 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun initial(name: String): String =
    name.trim().takeIf { it.isNotEmpty() }?.let { n -> String(Character.toChars(n.codePointAt(0))).uppercase() } ?: "📚"

// ------------------------------------------------------------------------------------------------ today

/**
 * Today's goal and the streak only. The whole card opens the statistics (total time, the week, the numbers, the
 * milestones and the most read titles), so the same minutes are not repeated twice on one screen.
 */
@Composable
private fun TodayCard(state: ProfileUi, onOpenStats: () -> Unit) {
    val minutes = (state.todayMs / 60_000).toInt()
    val goal = state.goalMinutes.coerceAtLeast(1)
    val label = stringResource(R.string.profile_open_stats)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = label, role = Role.Button, onClick = onOpenStats),
    ) {
        Column(Modifier.padding(Dimens.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_today_goal), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (state.streakDays > 0) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(Dimens.xxs))
                    Text(
                        stringResource(R.string.profile_header_streak, state.streakDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.s))
            }
            Row(Modifier.padding(top = Dimens.xs), verticalAlignment = Alignment.Bottom) {
                Text("$minutes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.profile_today_of_goal, goal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.xs, bottom = 3.dp),
                )
            }
            LinearProgressIndicator(
                progress = { (minutes.toFloat() / goal).coerceIn(0f, 1f) },
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s).height(6.dp),
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------ milestones

@Composable
private fun MilestonesCard(milestones: List<Milestone>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.l), verticalArrangement = Arrangement.spacedBy(Dimens.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Dimens.s))
                Text(stringResource(R.string.profile_milestones), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val reached = milestones.sumOf { it.level }
                val total = milestones.sumOf { it.maxLevel }
                Text("$reached / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            milestones.forEach { MilestoneRow(it) }
        }
    }
}

@Composable
private fun MilestoneRow(m: Milestone) {
    val (title, unit) = when (m.kind) {
        MilestoneKind.HOURS -> R.string.profile_ms_hours to R.string.profile_unit_hours
        MilestoneKind.STREAK -> R.string.profile_ms_streak to R.string.profile_unit_days
        MilestoneKind.BOOKS -> R.string.profile_ms_books to R.string.profile_unit_books
        MilestoneKind.PAGES -> R.string.profile_ms_pages to R.string.profile_unit_pages
        MilestoneKind.WORDS -> R.string.profile_ms_words to R.string.profile_unit_words
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LevelBadge(m.level, done = m.next == null)
        Spacer(Modifier.width(Dimens.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                val next = m.next
                Text(
                    if (next == null) stringResource(R.string.profile_ms_done)
                    else stringResource(R.string.profile_ms_progress, number(m.value), number(next), stringResource(unit)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { m.progress },
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp),
            )
        }
    }
}

@Composable
private fun LevelBadge(level: Int, done: Boolean) {
    val bg = if (level > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (level > 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(40.dp).background(bg, CircleShape).then(
            if (done) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape) else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(level.toString(), style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------------------------------------ sharing

@Composable
private fun ShareSection(
    busy: Boolean,
    onEdit: () -> Unit,
    onShareCard: () -> Unit,
    onShareText: () -> Unit,
    onShareHistory: () -> Unit,
    onShareBackup: () -> Unit,
    onOpenBackup: () -> Unit,
    showHeader: Boolean = true,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = Dimens.m)) {
            if (showHeader) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.l), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_share_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(R.string.profile_share_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.l, vertical = Dimens.xs),
                )
            }
            // The name and the avatar are edited here, next to the card they appear on.
            ShareRow(Icons.Outlined.Edit, R.string.profile_share_edit, R.string.profile_share_edit_sum, enabled = true, onClick = onEdit)
            ShareRow(Icons.Outlined.Image, R.string.profile_share_card, R.string.profile_share_card_sum, enabled = !busy, onClick = onShareCard)
            ShareRow(Icons.AutoMirrored.Outlined.ShortText, R.string.profile_share_text, R.string.profile_share_text_sum, enabled = true, onClick = onShareText)
            ShareRow(Icons.Outlined.Description, R.string.profile_share_history, R.string.profile_share_history_sum, enabled = !busy, onClick = onShareHistory)
            ShareRow(Icons.AutoMirrored.Outlined.SendToMobile, R.string.profile_share_backup, R.string.profile_share_backup_sum, enabled = !busy, onClick = onShareBackup)
            ShareRow(Icons.Outlined.Backup, R.string.profile_backup_settings, R.string.profile_backup_settings_sum, enabled = true, onClick = onOpenBackup)
        }
    }
}

@Composable
private fun ShareRow(icon: ImageVector, @StringRes title: Int, @StringRes summary: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touch)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.l, vertical = Dimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Dimens.l))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------------------------------------ edit

@Composable
private fun EditProfileDialog(name: String, avatar: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by rememberSaveable { mutableStateOf(name) }
    var a by rememberSaveable { mutableStateOf(avatar) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.m)) {
                OutlinedTextField(
                    value = n,
                    onValueChange = { n = it.take(ProfileViewModel.MAX_NAME) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    placeholder = { Text(stringResource(R.string.profile_default_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.profile_avatar), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.s), verticalArrangement = Arrangement.spacedBy(Dimens.s)) {
                    AvatarChoice(initial(n), selected = a.isEmpty(), onClick = { a = "" })
                    AVATARS.forEach { e -> AvatarChoice(e, selected = a == e, onClick = { a = e }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n, a) }) { Text(stringResource(CoreR.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.cancel)) } },
    )
}

@Composable
private fun AvatarChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(Dimens.touch)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .border(if (selected) 2.dp else 1.dp, border, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 22.sp)
    }
}

// ------------------------------------------------------------------------------------------------ formatting

private fun number(value: Int): String = NumberFormat.getIntegerInstance().format(value)
private fun number(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun formatDate(day: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()).format(day)

private fun duration(context: Context, ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> context.getString(R.string.profile_duration_m, m)
        m == 0 -> context.getString(R.string.profile_duration_h, h)
        else -> context.getString(R.string.profile_duration_hm, h, m)
    }
}

private fun displayName(context: Context, state: ProfileUi): String = state.name.ifBlank { context.getString(R.string.profile_default_name) }

/** The shared statistics as a few lines of text. */
private fun shareText(context: Context, s: ProfileUi): String {
    val r = context.resources
    return buildString {
        appendLine(context.getString(R.string.profile_text_header, displayName(context, s)))
        appendLine(context.getString(R.string.profile_text_time, duration(context, s.totalMs)))
        appendLine(context.getString(R.string.profile_text_books, s.finishedBooks))
        appendLine(
            context.getString(
                R.string.profile_text_streak,
                r.getQuantityString(R.plurals.profile_days, s.streakDays, s.streakDays),
                r.getQuantityString(R.plurals.profile_days, s.bestStreak, s.bestStreak),
            ),
        )
        appendLine(context.getString(R.string.profile_text_pages, number(s.pages)))
        if (s.wordsSaved > 0) appendLine(context.getString(R.string.profile_text_words, number(s.wordsSaved)))
        s.topTitles.firstOrNull()?.let { appendLine(context.getString(R.string.profile_text_top, it.title)) }
        append(context.getString(R.string.profile_text_footer))
    }
}

private fun cardContent(context: Context, s: ProfileUi): ProfileCard.Content = ProfileCard.Content(
    brand = context.getString(R.string.app_name),
    avatar = s.avatar.ifBlank { initial(s.name) },
    name = displayName(context, s),
    subtitle = s.since?.let { context.getString(R.string.profile_since, formatDate(it)) } ?: context.getString(R.string.profile_card_title),
    total = duration(context, s.totalMs),
    totalLabel = context.getString(R.string.profile_card_total),
    tiles = listOf(
        number(s.finishedBooks) to context.getString(R.string.profile_stat_books),
        number(s.streakDays) to context.getString(R.string.profile_stat_streak),
        number(s.pages) to context.getString(R.string.profile_stat_pages),
        if (s.wordsSaved > 0) number(s.wordsSaved) to context.getString(R.string.profile_stat_words)
        else number(s.activeDays) to context.getString(R.string.profile_stat_days),
    ),
    topLabel = context.getString(R.string.profile_top),
    top = s.topTitles.map { it.title },
    footer = context.getString(R.string.profile_card_footer, formatDate(LocalDate.now())),
)
