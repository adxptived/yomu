package app.yomu.navigation

import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.yomu.MainEvent
import app.yomu.MainViewModel
import app.yomu.R
import app.yomu.core.common.ErrorLog
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.components.FloatingNavBarDefaults
import app.yomu.core.ui.components.IncognitoBanner
import app.yomu.core.ui.components.LocalNavAnimatedScope
import app.yomu.core.ui.components.LocalNavBarHidden
import app.yomu.core.ui.components.LocalNavBarInset
import app.yomu.core.ui.components.LocalSharedTransitionScope
import app.yomu.core.ui.components.NavTab
import app.yomu.core.ui.components.SkeletonList
import app.yomu.core.ui.components.errorMessage
import app.yomu.core.ui.components.item
import app.yomu.data.audio.YouTubeAuth
import app.yomu.data.repository.toJson
import app.yomu.data.source.SourceManager
import app.yomu.domain.model.BookFormat
import app.yomu.domain.model.TextReaderSettings
import app.yomu.domain.repository.BookRepository
import app.yomu.feature.annotations.AnnotationsScreen
import app.yomu.feature.audio.AudioController
import app.yomu.feature.audio.AudioPlayerScreen
import app.yomu.feature.audio.NowPlayingBar
import app.yomu.feature.catalog.BrowseArgs
import app.yomu.feature.catalog.CatalogHomeViewModel
import app.yomu.feature.catalog.CatalogScreen
import app.yomu.feature.catalog.ExtensionsScreen
import app.yomu.feature.catalog.GlobalSearchScreen
import app.yomu.feature.catalog.OnlineLibraryPane
import app.yomu.feature.catalog.SearchArgs
import app.yomu.feature.catalog.SourceBrowseScreen
import app.yomu.feature.catalog.SourceBrowseViewModel
import app.yomu.feature.catalog.SourceConnectionScreen
import app.yomu.feature.catalog.SourceSettingsScreen
import app.yomu.feature.catalog.TitleScreen
import app.yomu.feature.catalog.TrackerTitleScreen
import app.yomu.feature.catalog.UpcomingScreen
import app.yomu.feature.catalog.WebViewScreen
import app.yomu.feature.dictionary.DictionariesScreen
import app.yomu.feature.dictionary.DictionaryScreen
import app.yomu.feature.dictionary.ReviewScreen
import app.yomu.feature.downloads.DownloadsScreen
import app.yomu.feature.editor.EditorScreen
import app.yomu.feature.library.BookDetailsScreen
import app.yomu.feature.library.LibraryScreen
import app.yomu.feature.library.OnlineLibrary
import app.yomu.feature.reader.DocxLayoutScreen
import app.yomu.feature.reader.ReaderArgs
import app.yomu.feature.reader.ReaderScreen
import app.yomu.feature.reader.ReadingStatsScreen
import app.yomu.feature.settings.AppUpdates
import app.yomu.feature.settings.CatalogUpdateBanner
import app.yomu.feature.settings.SettingsLinks
import app.yomu.feature.settings.SettingsScreen
import app.yomu.feature.settings.SettingsSection
import app.yomu.feature.translator.LanguagePacksScreen
import app.yomu.profile.ProfileScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The screens of the [NavTab]s, all inside [HomeRoute] (see [TabsHost]); [trailName] names them in crash reports. */
enum class TopLevel(val tab: NavTab, val trailName: String) {
    LIBRARY(NavTab.LIBRARY, "Library tab"),
    HISTORY(NavTab.HISTORY, "Profile tab"),
    CATALOG(NavTab.CATALOG, "Catalog tab"),
    DICTIONARY(NavTab.DICTIONARY, "Dictionary tab"),
    SETTINGS(NavTab.SETTINGS, "Settings tab"),
    ;

    companion object {
        fun of(tab: NavTab): TopLevel = entries.first { it.tab == tab }
    }
}

/**
 * A destination in the crash reports (1.9.0): the name of its route and its numeric arguments (ids), never texts such
 * as search queries, addresses or positions in a book.
 */
internal fun trailName(destination: NavDestination, arguments: Bundle?): String {
    val name = destination.route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.') ?: "?"
    val ids = arguments?.keySet().orEmpty().sorted().mapNotNull { key ->
        @Suppress("DEPRECATION")
        when (val value = arguments?.get(key)) {
            is Long -> value.takeIf { it >= 0 }?.let { "$key=$it" }
            is Int -> value.takeIf { it >= 0 }?.let { "$key=$it" }
            else -> null
        }
    }
    return (listOf(name) + ids).joinToString(" ")
}

