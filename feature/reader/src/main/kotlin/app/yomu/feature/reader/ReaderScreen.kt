package app.yomu.feature.reader

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
// Aliased: android.provider.Settings is imported in this file for the system settings screen.
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yomu.core.common.DictionaryLauncher
import app.yomu.core.common.Share
import app.yomu.core.common.UiState
import app.yomu.core.ui.components.EmptyState
import app.yomu.core.ui.components.ErrorState
import app.yomu.core.ui.components.KeyBus
import app.yomu.core.ui.components.OneTimeHint
import app.yomu.core.ui.components.YomuSheet
import app.yomu.core.ui.components.QuickActionMenu
import app.yomu.core.ui.components.QuickMenuItem
import app.yomu.core.ui.components.SkeletonBox
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.rememberHaptic
import app.yomu.core.ui.theme.Dimens
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.CustomAction
import app.yomu.domain.model.HighlightColor
import app.yomu.domain.model.HighlightStyle
import app.yomu.domain.model.Locator
import app.yomu.domain.model.MangaDirection
import app.yomu.domain.model.QuickActions
import app.yomu.domain.model.QuickMenuStyle
import app.yomu.domain.model.SelectionAction
import app.yomu.domain.model.TapZoneLayout
import app.yomu.domain.model.WebSearchEngine
import app.yomu.feature.annotations.NoteDialog
import app.yomu.feature.annotations.QuoteImage
import app.yomu.feature.annotations.SelectionMenu
import app.yomu.feature.annotations.copyToClipboard
import app.yomu.feature.annotations.icon
import app.yomu.feature.annotations.label
import app.yomu.feature.dictionary.LookupRequest
import app.yomu.feature.dictionary.WordLookupViewModel
import app.yomu.feature.dictionary.WordPopup
import app.yomu.feature.reader.epub.EpubReaderView
import app.yomu.feature.reader.manga.MangaReaderView
import app.yomu.feature.reader.pdf.PdfReaderView
import app.yomu.feature.translator.Speaker
import app.yomu.feature.translator.TranslationSheet
import app.yomu.feature.translator.TranslationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import app.yomu.core.ui.R as CoreR

internal enum class ReaderSheet { NONE, TOC, SEARCH, SETTINGS, TTS, PAGES, JUMPS, MORE }

