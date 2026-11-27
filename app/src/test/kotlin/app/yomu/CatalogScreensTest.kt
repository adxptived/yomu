package app.yomu

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.yomu.core.database.CatalogItemEntity
import app.yomu.core.database.ChapterEntity
import app.yomu.core.database.DownloadTaskEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.common.AppError
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.theme.ThemeConfig
import app.yomu.core.ui.theme.ThemeMode
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.data.source.ContentWarning
import app.yomu.data.source.ExtensionFormat
import app.yomu.data.source.ExtensionManager
import app.yomu.data.source.ExtensionStatus
import app.yomu.data.source.InstalledExtension
import app.yomu.data.source.SourceInfo
import app.yomu.data.source.SourceManager
import app.yomu.domain.catalog.ContentPreference
import app.yomu.domain.catalog.NsfwFilter
import app.yomu.domain.catalog.SourceListFilter
import app.yomu.domain.model.DownloadStatus
import app.yomu.feature.catalog.BrowseArgs
import app.yomu.feature.catalog.CatalogHomeViewModel
import app.yomu.feature.catalog.CatalogScreen
import app.yomu.feature.catalog.ExtensionsScreen
import app.yomu.feature.catalog.ExtensionsViewModel
import app.yomu.feature.catalog.GlobalSearchScreen
import app.yomu.feature.catalog.GlobalSearchViewModel
import app.yomu.feature.catalog.SearchArgs
import app.yomu.feature.catalog.SourceBrowseScreen
import app.yomu.feature.catalog.SourceBrowseViewModel
import app.yomu.feature.catalog.TitleScreen
import app.yomu.feature.downloads.DownloadsScreen
import app.yomu.source.CatalogSource
import app.yomu.source.FilterList
import app.yomu.source.ItemsPage
import app.yomu.source.SItem
import app.yomu.source.WebViewRequiredException
import app.yomu.sources.opds.OpdsSource
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Screens of the catalog added in 1.3 (chapter sort and filters, download menu and selection, the download queue and
 * the catalog library filters), rendered with data written straight into the database: nothing goes to the network.
 * Screenshots go to build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CatalogScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** Whole window, including bottom sheets and menus (they live in their own windows). */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shotScreen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    /** A followed title of a source that is not installed (so no refresh can reach the network) with 12 chapters. */
    private fun seed(): Long = runBlocking {
        val db = GlobalContext.get().get<YomuDatabase>()
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val id = db.catalog().insert(
            CatalogItemEntity(
                sourceId = MISSING_SOURCE, url = "/title/smoke", title = "Smoke Manga: a Title Long Enough to Wrap",
                author = "Test Author", artist = null, description = "A made-up title for screenshots of the catalog screens.",
                genres = "Action,Comedy,Slice of Life", status = 1, coverUrl = null, type = "MANGA", favorite = true, categoryId = null,
                addedAt = now - 30 * day, lastUpdateAt = now - day, initialized = true,
            ),
        )
        val chapters = (1..12).map { n ->
            ChapterEntity(
                itemId = id, url = "/c$n", name = "Chapter $n", number = n.toFloat(), scanlator = if (n % 4 == 0) "Beta Scans" else "Alpha Group",
                uploadDate = now - (12 - n) * day, read = n <= 4, bookmarked = n == 6, lastPageRead = if (n == 5) 7 else 0,
                downloaded = n == 5 || n == 6, localPath = null, isNew = false, sourceOrder = 12 - n, fetchedAt = now,
                lastReadAt = if (n <= 4) now - n * 3_600_000L else null,
            )
        }
        db.catalog().insertChapters(chapters)
        val byNumber = db.catalog().chapters(id).associateBy { it.number.toInt() }
        fun task(n: Int, status: DownloadStatus, done: Int = 0, error: String? = null, attempts: Int = 0) = DownloadTaskEntity(
            kind = "CHAPTER", title = "Smoke Manga: a Title Long Enough to Wrap — Chapter $n", url = "/c$n", headersJson = null,
            status = status.name, bytesDownloaded = 0, bytesTotal = 0, pagesDone = done, pagesTotal = if (done > 0) 24 else 0,
            error = error, chapterId = byNumber.getValue(n).id, itemId = id, sourceId = MISSING_SOURCE, destPath = null, mimeType = null,
            createdAt = now + n, updatedAt = now + n, attempts = attempts,
        )
        listOf(
            task(7, DownloadStatus.RUNNING, done = 9),
            task(8, DownloadStatus.QUEUED),
            task(9, DownloadStatus.PAUSED, error = "Not enough storage space"),
            task(10, DownloadStatus.FAILED, error = "The site answered with an error (HTTP 503)", attempts = 3),
            task(5, DownloadStatus.DONE),
        ).forEach { db.downloads().insert(it) }
        id
    }

    private fun showTitle(id: Long, onPreviewChapter: ((Long) -> Unit)? = null) = compose.setContent {
        YomuTheme {
            TitleScreen(
                viewModel = koinViewModel { parametersOf(id) },
                onBack = {}, onReadChapter = {}, onMigrate = { _, _ -> }, onOpenWebView = {}, onOpenDownloads = {},
                onPreviewChapter = onPreviewChapter,
            )
        }
    }

    /** The node of [description] nearest to the top of the screen (the app bar's button rather than a list row's). */
    private fun topmost(description: String): SemanticsNodeInteraction {
        val id = compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().minBy { it.boundsInRoot.top }.id
        return compose.onNode(SemanticsMatcher("node $id") { it.id == id })
    }

    @Test
    fun titleChaptersAndSortSheet() {
        showTitle(seed())
        // The card header is taller than the old one (a poster cover and the primary action), so the chapters need a
        // scroll before the last rows are composed.
        awaitText("Find a replacement")
        compose.mainClock.advanceTimeBy(1_000)
        shot("29_title_card")
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Chapter 12", substring = true))
        shot("30_title_chapters")

        compose.onAllNodes(hasContentDescription("Sort and filter"))[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        shotScreen("31_title_sort_filter")
    }

    @Test
    fun titleDownloadMenu() {
        showTitle(seed())
        awaitText("Find a replacement")
        topmost("Download").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        shotScreen("32_title_download_menu")
    }

    @Test
    fun duplicateComparisonWaitsForAddSeparately() {
        val saved = seed()
        val db = GlobalContext.get().get<YomuDatabase>()
        val incoming = runBlocking {
            val original = requireNotNull(db.catalog().get(saved))
            db.catalog().insert(original.copy(id = 0, sourceId = original.sourceId + 1, url = "/title/edition", favorite = false))
        }
        showTitle(incoming)
        awaitText("Find a replacement")
        topmost("Add to library").performClick()
        awaitText("Possible duplicate")
        awaitText("Read 4 of 12")
        assertFalse(runBlocking { requireNotNull(db.catalog().get(incoming)).favorite })
        compose.onAllNodesWithText("Add separately")[0].performClick()
        awaitText("In library")
        assertTrue(runBlocking { requireNotNull(db.catalog().get(incoming)).favorite })
        assertTrue(runBlocking { requireNotNull(db.catalog().get(saved)).favorite })
    }

    @Test
    fun titleSelection() {
        showTitle(seed())
        awaitText("Find a replacement")
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Chapter 11", substring = true))
        compose.onAllNodesWithText("Chapter 11")[0].performTouchInput { longClick() }
        compose.onAllNodesWithText("Chapter 12")[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        shot("33_title_selection")
    }

    @Test
    fun previewActionOpensSelectedBookmarkedChapter() {
        val id = seed()
        val chapter = runBlocking { GlobalContext.get().get<YomuDatabase>().catalog().chapters(id).single { it.bookmarked } }
        var previewedId: Long? = null
        showTitle(id) { previewedId = it }
        awaitText("Find a replacement")
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(chapter.name))
        compose.onAllNodesWithText(chapter.name)[0].performTouchInput { longClick() }
        topmost("Preview chapter without saving progress").performClick()
        compose.runOnIdle { assertEquals(chapter.id, previewedId) }
    }

    @Test
    fun downloadQueue() {
        val expectedTitle = seed()
        var openedTitle: Long? = null
        var wentBack = false
        compose.setContent {
            YomuTheme { DownloadsScreen(koinViewModel(), onBack = { wentBack = true }, onOpenTitle = { openedTitle = it }) }
        }
        awaitText("Pause all")
        awaitText("Resume paused")
        awaitText("Chapter 8")
        compose.mainClock.advanceTimeBy(1_000)
        shot("34_downloads")
        compose.onNode(hasText("Chapter 8", substring = true) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(expectedTitle, openedTitle) }
        val back = topmost("Back")
        val initialTop = back.getUnclippedBoundsInRoot().top
        val queue = compose.onNode(hasScrollAction())
        val initialScroll = queue.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.75f, endY = height * 0.36f, durationMillis = 550)
        }
        awaitText("Chapter 10")
        assertTrue(queue.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > initialScroll)
        assertEquals(initialTop, back.getUnclippedBoundsInRoot().top)
        shot("37_downloads_scrolled")
        back.performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun catalogLibraryAndFilters() {
        seed()
        compose.setContent {
            YomuTheme {
                CatalogScreen(
                    viewModel = koinViewModel<CatalogHomeViewModel>(), onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        compose.onAllNodesWithText("Library")[0].performClick()
        awaitText("Smoke Manga")
        compose.mainClock.advanceTimeBy(1_000)
        shot("35_catalog_library")
        compose.onAllNodesWithText("Filter")[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        shotScreen("36_catalog_library_filters")
    }

    @Test
    fun catalogStartsWithAllSourceLanguages() {
        lateinit var model: CatalogHomeViewModel
        compose.setContent {
            YomuTheme {
                model = koinViewModel<CatalogHomeViewModel>()
                CatalogScreen(
                    viewModel = model, onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        awaitText("Internet Archive")
        assertTrue(model.sourceListing.value.sources.any { it.name == "Wikisource" && it.lang == "fr" })
    }

    @Test
    fun sourceSearchAcceptsNameAndLanguageTogether() {
        lateinit var model: CatalogHomeViewModel
        compose.setContent {
            YomuTheme {
                model = koinViewModel<CatalogHomeViewModel>()
                CatalogScreen(
                    viewModel = model, onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        awaitText("Internet Archive")
        compose.runOnIdle { model.sourceQuery.value = "Wikisource fr" }
        compose.waitUntil(5_000) {
            model.sourceListing.value.sources.singleOrNull()?.let { it.name == "Wikisource" && it.lang == "fr" } == true
        }
    }

    @Test
    fun externalTachiyomiInstallIsDiscoveredWhileCatalogIsOpen() {
        val context = compose.activity.applicationContext
        val koin = GlobalContext.get()
        val manager = koin.get<ExtensionManager>()
        runBlocking { koin.get<SourceManager>().ready.first { it } }
        compose.setContent {
            YomuTheme {
                CatalogScreen(
                    viewModel = koinViewModel<CatalogHomeViewModel>(), onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        awaitText("Internet Archive")

        val pkg = "eu.kanade.tachiyomi.extension.en.testexternal"
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            versionName = "1.4.1"
            reqFeatures = arrayOf(FeatureInfo().apply { name = "tachiyomi.extension" })
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                metaData = Bundle().apply { putString("tachiyomi.extension.class", ".TestSource") }
            }
        })
        val added = Intent(Intent.ACTION_PACKAGE_ADDED, Uri.fromParts("package", pkg, null))
        // A package broadcast is sent by the OS on Android. Dispatch it to the registered receiver in Robolectric.
        val receiver = shadowOf(context as Application).getReceiversForIntent(added).single()
        compose.runOnIdle { receiver.onReceive(context, added) }
        compose.waitUntil(5_000) { manager.installed.value.any { it.pkg == pkg } }
    }

    @Test
    fun addedSourcesAreVisibleBeforePinnedBuiltInSources() {
        val source = seedExtensionSources().single()
        val manager = GlobalContext.get().get<SourceManager>()
        runBlocking {
            val infos = manager.infos.first { list -> list.any { it.kind == SourceInfo.Kind.BUILT_IN } }
            manager.setPinned(infos.filter { it.kind == SourceInfo.Kind.BUILT_IN }.map { it.id }, true)
        }
        compose.setContent {
            YomuTheme {
                CatalogScreen(
                    viewModel = koinViewModel<CatalogHomeViewModel>(), onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        awaitText(source.name)
        compose.onNodeWithText(source.name).assertIsDisplayed()
    }

    @Test
    fun checkingSourceOffersWebViewThenTreatsEmptyPageAsAvailable() {
        val connectivity = checkNotNull(compose.activity.getSystemService(ConnectivityManager::class.java))
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(connectivity).setNetworkCapabilities(checkNotNull(connectivity.activeNetwork), capabilities)
        val url = "https://fixture.invalid/verify"
        val attempts = AtomicInteger()
        val source = seedExtensionSources(popular = {
            if (attempts.incrementAndGet() == 1) throw WebViewRequiredException(url)
            ItemsPage(emptyList(), false)
        }).single()
        var openedWebView: String? = null
        compose.setContent {
            YomuTheme {
                CatalogScreen(
                    viewModel = koinViewModel<CatalogHomeViewModel>(),
                    onOpenSource = { error("Checking should keep the sources tab open") },
                    onOpenTitle = {}, onReadChapter = {}, onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                    onOpenWebView = { openedWebView = it },
                )
            }
        }
        awaitText(source.name)
        fun check() {
            compose.onNodeWithText(source.name).performTouchInput { longClick() }
            awaitText("Check availability")
            compose.onNode(hasText("Check availability") and hasClickAction()).performClick()
        }
        check()
        awaitText("Open in WebView")
        compose.onNodeWithText("Open in WebView").performClick()
        compose.runOnIdle { assertEquals(url, openedWebView) }
        check()
        awaitText("Responding")
        assertEquals(2, attempts.get())
        val infos = GlobalContext.get().get<SourceManager>().infos.value
        assertEquals(0L, infos.single { it.id == source.id }.lastUsedAt)
    }

    @Test
    fun loadedExtensionAppearsInCatalogAndHiddenSourceCanBeRevealed() {
        val source = seedExtensionSources().single()
        runBlocking { GlobalContext.get().get<SourceManager>().setHidden(source.id, true) }
        lateinit var model: CatalogHomeViewModel
        compose.setContent {
            YomuTheme {
                model = koinViewModel<CatalogHomeViewModel>()
                CatalogScreen(
                    viewModel = model, onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        awaitText("Show all")
        assertTrue(model.sourceListing.value.sources.none { it.id == source.id })
        compose.onNode(hasText("Show all") and hasClickAction()).performClick()
        awaitText("Added sources")
        awaitText(source.name)
        compose.runOnIdle { model.sourceQuery.value = source.name }
        compose.waitUntil(5_000) { model.sourceListing.value.sources.singleOrNull()?.id == source.id }
    }

    @Test
    fun filteredExtensionCanBeOpenedWithoutResettingSavedFilters() {
        val settings = GlobalContext.get().get<AppSettings>()
        val source = seedExtensionSources(warning = ContentWarning.NSFW).single()
        runBlocking {
            settings.contentPreference.set(ContentPreference.MIXED)
            settings.catalogSourceFilter.set(SourceListFilter(nsfw = NsfwFilter.HIDE))
        }
        lateinit var model: CatalogHomeViewModel
        var opened: Long? = null
        compose.setContent {
            YomuTheme {
                model = koinViewModel<CatalogHomeViewModel>()
                CatalogScreen(
                    viewModel = model, onOpenSource = { opened = it }, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        awaitText("Hide 18+")
        compose.runOnIdle { model.sourceQuery.value = source.name }
        awaitText("1 source filtered out")
        assertTrue(model.sourceListing.value.sources.none { it.id == source.id })
        compose.onNode(hasText("Show matches") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { model.sourceListing.value.sources.singleOrNull()?.id == source.id }
        compose.onNode(hasText(source.name) and hasText("Books") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(source.id, opened) }
        compose.onNode(hasText("Apply filters") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { model.sourceListing.value.sources.none { it.id == source.id } }
        assertEquals(NsfwFilter.HIDE, runBlocking { settings.catalogSourceFilter.get() }.nsfw)
    }

    @Test
    fun installedAdultSourceIsHiddenBySafeContentUntilItIsTurnedOn() {
        val settings = GlobalContext.get().get<AppSettings>()
        runBlocking {
            settings.contentPreference.set(ContentPreference.SAFE)
            settings.catalogSourceFilter.set(SourceListFilter())
        }
        lateinit var model: CatalogHomeViewModel
        compose.setContent {
            YomuTheme {
                model = koinViewModel()
                CatalogScreen(
                    viewModel = model, onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        awaitText("Find a source by name or language")
        // The startup/package scan publishes a complete inventory. Wait for it before adding the fake package.
        runBlocking { GlobalContext.get().get<ExtensionManager>().reload() }
        val source = seedExtensionSources(warning = ContentWarning.NSFW).single()
        compose.waitUntil(10_000) { model.installedAdultSourcesHidden.value }
        val notice = hasText("Show installed 18+ sources") and hasClickAction()
        val sourceList = compose.onNode(
            hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        )
        sourceList.performScrollToNode(notice)
        assertTrue(model.sourceListing.value.sources.none { it.id == source.id })
        compose.onNode(notice).performClick()
        awaitText(source.name)
        assertEquals(ContentPreference.MIXED, runBlocking { settings.contentPreference.get() })
        assertTrue(model.sourceListing.value.sources.single { it.id == source.id }.isNsfw)
    }

    @Test
    fun contentMigrationKeepsTheDefaultUnlessTheOldSwitchWasSaved() = runBlocking {
        val settings = GlobalContext.get().get<AppSettings>()
        settings.contentPreference.reset()
        settings.showNsfw.reset()

        settings.migrateContentPreference()
        assertEquals(ContentPreference.MIXED, settings.contentPreference.get())

        settings.showNsfw.set(false)
        settings.migrateContentPreference()
        assertEquals(ContentPreference.SAFE, settings.contentPreference.get())

        settings.contentPreference.set(ContentPreference.MIXED)
        settings.migrateContentPreference()
        assertEquals(ContentPreference.MIXED, settings.contentPreference.get())
    }

    @Test
    fun sourceWithoutPreferencesOpensFromLongExtensionDetails() {
        // Keep the store cache fresh so opening this screen only starts the installed-package scan.
        val manager = GlobalContext.get().get<ExtensionManager>()
        runBlocking { manager.refreshStores() }
        var opened: Long? = null
        compose.setContent {
            YomuTheme {
                ExtensionsScreen(koinViewModel<ExtensionsViewModel>(), onBack = {}, onOpenSource = { opened = it })
            }
        }
        awaitText("Installed")
        // reload shares the scan mutex. Inject the in-memory source after startup has released it.
        runBlocking { manager.reload() }
        val source = seedExtensionSources(count = 20).last()
        awaitText("A test extension")
        compose.onNode(hasText("A test extension") and hasClickAction()).performClick()
        val heading = hasText("Sources · 20")
        val target = hasText(source.name) and hasClickAction()
        awaitText("Sources · 20")
        val listId = compose.onNode(hasScrollAction() and hasAnyDescendant(heading)).fetchSemanticsNode().id
        compose.onNode(SemanticsMatcher("extension sources list") { it.id == listId }).performScrollToNode(target)
        compose.onNode(target).performClick()
        compose.runOnIdle { assertEquals(source.id, opened) }
    }

    private fun seedExtensionSources(
        count: Int = 1,
        warning: ContentWarning = ContentWarning.SAFE,
        popular: (suspend () -> ItemsPage)? = null,
        search: (suspend (String) -> ItemsPage)? = null,
    ): List<CatalogSource> {
        val koin = GlobalContext.get()
        val manager = koin.get<ExtensionManager>()
        val sources = koin.get<SourceManager>()
        runBlocking { sources.ready.first { it } }
        // Names and URLs carry the instance number: two fixtures with the same name would share a source id.
        val run = ++fixtureRuns
        val catalogs = List(count) { i ->
            val n = run * 100 + i
            val opds = OpdsSource("Fixture catalog $n", "https://fixture.invalid/opds/$n", "en")
            object : CatalogSource by opds {
                override suspend fun getPopular(page: Int): ItemsPage = popular?.invoke() ?: opds.getPopular(page)
                override suspend fun search(query: String, filters: FilterList, page: Int): ItemsPage =
                    search?.invoke(query) ?: opds.search(query, filters, page)
            }
        }
        @Suppress("UNCHECKED_CAST")
        val installed = ExtensionManager::class.java.getDeclaredField("_installed").apply { isAccessible = true }
            .get(manager) as MutableStateFlow<List<InstalledExtension>>
        installed.value += InstalledExtension(
            pkg = "app.yomu.fixture.extension", name = "A test extension", versionName = "1.0", versionCode = 1,
            format = ExtensionFormat.YOMU, libVersion = null, lang = "en", contentWarning = warning,
            signatures = listOf("fixture"), isPrivate = true, isBundled = false, status = ExtensionStatus.LOADED,
            sources = catalogs,
        )
        return catalogs
    }

    @Test
    fun globalSearchOffersAnExplicitLanguageScope() {
        compose.setContent {
            YomuTheme {
                GlobalSearchScreen(
                    viewModel = koinViewModel<GlobalSearchViewModel> { parametersOf(SearchArgs()) },
                    onBack = {}, onOpenTitle = {}, onOpenSource = { _, _ -> },
                )
            }
        }
        awaitText("Search languages:")
        compose.onNode(hasText("Search languages:", substring = true) and hasClickAction()).performClick()
        awaitText("Source languages")
        compose.onNode(hasText("All languages") and hasClickAction()).assertExists()
    }

    /** The pencil of the source list: select many sources and apply one action to all of them. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sourceBulkEditor() {
        val koin = GlobalContext.get()
        val settings = koin.get<AppSettings>()
        runBlocking {
            val sources = koin.get<SourceManager>()
            sources.addOpds("First fixture", "https://fixture.invalid/first", username = null, password = null)
            sources.addOpds("Second fixture", "https://fixture.invalid/second", username = null, password = null)
            settings.contentPreference.set(ContentPreference.MIXED)
            settings.catalogSourceFilter.set(SourceListFilter(kinds = setOf("OPDS")))
        }
        compose.setContent {
            YomuTheme {
                CatalogScreen(
                    viewModel = koinViewModel<CatalogHomeViewModel>(), onOpenSource = {}, onOpenTitle = {}, onReadChapter = {},
                    onGlobalSearch = {}, onOpenExtensions = {}, onOpenDownloads = {},
                )
            }
        }
        val editSources = hasContentDescription("Edit sources") and hasClickAction()
        compose.waitUntilAtLeastOneExists(editSources, 20_000)
        compose.onNode(editSources).performClick()
        awaitText("Select all")
        compose.onAllNodesWithText("Select all")[0].performClick()
        awaitText("Selected: 2")
        compose.mainClock.advanceTimeBy(1_000)
        shot("37_catalog_sources_bulk")
        compose.onAllNodesWithText("Actions")[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        shotScreen("38_catalog_bulk_actions")
    }

    /**
     * Global search answers source by source (package 2 of the UI plan): one source is ready, another times out;
     * the ready results stay visible while "Only with results" is on and after the failed source is retried.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun globalSearchKeepsResultsThroughPartialFailure() {
        goOnline()
        val manager = GlobalContext.get().get<SourceManager>()
        val slowCalls = AtomicInteger()
        val quick = seedExtensionSources(search = {
            ItemsPage(listOf(SItem(url = "/quick/1", title = "Alpha Fixture", author = "Quick Source")), false)
        }).single()
        val slow = seedExtensionSources(search = {
            // The repository itself retries a timeout once; the answer comes from the Retry button instead.
            if (slowCalls.incrementAndGet() <= 2) throw AppError.Timeout(SocketTimeoutException("fixture"))
            ItemsPage(listOf(SItem(url = "/slow/1", title = "Beta Fixture", author = "Slow Source")), false)
        }).single()
        runBlocking { manager.setEnabled(manager.infos.value.filter { it.id != quick.id && it.id != slow.id }.map { it.id }, false) }
        compose.waitUntil(5_000) { manager.infos.value.none { it.id != quick.id && it.id != slow.id && it.enabled } }

        lateinit var model: GlobalSearchViewModel
        compose.setContent {
            YomuTheme {
                model = koinViewModel<GlobalSearchViewModel> { parametersOf(SearchArgs()) }
                GlobalSearchScreen(viewModel = model, onBack = {}, onOpenTitle = {}, onOpenSource = { _, _ -> })
            }
        }
        awaitText("Search languages:")
        compose.runOnIdle { model.query.value = "fixture"; model.submit() }
        awaitText("Alpha Fixture")
        awaitText("The site is taking too long to respond")
        compose.mainClock.advanceTimeBy(1_000)
        shot("48_global_search_partial_failure")
        // "Only with results": the ready source stays, the failed one keeps its Retry button.
        compose.onNode(hasText("With results") and hasClickAction()).performClick()
        compose.onAllNodesWithText("Alpha Fixture")[0].assertIsDisplayed()
        compose.onNode(hasContentDescription("Retry")).performClick()
        awaitText("Beta Fixture")
        compose.onAllNodesWithText("Alpha Fixture")[0].assertIsDisplayed()
    }

    /** A source search keeps its ready pages when the next page fails; Retry finishes the list (package 2). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sourceSearchKeepsReadyPagesWhenNextPageFails() {
        goOnline()
        val pageCalls = AtomicInteger()
        val source = seedExtensionSources(search = { _ ->
            when (val call = pageCalls.incrementAndGet()) {
                1 -> ItemsPage(listOf(SItem(url = "/s/1", title = "Search Hit One", author = "Fixture Author")), true)
                2, 3 -> throw AppError.Timeout(SocketTimeoutException("fixture"))
                else -> ItemsPage(listOf(SItem(url = "/s/2", title = "Search Hit Two", author = "Fixture Author")), false)
            }
        }).single()
        compose.setContent {
            YomuTheme {
                SourceBrowseScreen(
                    viewModel = koinViewModel<SourceBrowseViewModel> { parametersOf(BrowseArgs(sourceId = source.id, query = "hit")) },
                    onBack = {}, onOpenTitle = {}, onOpenNavigation = { _, _, _ -> }, onOpenWebView = {},
                )
            }
        }
        awaitText("Search Hit One")
        awaitText("Could not load the next page")
        compose.onAllNodesWithText("Search Hit One")[0].assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        shot("49_source_search_append_failure")
        compose.onNode(hasText("Retry") and hasClickAction()).performClick()
        awaitText("Search Hit Two")
        compose.onAllNodesWithText("Search Hit One")[0].assertIsDisplayed()
    }

    /** The download queue on a narrow phone with large text in the dark theme (package 4 of the UI plan). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun downloadQueueLargeTextScrollsUnderHeader() {
        RuntimeEnvironment.setFontScale(1.5f)
        assertEquals(1.5f, compose.activity.resources.configuration.fontScale)
        val expectedTitle = seed()
        var openedTitle: Long? = null
        var wentBack = false
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                DownloadsScreen(koinViewModel(), onBack = { wentBack = true }, onOpenTitle = { openedTitle = it })
            }
        }
        // With 150% text the queued rows start below the fold: scroll until Chapter 8 is on screen.
        awaitText("Smoke Manga")
        val back = topmost("Back")
        val headerTop = back.getUnclippedBoundsInRoot().top
        val queue = compose.onNode(hasScrollAction())
        val initialScroll = queue.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.75f, endY = height * 0.30f, durationMillis = 550)
        }
        awaitText("Chapter 8")
        assertTrue(queue.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > initialScroll)
        assertEquals(headerTop, back.getUnclippedBoundsInRoot().top)
        compose.mainClock.advanceTimeBy(1_000)
        shot("75_downloads_narrow_dark_large_text")
        compose.onNode(hasText("Chapter 8", substring = true) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(expectedTitle, openedTitle) }
        back.performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    /** Number of seedExtensionSources calls; two fixtures must not share a source id. */
    private var fixtureRuns = 0

    /** Robolectric starts offline; source calls skip their retries (and the app's calls fail at once) without this. */
    private fun goOnline() {
        val connectivity = checkNotNull(compose.activity.getSystemService(ConnectivityManager::class.java))
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(connectivity).setNetworkCapabilities(checkNotNull(connectivity.activeNetwork), capabilities)
    }

    private companion object {
        const val MISSING_SOURCE = 987_654_321L
    }
}