/**
 * App shell: adaptive navigation (bottom bar / rail) for the top-level tabs and all other screens on top.
 * [tabs] are the tabs of the bar in the user's order; the first one is the screen the app opens with. Hidden tabs keep
 * their destinations (the library still links to the catalog and the settings).
 */
@Composable
fun YomuNavigation(main: MainViewModel, reduceMotion: Boolean, navLabels: Boolean = true, tabs: List<NavTab> = NavTab.entries) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val shown = tabs.ifEmpty { NavTab.entries }.map(TopLevel::of)
    // The tab the app opens with is fixed for the lifetime of the screen: a new order applies from the next start.
    val tabsState = rememberSaveable(saver = TabsState.Saver) { TabsState(shown.first()) }
    val onHome = entry?.destination?.hasRoute(HomeRoute::class) == true
    // The reader and the editor have the whole screen (text, pages, the editing canvas); every other screen keeps the
    // bar, so another tab is always one tap away instead of a Back and a tap.
    val immersive = entry?.destination?.let { it.hasRoute(ReaderRoute::class) || it.hasRoute(EditorRoute::class) } == true
    val context = LocalContext.current
    val resources = LocalResources.current

    // The screen on display for the crash reports, set before the new screen is composed (a crash there names it).
    DisposableEffect(nav, tabsState) {
        val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
            ErrorLog.screen(if (destination.hasRoute(HomeRoute::class)) tabsState.selected.trailName else trailName(destination, arguments))
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }

    LaunchedEffect(main, nav) {
        main.events.collect { e ->
            when (e) {
                is MainEvent.OpenBook -> nav.navigate(ReaderRoute(bookId = e.bookId))
                is MainEvent.Lookup -> nav.navigate(LookupRoute(e.text))
                is MainEvent.AddExtensionStore -> nav.navigate(ExtensionsRoute(addStore = e.url))
                MainEvent.OpenDownloads -> nav.navigate(DownloadsRoute) { launchSingleTop = true }
                MainEvent.OpenCatalog -> tabsState.select(TopLevel.CATALOG)
                is MainEvent.OpenChapter -> nav.navigate(ReaderRoute(chapterId = e.chapterId))
                is MainEvent.OpenTitle -> nav.navigate(TitleRoute(e.entryId))
                is MainEvent.AddSource -> nav.navigate(SourceConnectionRoute(e.url))
                is MainEvent.Imported -> {
                    val text = when {
                        e.error != null && e.added == 0 -> errorMessage(context, e.error)
                        else -> resources.getQuantityString(R.plurals.import_result, e.added, e.added) +
                            if (e.failed > 0) " · " + resources.getString(R.string.import_failed_count, e.failed) else ""
                    }
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val adaptive = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val update by main.updateAvailable.collectAsStateWithLifecycle()
    val openUpdates by main.openUpdates.collectAsStateWithLifecycle()
    val items = shown.map { top -> top.tab.item().let { if (top.tab == NavTab.SETTINGS && update != null) it.copy(badge = true) else it } }
    // A top-level screen can hide the bar for a while (the library's own bar for selected books takes its place).
    val barHidden = remember { mutableStateOf(false) }
    // The floating bar is drawn over the top-level screens; they leave this much room at the bottom of their lists.
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barInset = if (adaptive == NavigationSuiteType.NavigationBar) FloatingNavBarDefaults.height(navLabels) + navBarsBottom else 0.dp
    val incognito by main.incognito.collectAsStateWithLifecycle()
    // The bar is on every screen but the reader and the editor. On the tabs themselves the library's own bar for
    // selected books can take its place for a while; there is nothing to replace on a detail screen, so it stays.
    val barVisible = !immersive && (!onHome || !barHidden.value)
    // Every screen but the reader, which keeps the whole screen for the text.
    val showIncognito = incognito && entry?.destination?.hasRoute(ReaderRoute::class) != true
    // An audiobook playing in the background: a bar on every screen but its player and the reader.
    val audio: AudioController = koinInject()
    // The position is not part of this state (it changes every second): the bar reads it on its own, so only the bar
    // recomposes while the numbers move.
    val nowPlaying by audio.nowPlaying.collectAsStateWithLifecycle(null)
    val playing = nowPlaying?.takeIf { entry?.destination?.hasRoute(ReaderRoute::class) != true }
    Column(Modifier.fillMaxSize()) {
        if (showIncognito) IncognitoBanner(onTurnOff = { main.setIncognito(false) })
        val topTaken = showIncognito
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(if (topTaken) WindowInsets.statusBars else WindowInsets(0, 0, 0, 0))) {
            // The mini player floats above the navigation bar; the screens leave room for it in their lists.
            val density = LocalDensity.current
            var miniHeight by remember(density) { mutableStateOf(MINI_PLAYER_ROOM) }
            Shell(adaptive, onHome, barVisible, tabsState.selected, barHidden, items, shown, navLabels, tabsState, barInset + if (playing != null) miniHeight else 0.dp, nav, reduceMotion, main)
            playing?.let { np ->
                MiniPlayer(
                    audio, np,
                    onOpen = { nav.navigate(ReaderRoute(bookId = np.bookId)) { launchSingleTop = true } },
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).padding(
                        // Above the bar; a tab with its own bottom bar (library selection) keeps the same room, so
                        // the mini player never sits on top of those actions.
                        bottom = if (adaptive == NavigationSuiteType.NavigationBar && (barVisible || onHome)) barInset else navBarsBottom,
                    ).onSizeChanged { miniHeight = with(density) { it.height.toDp() } },
                )
            }
        }
    }
}

/** Room the mini player needs above the floating navigation bar (cover row and the seek line). */
private val MINI_PLAYER_ROOM = 88.dp

/** The mini player alone collects the moving position, so the rest of the shell does not recompose every second. */
@Composable
private fun MiniPlayer(
    audio: AudioController,
    np: app.yomu.feature.audio.PlayerSnapshot,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live by audio.miniState.collectAsStateWithLifecycle(np)
    NowPlayingBar(
        live?.takeIf { it.bookId == np.bookId } ?: np,
        onOpen = onOpen,
        onToggle = audio::togglePlay,
        onStop = audio::stop,
        onSeek = audio::seekTo,
        modifier = modifier,
    )
}

@Composable
private fun Shell(
    adaptive: NavigationSuiteType,
    onHome: Boolean,
    barVisible: Boolean,
    selectedTab: TopLevel,
    barHidden: androidx.compose.runtime.MutableState<Boolean>,
    items: List<app.yomu.core.ui.components.NavItem>,
    shown: List<TopLevel>,
    navLabels: Boolean,
    tabsState: TabsState,
    barInset: androidx.compose.ui.unit.Dp,
    nav: NavHostController,
    reduceMotion: Boolean,
    main: MainViewModel,
) {
    NavShell(
        type = adaptive,
        visible = barVisible,
        items = items,
        selected = shown.indexOf(selectedTab),
        showLabels = navLabels,
        onSelect = { index ->
            shown.getOrNull(index)?.let { target ->
                // From a detail screen the tab goes back to the tabs: switching the hidden tab alone would leave the
                // same screen on top and make the tap look dead.
                if (!onHome) nav.popBackStack(HomeRoute, inclusive = false)
                tabsState.select(target)
            }
        },
        // A press already composes a tab never opened: by the time the finger lifts, it is ready to be shown.
        onPress = { index -> shown.getOrNull(index)?.let(tabsState::prepare) },
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalNavBarHidden provides barHidden,
                LocalTabBarInset provides barInset,
                // Every screen draws under the floating bar and leaves room for it in its lists, FABs and snackbars.
                LocalNavBarInset provides if (barVisible) barInset else 0.dp,
            ) {
                YomuNavHost(nav, reduceMotion, tabsState, main)
            }
        }
    }
}

