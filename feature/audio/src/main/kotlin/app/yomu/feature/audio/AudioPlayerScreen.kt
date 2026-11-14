package app.yomu.feature.audio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.yomu.core.common.Files
import app.yomu.core.ui.components.CoverImage
import app.yomu.core.ui.components.ErrorState
import app.yomu.core.ui.components.RadialAction
import app.yomu.core.ui.components.RadialMenu
import app.yomu.core.ui.components.RadialState
import app.yomu.core.ui.components.SheetTitle
import app.yomu.core.ui.components.YomuSheet
import app.yomu.core.ui.components.coverTint
import app.yomu.core.ui.components.rememberCoverColor
import app.yomu.data.audio.AudioDownloadProgress
import app.yomu.data.audio.AudioDownloadState
import app.yomu.data.audio.YouTubeAuth
import app.yomu.domain.model.AudioNavEntry
import app.yomu.domain.model.AudioOrigin
import app.yomu.domain.model.AudioPositions
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File
import java.util.Locale
import app.yomu.core.ui.R as CoreR

private val SPEEDS = listOf(0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
private const val SPEED_STEP = 0.05f
private val SLEEP_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)
private const val SLEEP_EXTRA_MIN = 5
private const val MAX_SLEEP_MIN = 24 * 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(viewModel: AudioPlayerViewModel, onBack: () -> Unit, onYouTubeSignIn: (() -> Unit)? = null) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val chapterLengths by viewModel.chapterLengths.collectAsStateWithLifecycle()
    val settings by viewModel.audioSettings.collectAsStateWithLifecycle()
    val origin by viewModel.origin.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    // Captured when the bookmark button is pressed: typing a note never moves the saved spot. Saved as primitives so a
    // rotation or a killed process keeps the anchor and the bookmark being edited.
    var bookmarkTrack by rememberSaveable { mutableIntStateOf(-1) }
    var bookmarkPos by rememberSaveable { mutableLongStateOf(-1L) }
    var editingAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val bookmarkAt = if (bookmarkTrack >= 0 && bookmarkPos >= 0) bookmarkTrack to bookmarkPos else null
    val editingBookmark = editingAt?.let { at -> settings.bookmarks.firstOrNull { it.createdAt == at } }
    val playing = player?.isPlaying == true
    var more by remember { mutableStateOf(false) }
    var wheel by remember { mutableStateOf<RadialState?>(null) }
    var coverViewer by rememberSaveable { mutableStateOf(false) }
    // The bookmark anchor is taken when the action is pressed, so typing a note never moves the saved spot.
    val addBookmark: () -> Unit = {
        val point = viewModel.bookmarkPoint()
        editingAt = null
        if (point != null) {
            bookmarkTrack = point.first
            bookmarkPos = point.second
            dialog = "bookmark"
        }
    }
    // The same wheel as the library: full cover, offline download, bookmark, chapters, sleep timer, speed.
    val fullCoverLabel = stringResource(R.string.audio_full_cover)
    val offlineLabel = stringResource(R.string.audio_offline)
    val bookmarkLabel = stringResource(R.string.audio_bookmark_add)
    val chaptersLabel = stringResource(R.string.audio_chapters)
    val sleepLabelText = stringResource(R.string.audio_sleep)
    val speedLabelText = stringResource(R.string.audio_speed)
    val coverActions = listOfNotNull(
        RadialAction(Icons.Default.Fullscreen, fullCoverLabel) { coverViewer = true },
        offline?.let { RadialAction(Icons.Outlined.Download, offlineLabel) { sheet = "offline" } },
        RadialAction(Icons.Default.BookmarkAdd, bookmarkLabel) { addBookmark() },
        RadialAction(Icons.AutoMirrored.Filled.FormatListBulleted, chaptersLabel) { sheet = "chapters" },
        RadialAction(Icons.Default.Bedtime, sleepLabelText) { sheet = "sleep" },
        RadialAction(Icons.Default.Speed, speedLabelText) { sheet = "speed" },
    )
    // Paused, the position only moves on seeks, which are player events: no need to poll.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playing, lifecycle) { if (playing) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.tickWhilePlaying() } }
    // Back from the YouTube sign-in page: a video that only needed an account starts again on its own.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val error = viewModel.player.value?.error
                if (error != null && needsSignIn(error) && YouTubeAuth.isSignedIn()) viewModel.retryPlayback()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val cover = book?.coverPath?.let(::File)
    val tint = rememberCoverColor(cover)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).coverTint(tint)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, stringResource(CoreR.string.back), Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.audio_playing_from, originLabel(origin) ?: stringResource(R.string.audio_local_files)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            book?.title.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(onClick = { more = true }, enabled = player != null) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.audio_more))
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.audio_bookmark_add)) },
                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, null) },
                                onClick = { more = false; addBookmark() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.audio_history)) },
                                leadingIcon = { Icon(Icons.Default.History, null) },
                                onClick = { more = false; sheet = "history" },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.audio_chapters)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) },
                                onClick = { more = false; sheet = "chapters" },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.audio_skip_intervals)) },
                                leadingIcon = { Icon(Icons.Default.Tune, null) },
                                onClick = { more = false; sheet = "skip" },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.audio_skip_silence)) },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, null) },
                                trailingIcon = { if (player?.skipSilence == true) Icon(Icons.Default.Check, null) },
                                onClick = { more = false; viewModel.toggleSkipSilence() },
                            )
                            if (offline != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.audio_offline)) },
                                    leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                    onClick = { more = false; sheet = "offline" },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            val error = loadError
            if (error != null) {
                ErrorState(
                    error,
                    Modifier.padding(padding).fillMaxSize(),
                    onRetry = viewModel::retryPlayback,
                    retryLabel = stringResource(R.string.audio_retry_now),
                    secondaryLabel = stringResource(CoreR.string.back),
                    onSecondary = onBack,
                )
                return@Scaffold
            }
            val s = player
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                val wide = maxWidth >= 560.dp && maxWidth > maxHeight
                val chapterIndex = s?.let { AudioPositions.current(chapters, it.track, it.positionMs) } ?: -1
                val chapterTitle = chapters.getOrNull(chapterIndex)?.title ?: s?.trackTitle.orEmpty()
                val bookmark = if (s != null) addBookmark else null
                val history: @Composable (Modifier) -> Unit = { m ->
                    s?.history?.lastOrNull()?.takeIf { it.isJump }?.let { e ->
                        Box(m) { HistoryChip(e) { viewModel.jumpTo(e.fromTrack, e.fromMs) } }
                    }
                }
                if (wide) {
                    val side = minOf(maxWidth * 0.42f, maxHeight * 0.78f)
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                    ) {
                        PlayerCover(
                            cover, book?.title.orEmpty(), Modifier.size(side),
                            onOpen = { coverViewer = true },
                            onLongPress = { at -> wheel = RadialState(at, coverActions, book?.title) },
                        )
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            NowPlaying(
                                title = book?.title.orEmpty(),
                                author = book?.authorsText.orEmpty(),
                                origin = originLabel(origin),
                                chapterTitle = chapterTitle,
                                onChapters = { sheet = "chapters" },
                                onBookmark = bookmark,
                            )
                            history(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(20.dp))
                            PlayerControls(viewModel, s, settings, chapters, chapterIndex, onSheet = { sheet = it }, onYouTubeSignIn = onYouTubeSignIn)
                        }
                    }
                } else {
                    val side = minOf(maxWidth * 0.9f, maxHeight * 0.4f).coerceAtLeast(150.dp)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PlayerCover(
                                cover, book?.title.orEmpty(), Modifier.size(side),
                                onOpen = { coverViewer = true },
                                onLongPress = { at -> wheel = RadialState(at, coverActions, book?.title) },
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        NowPlaying(
                            title = book?.title.orEmpty(),
                            author = book?.authorsText.orEmpty(),
                            origin = originLabel(origin),
                            chapterTitle = chapterTitle,
                            onChapters = { sheet = "chapters" },
                            onBookmark = bookmark,
                        )
                        history(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                        PlayerControls(viewModel, s, settings, chapters, chapterIndex, onSheet = { sheet = it }, onYouTubeSignIn = onYouTubeSignIn)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    when (sheet) {
        "chapters" -> YomuSheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.audio_chapters))
            val current = player?.let { AudioPositions.current(chapters, it.track, it.positionMs) } ?: -1
            val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
            LazyColumn(state = state, modifier = Modifier.heightIn(max = 520.dp)) {
                itemsIndexed(chapters) { i, entry ->
                    ChapterRow(entry, chapterLengths.getOrElse(i) { 0 }, i == current) { viewModel.jump(entry); sheet = null }
                }
            }
        }
        "speed" -> YomuSheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.audio_speed))
            val current = player?.speed ?: 1f
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setSpeed(current - SPEED_STEP) }, enabled = current > AudioSettings.MIN_SPEED + 0.001f) {
                    Icon(Icons.Default.Remove, stringResource(R.string.audio_speed_down))
                }
                Text(speedLabel(current), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.setSpeed(current + SPEED_STEP) }, enabled = current < AudioSettings.MAX_SPEED - 0.001f) {
                    Icon(Icons.Default.Add, stringResource(R.string.audio_speed_up))
                }
            }
            SPEEDS.forEach { v ->
                OptionRow(speedLabel(v), selected = kotlin.math.abs(current - v) < 0.01f) { viewModel.setSpeed(v); sheet = null }
            }
        }
        "sleep" -> YomuSheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.audio_sleep))
            val timerOn = (player?.sleepAt ?: 0L) > 0
            if (timerOn) OptionRow(stringResource(R.string.audio_sleep_extend), selected = false) { viewModel.extendSleep(SLEEP_EXTRA_MIN); sheet = null }
            OptionRow(stringResource(R.string.audio_sleep_off), selected = player?.let { it.sleepAt == 0L && !it.sleepEndOfChapter } ?: true) { viewModel.sleep(0); sheet = null }
            OptionRow(stringResource(R.string.audio_sleep_chapter), selected = player?.sleepEndOfChapter == true) {
                viewModel.sleep(AudioPlaybackService.SLEEP_END_OF_CHAPTER); sheet = null
            }
            SLEEP_MINUTES.forEach { m -> OptionRow(stringResource(R.string.audio_sleep_minutes, m), selected = false) { viewModel.sleep(m); sheet = null } }
            OptionRow(stringResource(R.string.audio_sleep_custom), selected = false) { sheet = null; dialog = "sleep" }
        }
        "bookmarks" -> YomuSheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.audio_bookmarks))
            Button(
                onClick = {
                    val point = viewModel.bookmarkPoint()
                    editingAt = null
                    if (point != null) {
                        bookmarkTrack = point.first
                        bookmarkPos = point.second
                        sheet = null
                        dialog = "bookmark"
                    }
                },
                enabled = player != null,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.BookmarkAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.audio_bookmark_add))
            }
            if (settings.bookmarks.isEmpty()) {
                Text(
                    stringResource(R.string.audio_bookmarks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    itemsIndexed(settings.bookmarks) { _, mark ->
                        BookmarkRow(
                            mark, chapters,
                            onClick = { viewModel.jump(mark); sheet = null },
                            onEdit = {
                                bookmarkTrack = mark.track
                                bookmarkPos = mark.positionMs
                                editingAt = mark.createdAt
                                sheet = null
                                dialog = "bookmark"
                            },
                            onDelete = { viewModel.removeBookmark(mark) },
                        )
                    }
                }
            }
        }
        "history" -> YomuSheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.audio_history))
            val events = player?.history.orEmpty().asReversed()
            if (events.isEmpty()) {
                Text(
                    stringResource(R.string.audio_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    itemsIndexed(events) { _, e ->
                        HistoryRow(e, chapters) { viewModel.jumpTo(e.fromTrack, e.fromMs); sheet = null }
                    }
                }
                TextButton(onClick = { viewModel.clearHistory(); sheet = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.audio_history_clear))
                }
            }
        }
        "skip" -> YomuSheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.audio_skip_intervals))
            SkipChoices(stringResource(R.string.audio_skip_back_title), AudioSettings.SKIP_BACK_OPTIONS, settings.skipBackSec) {
                viewModel.setSkipIntervals(it, settings.skipForwardSec)
            }
            SkipChoices(stringResource(R.string.audio_skip_forward_title), AudioSettings.SKIP_FORWARD_OPTIONS, settings.skipForwardSec) {
                viewModel.setSkipIntervals(settings.skipBackSec, it)
            }
            Spacer(Modifier.height(16.dp))
        }
        "offline" -> YomuSheet(onDismiss = { sheet = null }) {
            SheetTitle(stringResource(R.string.audio_offline))
            OfflinePanel(offline, download, viewModel::startDownload, viewModel::cancelDownload) { viewModel.removeDownload(); sheet = null }
        }
    }

    when (dialog) {
        "bookmark" -> {
            val at = bookmarkAt
            val editing = editingBookmark
            var note by rememberSaveable(bookmarkTrack, bookmarkPos, editingAt) { mutableStateOf(editing?.note.orEmpty()) }
            val close = {
                dialog = null
                bookmarkTrack = -1
                bookmarkPos = -1L
                editingAt = null
            }
            AlertDialog(
                onDismissRequest = close,
                title = { Text(stringResource(R.string.audio_bookmark_here, time(at?.second ?: 0))) },
                text = {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(200) }, singleLine = true,
                        label = { Text(stringResource(R.string.audio_bookmark_note)) },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (bookmarkTrack >= 0 && bookmarkPos >= 0) {
                                if (editing != null) viewModel.updateBookmark(editing, note)
                                else viewModel.addBookmark(bookmarkTrack, bookmarkPos, note)
                            }
                            close()
                        },
                    ) { Text(stringResource(R.string.audio_bookmark_save)) }
                },
                dismissButton = { TextButton(onClick = close) { Text(stringResource(CoreR.string.cancel)) } },
            )
        }
        "sleep" -> {
            var minutes by rememberSaveable { mutableStateOf("") }
            val value = minutes.toIntOrNull()?.takeIf { it in 1..MAX_SLEEP_MIN }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.audio_sleep_custom_title)) },
                text = {
                    OutlinedTextField(
                        value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(R.string.audio_minutes_label)) },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { value?.let(viewModel::sleep); dialog = null }, enabled = value != null) { Text(stringResource(R.string.audio_start)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(CoreR.string.cancel)) } },
            )
        }
    }

    wheel?.let { RadialMenu(state = it, dragSelect = false, onDismiss = { wheel = null }) }
    if (coverViewer) CoverViewerDialog(cover, book?.title.orEmpty()) { coverViewer = false }
}