/**
 * The reader: hosts one of the three engines and the shared chrome (top/bottom bars that auto-hide),
 * selection toolbar, dictionary popup, translation sheet, TOC / search / TTS / settings sheets.
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenAnnotations: (Long) -> Unit,
    onOpenDictionary: (String) -> Unit,
    onOpenPacks: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenEditor: ((Long) -> Unit)? = null,
    onOpenPageLayout: ((Long) -> Unit)? = null,
    onOpenLinked: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val density = LocalDensity.current.density
    SideEffect { viewModel.density = density }

    val content by viewModel.content.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val chrome by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val wordTap by viewModel.wordTap.collectAsStateWithLifecycle()
    val footnote by viewModel.footnote.collectAsStateWithLifecycle()
    val image by viewModel.image.collectAsStateWithLifecycle()
    val translateText by viewModel.translateText.collectAsStateWithLifecycle()
    val passwordNeeded by viewModel.passwordNeeded.collectAsStateWithLifecycle()
    val common by viewModel.common.collectAsStateWithLifecycle()
    val textSettings by viewModel.textSettings.collectAsStateWithLifecycle()
    val readerText by viewModel.effectiveText.collectAsStateWithLifecycle()
    val imageCanvas by viewModel.imageCanvas.collectAsStateWithLifecycle()
    val mangaSettings by viewModel.mangaSettings.collectAsStateWithLifecycle()
    val verticalChapterNavigator by viewModel.verticalChapterNavigator.collectAsStateWithLifecycle()
    val pdfSettings by viewModel.pdfSettings.collectAsStateWithLifecycle()
    val perBook by viewModel.perBook.collectAsStateWithLifecycle()
    val wordTapSetting by viewModel.wordTapSetting.collectAsStateWithLifecycle()
    val actions by viewModel.selectionActions.collectAsStateWithLifecycle()
    val selectionCustomActions by viewModel.customActions.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val hasPrev by viewModel.hasPrevChapter.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNextChapter.collectAsStateWithLifecycle()
    val ttsActive by viewModel.ttsActive.collectAsStateWithLifecycle()
    val ttsPlaying by viewModel.tts.playing.collectAsStateWithLifecycle()
    val autoScroll by viewModel.autoScroll.collectAsStateWithLifecycle()
    val bilingual by viewModel.bilingualProgress.collectAsStateWithLifecycle()
    val ocrRunning by viewModel.ocrRunning.collectAsStateWithLifecycle()
    val webSearch by viewModel.settings.webSearch.flow.collectAsStateWithLifecycle(WebSearchEngine.GOOGLE)
    val customSearchUrl by viewModel.settings.customSearchUrl.flow.collectAsStateWithLifecycle("")
    val cpm by produceState(1000f) { value = viewModel.readingSpeed() }
    val jumpBack by viewModel.jumpBack.collectAsStateWithLifecycle()
    val jumpHistory by viewModel.jumpHistory.collectAsStateWithLifecycle()
    val linkedEditionId by viewModel.linkedEditionId.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val chapterStarts by viewModel.chapterStarts.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    val textReady by viewModel.textReady.collectAsStateWithLifecycle()
    val selectionMenuStyle by viewModel.selectionMenuStyle.collectAsStateWithLifecycle()
    val pageMenu by viewModel.pageMenu.collectAsStateWithLifecycle()
    val pageMenuStyle by viewModel.pageMenuStyle.collectAsStateWithLifecycle()
    val pageActions by viewModel.pageActions.collectAsStateWithLifecycle()
    var savePageAs by remember { mutableStateOf<ReaderEvent.SavePageAs?>(null) }
    val savePageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val pending = savePageAs
            savePageAs = null
            if (uri != null && pending != null) viewModel.savePageTo(pending.file, uri)
        }

    var sheet by rememberSaveable { mutableStateOf(ReaderSheet.NONE) }
    var noteFor by remember { mutableStateOf<ReaderSelection?>(null) }
    var pageNote by remember { mutableStateOf<PageNoteTarget?>(null) }
    var lookup by remember { mutableStateOf<LookupRequest?>(null) }
    var lookupAnchor by remember { mutableStateOf<Rect?>(null) }
    var searchSeed by rememberSaveable { mutableStateOf("") }
    var showFinished by rememberSaveable { mutableStateOf(false) }
    var seekDrag by remember { mutableStateOf<Float?>(null) }
    var quickPanel by rememberSaveable { mutableStateOf(false) }
    var tapZonesDone by rememberSaveable { mutableStateOf(false) }
    /** The reader asked for the zone diagram again from the menu; it bypasses the stored one-time flag. */
    var tapZonesForced by rememberSaveable { mutableStateOf(false) }
    var goToPage by rememberSaveable { mutableStateOf(false) }
    var readyTimedOut by remember { mutableStateOf(false) }
    var einkFlash by remember { mutableStateOf(false) }
    var lastFlashPage by remember { mutableIntStateOf(-1) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Written on every pointer event without recomposing the reader (35 state reads) for each finger movement.
    val interactionClock = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val snackbar = remember { SnackbarHostState() }
    val speaker: Speaker = koinInject()
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    LaunchedEffect(landscape) { viewModel.setLandscape(landscape) }
    val data = (content as? UiState.Data)?.value
    // The status bar sits on the page while the controls are hidden (a paper theme can be light inside a dark app
    // theme, where the white icons disappeared on it), and on the app's top bar while the controls are shown.
    val view = LocalView.current
    val readingBackground = if (data is ReaderContent.Text || (data == null && !imageCanvas)) Color(readerText.background) else Color.Black
    val readingLight = readingBackground.luminance() > 0.5f
    val chromeLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(view, readingLight, chrome) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = if (chrome) chromeLight else readingLight
        onDispose { controller?.isAppearanceLightStatusBars = chromeLight }
    }
    // The settings sample shows the open page, not a generic paragraph; it is read once when the sheet opens.
    val previewSnippet by produceState<String?>(null, sheet, data) {
        value = if (sheet == ReaderSheet.SETTINGS) viewModel.visibleSnippet() else null
    }
    val bookLang = viewModel.book?.language
    val author = viewModel.book?.authors?.joinToString()

    // The "More" sheet: the former dropdown of fifteen items, grouped by subject. The top bar's ⋮ and the
    // floating circle above the bottom bar both open it, so the rare commands stay one tap from the thumb.
    val moreMenu: @Composable (dismiss: () -> Unit) -> Unit = { dismiss ->
        // Voice and scrolling
        MoreGroup(stringResource(R.string.rmore_voice))
        if (data is ReaderContent.Text || data is ReaderContent.Pdf) {
            MoreAction(Icons.Default.Headphones, stringResource(R.string.reader_tts)) {
                dismiss()
                if (!ttsActive) viewModel.startTts()
                sheet = ReaderSheet.TTS
            }
        }
        MoreAction(if (autoScroll) Icons.Default.Pause else Icons.Default.SlowMotionVideo, stringResource(R.string.reader_autoscroll)) {
            dismiss(); viewModel.toggleAutoScroll(); viewModel.chromeVisible.value = false
        }
        // Translate and dictionary
        MoreGroup(stringResource(R.string.rmore_translate))
        MoreAction(Icons.Default.Translate, stringResource(R.string.reader_translate_page)) {
            dismiss(); viewModel.translatePage()
        }
        MoreAction(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.reader_dictionary)) {
            dismiss(); onOpenDictionary("")
        }
        MoreAction(Icons.Outlined.Language, stringResource(R.string.reader_language_packs)) { dismiss(); onOpenPacks() }
        // Notes and annotations
        if (viewModel.bookId > 0) {
            MoreGroup(stringResource(R.string.rmore_notes))
            MoreAction(Icons.Outlined.EditNote, stringResource(R.string.reader_annotations)) {
                dismiss(); onOpenAnnotations(viewModel.bookId)
            }
            MoreAction(Icons.AutoMirrored.Outlined.NoteAdd, stringResource(R.string.reader_page_note)) {
                dismiss(); pageNote = viewModel.pageNoteTarget()
            }
        }
        // Page
        MoreGroup(stringResource(R.string.rmore_page))
        if (data is ReaderContent.Pdf || data is ReaderContent.Manga) {
            MoreAction(Icons.Outlined.TouchApp, stringResource(R.string.reader_page_actions)) {
                dismiss(); viewModel.showCurrentPageActions()
            }
            MoreAction(Icons.Default.GridView, stringResource(R.string.reader_pages)) {
                dismiss(); sheet = ReaderSheet.PAGES
            }
        }
        if (data is ReaderContent.Text) {
            MoreAction(Icons.Outlined.ViewAgenda, stringResource(R.string.reader_bilingual)) { dismiss(); viewModel.toggleBilingual() }
        }
        // Everything else
        MoreGroup(stringResource(R.string.rmore_other))
        if (jumpHistory.isNotEmpty()) {
            MoreAction(Icons.Outlined.History, stringResource(R.string.reader_recent_jumps)) { dismiss(); sheet = ReaderSheet.JUMPS }
        }
        MoreAction(Icons.Outlined.BarChart, stringResource(R.string.reader_stats)) { dismiss(); onOpenStats() }
        // The gesture scheme can be shown again: no screen must be the only way to a function.
        MoreAction(Icons.Outlined.TouchApp, stringResource(R.string.reader_how_to_control)) {
            dismiss()
            tapZonesDone = false
            tapZonesForced = true
            if (data is ReaderContent.Manga) viewModel.previewTapZones() else viewModel.chromeVisible.value = false
        }
        if (onOpenPageLayout != null && viewModel.bookId > 0 && viewModel.book?.format == BookFormat.DOCX) {
            MoreAction(Icons.Outlined.Description, stringResource(R.string.reader_page_layout)) {
                dismiss(); onOpenPageLayout(viewModel.bookId)
            }
        }
        if (onOpenEditor != null && viewModel.bookId > 0 && !viewModel.isOnline) {
            MoreAction(Icons.Outlined.Edit, stringResource(R.string.reader_edit_book)) {
                dismiss(); onOpenEditor(viewModel.bookId)
            }
        }
        val linkedEdition = linkedEditionId
        if (onOpenLinked != null && linkedEdition != null) {
            MoreAction(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.reader_open_linked)) {
                dismiss(); onOpenLinked(linkedEdition)
            }
        }
    }

    // One haptic tick per chapter turn, whether by page turn, buttons or TTS. Skips the initial open.
    val chapterId by viewModel.chapterId.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        var lastChapter = -1
        var lastId = -1L
        var baselineAt = 0L
        snapshotFlow { Triple(content is UiState.Data, position.chapter, chapterId) }.collect { (ready, ch, id) ->
            if (!ready) { lastChapter = -1; baselineAt = 0L; return@collect }
            val now = System.currentTimeMillis()
            if (baselineAt == 0L) baselineAt = now
            val settled = now - baselineAt > 1_500
            val turned = if (lastId != -1L) id != lastId else lastChapter != -1 && settled && kotlin.math.abs(ch - lastChapter) == 1
            if (turned) haptic.tick()
            if (lastId == -1L || id != lastId) baselineAt = now
            lastChapter = ch
            lastId = id
        }
    }

    val appDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    LaunchedEffect(appDark) { viewModel.setAppDark(appDark) }

    // Live value of an edge swipe; written to the settings once, when the finger lifts.
    var edge by remember { mutableStateOf<EdgeAdjust?>(null) }
    var edgeShown by remember { mutableStateOf(false) }
    var edgeHold by remember { mutableStateOf<EdgeAdjust?>(null) }
    val liveCommon = edgeHold?.let { if (it.brightness) common.copy(brightness = it.value) else common.copy(warmFilter = it.value) } ?: common
    val context0 = LocalContext.current
    LaunchedEffect(edgeHold) { if (edgeShown && edgeHold == null) { delay(700); edgeShown = false } }

    ReaderWindowEffects(liveCommon, chrome || sheet != ReaderSheet.NONE, lastInteraction)

    // Panels hide by themselves after a few seconds of inactivity: reading comes first.
    LaunchedEffect(chrome, lastInteraction, sheet, quickPanel, seekDrag != null, selection != null, wordTap != null) {
        val busy = selection != null || wordTap != null
        if (chrome && sheet == ReaderSheet.NONE && !quickPanel && seekDrag == null && !busy && common.autoHideMenuSeconds > 0) {
            delay(common.autoHideMenuSeconds * 1000L)
            viewModel.chromeVisible.value = false
        }
    }
    LaunchedEffect(chrome) { if (!chrome) quickPanel = false }
    // The "back" offer is a hint, not a permanent button: after a jump it fades by itself.
    LaunchedEffect(jumpBack) {
        if (jumpBack != null) {
            delay(6_000)
            viewModel.dismissJumpBack()
        }
    }
    // Android Back closes the layer that is actually on top: the match bar, then the chrome, then the book. Sheets,
    // dialogs and menus are separate windows and dismiss themselves before this handler is reached.
    // "You stopped here": one quiet mark after the saved place is confirmed for the first time in this session. A new
    // book, an unread one and a book opened at the very beginning stay silent.
    var resumeFrom by remember { mutableStateOf<Int?>(null) }
    var resumeChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(data, textReady, position.bookPage, resumeChecked) {
        if (resumeChecked || data == null) return@LaunchedEffect
        val saved = viewModel.book?.progress ?: 0f
        if (saved < 0.02f) {
            resumeChecked = true
            return@LaunchedEffect
        }
        if (position.bookPage <= 1) return@LaunchedEffect
        resumeChecked = true
        resumeFrom = position.bookPage
        delay(6_000)
        resumeFrom = null
    }
    BackHandler(enabled = sheet == ReaderSheet.NONE && (chrome || searchState.active != null || jumpBack != null || resumeFrom != null)) {
        when {
            quickPanel -> quickPanel = false
            searchState.active != null -> viewModel.clearSearch()
            resumeFrom != null -> resumeFrom = null
            jumpBack != null -> viewModel.dismissJumpBack()
            else -> viewModel.chromeVisible.value = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    // Manga have their own volume key switch (on by default) and may turn forward with volume up.
    val isManga = data is ReaderContent.Manga
    val volumeKeys = if (isManga) mangaSettings.volumeKeys else common.volumeKeys
    val volumeForward = isManga && mangaSettings.invertVolumeKeys
    DisposableEffect(volumeKeys, volumeForward) {
        KeyBus.volumeHandler = if (volumeKeys) { up -> if (up != volumeForward) viewModel.prevPage() else viewModel.nextPage(); true } else null
        onDispose { KeyBus.volumeHandler = null }
    }
    // Hardware page-turners and D-pad arrows page through the same bus; the direction comes from the book, so a
    // right-to-left manga turns the other way. Outside the reader nobody is registered, so the keys stay with the
    // platform and with whatever is playing.
    val readerRtl = isManga && mangaSettings.direction == MangaDirection.RTL
    DisposableEffect(data != null, readerRtl) {
        KeyBus.pageHandler = { forward -> if (forward) viewModel.nextPage() else viewModel.prevPage(); true }
        KeyBus.rtl = readerRtl
        onDispose {
            KeyBus.pageHandler = null
            KeyBus.rtl = false
        }
    }
    LaunchedEffect(wordTap) {
        val w = wordTap ?: return@LaunchedEffect
        lookup = LookupRequest(w.word, w.sentence, bookLang, viewModel.bookId.takeIf { it > 0 }, title, w.locator)
        lookupAnchor = w.anchor
        viewModel.wordTap.value = null
    }
    // Word documents: offer the print layout once per document.
    val pageLayoutBook = viewModel.book?.takeIf { data != null && onOpenPageLayout != null && it.format == BookFormat.DOCX && it.id > 0 }
    LaunchedEffect(pageLayoutBook?.id) {
        val b = pageLayoutBook ?: return@LaunchedEffect
        if (!viewModel.takePageLayoutHint(b.id)) return@LaunchedEffect
        delay(800)
        val r = snackbar.showSnackbar(
            resources.getString(R.string.reader_page_layout_hint),
            resources.getString(R.string.reader_page_layout_open),
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (r == SnackbarResult.ActionPerformed) onOpenPageLayout?.invoke(b.id)
    }
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { e ->
            suspend fun undoable(message: String, onUndo: () -> Unit) {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(message, resources.getString(CoreR.string.undo), withDismissAction = true, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) onUndo()
            }
            when (e) {
                is ReaderEvent.BookmarkAdded -> { haptic.perform(); launch { undoable(resources.getString(R.string.reader_bookmark_added)) { viewModel.undoBookmark(e) } } }
                is ReaderEvent.BookmarkRemoved -> launch { undoable(resources.getString(R.string.reader_bookmark_removed)) { viewModel.undoBookmark(e) } }
                is ReaderEvent.HighlightDeleted -> launch { undoable(resources.getString(R.string.reader_highlight_deleted)) { viewModel.restoreHighlight(e.highlight) } }
                is ReaderEvent.WordSaved -> { haptic.perform(); launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(resources.getString(R.string.reader_word_saved, e.term)) } }
                is ReaderEvent.Message -> launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(resources.getString(e.res)) }
                is ReaderEvent.Error -> launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(errorMessage(context, e.error)) }
                is ReaderEvent.OpenUrl -> Share.openUrl(context, e.url)
                is ReaderEvent.OpenBook -> onOpenLinked?.invoke(e.id)
                ReaderEvent.Finished -> showFinished = true
                is ReaderEvent.PageNote -> pageNote = PageNoteTarget(e.locator, e.page)
                is ReaderEvent.SavePageAs -> {
                    savePageAs = e
                    runCatching { savePageLauncher.launch(e.name) }
                }
            }
        }
    }

    fun webSearchUrl(text: String): String {
        val template = if (webSearch == WebSearchEngine.CUSTOM) customSearchUrl.ifBlank { WebSearchEngine.GOOGLE.template } else webSearch.template
        val lang = bookLang?.take(2) ?: Locale.getDefault().language
        return template.replace("{text}", URLEncoder.encode(text, "UTF-8")).replace("{lang}", lang)
    }

    fun runCustom(action: CustomAction, text: String) {
        if (action.packageName.isNotBlank()) {
            val process = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage(action.packageName)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            val ok = runCatching { context.startActivity(process.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (!ok) runCatching {
                context.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(action.packageName).putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { scope.launch { snackbar.showSnackbar(resources.getString(R.string.reader_app_not_found)) } }
        } else if (action.urlTemplate.isNotBlank()) {
            Share.openUrl(context, action.urlTemplate.replace("{text}", URLEncoder.encode(text, "UTF-8")))
        }
        viewModel.clearSelection()
    }

    fun onAction(action: SelectionAction, sel: ReaderSelection) {
        when (action) {
            SelectionAction.HIGHLIGHT, SelectionAction.UNDERLINE, SelectionAction.STRIKE, SelectionAction.CUSTOM -> Unit
            SelectionAction.NOTE -> noteFor = sel
            SelectionAction.QUOTE -> { haptic.perform(); viewModel.highlight(HighlightColor.YELLOW, HighlightStyle.HIGHLIGHT, quote = true) }
            SelectionAction.MEANING -> {
                lookup = LookupRequest(sel.text, sel.sentence.ifBlank { null }, bookLang, viewModel.bookId.takeIf { it > 0 }, title,
                    Locator(chapter = sel.chapter, offset = sel.start, endOffset = sel.end, text = sel.text))
                lookupAnchor = sel.anchor
                viewModel.clearSelection()
            }
            SelectionAction.TRANSLATE -> { viewModel.translateText.value = sel.text; viewModel.clearSelection() }
            SelectionAction.SAVE_WORD -> viewModel.saveSelectionAsWord()
            SelectionAction.COPY -> {
                copyToClipboard(context, sel.text)
                viewModel.clearSelection()
            }
            SelectionAction.SHARE -> {
                val quote = buildString {
                    append('“').append(sel.text.trim()).append('”')
                    append("\n— ").append(listOfNotNull(author?.ifBlank { null }, title.ifBlank { null }).joinToString(", "))
                }
                Share.shareText(context, quote, resources.getString(CoreR.string.share))
                viewModel.clearSelection()
            }
            SelectionAction.SHARE_IMAGE -> {
                val dark = Color(readerText.background).luminance() < 0.5f
                scope.launch { runCatching { QuoteImage.share(context, sel.text.trim(), title, author, dark, resources.getString(CoreR.string.share)) } }
                viewModel.clearSelection()
            }
            SelectionAction.SEARCH_BOOK -> {
                searchSeed = sel.text.trim().take(100)
                viewModel.search(searchSeed)
                sheet = ReaderSheet.SEARCH
                viewModel.clearSelection()
            }
            SelectionAction.SEARCH_WEB -> {
                Share.openUrl(context, webSearchUrl(sel.text.trim()))
                viewModel.clearSelection()
            }
            SelectionAction.SPEAK -> speaker.speak(sel.text, bookLang)
            SelectionAction.SELECT_SENTENCE -> viewModel.expandSelection(paragraph = false)
            SelectionAction.SELECT_PARAGRAPH -> viewModel.expandSelection(paragraph = true)
            SelectionAction.BOOKMARK -> { viewModel.toggleBookmark(); viewModel.clearSelection() }
            SelectionAction.DICTIONARY -> {
                val opened = DictionaryLauncher.lookup(context, sel.text, viewModel.dictionaryApp().packageNames)
                if (!opened) scope.launch { snackbar.showSnackbar(resources.getString(R.string.reader_app_not_found)) }
                viewModel.clearSelection()
            }
        }
    }

    val displayControls: @Composable () -> Unit = {
        ReaderQuickPanel(
            content = data,
            common = common,
            text = textSettings.takeIf { data is ReaderContent.Text },
            manga = mangaSettings.takeIf { data is ReaderContent.Manga },
            pdf = pdfSettings.takeIf { data is ReaderContent.Pdf },
            perBook = perBook,
            vm = viewModel,
            onOpenAll = { quickPanel = false; sheet = ReaderSheet.SETTINGS },
            onClose = { quickPanel = false },
        )
    }

    val showControlsLabel = stringResource(R.string.reader_show_controls)
    Box(
        Modifier
            .fillMaxSize()
            // Loading, errors and "no text" use the page colours too: no black flash before a light page, readable messages.
            .background(if (data is ReaderContent.Text || (data == null && !imageCanvas)) Color(readerText.background) else Color.Black)
            // With the chrome hidden, a central tap is invisible to TalkBack: its actions menu offers the same command.
            .semantics {
                if (!chrome) {
                    customActions = listOf(
                        CustomAccessibilityAction(showControlsLabel) {
                            viewModel.chromeVisible.value = true
                            true
                        },
                    )
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        val now = System.currentTimeMillis()
                        // Once a second is enough to keep the screen on and the panels open; per-event writes made
                        // every finger movement recompose the whole reader.
                        if (now - interactionClock.get() > 1_000) {
                            interactionClock.set(now)
                            lastInteraction = now
                        }
                    }
                }
            }
            .edgeAdjust(
                enabled = common.edgeGestures && (common.edgeBrightness || common.edgeWarm) && (data is ReaderContent.Text || data is ReaderContent.Pdf) && !chrome && sheet == ReaderSheet.NONE && selection == null,
                allowBrightness = common.edgeBrightness,
                allowWarm = common.edgeWarm,
                startValue = { brightness ->
                    if (!brightness) common.warmFilter
                    else if (common.brightness >= 0f) common.brightness
                    else runCatching { Settings.System.getInt(context0.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
                },
                onChange = { edge = it; edgeHold = it; edgeShown = true },
                onEnd = { v ->
                    scope.launch {
                        viewModel.updateCommon { if (v.brightness) it.copy(brightness = v.value) else it.copy(warmFilter = v.value) }.join()
                        delay(200)
                        edgeHold = null
                    }
                },
            ),
    ) {
        val stateColor = if (imageCanvas) Color(0xFFE3E3E3) else Color(readerText.foreground)
        when (val s = content) {
            UiState.Loading -> Box(Modifier.fillMaxSize()) {
                ReaderSkeleton(stateColor)
                // The first open of a book lays out the whole publication as one document: say so, with progress.
                preparing?.let { p ->
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.reader_preparing_pages), color = stateColor, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.widthIn(max = 260.dp))
                    }
                }
            }
            UiState.Empty -> CompositionLocalProvider(LocalContentColor provides stateColor) {
                EmptyState(stringResource(R.string.reader_no_text))
            }
            is UiState.Error -> CompositionLocalProvider(LocalContentColor provides stateColor) {
                // A moved or deleted file, or a permission lost after reinstalling: let the user point at it again.
                val relink =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.relink(uri) }
                if (viewModel.canRelink(s.error)) {
                    ErrorState(s.error, onRetry = { relink.launch(arrayOf("*/*")) }, retryLabel = stringResource(R.string.reader_relink),
                        secondaryLabel = stringResource(CoreR.string.back), onSecondary = onBack)
                } else {
                    ErrorState(s.error, onRetry = viewModel::retry, secondaryLabel = stringResource(CoreR.string.back), onSecondary = onBack)
                }
            }
            is UiState.Data -> when (val c = s.value) {
                is ReaderContent.Text -> key(c.controller) {
                    EpubReaderView(c.controller, onAttached = { viewModel.onWebViewAttached(c.controller) })
                }
                is ReaderContent.Pdf -> PdfReaderView(c.controller, viewModel, readerText, landscape)
                is ReaderContent.Manga -> key(c.controller) {
                    MangaReaderView(
                        c.controller,
                        viewModel,
                        landscape,
                        hasPrev,
                        hasNext,
                        animationSpeed = common.animationSpeed,
                        preloadAllowed = viewModel::mayPreload,
                        verticalChapterNavigator = verticalChapterNavigator,
                    )
                }
            }
        }

        // A WebView that cannot be created reports its own error instead of the page: after a few seconds it must show.
        LaunchedEffect(data, textReady) {
            readyTimedOut = false
            if (data is ReaderContent.Text && !textReady) {
                delay(5_000)
                readyTimedOut = true
            }
        }
        // The first page of chapter 0 is laid out before the WebView can jump to the restored page: hide that moment.
        if (data is ReaderContent.Text && !textReady && !readyTimedOut) {
            Box(
                Modifier.fillMaxSize().background(Color(readerText.background)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.reader_preparing_pages),
                        color = Color(readerText.foreground),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (liveCommon.warmFilter > 0f) {
            Box(Modifier.matchParentSize().background(Color(0xFFFF8A1F).copy(alpha = (liveCommon.warmFilter * 0.35f).coerceIn(0f, 0.35f))))
        }

        EdgeAdjustPill(edge, edgeShown, Modifier.align(Alignment.Center))

        // Zoom lock: pages and comics open at the zoom of the current page. A side button, within thumb reach.
        // Hidden while a sheet or the quick panel is open: it used to hang over their top edge like a leftover.
        if (chrome && sheet == ReaderSheet.NONE && !quickPanel && (data is ReaderContent.Pdf || data is ReaderContent.Manga)) {
            val locked = if (data is ReaderContent.Pdf) pdfSettings.zoomLock else mangaSettings.zoomLock
            val label = stringResource(if (locked) R.string.reader_zoom_lock_on else R.string.reader_zoom_lock_off)
            Surface(
                onClick = {
                    if (data is ReaderContent.Pdf) viewModel.updatePdf { it.copy(zoomLock = !it.zoomLock) }
                    else viewModel.updateManga { it.copy(zoomLock = !it.zoomLock) }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(48.dp).semantics { contentDescription = label },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        label,
                        Modifier.size(22.dp),
                        tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        bilingual?.let { p ->
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding())
        }

        if (data is ReaderContent.Manga && !chrome) {
            // Below the tap zones that are shown on opening, so it doesn't cover their labels.
            OneTimeHint(
                "manga_gestures", stringResource(R.string.hint_manga_gestures),
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Dimens.xl).widthIn(max = 420.dp),
            )
        } else if (data != null && !chrome && sheet == ReaderSheet.NONE) {
            // Order: tap-zone diagram, then selection/menu text hint, then edge gestures.
            if (!tapZonesDone) {
                ReaderTapZonesHint(
                    layout = if (data is ReaderContent.Text && common.tapToTurn) common.textTapZones else TapZoneLayout.DISABLED,
                    rtl = textSettings.rtl || (data as? ReaderContent.Text)?.controller?.document?.rtl == true,
                    edgeGestures = common.edgeGestures,
                    onDone = { tapZonesDone = true; tapZonesForced = false },
                    force = tapZonesForced,
                )
            } else {
                OneTimeHint(
                    "reader_gestures", stringResource(R.string.hint_reader_gestures), Modifier.align(Alignment.Center).widthIn(max = 420.dp),
                    after = "reader_tap_zones",
                )
                if (common.edgeGestures && (data is ReaderContent.Text || data is ReaderContent.Pdf)) {
                    OneTimeHint(
                        "reader_edge_gestures", stringResource(R.string.hint_reader_edges),
                        Modifier.align(Alignment.Center).widthIn(max = 420.dp), after = "reader_gestures",
                    )
                }
            }
        }

        // Status line while reading: page, progress, chapter, clock.
        if (data != null && common.footer && sheet == ReaderSheet.NONE && (data is ReaderContent.Text || data is ReaderContent.Pdf)) {
            AnimatedVisibility(
                visible = !chrome,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ReaderFooter(
                    position = position,
                    color = Color(readerText.foreground).copy(alpha = 0.55f),
                    showClock = common.footerClock,
                    showChapter = common.footerChapter,
                    horizontalPadding = readerText.marginHorizontal.coerceIn(12, 48),
                    pill = data is ReaderContent.Pdf,
                    minutesLeft = if (data is ReaderContent.Text && position.charsLeftInBook > 0) {
                        (position.charsLeftInBook / cpm).toInt().takeIf { it in 1..6000 }
                    } else {
                        null
                    },
                )
            }
        }

        // N03: an optional software flash, only on the configured cadence and never on the first page or over a sheet.
        LaunchedEffect(position.bookPage, common.flashEveryPages) {
            val every = common.flashEveryPages
            val page = position.bookPage
            if (every > 0 && lastFlashPage > 0 && page != lastFlashPage &&
                app.yomu.domain.reader.EinkPreset.flashDue(page, every) && sheet == ReaderSheet.NONE
            ) {
                // A quick page turn cancels this effect while it waits; the preset removes the layer in `finally`.
                app.yomu.domain.reader.EinkPreset.flash { einkFlash = it }
            }
            lastFlashPage = page
        }
        if (einkFlash && sheet == ReaderSheet.NONE) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }

        // Optional vertical page slider. It commits only on release, so a slow page cannot pull the thumb back.
        if (data != null && sheet == ReaderSheet.NONE && common.navigator != app.yomu.domain.reader.NavigatorSide.OFF) {
            val pages = position.bookPages.coerceAtLeast(1)
            VerticalNavigator(
                side = common.navigator,
                progress = position.seekFraction,
                current = position.bookPage,
                total = pages,
                onCommit = { fraction -> viewModel.goToPage(navigatorPage(fraction, pages)) },
                modifier = Modifier
                    .align(navigatorAlignment(common.navigator))
                    .padding(horizontal = 2.dp),
            )
        }

        seekDrag?.let { v ->
            val preview = viewModel.seekPreview(v, cpm)
            val manga = (data as? ReaderContent.Manga)?.controller
            // A short pause first: dragging across the book must not fetch every page on the way.
            val thumbnail by produceState<File?>(null, manga, preview.page) {
                value = null
                if (manga != null) {
                    delay(150)
                    value = runCatching { manga.file(manga.globalOfCurrent(preview.page - 1)) }.getOrNull()
                }
            }
            SeekPreviewCard(preview, v, Modifier.align(Alignment.Center), thumbnail = thumbnail)
        }

        // ---------------------------------------------------------------- top bar
        AnimatedVisibility(
            visible = chrome && data != null,
            enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val bookmarked = remember(bookmarks, position) { viewModel.bookmarkHere() != null }
            ReaderTopBar(
                title = title,
                subtitle = position.chapterTitle,
                bookmarked = bookmarked,
                onBack = onBack,
                onBookmark = { haptic.perform(); viewModel.toggleBookmark() },
            )
        }

        // ---------------------------------------------------------------- bottom area
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            SnackbarHost(snackbar, Modifier.align(Alignment.CenterHorizontally))
            if (ttsActive && sheet != ReaderSheet.TTS) {
                MiniPill(Modifier.align(Alignment.CenterHorizontally)) {
                    IconButton(onClick = { if (ttsPlaying) viewModel.pauseTts() else viewModel.resumeTts() }) {
                        Icon(if (ttsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (ttsPlaying) R.string.tts_pause else R.string.tts_play))
                    }
                    IconButton(onClick = { sheet = ReaderSheet.TTS }) { Icon(Icons.Default.Headphones, stringResource(R.string.reader_tts)) }
                    IconButton(onClick = viewModel::stopTts) { Icon(Icons.Default.Stop, stringResource(R.string.tts_stop)) }
                }
            }
            if (autoScroll) {
                MiniPill(Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.reader_autoscroll_speed, common.autoScrollSpeed), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { viewModel.updateCommon { it.copy(autoScrollSpeed = (it.autoScrollSpeed - 5).coerceAtLeast(5)) } }) {
                        Icon(Icons.Default.Remove, stringResource(R.string.reader_autoscroll_slower))
                    }
                    IconButton(onClick = { viewModel.updateCommon { it.copy(autoScrollSpeed = (it.autoScrollSpeed + 5).coerceAtMost(200)) } }) {
                        Icon(Icons.Default.Add, stringResource(R.string.reader_autoscroll_faster))
                    }
                    IconButton(onClick = viewModel::stopAutoScroll) { Icon(Icons.Default.Stop, stringResource(R.string.reader_autoscroll_stop)) }
                }
            }
            if (ocrRunning) {
                MiniPill(Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.reader_ocr_running), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            resumeFrom?.let { page ->
                AnimatedVisibility(
                    visible = seekDrag == null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    MiniPill {
                        Text(
                            stringResource(R.string.reader_resumed_at, page),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        TextButton(onClick = { resumeFrom = null }) { Text(stringResource(R.string.reader_resumed_dismiss)) }
                    }
                }
            }
            // The result of a search stays on the page: previous / next / all results, without reopening the sheet.
            AnimatedVisibility(
                visible = searchState.active != null && sheet == ReaderSheet.NONE && seekDrag == null,
                enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 },
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                SearchMatchBar(
                    state = searchState,
                    onPrev = { viewModel.stepHit(-1) },
                    onNext = { viewModel.stepHit(1) },
                    onList = { searchSeed = searchState.query; sheet = ReaderSheet.SEARCH },
                    onClose = viewModel::clearSearch,
                )
            }
            val jump = jumpBack
            AnimatedVisibility(
                visible = chrome && data != null && jump != null && seekDrag == null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                JumpBackChip(page = jump?.page ?: 0, onBack = viewModel::jumpBack, onDismiss = viewModel::dismissJumpBack)
            }
            // Keep the reader actions beside the bottom controls.
            AnimatedVisibility(
                visible = chrome && data != null && sheet == ReaderSheet.NONE && !quickPanel && seekDrag == null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.align(Alignment.End).padding(end = Dimens.l, bottom = Dimens.s),
            ) {
                Surface(
                    onClick = { sheet = ReaderSheet.MORE },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.MoreVert, stringResource(CoreR.string.more))
                    }
                }
            }
            if (!landscape && chrome && quickPanel && data != null && seekDrag == null) {
                Box(Modifier.fillMaxWidth()) {
                    Popup(
                        alignment = Alignment.BottomCenter,
                        onDismissRequest = { quickPanel = false },
                        properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false),
                    ) { displayControls() }
                }
            }
            AnimatedVisibility(
                visible = chrome && data != null,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            ) {
                val pdfController = (data as? ReaderContent.Pdf)?.controller
                val textController = (data as? ReaderContent.Text)?.controller
                val viewportBookmarkMarks by produceState(
                    emptyList<Float>(), textController, bookmarks, position.viewportSeek, textReady, chapterStarts, position.bookPages,
                ) {
                    value = if (position.viewportSeek && textReady && textController != null) {
                        textController.scrollBookmarkFractions(bookmarks.map { it.locator })
                    } else {
                        emptyList()
                    }
                }
                val spread = pdfController?.visiblePages(position.page)?.takeIf { it.first < it.last }
                ReaderBottomBar(
                    position = position,
                    marks = if (position.viewportSeek) viewportBookmarkMarks else remember(bookmarks) { bookmarks.map { it.locator.totalProgression } },
                    chapterMarks = chapterStarts,
                    isText = data is ReaderContent.Text,
                    pageArrows = pdfController != null || (data is ReaderContent.Manga && !viewModel.isOnline),
                    isOnlineManga = data is ReaderContent.Manga && viewModel.isOnline,
                    spread = spread,
                    reversed = data is ReaderContent.Manga && mangaSettings.direction == MangaDirection.RTL,
                    canPrev = pdfController?.canTurnPage(false) ?: (position.chapter > 0 || hasPrev),
                    canNext = pdfController?.canTurnPage(true) ?: (position.chapter < position.chapterCount - 1 || hasNext),
                    cpm = cpm,
                    onDrag = { seekDrag = it },
                    onSeek = viewModel::seekTotal,
                    onSeekFailed = { scope.launch { snackbar.showSnackbar(resources.getString(R.string.reader_seek_failed)) } },
                    onGoToPage = { goToPage = true },
                    onPrevChapter = viewModel::prevChapter,
                    onNextChapter = viewModel::nextChapter,
                    onToc = { sheet = ReaderSheet.TOC },
                    onSearch = { searchSeed = ""; sheet = ReaderSheet.SEARCH },
                    onQuick = { quickPanel = !quickPanel },
                )
            }
        }

        // ---------------------------------------------------------------- overlays
        val sel = selection
        if (sel != null && noteFor == null) {
            val currentColor = sel.highlightId?.let { id -> highlights.firstOrNull { it.id == id }?.color }
            SelectionMenu(
                style = selectionMenuStyle,
                anchor = sel.anchor,
                actions = actions,
                customActions = selectionCustomActions,
                currentColor = currentColor,
                onHighlight = { color, style -> haptic.perform(); viewModel.highlight(color, style) },
                onAction = { onAction(it, sel) },
                onCustom = { runCustom(it, sel.text) },
                onDelete = sel.highlightId?.let { { viewModel.deleteHighlight() } },
                onDismiss = viewModel::clearSelection,
            )
        }
        pageMenu?.let { req ->
            // Only actions this engine can really perform are listed: a command that would silently do
            // nothing (a PDF page that cannot be rendered, a cover for an online entry) is not offered.
            val items = QuickActions.normalize(pageActions)
                .filter { it.enabled && viewModel.isPageActionSupported(it.action, req.context) }
                .map { cfg ->
                    QuickMenuItem(cfg.action.name, cfg.action.icon(), stringResource(cfg.action.label())) {
                        haptic.perform(); viewModel.runPageAction(req.context, cfg.action)
                    }
                }
            QuickActionMenu(
                radial = pageMenuStyle == QuickMenuStyle.RADIAL,
                anchor = req.anchor,
                items = items,
                onDismiss = { viewModel.pageMenu.value = null },
                title = stringResource(R.string.reader_page_n, req.page + 1),
            )
        }
    }

    if (landscape && chrome && quickPanel && data != null) {
        YomuSheet(onDismiss = { quickPanel = false }) { displayControls() }
    }

    noteFor?.let { sel ->
        val key = remember(sel) { viewModel.noteDraftKey(sel) }
        NoteDialog(
            initial = remember(sel) { viewModel.noteOfSelection(sel).orEmpty() },
            quote = sel.text,
            draft = remember(key) { viewModel.noteDraft(key) },
            onDraft = { viewModel.saveNoteDraft(sel, it) },
            onSave = { viewModel.saveNote(sel, it) },
            onDelete = sel.highlightId?.let { id ->
                {
                    viewModel.discardNoteDraft(key)
                    viewModel.deleteHighlight(id)
                    noteFor = null
                }
            },
            onDismiss = { noteFor = null; viewModel.clearSelection() },
        )
    }

    pageNote?.let { target ->
        NoteDialog(
            initial = "",
            quote = null,
            draft = remember(target) { viewModel.pageNoteDraft(target) },
            onDraft = { viewModel.savePageNoteDraft(target, it) },
            onSave = { viewModel.addPageNote(target, it) },
            onDelete = null,
            onDismiss = { pageNote = null },
        )
    }

    lookup?.let { req ->
        val wordVm: WordLookupViewModel = koinViewModel()
        LaunchedEffect(req) { wordVm.lookup(req) }
        WordPopup(
            viewModel = wordVm,
            anchor = lookupAnchor,
            onDismiss = { lookup = null },
            onTranslate = { lookup = null; viewModel.translateText.value = it },
            onOpenDictionary = { lookup = null; onOpenDictionary(it) },
        )
    }

    translateText?.let { text ->
        val trVm: TranslationViewModel = koinViewModel()
        TranslationSheet(
            viewModel = trVm,
            text = text,
            sourceLang = bookLang,
            onDismiss = { viewModel.translateText.value = null },
            onSaveWord = { term, translated, lang -> viewModel.saveWord(term, translated, lang) },
            onOpenPacks = onOpenPacks,
        )
    }

    val markedPages = remember(bookmarks) { bookmarks.map { it.locator.chapter }.toSet() }
    when (sheet) {
        ReaderSheet.NONE -> Unit
        ReaderSheet.TOC -> TocSheet(
            toc = toc,
            currentChapter = position.chapter,
            bookmarks = bookmarks,
            highlights = highlights,
            onToc = { viewModel.goToToc(it); sheet = ReaderSheet.NONE },
            onLocator = { viewModel.goToLocator(it); sheet = ReaderSheet.NONE },
            onDeleteBookmark = viewModel::deleteBookmark,
            onOpenAll = if (viewModel.bookId > 0) ({ sheet = ReaderSheet.NONE; onOpenAnnotations(viewModel.bookId) }) else null,
            onDismiss = { sheet = ReaderSheet.NONE },
        )
        ReaderSheet.SEARCH -> {
            SearchSheet(
                state = searchState,
                initial = searchSeed.ifBlank { searchState.query },
                onSearch = viewModel::search,
                onHit = { viewModel.openHit(it); sheet = ReaderSheet.NONE },
                onDismiss = { sheet = ReaderSheet.NONE; searchSeed = "" },
                onClear = viewModel::clearSearch,
            )
        }
        ReaderSheet.JUMPS -> RecentJumpsSheet(
            history = jumpHistory,
            onOpen = { viewModel.openJump(it); sheet = ReaderSheet.NONE },
            onDismiss = { sheet = ReaderSheet.NONE },
        )
        ReaderSheet.SETTINGS -> data?.let { c ->
            ReaderSettingsSheet(
                viewModel, c, textSettings, mangaSettings, pdfSettings, common, perBook, wordTapSetting,
                onDismiss = { sheet = ReaderSheet.NONE },
                previewText = readerText,
                previewSnippet = previewSnippet,
            )
        }
        ReaderSheet.TTS -> TtsSheet(viewModel, common, onDismiss = { sheet = ReaderSheet.NONE })
        ReaderSheet.MORE -> YomuSheet(onDismiss = { sheet = ReaderSheet.NONE }, skipPartial = false) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { moreMenu { sheet = ReaderSheet.NONE } }
        }
        ReaderSheet.PAGES -> when (val c = data) {
            is ReaderContent.Pdf -> PagesSheet(
                count = c.controller.pageCount,
                current = position.page,
                onPick = { viewModel.goToPage(it + 1); sheet = ReaderSheet.NONE },
                onDismiss = { sheet = ReaderSheet.NONE },
                marked = markedPages,
            ) { index -> PdfPageThumb(c.controller, index) }
            is ReaderContent.Manga -> PagesSheet(
                count = c.controller.localCount,
                current = position.page,
                onPick = { viewModel.goToPage(it + 1); sheet = ReaderSheet.NONE },
                onDismiss = { sheet = ReaderSheet.NONE },
                marked = if (viewModel.isOnline) emptySet() else markedPages,
            ) { index -> MangaPageThumb(c.controller, c.controller.globalOfCurrent(index)) }
            else -> Unit
        }
    }

    footnote?.let { html -> FootnoteDialog(html) { viewModel.footnote.value = null } }
    image?.let { model -> ImageViewerDialog(model) { viewModel.image.value = null } }
    if (passwordNeeded) PasswordDialog(onSubmit = viewModel::submitPassword, onDismiss = onBack)
    if (showFinished) FinishedDialog(onBack = { showFinished = false; onBack() }, onDismiss = { showFinished = false })
    if (goToPage && data != null) {
        GoToPageDialog(
            page = position.bookPage,
            pages = position.bookPages.coerceAtLeast(1),
            approximate = position.approximate,
            percent = (position.seekFraction * 100).toInt().coerceIn(0, 100),
            onPage = { viewModel.goToPage(it); goToPage = false },
            onPercent = { viewModel.seekTotal(it); goToPage = false },
            onDismiss = { goToPage = false },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String?,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreR.string.back)) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank() && subtitle != title) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val bookmarkLabel =
                stringResource(if (bookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark)
            IconButton(onClick = onBookmark, modifier = Modifier.semantics { stateDescription = bookmarkLabel }) {
                Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, bookmarkLabel,
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    position: ReaderPosition,
    marks: List<Float>,
    /** Chapter boundaries on the slider, 0..1 of the book. */
    chapterMarks: List<Float>,
    isText: Boolean,
    pageArrows: Boolean,
    isOnlineManga: Boolean,
    spread: IntRange?,
    /** Right-to-left manga: the slider starts on the right, like the pages. */
    reversed: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    cpm: Float,
    onDrag: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFailed: () -> Unit,
    onGoToPage: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onQuick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            var dragging by remember { mutableStateOf<Float?>(null) }
            var pending by remember { mutableStateOf<PendingReaderSeek?>(null) }
            val interactionSource = remember { MutableInteractionSource() }
            val reportDrag by rememberUpdatedState(onDrag)
            val seekFailed by rememberUpdatedState(onSeekFailed)
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect {
                    if (it is DragInteraction.Cancel) {
                        dragging = null
                        reportDrag(null)
                    }
                }
            }
            DisposableEffect(Unit) { onDispose { reportDrag(null) } }
            LaunchedEffect(pending) {
                if (pending != null) {
                    delay(3_000)
                    pending = null
                    seekFailed()
                }
            }
            LaunchedEffect(pending, position.seekFraction, position.seekPageCount) {
                val target = pending?.fraction ?: return@LaunchedEffect
                val tolerance = if (position.seekPageCount > 0) 0.0001f else 0.02f
                if (kotlin.math.abs(position.seekFraction - target) <= tolerance) pending = null
            }
            val shown = dragging ?: pending?.fraction ?: position.seekFraction
            val percent = (shown * 100).toInt().coerceIn(0, 100)
            val progressLabel = stringResource(R.string.reader_progress_percent, percent)
            val scopeLabel = if (isOnlineManga) stringResource(R.string.reader_chapter_progress, percent) else progressLabel
            // Where am I: page of the book (tap to jump to a page) and how much is left.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevChapter, enabled = canPrev) {
                    Icon(Icons.Default.SkipPrevious, stringResource(if (pageArrows) R.string.reader_prev_page else R.string.reader_prev_chapter))
                }
                if (position.bookPages > 0) {
                    PageChip(position.bookPage, position.bookPages, position.approximate, onGoToPage, spread = spread)
                } else {
                    Text(stringResource(R.string.reader_page_of, position.page + 1, position.pages.coerceAtLeast(1)), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                val minutesLeft = (position.charsLeftInBook / cpm.coerceAtLeast(1f)).toInt()
                val left = if (isText && minutesLeft in 1..6000) {
                    stringResource(R.string.reader_left_book, formatMinutes(minutesLeft))
                } else null
                Text(listOfNotNull(left, scopeLabel).joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                IconButton(onClick = onNextChapter, enabled = canNext) {
                    Icon(Icons.Default.SkipNext, stringResource(if (pageArrows) R.string.reader_next_page else R.string.reader_next_chapter))
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    if (chapterMarks.isNotEmpty()) {
                        // Chapter starts, so the book's structure is visible on the track.
                        val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        Canvas(Modifier.matchParentSize().padding(horizontal = 2.dp)) {
                            val w = 1.dp.toPx()
                            chapterMarks.forEach { m ->
                                val x = size.width * m.coerceIn(0f, 1f).let { if (reversed) 1f - it else it }
                                drawRect(tick, topLeft = Offset(x - w / 2, size.height / 2 - 8.dp.toPx()), size = Size(w, 16.dp.toPx()))
                            }
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides if (reversed) LayoutDirection.Rtl else LocalLayoutDirection.current) {
                        Slider(
                            value = shown,
                            onValueChange = { dragging = it; onDrag(it) },
                            onValueChangeFinished = {
                                val v = dragging
                                dragging = null
                                if (v != null) {
                                    val target = if (position.seekPageCount > 0) {
                                        pageSeekFraction(pageAtSeekFraction(v, position.seekPageCount), position.seekPageCount)
                                    } else {
                                        v
                                    }
                                    pending = PendingReaderSeek(target)
                                    onSeek(target)
                                }
                                onDrag(null)
                            },
                            enabled = position.bookPages > 1,
                            interactionSource = interactionSource,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = scopeLabel },
                        )
                    }
                    if (marks.isNotEmpty()) {
                        // Bookmarks as small ticks above the track.
                        val tick = MaterialTheme.colorScheme.tertiary
                        Canvas(Modifier.matchParentSize().padding(horizontal = 2.dp)) {
                            val w = 3.dp.toPx()
                            val h = 8.dp.toPx()
                            marks.forEach { m ->
                                val x = size.width * m.coerceIn(0f, 1f).let { if (reversed) 1f - it else it }
                                drawRect(tick, topLeft = Offset(x - w / 2, 2.dp.toPx()), size = Size(w, h))
                            }
                        }
                    }
            }
            // Chapter and page details of a book; comics and PDFs have the page chip above and no chapter of their own.
            if (isText) {
                val chapterMin = (position.charsLeftInChapter / cpm.coerceAtLeast(1f)).toInt()
                val info = buildList {
                    position.chapterTitle?.takeIf { it.isNotBlank() }?.let(::add)
                    if (position.chapterCount > 1) add(stringResource(R.string.reader_chapter_of, position.chapter + 1, position.chapterCount))
                    if (position.pages > 1) add(stringResource(R.string.reader_page_in_chapter, position.page + 1, position.pages))
                    if (position.charsLeftInChapter > 0 && position.chapterCount > 1) add(stringResource(R.string.reader_left_chapter, formatMinutes(chapterMin)))
                }.joinToString(" · ")
                if (info.isNotEmpty()) {
                    Text(info, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ChromeButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.reader_toc), onToc, Modifier.weight(1f))
                ChromeButton(Icons.Default.Search, stringResource(R.string.reader_search), onSearch, Modifier.weight(1f))
                ChromeButton(Icons.Default.SettingsIcon, stringResource(R.string.reader_quick_display), onQuick, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun formatMinutes(min: Int): String =
    if (min >= 60) stringResource(R.string.reader_hours_minutes, min / 60, min % 60) else stringResource(R.string.reader_minutes, min.coerceAtLeast(1))

@Composable
private fun ChromeButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}

/** A group title of the "More" sheet: the commands are grouped, not one flat wall of rows. */
@Composable
private fun MoreGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.xl, end = Dimens.xl, top = Dimens.s, bottom = Dimens.xxs),
    )
}

/** One command of the "More" sheet. */
@Composable
private fun MoreAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.touch).clickable(onClick = onClick)
            .padding(horizontal = Dimens.xl, vertical = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.l),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private class PendingReaderSeek(val fraction: Float)

@Composable
private fun MiniPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 4.dp, shadowElevation = 4.dp,
        modifier = modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) { content() }
    }
}

@Composable
private fun ReaderSkeleton(textColor: Color) {
    // Lines tinted with the theme's text colour, so a dark theme doesn't flash bright bars while the book opens.
    val base = textColor.copy(alpha = 0.12f)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        SkeletonBox(Modifier.fillMaxWidth(0.6f).height(28.dp), base)
        Spacer(Modifier.height(24.dp))
        repeat(14) { i ->
            SkeletonBox(Modifier.fillMaxWidth(if (i % 5 == 4) 0.55f else 1f).height(14.dp), base)
            Spacer(Modifier.height(12.dp))
        }
    }
}
