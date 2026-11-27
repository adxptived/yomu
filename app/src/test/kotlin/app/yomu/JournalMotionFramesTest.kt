package app.yomu

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.yomu.core.database.BookEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.ui.theme.ThemeConfig
import app.yomu.core.ui.theme.ThemeMode
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.domain.repository.StatsRepository
import app.yomu.profile.ProfileScreen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * Frames of the journal → history transition (2.20.0), one PNG per 20 ms, to assemble a short GIF for the report.
 * The clock is driven by hand so every frame lands inside the 200 ms slide-and-fade.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class JournalMotionFramesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val db: YomuDatabase get() = GlobalContext.get().get()
    private val stats: StatsRepository get() = GlobalContext.get().get()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun journalToHistoryFrames() {
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
        }
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                ProfileScreen(
                    viewModel = koinViewModel(), historyViewModel = koinViewModel(), statsViewModel = koinViewModel(),
                    onOpenBook = {}, onBookDetails = {}, onReadChapter = {}, onOpenTitle = {},
                    onOpenSettings = {}, onOpenLibrary = {}, onOpenCatalog = {},
                )
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("Blue Lock"), 60_000)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/journal_motion/00_start.png")
        compose.onAllNodesWithContentDescription("History")[0].performClick()
        var frame = 1
        repeat(11) {
            compose.mainClock.advanceTimeByFrame()
            captureScreenRoboImage("build/outputs/roborazzi/journal_motion/${frame.toString().padStart(2, '0')}_in.png")
            frame++
        }
        // Let the entry finish before the return gesture starts.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/journal_motion/${frame.toString().padStart(2, '0')}_history.png")
        frame++
        compose.mainClock.autoAdvance = false
        // And back: the pop transition is shorter; ten frames cover it.
        compose.onAllNodesWithContentDescription("Back")[0].performClick()
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            captureScreenRoboImage("build/outputs/roborazzi/journal_motion/${frame.toString().padStart(2, '0')}_back.png")
            frame++
        }
        compose.mainClock.autoAdvance = true
    }
}