@Composable
private fun YomuNavHost(nav: NavHostController, reduceMotion: Boolean, tabs: TabsState, main: MainViewModel) {
    val back: () -> Unit = { nav.popBackStack() }
    val openUpdates by main.openUpdates.collectAsStateWithLifecycle()
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.97f) },
        exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(90)) },
        // During a predictive back gesture NavHost seeks these two with the finger: the screen shrinks away like a card
        // and the one below comes up from a slight zoom, the Android 14+ "peek" look.
        popEnterTransition = {
            if (reduceMotion) EnterTransition.None else fadeIn(tween(240)) + scaleIn(tween(300), initialScale = 0.94f)
        },
        popExitTransition = {
            if (reduceMotion) ExitTransition.None else fadeOut(tween(220, delayMillis = 60)) + scaleOut(tween(300), targetScale = 0.88f)
        },
    ) {
        // ---------------------------------------------------------------- tabs
        val settingsLinks = SettingsLinks(
            onOpenDictionaries = { nav.navigate(DictionariesRoute) },
            onOpenLanguagePacks = { nav.navigate(LanguagePacksRoute) },
            onOpenExtensions = { nav.navigate(ExtensionsRoute()) },
            onOpenDownloads = { nav.navigate(DownloadsRoute) },
            onOpenStats = { nav.navigate(StatsRoute) },
            onOpenYouTubeLogin = { nav.navigate(WebViewRoute(YouTubeAuth.SIGN_IN_URL)) },
        )
        screen<HomeRoute> {
            val wide = windowWidthDp() >= TWO_PANE_MIN_WIDTH_DP.dp
            var settingsPane by rememberSaveable { mutableStateOf(SettingsSection.APPEARANCE.name) }
            TabsHost(tabs, reduceMotion) { tab ->
                CompositionLocalProvider(LocalNavBarInset provides LocalTabBarInset.current) {
                    when (tab) {
                        TopLevel.LIBRARY -> LibraryTab(nav, tabs)
                        TopLevel.HISTORY -> ProfileScreen(
                            viewModel = koinViewModel(),
                            historyViewModel = koinViewModel(),
                            statsViewModel = koinViewModel(),
                            onOpenBook = { nav.navigate(ReaderRoute(bookId = it)) },
                            onBookDetails = { nav.navigate(BookDetailsRoute(it)) },
                            onOpenTrackerPage = { service, id -> nav.navigate(TrackerTitleRoute(service.id, id)) },
                            onReadChapter = { nav.navigate(ReaderRoute(chapterId = it)) },
                            onOpenTitle = { nav.navigate(TitleRoute(it)) },
                            onOpenSettings = { section ->
                                if (wide) {
                                    settingsPane = section.name
                                    tabs.select(TopLevel.SETTINGS)
                                } else {
                                    nav.navigate(SettingsSectionRoute(section.name))
                                }
                            },
                            onOpenDownloads = { nav.navigate(DownloadsRoute) },
                            // The library-wide list: annotations of every book, not only the one just read.
                            onOpenNotes = { nav.navigate(AnnotationsRoute(bookId = -1)) },
                            onOpenLibrary = { tabs.select(TopLevel.LIBRARY) },
                            onOpenCatalog = { tabs.select(TopLevel.CATALOG) },
                        )
                        TopLevel.CATALOG -> CatalogScreen(
                            viewModel = koinViewModel(),
                            updateBanner = { CatalogUpdateBanner(koinInject<AppUpdates>()) },
                            openUpdatesTab = openUpdates,
                            onUpdatesTabOpened = main::updatesOpened,
                            onOpenSource = { nav.navigate(SourceRoute(it)) },
                            onOpenTitle = { nav.navigate(TitleRoute(it)) },
                            onReadChapter = { nav.navigate(ReaderRoute(chapterId = it)) },
                            onGlobalSearch = { nav.navigate(GlobalSearchRoute()) },
                            onOpenExtensions = { nav.navigate(ExtensionsRoute()) },
                            onOpenDownloads = { nav.navigate(DownloadsRoute) },
                            onOpenWebView = { nav.navigate(WebViewRoute(it)) },
                            onOpenSourceSettings = { nav.navigate(SourceSettingsRoute(it)) },
                            onAddSource = { nav.navigate(SourceConnectionRoute()) },
                            onOpenUpdatesPlan = { nav.navigate(UpcomingRoute) },
                        )
                        TopLevel.DICTIONARY -> DictionaryScreen(
                            viewModel = koinViewModel(),
                            wordsViewModel = koinViewModel(),
                            onOpenDictionaries = { nav.navigate(DictionariesRoute) },
                            onStartReview = { nav.navigate(ReviewRoute) },
                            onOpenBook = { id, locator, reference -> nav.navigate(ReaderRoute(bookId = id, locator = locator?.toJson(), positionReference = reference)) },
                        )
                        TopLevel.SETTINGS -> if (wide) {
                            SettingsTwoPane(
                                selected = SettingsSection.entries.firstOrNull { it.name == settingsPane } ?: SettingsSection.APPEARANCE,
                                onSelect = { settingsPane = it.name },
                                links = settingsLinks,
                            )
                        } else {
                            SettingsScreen(viewModel = koinViewModel(), links = settingsLinks)
                        }
                    }
                }
            }
        }
        screen<SettingsSectionRoute> { entry ->
            val section = entry.toRoute<SettingsSectionRoute>().section
            SettingsScreen(
                viewModel = koinViewModel(),
                links = settingsLinks,
                onBack = back,
                initialSection = SettingsSection.entries.firstOrNull { it.name == section },
            )
        }

        // ---------------------------------------------------------------- library & reading
        screen<BookDetailsRoute> { entry ->
            val r = entry.toRoute<BookDetailsRoute>()
            BookDetailsScreen(
                viewModel = koinViewModel { parametersOf(r.bookId) },
                onBack = back,
                onRead = { nav.navigate(ReaderRoute(bookId = it.id)) },
                onOpenEditor = { nav.navigate(EditorRoute(it.id)) },
                onOpenAnnotations = { nav.navigate(AnnotationsRoute(it.id)) },
                onOpenPageLayout = { nav.navigate(DocxLayoutRoute(it.id)) },
                onOpenTrackerPage = { service, id -> nav.navigate(TrackerTitleRoute(service.id, id)) },
            )
        }
        screen<ReaderRoute> { entry ->
            val r = entry.toRoute<ReaderRoute>()
            // Audiobooks open in the player; everything else in the reader.
            val books: BookRepository = koinInject()
            val isAudio by produceState<Boolean?>(if (r.bookId < 0) false else null, r.bookId) {
                if (r.bookId >= 0) value = runCatching { books.getBook(r.bookId)?.format?.isAudio == true }.getOrDefault(false)
            }
            // A Word document can open directly in the printed-page layout (Settings → Reader), unless the reading
            // mode chosen for the book is a continuous one: "scroll" and "long strip" are promises the page layout
            // cannot keep, so they win over the default view.
            val appSettings: AppSettings = koinInject()
            val docxPages by produceState<Boolean?>(if (r.bookId < 0) false else null, r.bookId) {
                if (r.bookId >= 0) {
                    val book = runCatching { books.getBook(r.bookId) }.getOrNull()
                    // A locator or chapter means "open exactly here" (an annotation, Continue): the page layout
                    // cannot honour it, so the text reader opens instead.
                    val exactPosition = r.locator != null || r.chapterId >= 0 || r.positionReference != null
                    val text = runCatching { appSettings.textSettings.get() }.getOrDefault(TextReaderSettings())
                    val continuous = text.longStrip || !text.paged
                    value = book?.format == BookFormat.DOCX && !continuous && !exactPosition &&
                        runCatching { appSettings.docxPageLayout.get() }.getOrDefault(false)
                }
            }
            when {
                isAudio == null || docxPages == null -> Unit
                isAudio == true -> AudioPlayerScreen(
                    koinViewModel { parametersOf(r.bookId) },
                    back,
                    onYouTubeSignIn = { nav.navigate(WebViewRoute(YouTubeAuth.SIGN_IN_URL)) },
                    onOpenLinked = { nav.navigate(ReaderRoute(bookId = it)) { launchSingleTop = true } },
                )
                docxPages == true -> DocxLayoutScreen(
                    koinViewModel { parametersOf(r.bookId) }, back,
                    onOpenText = { nav.navigate(ReaderRoute(bookId = r.bookId)) { popUpTo<DocxLayoutRoute> { inclusive = true } } },
                )
                else -> ReaderScreen(
                    viewModel = koinViewModel { parametersOf(ReaderArgs(bookId = r.bookId, locatorJson = r.locator, chapterId = r.chapterId, positionReference = r.positionReference, preview = r.preview)) },
                    onBack = back,
                    onOpenAnnotations = { nav.navigate(AnnotationsRoute(it)) },
                    onOpenDictionary = { nav.navigate(LookupRoute(it)) },
                    onOpenPacks = { nav.navigate(LanguagePacksRoute) },
                    onOpenStats = { nav.navigate(StatsRoute) },
                    onOpenEditor = { nav.navigate(EditorRoute(it)) },
                    onOpenPageLayout = { nav.navigate(DocxLayoutRoute(it)) },
                    onOpenLinked = { nav.navigate(ReaderRoute(bookId = it)) { launchSingleTop = true } },
                )
            }
        }
        screen<DocxLayoutRoute> { entry ->
            val r = entry.toRoute<DocxLayoutRoute>()
            DocxLayoutScreen(
                koinViewModel { parametersOf(r.bookId) }, back,
                onOpenText = { nav.navigate(ReaderRoute(bookId = r.bookId)) { popUpTo<DocxLayoutRoute> { inclusive = true } } },
            )
        }
        screen<AnnotationsRoute> { entry ->
            val r = entry.toRoute<AnnotationsRoute>()
            AnnotationsScreen(
                viewModel = koinViewModel { parametersOf(r.bookId) },
                onBack = back,
                onOpen = { bookId, locator, reference ->
                    // Coming from the reader of the same book: go back to it at the new position.
                    nav.navigate(ReaderRoute(bookId = bookId, locator = locator?.toJson(), positionReference = reference)) {
                        popUpTo<ReaderRoute> { inclusive = true }
                    }
                },
            )
        }
        screen<StatsRoute> { ReadingStatsScreen(koinViewModel(), back) }
        screen<EditorRoute> { entry ->
            val r = entry.toRoute<EditorRoute>()
            EditorScreen(koinViewModel { parametersOf(r.bookId) }, back)
        }

        // ---------------------------------------------------------------- dictionary & translation
        screen<LookupRoute> { entry ->
            val r = entry.toRoute<LookupRoute>()
            DictionaryScreen(
                viewModel = koinViewModel(),
                wordsViewModel = koinViewModel(),
                onOpenDictionaries = { nav.navigate(DictionariesRoute) },
                onStartReview = { nav.navigate(ReviewRoute) },
                onOpenBook = { id, locator, reference -> nav.navigate(ReaderRoute(bookId = id, locator = locator?.toJson(), positionReference = reference)) },
                initialQuery = r.query,
            )
        }
        screen<DictionariesRoute> { DictionariesScreen(koinViewModel(), back) }
        screen<ReviewRoute> { ReviewScreen(koinViewModel(), back) }
        screen<LanguagePacksRoute> { LanguagePacksScreen(koinViewModel(), back) }

        // ---------------------------------------------------------------- catalog
        screen<SourceRoute> { entry ->
            val r = entry.toRoute<SourceRoute>()
            val ready by koinInject<SourceManager>().ready.collectAsStateWithLifecycle()
            if (!ready) {
                SkeletonList(Modifier.fillMaxSize())
                return@screen
            }
            // Wide windows keep the grid and show the tapped title in a pane beside it.
            val twoPane = windowWidthDp() >= 840.dp
            var paneTitle by rememberSaveable { mutableStateOf<Long?>(null) }
            val pane = paneTitle
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxSize()) {
                    SourceBrowseScreen(
                        viewModel = koinViewModel<SourceBrowseViewModel> { parametersOf(BrowseArgs(r.sourceId, r.query, r.navUrl, r.navTitle, r.tab)) },
                        onBack = back,
                        onOpenTitle = { if (twoPane) paneTitle = it else nav.navigate(TitleRoute(it)) },
                        onOpenNavigation = { sourceId, url, title -> nav.navigate(SourceRoute(sourceId, navUrl = url, navTitle = title)) },
                        onOpenWebView = { nav.navigate(WebViewRoute(it)) },
                        // Another language of the source takes the place of this one: Back still leads to the catalog.
                        onSwitchSource = { sourceId, query, tab ->
                            nav.navigate(SourceRoute(sourceId, query = query, tab = tab)) { popUpTo<SourceRoute> { inclusive = true } }
                        },
                    )
                }
                if (twoPane && pane != null) {
                    VerticalDivider()
                    key(pane) {
                        Box(Modifier.width(440.dp).fillMaxSize()) {
                            TitleScreen(
                                viewModel = koinViewModel(key = "pane-$pane") { parametersOf(pane) },
                                onBack = { paneTitle = null },
                                onReadChapter = { nav.navigate(ReaderRoute(chapterId = it)) },
                                onPreviewChapter = { nav.navigate(ReaderRoute(chapterId = it, preview = true)) },
                                onOpenSavedEdition = { paneTitle = it },
                                onReplaceSavedEdition = { from, to -> nav.navigate(GlobalSearchRoute(migrateFrom = from, migrateTo = to)) },
                                onMigrate = { id, title -> nav.navigate(GlobalSearchRoute(query = title, migrateFrom = id)) },
                                onOpenWebView = { nav.navigate(WebViewRoute(it)) },
                                onOpenDownloads = { nav.navigate(DownloadsRoute) },
                                inPane = true,
                                onOpenBook = { nav.navigate(ReaderRoute(bookId = it)) },
                            )
                        }
                    }
                }
            }
        }
        screen<GlobalSearchRoute> { entry ->
            val r = entry.toRoute<GlobalSearchRoute>()
            val migrate = r.migrateFrom.takeIf { it >= 0 }
            val viewModel: app.yomu.feature.catalog.GlobalSearchViewModel = koinViewModel { parametersOf(SearchArgs(r.query, migrate)) }
            LaunchedEffect(viewModel, r.migrateTo) {
                if (r.migrateTo > 0) viewModel.previewExistingMigrationTarget(r.migrateTo)
            }
            GlobalSearchScreen(
                viewModel = viewModel,
                onBack = back,
                onOpenTitle = { id ->
                    if (r.migrateTo > 0 && migrate != null) {
                        nav.navigate(TitleRoute(id)) {
                            popUpTo<GlobalSearchRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    } else if (migrate != null) {
                        // The old title page is obsolete after a migration.
                        nav.navigate(TitleRoute(id)) { popUpTo<TitleRoute> { inclusive = true } }
                    } else {
                        nav.navigate(TitleRoute(id))
                    }
                },
                onOpenSource = { sourceId, query -> nav.navigate(SourceRoute(sourceId, query = query)) },
                focusSearchOnOpen = r.migrateTo <= 0,
            )
        }
        screen<TitleRoute> { entry ->
            val r = entry.toRoute<TitleRoute>()
            TitleScreen(
                viewModel = koinViewModel { parametersOf(r.entryId) },
                onBack = back,
                onReadChapter = { nav.navigate(ReaderRoute(chapterId = it)) },
                onPreviewChapter = { nav.navigate(ReaderRoute(chapterId = it, preview = true)) },
                onOpenSavedEdition = { nav.navigate(TitleRoute(it)) },
                onReplaceSavedEdition = { from, to -> nav.navigate(GlobalSearchRoute(migrateFrom = from, migrateTo = to)) },
                onMigrate = { id, title -> nav.navigate(GlobalSearchRoute(query = title, migrateFrom = id)) },
                onOpenWebView = { nav.navigate(WebViewRoute(it)) },
                onOpenDownloads = { nav.navigate(DownloadsRoute) },
                onOpenBook = { nav.navigate(ReaderRoute(bookId = it)) },
                onOpenTrackerPage = { service, id -> nav.navigate(TrackerTitleRoute(service.id, id)) },
            )
        }
        // C3: a tracker title opens in Yomu too. A confirmed local link goes to its card; otherwise the reader can
        // look for a source of the same title without leaving the app.
        screen<TrackerTitleRoute> { entry ->
            val r = entry.toRoute<TrackerTitleRoute>()
            TrackerTitleScreen(
                viewModel = koinViewModel { parametersOf(r.service, r.remoteId) },
                onBack = back,
                onOpenLocal = { nav.navigate(TitleRoute(it)) },
                onOpenLocalBook = { nav.navigate(BookDetailsRoute(it)) },
                onOpenTrackerTitle = { service, id -> nav.navigate(TrackerTitleRoute(service.id, id)) },
                onFindSource = { title -> nav.navigate(GlobalSearchRoute(query = title)) },
                onOpenWeb = { nav.navigate(WebViewRoute(it)) },
            )
        }
        screen<ExtensionsRoute> { entry ->
            val r = entry.toRoute<ExtensionsRoute>()
            ExtensionsScreen(
                viewModel = koinViewModel(),
                onBack = back,
                onOpenSourceSettings = { nav.navigate(SourceSettingsRoute(it)) },
                addStoreUrl = r.addStore,
                onOpenSource = { nav.navigate(SourceRoute(it)) },
            )
        }
        screen<SourceConnectionRoute> { entry ->
            SourceConnectionScreen(
                viewModel = koinViewModel(),
                onBack = back,
                onOpenExtensions = { nav.navigate(ExtensionsRoute()) },
                initialLink = entry.toRoute<SourceConnectionRoute>().link,
                onSaved = { saved ->
                    // The wizard's own result is the target: the new catalogue opens, an audio link goes straight to
                    // the player, and a subscription opens the book it created. No guessing from the title.
                    val book = saved.bookId
                    val source = saved.sourceId
                    when {
                        book != null && book > 0 -> nav.navigate(ReaderRoute(bookId = book)) {
                            popUpTo<SourceConnectionRoute> { inclusive = true }
                        }
                        source != null && source > 0 -> nav.navigate(SourceRoute(source)) {
                            popUpTo<SourceConnectionRoute> { inclusive = true }
                        }
                        else -> back()
                    }
                },
            )
        }
        screen<UpcomingRoute> {
            UpcomingScreen(
                viewModel = koinViewModel(),
                onBack = back,
                onOpenTitle = { nav.navigate(TitleRoute(it)) },
            )
        }
        screen<SourceSettingsRoute> { entry -> SourceSettingsScreen(entry.toRoute<SourceSettingsRoute>().sourceId, back) }
        screen<WebViewRoute> { entry -> WebViewScreen(entry.toRoute<WebViewRoute>().url, back) }
        screen<DownloadsRoute> { DownloadsScreen(koinViewModel(), back, onOpenTitle = { nav.navigate(TitleRoute(it)) }) }
    }
}