/**
 * Square album-style cover: the artwork is the page, so it gets the room. A tap opens the whole artwork (a YouTube
 * thumbnail is cropped to a square here), a long press opens the action wheel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerCover(
    model: File?,
    title: String,
    modifier: Modifier,
    onOpen: () -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    // The long press opens the wheel at the middle of the cover, in window coordinates.
    var center by remember { mutableStateOf(Offset.Unspecified) }
    Box(
        modifier
            .shadow(24.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .onGloballyPositioned { c ->
                if (c.isAttached) center = c.localToWindow(Offset(c.size.width / 2f, c.size.height / 2f))
            }
            .combinedClickable(onClick = onOpen, onLongClick = { onLongPress(center) }),
    ) {
        CoverImage(model, title, Modifier.fillMaxSize())
        // The visible way to see the whole artwork; the same action sits in the long-press wheel.
        val label = stringResource(R.string.audio_full_cover)
        Surface(
            onClick = onOpen,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(38.dp).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Fullscreen, null, Modifier.size(20.dp)) }
        }
    }
}

/** The whole artwork, uncropped and zoomable: for covers whose interesting part is not in the middle. */
@Composable
private fun CoverViewerDialog(model: File?, title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (model != null) {
                ZoomableAsyncImage(
                    model = model,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CoverImage(null, title, Modifier.fillMaxSize())
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Default.Close, stringResource(CoreR.string.close), tint = Color.White)
            }
        }
    }
}

