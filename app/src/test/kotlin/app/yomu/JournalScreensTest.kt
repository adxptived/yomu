package app.yomu

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.yomu.core.database.BookEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.components.LocalReduceMotion
import app.yomu.core.ui.theme.ThemeConfig
import app.yomu.core.ui.theme.ThemeMode
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.domain.repository.BookRepository
import app.yomu.domain.repository.StatsRepository
import app.yomu.profile.ProfileScreen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The journal (2.20.0): the first book, today's goal, the tools and the way to the history and the statistics.
 * Screenshots cover the first start, past sessions without a history, hidden blocks, Russian, the light theme and a
 * narrow window; the assertions cover the tap counts and the return path from the statistics.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class JournalScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dark = ThemeConfig(mode = ThemeMode.DARK)
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings: AppSettings get() = GlobalContext.get().get()
    private val db: YomuDatabase get() = GlobalContext.get().get()
    private val stats: StatsRepository get() = GlobalContext.get().get()

    @Before
    fun resetJournalSettings() = runBlocking {
        // Settings live outside the database fixtures: a hidden-block test must not decide the next screenshot.
        settings.profileShowStats.set(true)
        settings.profileShowMilestones.set(true)
        settings.profileShowTop.set(true)
        settings.profileShowRecent.set(true)
        settings.profileShowShare.set(true)
        val config = Configuration(context.resources.configuration).apply {
            fontScale = 1f
            setLocale(java.util.Locale.ENGLISH)
        }
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    private fun setJournal(theme: ThemeConfig = dark, reduceMotion: Boolean = false) {
        compose.setContent {
            YomuTheme(theme) {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    ProfileScreen(
                        viewModel = koinViewModel(),
                        historyViewModel = koinViewModel(),
                        statsViewModel = koinViewModel(),
                        onOpenBook = {},
                        onBookDetails = {},
                        onReadChapter = {},
                        onOpenTitle = {},
                        onOpenSettings = {},
                        onOpenDownloads = {},
                        onOpenNotes = {},
                        onOpenLibrary = {},
                        onOpenCatalog = {},
                    )
                }
            }
        }
    }

    private fun seedBook(title: String = "Blue Lock", progress: Float = 0.35f, format: String = "EPUB"): Long = runBlocking {
        db.books().insert(
            BookEntity(
                title = title, sortTitle = title, authorsText = "Muneyuki Kaneshiro", seriesName = null,
                seriesId = null, seriesIndex = null, language = "en", description = null, publisher = null,
                format = format, uri = "file:///$title", isLinked = false, fileSize = 10,
                fileHash = title, coverPath = null, status = "READING", isManga = false,
                addedAt = 1, lastReadAt = null, progress = progress, readerSettingsJson = null,
            ),
        )
    }

    private fun seedSession(bookId: Long?, minutes: Int = 25) = runBlocking {
        val now = System.currentTimeMillis()
        stats.recordSession(bookId, now - minutes * 60_000L, minutes * 60_000L, pages = minutes, chars = minutes * 900L)
    }

    @Test
    fun firstStartOffersBothWaysToTheFirstBook() {
        runBlocking { settings.onboardingDone.set(true) }
        setJournal()
        awaitText("Start reading")
        awaitText("Open library")
        awaitText("Find in catalog")
        // No name, registration date or Overview/History switch on the journal.
        assertTrue(compose.onAllNodes(hasText("Overview")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasText("Reader", substring = false)).fetchSemanticsNodes().isEmpty())
        shot("240_journal_first_start")
    }

    @Test
    fun pastSessionsWithoutHistoryKeepTheNumbers() {
        // A session of a book that is gone: the statistics exist, the history entry cannot be shown.
        seedSession(bookId = 424242L, minutes = 37)
        setJournal()
        awaitText("Choose a book")
        // "Start reading" would be wrong for a returning reader.
        assertTrue(compose.onAllNodes(hasText("Start reading")).fetchSemanticsNodes().isEmpty())
        awaitText("Today")
        shot("241_journal_past_sessions")
    }

    @Test
    fun hiddenBlocksKeepHistoryAndStatisticsReachable() {
        seedSession(seedBook())
        runBlocking {
            settings.profileShowRecent.set(false)
            settings.profileShowStats.set(false)
            settings.profileShowMilestones.set(false)
            settings.profileShowTop.set(false)
            settings.profileShowShare.set(false)
        }
        setJournal()
        awaitText("My tools")
        // Hidden blocks are gone, but the tools still open the statistics and the top bar the history.
        assertTrue(compose.onAllNodes(hasText("Today")).fetchSemanticsNodes().isEmpty())
        compose.onNode(hasText("Statistics") and hasClickAction()).performClick()
        awaitText("Time read")
        compose.onNodeWithContentDescription("Back").performClick()
        awaitText("My tools")
        shot("242_journal_hidden_blocks")
    }

    @Test
    fun historyHasItsOwnTitleAndReturnsToTheJournal() {
        val bookId = seedBook()
        seedSession(bookId)
        setJournal()
        awaitText("Blue Lock")
        compose.onAllNodesWithContentDescription("History")[0].performClick()
        awaitText("History")
        awaitText("Blue Lock")
        shot("243_journal_history")
        // Statistics opened from the history come back to the history, not to the journal.
        compose.onNodeWithContentDescription("Statistics").performClick()
        awaitText("Time read")
        compose.onNodeWithContentDescription("Back").performClick()
        awaitText("History")
        // The statistics returned to the history, not to the journal.
        assertTrue(compose.onAllNodes(hasText("My tools")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Back").performClick()
        awaitText("My tools")
    }

    /** The three journal actions are one focus each, tall enough for a finger, and named by their destination. */
    @Test
    fun journalActionsAreSingleAccessibleTargets() {
        val bookId = seedBook("Blue Lock")
        seedSession(bookId)
        setJournal()
        awaitText("Blue Lock")
        val book = compose.onNode(hasText("Blue Lock") and hasClickAction())
        book.assertExists()
        assertEquals(1, compose.onAllNodes(hasText("Blue Lock") and hasClickAction()).fetchSemanticsNodes().size)
        assertTrue(book.fetchSemanticsNode().boundsInRoot.height >= 48 * compose.density.density)
        // The book row merges its cover, title and meta into one spoken label with a "Continue reading" action.
        assertTrue(book.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
        compose.onNodeWithContentDescription("History").assertExists()
        compose.onNodeWithContentDescription("History").performClick()
        awaitText("History")
        compose.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun continueCardNamesTheSavedChapter() {
        val bookId = seedBook("Blue Lock")
        seedSession(bookId)
        val books: BookRepository = GlobalContext.get().get()
        runBlocking {
            books.saveProgress(
                bookId,
                app.yomu.domain.model.Locator(chapter = 1, chapterTitle = "Chapter 1", totalProgression = 0.35f),
            )
        }
        setJournal()
        awaitText("Chapter 1")
        shot("247_journal_saved_chapter")
    }

    @Test
    fun aBookWithoutSavedProgressDoesNotInheritThePreviousChapter() {
        val firstBook = seedBook()
        seedSession(firstBook)
        val books: BookRepository = GlobalContext.get().get()
        runBlocking {
            books.saveProgress(firstBook, app.yomu.domain.model.Locator(chapter = 2, chapterTitle = "The previous chapter"))
        }
        setJournal()
        awaitText("The previous chapter")

        seedSession(seedBook("A new book"))

        awaitText("A new book")
        compose.onNode(hasText("A new book") and hasClickAction()).assertExists()
        compose.onNode(hasText("The previous chapter", substring = true)).assertDoesNotExist()
    }

    @Test
    fun anAudiobookOffersToContinueListening() {
        seedSession(seedBook("Audiobook", format = "AUDIO"))
        setJournal()

        awaitText("Audiobook")
        compose.onNode(hasText("Continue listening") and hasClickAction()).assertExists()
        shot("250_journal_audiobook")
    }

    @Test
    fun reducedMotionReplacesJournalTitleAndBodyWithoutACrossfade() {
        seedSession(seedBook())
        setJournal(reduceMotion = true)
        awaitText("My tools")
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithContentDescription("History").performClick()
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            compose.onNode(hasText("History"), useUnmergedTree = true).assertExists()
            compose.onNode(hasText("Journal")).assertDoesNotExist()
            compose.onNode(hasText("My tools")).assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun oneTapOpensTheSavedBook() {
        var opened: Long? = null
        val bookId = seedBook()
        seedSession(bookId)
        compose.setContent {
            YomuTheme(dark) {
                ProfileScreen(
                    viewModel = koinViewModel(), historyViewModel = koinViewModel(), statsViewModel = koinViewModel(),
                    onOpenBook = { opened = it }, onBookDetails = {}, onReadChapter = {}, onOpenTitle = {},
                    onOpenSettings = {}, onOpenLibrary = {}, onOpenCatalog = {},
                )
            }
        }
        awaitText("Blue Lock")
        compose.onNode(hasText("Continue reading") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(bookId, opened)
    }

    @Test
    fun journalAtTwoHundredPercentFont() {
        val config = Configuration(context.resources.configuration).apply { fontScale = 2f }
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        val bookId = seedBook()
        seedSession(bookId)
        setJournal()
        awaitText("Blue Lock")
        shot("244_journal_font_200")
        // Every tool stays reachable: the list scrolls, so the last row can be brought above the bar.
        compose.onNode(hasText("Tracking")).performScrollTo()
        assertTrue(compose.onNode(hasText("Tracking")).fetchSemanticsNode().boundsInRoot.bottom > 0f)
    }

    @Test
    fun journalInRussianAndLightTheme() {
        val bookId = seedBook("Синий замок")
        seedSession(bookId)
        val config = Configuration(context.resources.configuration).apply { setLocale(java.util.Locale("ru")) }
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        setJournal(ThemeConfig(mode = ThemeMode.LIGHT))
        awaitText("Журнал")
        awaitText("Синий замок")
        shot("245_journal_ru_light")
    }
}

/** The same journal on the smallest accepted window: 360 × 640 dp. */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class JournalCompactScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val settings: AppSettings get() = GlobalContext.get().get()
    private val db: YomuDatabase get() = GlobalContext.get().get()
    private val stats: StatsRepository get() = GlobalContext.get().get()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun journalFitsOnA360Window() {
        val bookId = runBlocking {
            db.books().insert(
                BookEntity(
                    title = "Blue Lock", sortTitle = "Blue Lock", authorsText = "Muneyuki Kaneshiro", seriesName = null,
                    seriesId = null, seriesIndex = null, language = "en", description = null, publisher = null,
                    format = "EPUB", uri = "file:///blue-lock.epub", isLinked = false, fileSize = 10,
                    fileHash = "blue-lock", coverPath = null, status = "READING", isManga = false,
                    addedAt = 1, lastReadAt = null, progress = 0.35f, readerSettingsJson = null,
                ),
            )
        }
        runBlocking {
            val now = System.currentTimeMillis()
            stats.recordSession(bookId, now - 25 * 60_000L, 25 * 60_000L, pages = 25, chars = 22_500)
            settings.onboardingDone.set(true)
        }
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                ProfileScreen(
                    viewModel = koinViewModel(), historyViewModel = koinViewModel(), statsViewModel = koinViewModel(),
                    onOpenBook = {}, onBookDetails = {}, onReadChapter = {}, onOpenTitle = {},
                    onOpenSettings = {}, onOpenDownloads = {}, onOpenNotes = {}, onOpenLibrary = {}, onOpenCatalog = {},
                )
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("Blue Lock"), 60_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/246_journal_360.png")
    }
}