/**
 * Room the floating bar takes over the top-level screens. A composition local rather than a value captured by the
 * graph builder: a new value (rotation, labels on or off) must not rebuild the navigation graph.
 */
private val LocalTabBarInset = compositionLocalOf { 0.dp }

/**
 * Width of the window the app actually has. A split-screen or free-form window is narrower than the screen, so
 * [androidx.compose.ui.platform.LocalConfiguration.screenWidthDp] would keep a two-pane layout that no longer fits.
 */
@Composable
private fun windowWidthDp(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/** A destination that exposes its animation scope for shared cover transitions. */
private inline fun <reified T : Any> NavGraphBuilder.screen(crossinline content: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

/** The library with the titles added with the heart in the catalog: the "Online" tab, a shelf in "All" and the user's tabs. */
@Composable
private fun LibraryTab(nav: NavHostController, tabs: TabsState) {
    val catalogVm: CatalogHomeViewModel = koinViewModel()
    val openTitle: (Long) -> Unit = { nav.navigate(TitleRoute(it)) }
    val online = remember(catalogVm) {
        OnlineLibrary { snackbar, query ->
            OnlineLibraryPane(catalogVm, snackbar, query, onOpenTitle = openTitle, onBrowse = { tabs.select(TopLevel.CATALOG) })
        }
    }
    // Wide windows keep the grid and show the details of the tapped book in a pane beside it.
    val twoPane = windowWidthDp() >= 840.dp
    var paneBook by rememberSaveable { mutableStateOf<Long?>(null) }
    val library: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            LibraryScreen(
                viewModel = koinViewModel(),
                // A tile tap opens the card; the explicit read action and the Continue shelf open the reader at
                // the saved position. Both decisions come from LibraryOpenResolver, not from per-call exceptions.
                onOpenDetails = { if (twoPane) paneBook = it.id else nav.navigate(BookDetailsRoute(it.id)) },
                onOpenBook = { nav.navigate(ReaderRoute(bookId = it.id)) },
                onFindBooksOnline = { tabs.select(TopLevel.CATALOG) },
                onOpenSettings = { tabs.select(TopLevel.SETTINGS) },
                onOpenTitle = openTitle,
                onReadChapter = { nav.navigate(ReaderRoute(chapterId = it)) },
                onOpenDownloads = { nav.navigate(DownloadsRoute) },
                onAddSource = { nav.navigate(SourceConnectionRoute()) },
                onOpenSources = { nav.navigate(ExtensionsRoute()) },
                online = online,
                // The pane keeps the list on screen: the book it shows is marked in the list.
                highlightBookId = paneBook,
            )
        }
    }
    val pane = paneBook
    if (twoPane && pane != null) {
        Row(Modifier.fillMaxSize()) {
            library(Modifier.weight(1f).fillMaxSize())
            VerticalDivider()
            key(pane) {
                Box(Modifier.width(420.dp).fillMaxSize()) {
                    BookDetailsScreen(
                        viewModel = koinViewModel(key = "pane-$pane") { parametersOf(pane) },
                        onBack = { paneBook = null },
                        onRead = { nav.navigate(ReaderRoute(bookId = it.id)) },
                        onOpenEditor = { nav.navigate(EditorRoute(it.id)) },
                        onOpenAnnotations = { nav.navigate(AnnotationsRoute(it.id)) },
                        onOpenPageLayout = { nav.navigate(DocxLayoutRoute(it.id)) },
                        inPane = true,
                        onOpenTrackerPage = { service, id -> nav.navigate(TrackerTitleRoute(service.id, id)) },
                    )
                }
            }
        }
    } else {
        library(Modifier.fillMaxSize())
    }
}