/** What is playing: the current chapter as the "lyric" line, then the title, the author and a bookmark action. */
@Composable
private fun NowPlaying(
    title: String,
    author: String,
    origin: String?,
    chapterTitle: String,
    onChapters: () -> Unit,
    onBookmark: (() -> Unit)?,
) {
    Text(
        chapterTitle.ifBlank { title },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onChapters).heightIn(min = 32.dp).padding(vertical = 6.dp),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(author.ifBlank { null }, origin).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onBookmark != null) {
            IconButton(onClick = onBookmark) { Icon(Icons.Default.BookmarkAdd, stringResource(R.string.audio_bookmark_add)) }
        }
    }
}

@Composable
private fun PlayerControls(
    viewModel: AudioPlayerViewModel,
    s: PlayerSnapshot?,
    settings: AudioSettings,
    chapters: List<AudioNavEntry>,
    chapterIndex: Int,
    onSheet: (String) -> Unit,
    onYouTubeSignIn: (() -> Unit)?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val span = PlayerMath.span(chapters, chapterIndex, s?.durationMs ?: 0)
        SeekBar(s, span) { viewModel.seekTo(it) }
        val progress = s?.let(viewModel::bookProgress)
        val left = s?.let(viewModel::timeLeftMs)
        val line = listOfNotNull(
            if (chapters.size > 1 && chapterIndex >= 0) stringResource(R.string.audio_chapter_of, chapterIndex + 1, chapters.size) else null,
            progress?.let { stringResource(R.string.audio_progress_percent, (it * 100).toInt()) },
            left?.let { stringResource(R.string.audio_book_left, duration(it)) },
        ).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(
                line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        s?.error?.let {
            Text(
                stringResource(R.string.audio_play_error, it), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp),
            )
            // A retry that does not replay the whole book: the same position, prepared again.
            TextButton(onClick = viewModel::retryNow) { Text(stringResource(R.string.audio_retry_now)) }
            // The hint is only useful without a session: with one, "sign in" in the message comes from another
            // client's bot check, and asking again would be a loop.
            if (needsSignIn(it) && !YouTubeAuth.isSignedIn()) {
                Text(
                    stringResource(R.string.audio_sign_in_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
                if (onYouTubeSignIn != null) {
                    TextButton(onClick = onYouTubeSignIn, modifier = Modifier.padding(bottom = 4.dp)) {
                        Text(stringResource(R.string.audio_sign_in))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SkipButton(settings.skipBackSec, forward = false, enabled = s != null, onClick = viewModel::back)
            IconButton(onClick = viewModel::previous, enabled = s != null, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipPrevious, stringResource(R.string.audio_previous), Modifier.size(36.dp))
            }
            PlayButton(s, viewModel::playPause)
            IconButton(onClick = viewModel::next, enabled = s != null, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipNext, stringResource(R.string.audio_next), Modifier.size(36.dp))
            }
            SkipButton(settings.skipForwardSec, forward = true, enabled = s != null, onClick = viewModel::forward)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = { onSheet("speed") },
                label = { Text(speedLabel(s?.speed ?: 1f)) },
                leadingIcon = { Icon(Icons.Default.Speed, null) },
            )
            Spacer(Modifier.weight(1f))
            val sleepOn = s != null && (s.sleepAt > 0 || s.sleepEndOfChapter)
            IconButton(onClick = { onSheet("sleep") }) {
                Icon(
                    Icons.Default.Bedtime, sleepLabel(s),
                    tint = if (sleepOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onSheet("bookmarks") }) {
                Icon(Icons.Default.Bookmarks, stringResource(R.string.audio_bookmarks), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onSheet("chapters") }, enabled = chapters.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.audio_chapters),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The one big white button of the screen, as on a record sleeve. */
@Composable
private fun PlayButton(s: PlayerSnapshot?, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = s != null,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = Modifier.size(72.dp),
    ) {
        when {
            s == null || s.isBuffering && s.playWhenReady -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.Black)
            s.playWhenReady -> Icon(Icons.Default.Pause, stringResource(R.string.audio_pause), Modifier.size(36.dp))
            else -> Icon(Icons.Default.PlayArrow, stringResource(R.string.audio_play), Modifier.size(40.dp))
        }
    }
}

@Composable
private fun SkipButton(seconds: Int, forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (forward) R.string.audio_forward_n else R.string.audio_back_n, seconds)
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            // Forward is the rewind arrow mirrored; the number is drawn on top so it is not.
            Icon(Icons.Default.Replay, label, Modifier.size(40.dp).graphicsLayer { scaleX = if (forward) -1f else 1f })
            Text(seconds.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkipChoices(title: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { sec ->
                FilterChip(selected = sec == selected, onClick = { onSelect(sec) }, label = { Text(stringResource(R.string.audio_seconds_short, sec)) })
            }
        }
    }
}

@Composable
private fun SeekBar(s: PlayerSnapshot?, span: ChapterSpan, onSeek: (Long) -> Unit) {
    val length = span.lengthMs
    var dragging by remember(s?.bookId, s?.track, span) { mutableStateOf(false) }
    var value by remember(s?.bookId, s?.track, span) { mutableFloatStateOf(span.progress(s?.positionMs ?: 0)) }
    val position = s?.positionMs ?: 0
    LaunchedEffect(position, span) { if (!dragging && length > 0) value = span.progress(position) }
    Slider(
        value = if (length > 0) value.coerceIn(0f, 1f) else 0f,
        onValueChange = { dragging = true; value = it },
        onValueChangeFinished = { dragging = false; if (length > 0) onSeek(span.positionAt(value)) },
        enabled = length > 0,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val elapsed = if (length > 0) (value * length).toLong() else (position - span.startMs).coerceAtLeast(0)
        Text(time(elapsed), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(if (length > 0) "-" + time(length - elapsed) else "", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ChapterRow(entry: AudioNavEntry, lengthMs: Long, current: Boolean, onClick: () -> Unit) {
    val bg = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bg).heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (current) Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            entry.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        )
        val label = if (lengthMs > 0) time(lengthMs) else if (entry.isMark) time(entry.startMs) else null
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Back to 0:42:18" for the last manual jump; the full journal is in the History sheet. */
@Composable
private fun HistoryChip(e: AudioPositionEvent, onBack: () -> Unit) {
    AssistChip(
        onClick = onBack,
        label = { Text(stringResource(R.string.audio_back_to, duration(e.fromMs))) },
        leadingIcon = { Icon(Icons.Default.History, null) },
    )
}

@Composable
private fun HistoryRow(e: AudioPositionEvent, chapters: List<AudioNavEntry>, onBack: () -> Unit) {
    val chapter = chapters.getOrNull(AudioPositions.current(chapters, e.fromTrack, e.fromMs))?.title.orEmpty()
    val reason = stringResource(
        when (e.reason) {
            AudioHistory.BACK -> R.string.audio_reason_back
            AudioHistory.FORWARD -> R.string.audio_reason_forward
            AudioHistory.CHAPTER -> R.string.audio_reason_chapter
            AudioHistory.RETURN -> R.string.audio_reason_return
            AudioHistory.BOOKMARK -> R.string.audio_reason_bookmark
            else -> R.string.audio_reason_seek
        },
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onBack).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text("${duration(e.fromMs)} → ${duration(e.toMs)}", maxLines = 1)
            val where = listOf(reason, chapter).filter { it.isNotBlank() }.joinToString(" · ")
            Text(where, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BookmarkRow(mark: Bookmark, chapters: List<AudioNavEntry>, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val chapter = chapters.getOrNull(AudioPositions.current(chapters, mark.track, mark.positionMs))?.title.orEmpty()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(mark.note.ifBlank { chapter }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val where = listOf(chapter, time(mark.positionMs)).filter { it.isNotBlank() && it != mark.note }.joinToString(" · ")
            Text(where, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.audio_bookmark_edit)) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.audio_bookmark_delete)) }
    }
}

@Composable
private fun OfflinePanel(
    state: AudioDownloadState?,
    download: AudioDownloadProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val active = download?.takeIf { it.running }
        val running = active != null
        val status = when {
            active != null && active.total > 0 -> stringResource(R.string.audio_offline_running, active.track + 1, active.total)
            running -> stringResource(R.string.audio_offline_waiting)
            state == null -> ""
            state.complete -> stringResource(R.string.audio_offline_complete, Files.humanSize(state.bytes))
            state.downloaded > 0 -> stringResource(R.string.audio_offline_state, state.downloaded, state.total, Files.humanSize(state.bytes))
            else -> stringResource(R.string.audio_offline_hint)
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (active != null) LinearProgressIndicator(progress = { active.fraction }, modifier = Modifier.fillMaxWidth())
        download?.error?.takeIf { !running }?.let {
            Text(stringResource(R.string.audio_offline_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                running -> OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.audio_download_cancel)) }
                state?.complete != true -> Button(onClick = onDownload) {
                    Text(stringResource(if ((state?.downloaded ?: 0) > 0) R.string.audio_download_rest else R.string.audio_download))
                }
            }
            if (!running && (state?.downloaded ?: 0) > 0) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.audio_download_remove)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun originLabel(origin: AudioOrigin): String? = when (origin) {
    AudioOrigin.FILES -> null
    AudioOrigin.CATALOG -> stringResource(R.string.audio_origin_catalog)
    AudioOrigin.YOUTUBE -> "YouTube"
    AudioOrigin.PODCAST -> stringResource(R.string.audio_origin_podcast)
    AudioOrigin.NOTEBOOKLM -> "NotebookLM"
    AudioOrigin.URL -> stringResource(R.string.audio_origin_link)
}

@Composable
private fun sleepLabel(s: PlayerSnapshot?): String = when {
    s == null -> stringResource(R.string.audio_sleep)
    s.sleepEndOfChapter -> stringResource(R.string.audio_sleep_chapter)
    s.sleepAt > 0 -> {
        val left = ((s.sleepAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
        stringResource(R.string.audio_sleep_left, left.toInt())
    }
    else -> stringResource(R.string.audio_sleep)
}

private fun needsSignIn(message: String): Boolean =
    message.contains("sign in", ignoreCase = true) || message.contains("login", ignoreCase = true)

private fun speedLabel(speed: Float): String {
    val rounded = Math.round(speed * 100) / 100f
    return String.format(Locale.ROOT, if (Math.round(rounded * 100) % 10 == 0) "%.1f×" else "%.2f×", rounded)
}

/** "3 h 05 min" style length for the time left. */
@Composable
private fun duration(ms: Long): String {
    val minutes = (ms + 59_999) / 60_000
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return if (h > 0) stringResource(R.string.audio_hours_minutes, h, m) else stringResource(R.string.audio_sleep_minutes, m)
}

internal fun time(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
