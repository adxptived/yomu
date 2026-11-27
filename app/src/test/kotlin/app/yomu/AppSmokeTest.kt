package app.yomu

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import app.yomu.data.source.BrokenReason
import app.yomu.data.source.ContentWarning
import app.yomu.data.source.ExtensionFormat
import app.yomu.data.source.ExtensionManager
import app.yomu.data.source.ExtensionStatus
import app.yomu.data.source.InstalledExtension
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import app.yomu.data.book.EpubBuilder
import app.yomu.data.book.EpubMeta
import app.yomu.data.library.ImportOutcome
import app.yomu.data.library.ImportService
import app.yomu.core.datastore.AppSettings
import app.yomu.core.datastore.SettingsJson
import app.yomu.domain.model.BookReaderSettings
import app.yomu.domain.model.TextReaderSettings
import app.yomu.domain.repository.BookRepository
import app.yomu.domain.repository.StatsRepository
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.koin.core.context.GlobalContext
import app.yomu.feature.annotations.AnnotationsViewModel
import app.yomu.feature.catalog.BrowseArgs
import app.yomu.feature.catalog.CatalogHomeViewModel
import app.yomu.feature.catalog.ExtensionsViewModel
import app.yomu.feature.catalog.GlobalSearchViewModel
import app.yomu.feature.catalog.SearchArgs
import app.yomu.feature.catalog.SourceBrowseViewModel
import app.yomu.feature.catalog.TitleViewModel
import app.yomu.feature.dictionary.DictionariesViewModel
import app.yomu.feature.dictionary.DictionaryViewModel
import app.yomu.feature.dictionary.ReviewViewModel
import app.yomu.feature.dictionary.WordLookupViewModel
import app.yomu.feature.dictionary.WordsViewModel
import app.yomu.feature.downloads.DownloadsViewModel
import app.yomu.feature.editor.EditorViewModel
import app.yomu.feature.library.BookDetailsViewModel
import app.yomu.feature.library.LibraryViewModel
import app.yomu.feature.reader.DocxLayoutViewModel
import app.yomu.feature.reader.ReaderArgs
import app.yomu.feature.reader.ReaderViewModel
import app.yomu.feature.reader.ReadingStatsViewModel
import app.yomu.feature.settings.SettingsViewModel
import app.yomu.profile.ProfileTrackingViewModel
import app.yomu.profile.ProfileViewModel
import app.yomu.feature.translator.LanguagePacksViewModel
import app.yomu.feature.translator.TranslationViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.reflect.KClass
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import androidx.compose.runtime.snapshots.Snapshot

