package app.yomu

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.yomu.core.database.BookEntity
import app.yomu.core.database.ReadingSessionEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.feature.library.HistoryPane
import app.yomu.feature.library.HistoryViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The reading history pane (its own screen and the History tab of the profile, package 3 of the UI plan): a record
 * of yesterday shows the book and the stopping place in its row, opens the book, and coming back keeps the date.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 20_000)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun yesterdayEntryOpensTheBookAndKeepsItsDate() {
        val db = GlobalContext.get().get<YomuDatabase>()
        val yesterday = System.currentTimeMillis() - 86_400_000L
        val bookId = runBlocking {
            db.books().insert(
                BookEntity(
                    title = "The Yesterday Ledger", sortTitle = "the yesterday ledger", authorsText = "Mara Ellison",
                    seriesName = null, seriesId = null, seriesIndex = null, language = "en", description = null,
                    publisher = null, format = "EPUB", uri = "file:///ledger.epub", isLinked = false, fileSize = 1200,
                    fileHash = null, coverPath = null, status = "READING", isManga = false, addedAt = yesterday,
                    lastReadAt = yesterday, progress = 0.3f, readerSettingsJson = null,
                ),
            )
        }
        runBlocking {
            db.sessions().insert(
                ReadingSessionEntity(bookId = bookId, startedAt = yesterday, durationMs = 120_000, pages = 14, chars = 5_000),
            )
        }

        lateinit var model: HistoryViewModel
        var openedLocally: Long? = null
        var inHistory by mutableStateOf(true)
        compose.setContent {
            YomuTheme {
                if (inHistory) {
                    HistoryPane(
                        viewModel = koinViewModel<HistoryViewModel>().also { model = it },
                        searching = false,
                        onSearching = {},
                        snackbar = remember { SnackbarHostState() },
                        onOpenBook = { openedLocally = it },
                        onBookDetails = {},
                        onReadChapter = {},
                        onOpenTitle = {},
                        onOpenStats = {},
                    )
                } else {
                    Text("Reading in progress")
                }
            }
        }
        awaitText("Yesterday")
        awaitText("The Yesterday Ledger")
        awaitText("Mara Ellison")
        // The stopping place stands in the meta line next to the title (30% of the book).
        awaitText("30%")
        compose.mainClock.advanceTimeBy(1_000)
        shot("76_history_yesterday")
        compose.onNode(hasText("The Yesterday Ledger") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(bookId, openedLocally) }

        // Back from the book: the pane leaves composition and returns; the tab's ViewModel keeps the same date.
        compose.runOnIdle { inHistory = false }
        awaitText("Reading in progress")
        compose.runOnIdle { inHistory = true }
        awaitText("Yesterday")
        awaitText("The Yesterday Ledger")
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}