/**
 * Starts the real application (Koin graph, database, settings) and walks through onboarding and the top level
 * screens. Catches crashes on startup and missing dependency definitions; screenshots go to build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<YomuActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String, timeout: Long = 20_000) {
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)
    }

    /** Item of the bottom navigation bar (the screens themselves may contain the same words). */
    private fun clickTab(label: String) {
        compose.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).let { nodes ->
            val count = nodes.fetchSemanticsNodes().size
            nodes[count - 1].performClick()
        }
    }

    /** Runs the platform animations too (the ripples of the navigation bar are RippleDrawables on the main looper). */
    private fun settle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    @Test
    fun firstLaunchOpensCatalogFromImportPage() {
        val settings = GlobalContext.get().get<app.yomu.core.datastore.AppSettings>()
        runBlocking { settings.onboardingDone.set(false) }
        awaitText("Welcome to Yomu")
        awaitText("Appearance")
        compose.onAllNodesWithText("Light", substring = false)[0].performClick()
        compose.waitUntil(10_000) { runBlocking { settings.themeMode.get() == "LIGHT" } }
        compose.onAllNodesWithText("Next")[0].performClick()
        awaitText("Find a book in the catalog")
        compose.onAllNodesWithText("Find a book in the catalog")[0].performClick()
        awaitText("Internet Archive")
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun emptyProfileCanOpenLibraryAndCatalog() {
        val settings = GlobalContext.get().get<app.yomu.core.datastore.AppSettings>()
        runBlocking { settings.onboardingDone.set(true) }
        clickTab("Journal")
        awaitText("Start reading")
        compose.onAllNodesWithText("Open library")[0].performClick()
        awaitText("Your library is empty")

        clickTab("Journal")
        compose.onAllNodesWithText("Find in catalog")[0].performClick()
        awaitText("Internet Archive")
    }

    /** Every ViewModel of every screen can be created from the real Koin graph (catches missing definitions). */
    @Test
    fun everyViewModelResolves() {
        val missing = -424242L
        val all: List<Pair<KClass<*>, ParametersDefinition?>> = listOf(
            MainViewModel::class to null,
            LibraryViewModel::class to null,
            BookDetailsViewModel::class to { parametersOf(missing) },
            ReaderViewModel::class to { parametersOf(ReaderArgs(bookId = missing)) },
            ReadingStatsViewModel::class to null,
            DocxLayoutViewModel::class to { parametersOf(missing) },
            AnnotationsViewModel::class to { parametersOf(-1L) },
            DictionaryViewModel::class to null,
            WordsViewModel::class to null,
            WordLookupViewModel::class to null,
            ReviewViewModel::class to null,
            DictionariesViewModel::class to null,
            TranslationViewModel::class to null,
            LanguagePacksViewModel::class to null,
            EditorViewModel::class to { parametersOf(missing) },
            CatalogHomeViewModel::class to null,
            SourceBrowseViewModel::class to { parametersOf(BrowseArgs(sourceId = missing)) },
            GlobalSearchViewModel::class to { parametersOf(SearchArgs()) },
            TitleViewModel::class to { parametersOf(missing) },
            ExtensionsViewModel::class to null,
            DownloadsViewModel::class to null,
            SettingsViewModel::class to null,
            ProfileViewModel::class to null,
            ProfileTrackingViewModel::class to null,
        )
        val koin = GlobalContext.get()
        val failures = mutableListOf<String>()
        compose.runOnUiThread {
            all.forEach { (type, params) ->
                runCatching { koin.get<Any>(type, null, params) }.onFailure { failures += "${type.simpleName}: $it" }
            }
        }
        compose.waitForIdle()
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun startsAndShowsMainScreens() {
        val welcome = hasText("Welcome to Yomu")
        val libraryTab = hasText("Library") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.waitUntilAtLeastOneExists(welcome or libraryTab, 20_000)
        if (compose.onAllNodes(welcome).fetchSemanticsNodes().isNotEmpty()) {
            shot("01_onboarding")
            compose.onAllNodesWithText("Next")[0].performClick()
            awaitText("Where are your books?")
            shot("02_onboarding_folder")
            compose.onAllNodesWithText("Skip")[0].performClick()
        }

        // The empty state proves that the database query of the library finished.
        awaitText("Your library is empty")
        shot("03_library")
        for ((tab, file, marker) in listOf(
            Triple("Catalog", "04_catalog", "Internet Archive"),
            Triple("Words", "05_dictionary", "Look up a word"),
            Triple("Settings", "06_settings", "Search settings"),
        )) {
            clickTab(tab)
            awaitText(marker)
            settle()
            shot(file)
        }

        // Import a generated EPUB and open it in the reader.
        val epub = File(compose.activity.cacheDir, "smoke.epub")
        EpubBuilder(EpubMeta(title = "Smoke Test Book", authors = listOf("Robolectric"), language = "en")).apply {
            addChapter("Chapter 1", "<h2>Chapter 1</h2><p>Hello world. This is a test.</p>")
            addChapter("Chapter 2", "<p>Second chapter.</p>")
        }.writeTo(epub)
        val outcome = runBlocking { GlobalContext.get().get<ImportService>().importLocalFile(epub, "smoke.epub") }
        assertTrue("import failed: $outcome", outcome is ImportOutcome.Added)
        clickTab("Library")
        awaitText("Smoke Test Book")
        settle()
        shot("07_library_book")
        // Tapping the title opens the details; the cover opens the reader.
        compose.onAllNodes(hasContentDescription("Smoke Test Book", substring = true))[0].performClick()
        compose.mainClock.advanceTimeBy(2_000)
        shot("08_reader")

        // Reader chrome and settings sheet (only for the screenshots: the chrome may be hidden until a tap).
        runCatching {
            val view = compose.onAllNodes(hasText("Settings") and hasClickAction())
            if (view.fetchSemanticsNodes().isNotEmpty()) {
                view[0].performClick()
                compose.onNode(hasText("All reading settings") and hasClickAction()).performClick()
                compose.mainClock.advanceTimeBy(1_000)
                shot("09_reader_settings")
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }

        // History lists the opened book; statistics open from its toolbar. The WebView reader does not run under
        // Robolectric, so a few reading sessions are recorded directly.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        val bookId = (outcome as ImportOutcome.Added).bookId
        runBlocking {
            val stats = GlobalContext.get().get<StatsRepository>()
            val now = System.currentTimeMillis()
            val day = 24 * 60 * 60 * 1000L
            listOf(0 to 25, 1 to 40, 2 to 15, 4 to 55, 6 to 30, 9 to 20).forEach { (daysAgo, minutes) ->
                stats.recordSession(bookId, now - daysAgo * day - minutes * 60_000L, minutes * 60_000L, pages = minutes, chars = minutes * 900L)
            }
        }
        // The journal: the last book, today's goal and the tools. History is one tap away in its top bar, statistics
        // open from the today card.
        clickTab("Journal")
        awaitText("Smoke Test Book")
        awaitText("Today")
        settle()
        shot("10_profile")
        compose.onAllNodes(hasContentDescription("History"))[0].performClick()
        awaitText("Smoke Test Book")
        settle()
        shot("11_history")
        compose.onNodeWithContentDescription("Back").performClick()
        awaitText("Today")
        compose.onNode(hasText("Today") and hasClickAction()).performClick()
        awaitText("Time read")
        settle()
        shot("12_stats")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun wordPageLayoutCanSwitchToTextWithLayoutAsTheDefault() {
        openWordFromJournal()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Text mode"), 20_000)
        shot("248_docx_page_layout")
        compose.onNodeWithContentDescription("Text mode").performClick()
        compose.waitUntilDoesNotExist(hasText("Page layout"), 20_000)

        back()
        awaitText("My tools")
        shot("249_docx_back_to_journal")
    }

    @Test
    fun wordWithBookSpecificScrollingOpensInTheTextReader() {
        openWordFromJournal(TextReaderSettings(paged = false))
        awaitText("Got it")
        compose.onNode(hasText("Page layout")).assertDoesNotExist()
    }

    private fun openWordFromJournal(bookTextSettings: TextReaderSettings? = null) {
        val koin = GlobalContext.get()
        val settings = koin.get<AppSettings>()
        val source = File("../data/src/test/resources/docx/sample.docx")
        val wordFile = source.copyTo(File(compose.activity.cacheDir, "smoke.docx"), overwrite = true)
        val bookTitle = runBlocking {
            settings.onboardingDone.set(true)
            settings.docxPageLayout.set(true)
            settings.textSettings.set(TextReaderSettings(paged = true, longStrip = false))
            val imported = koin.get<ImportService>().importLocalFile(wordFile, "smoke.docx") as ImportOutcome.Added
            val books = koin.get<BookRepository>()
            bookTextSettings?.let {
                books.saveReaderSettings(imported.bookId, SettingsJson.encodeToString(BookReaderSettings.serializer(), BookReaderSettings(text = it)))
            }
            val now = System.currentTimeMillis()
            koin.get<StatsRepository>().recordSession(imported.bookId, now - 60_000L, 60_000L, pages = 1, chars = 0L)
            checkNotNull(books.getBook(imported.bookId)).title
        }
        clickTab("Journal")
        awaitText(bookTitle)
        compose.onNode(hasText(bookTitle) and hasClickAction()).performClick()
    }

    /** Generated comic pages: a coloured page with two panels and a big page number. */
    private fun writeCbz(file: File, pages: Int) {
        val colors = listOf(0xFFF3E9D2, 0xFFDCE6F2, 0xFFE8DDF0, 0xFFE2F0DC)
        ZipOutputStream(file.outputStream()).use { zip ->
            repeat(pages) { i ->
                val bmp = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(colors[i % colors.size].toInt())
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = 0xFF222222.toInt() }
                canvas.drawRect(30f, 30f, 570f, 420f, stroke)
                canvas.drawRect(30f, 450f, 570f, 870f, stroke)
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222222.toInt(); textSize = 180f; textAlign = Paint.Align.CENTER }
                canvas.drawText("${i + 1}", 300f, 300f, text)
                zip.putNextEntry(ZipEntry("page%03d.png".format(i + 1)))
                bmp.compress(Bitmap.CompressFormat.PNG, 100, zip)
                zip.closeEntry()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pageGridSelectsMultipleComicPages() {
        val koin = GlobalContext.get()
        runBlocking {
            val settings = koin.get<app.yomu.core.datastore.AppSettings>()
            settings.onboardingDone.set(true)
            settings.commonReader.update { it.copy(autoHideMenuSeconds = 0) }
        }
        val cbz = File(compose.activity.cacheDir, "Select comic pages.cbz")
        writeCbz(cbz, 4)
        val outcome = runBlocking { koin.get<ImportService>().importLocalFile(cbz, cbz.name) }
        assertTrue("import failed: $outcome", outcome is ImportOutcome.Added)
        clickTab("Library")
        awaitText("Select comic pages")
        compose.onAllNodesWithText("Select comic pages")[0].performClick()
        awaitText("Read")
        compose.onNode(hasText("Read", substring = false) and hasClickAction()).performClick()
        awaitText("1 / 4")
        val actions = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { it.config[SemanticsActions.CustomActions] }
        actions.firstOrNull { it.label == "Show the reading controls" }?.let { action ->
            compose.runOnIdle { action.action() }
        }
        val more = hasContentDescription("More")
        compose.waitUntil(10_000) { compose.onAllNodes(more, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(more, useUnmergedTree = true).performTouchInput { click(center) }
        awaitText("Pages")
        compose.onNode(hasText("Pages", substring = false) and hasClickAction()).performClick()
        awaitText("Select pages")
        compose.onNode(hasText("Select pages", substring = false)).performClick()
        compose.onNode(hasText("2", substring = false) and hasClickAction()).performClick()
        compose.onNode(hasText("3", substring = false) and hasClickAction()).performClick()
        awaitText("Save selected (2)")
        compose.onNode(hasText("2", substring = false) and hasClickAction()).performClick()
        awaitText("Save selected (1)")
    }

    /** Whole screen including dialogs and bottom sheets (they live in their own windows). */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shotScreen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    /**
     * Dialogs with text fields never become idle under Robolectric, so the clock is advanced by hand while such a
     * dialog is open, and it is closed with Back.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shotTextDialog(name: String, open: () -> Unit) {
        compose.mainClock.autoAdvance = false
        open()
        repeat(120) {
            Snapshot.sendApplyNotifications()
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
        }
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
        dialogBack(idle = false)
        repeat(20) {
            Snapshot.sendApplyNotifications()
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** Extension icon: a coloured rounded square with white letters. */
    private fun iconDrawable(color: Int, letters: String): Drawable {
        val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawRoundRect(0f, 0f, 192f, 192f, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt(); textSize = 84f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        canvas.drawText(letters, 96f, 126f, text)
        return BitmapDrawable(compose.activity.resources, bmp)
    }

    /** Back inside the topmost dialog or bottom sheet (they have their own windows and back dispatchers). */
    private fun dialogBack(idle: Boolean = true) {
        compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        if (idle) compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Catalog sources with their logos, the extensions screen, the "Add repository" dialog and the manga reader. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun catalogExtensionsAndManga() {
        // Settings live in a process-wide DataStore: when another test ran first, onboarding is already done.
        val tab = hasText("Catalog") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.waitUntilAtLeastOneExists(hasText("Welcome to Yomu", substring = true) or tab, 20_000)
        if (compose.onAllNodes(hasText("Welcome to Yomu", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("Next")[0].performClick()
            awaitText("Where are your books?")
            compose.onAllNodesWithText("Skip")[0].performClick()
        }
        compose.waitUntilAtLeastOneExists(tab, 20_000)

        clickTab("Catalog")
        awaitText("Internet Archive")
        compose.mainClock.advanceTimeBy(1_000)
        shot("20_catalog_sources")

        compose.onNode(hasText("Add a source") and hasClickAction()).performClick()
        awaitText("What do you want to add?")
        shot("25_add_source")
        back()
        awaitText("Internet Archive")

        compose.onNode(hasContentDescription("Extensions") and hasClickAction()).performClick()
        awaitText("Installed")
        compose.mainClock.advanceTimeBy(2_000)

        // The bundled extensions cannot run under Robolectric (no dex), so the list is replaced with made-up ones:
        // three untrusted (one with an icon, two with monograms), one that failed to start and one working.
        val manager = GlobalContext.get().get<ExtensionManager>()
        runBlocking { manager.reload() } // waits for the scan started by the screen, so it can't overwrite the fakes
        @Suppress("UNCHECKED_CAST")
        val installed = ExtensionManager::class.java.getDeclaredField("_installed").apply { isAccessible = true }
            .get(manager) as MutableStateFlow<List<InstalledExtension>>
        fun fake(name: String, lang: String, status: ExtensionStatus, icon: Drawable? = null, error: String? = null) = InstalledExtension(
            pkg = "eu.kanade.tachiyomi.extension.$lang.${name.lowercase().replace(" ", "")}", name = name,
            versionName = "1.4.${name.length}", versionCode = 1, format = ExtensionFormat.TACHIYOMI, libVersion = 1.4, lang = lang,
            contentWarning = ContentWarning.SAFE, signatures = listOf("9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"),
            isPrivate = true, isBundled = false, status = status,
            brokenReason = if (status == ExtensionStatus.BROKEN) BrokenReason.FAILED else null, error = error, icon = icon,
        )
        compose.runOnUiThread {
            installed.value = listOf(
                fake("AComics", "ru", ExtensionStatus.UNTRUSTED, icon = iconDrawable(0xFF2E7D32.toInt(), "AC")),
                fake("Weeb Central", "en", ExtensionStatus.UNTRUSTED),
                fake("Comick", "all", ExtensionStatus.UNTRUSTED),
                fake("Old Reader", "en", ExtensionStatus.BROKEN, error = "java.lang.NoSuchFieldException: No field headers\$delegate in class " +
                    "Leu/kanade/tachiyomi/source/online/HttpSource;"),
                fake("Manga Park", "en", ExtensionStatus.LOADED, icon = iconDrawable(0xFFC62828.toInt(), "MP")),
            )
        }
        awaitText("Weeb Central")
        compose.mainClock.advanceTimeBy(1_000)
        shot("21_extensions")
        compose.onAllNodesWithText("Trust all")[0].performClick()
        compose.mainClock.advanceTimeBy(500)
        shotScreen("21b_trust_all")
        compose.onAllNodesWithText("Cancel")[0].performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Old Reader")[0].performClick()
        compose.mainClock.advanceTimeBy(1_500)
        shotScreen("21c_broken_details")
        dialogBack()
        compose.mainClock.advanceTimeBy(1_000)

        clickTab("Repositories")
        // Adding a repository is an inline card in the tab now: the field is right there, no dialog to open.
        compose.waitUntilAtLeastOneExists(hasText("Repository address"), 20_000)
        shot("22_add_repository")
        back()

        // Manga: a generated CBZ opens left to right; since 1.8.0 without the tap zones (they are opt-in again).
        val cbz = File(compose.activity.cacheDir, "Smoke Manga.cbz")
        writeCbz(cbz, 4)
        val outcome = runBlocking { GlobalContext.get().get<ImportService>().importLocalFile(cbz, "Smoke Manga.cbz") }
        assertTrue("import failed: $outcome", outcome is ImportOutcome.Added)
        clickTab("Library")
        awaitText("Smoke Manga")
        // A tile opens the card; the card's read action starts the reader (LibraryOpenResolver).
        compose.onAllNodesWithText("Smoke Manga")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasText("Read") and hasClickAction(), 20_000)
        compose.onNode(hasText("Read") and hasClickAction()).performClick()
        // The page number shows once the pages are in; the tap zones ("Back / Menu / Forward") are not shown.
        awaitText("1 / 4")
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("tap zones shown on opening", compose.onAllNodesWithText("Forward").fetchSemanticsNodes().isEmpty())
        shot("23_manga_reader")
        compose.mainClock.advanceTimeBy(4_000)
        runCatching {
            val view = compose.onAllNodes(hasText("Settings") and hasClickAction())
            if (view.fetchSemanticsNodes().isEmpty()) {
                compose.onAllNodes(isRoot())[0].performTouchInput { click(center) }
                compose.mainClock.advanceTimeBy(1_000)
            }
            view[0].performClick()
            compose.onNode(hasText("All reading settings") and hasClickAction()).performClick()
            compose.mainClock.advanceTimeBy(1_000)
            shotScreen("24_manga_settings")
        }.onFailure { println("manga settings screenshot skipped: $it") }
    }
}
