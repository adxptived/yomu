package app.yomu

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import app.yomu.core.database.BookEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.feature.library.LibraryScreen
import com.github.takahirom.roborazzi.captureRoboImage
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
 * 2.4: choosing books with checkboxes, the selection bar and the circular long-press wheel. Rows go straight into the
 * database; nothing touches the network.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class Release240ScreensTest {
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

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 20_000)

    private fun seed() = runBlocking {
        val db = GlobalContext.get().get<YomuDatabase>()
        val now = System.currentTimeMillis()
        listOf(
            Triple("The Long Road Home", "EPUB", 4),
            Triple("North of Everything", "PDF", 5),
            Triple("A Small Blue Book", "FB2", 2),
            Triple("Collected Short Stories", "DOCX", 0),
        ).forEachIndexed { i, (title, format, rating) ->
            db.books().insert(
                BookEntity(
                    title = title, sortTitle = title, authorsText = "Author ${i + 1}", seriesName = null, seriesId = null,
                    seriesIndex = null, language = "en", description = null, publisher = null, format = format,
                    uri = "file:///books/$i.$format", isLinked = false, fileSize = 1_000_000L * (i + 1), fileHash = null,
                    coverPath = null, status = if (i == 1) "READING" else "NEW", isManga = false,
                    addedAt = now - i * 86_400_000L, lastReadAt = if (i == 1) now else null,
                    progress = if (i == 1) 0.42f else 0f, readerSettingsJson = null, favorite = i == 0, rating = rating,
                ),
            )
        }
    }

    @Test
    fun librarySelectionAndWheel() {
        seed()
        compose.setContent {
            YomuTheme(forceDark = true) {
                LibraryScreen(
                    viewModel = koinViewModel(), onOpenBook = {}, onOpenDetails = {}, onFindBooksOnline = {},
                    onOpenSettings = {},
                )
            }
        }
        awaitText("The Long Road Home")
        compose.mainClock.advanceTimeBy(1_000)
        // The one-time hint would sit on top of the grid in a fresh profile.
        runCatching { compose.onAllNodes(hasText("Got it"))[0].performClick(); compose.waitForIdle() }
        shot("240_library")

        compose.onAllNodes(hasContentDescription("Select books"))[0].performClick()
        compose.mainClock.advanceTimeBy(800)
        runCatching {
            compose.onAllNodes(hasContentDescription("North of Everything", substring = true))[0].performClick()
            compose.onAllNodes(hasContentDescription("A Small Blue Book", substring = true))[0].performClick()
        }
        shot("241_library_selection")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        runCatching {
            compose.onAllNodes(hasContentDescription("The Long Road Home", substring = true))[0].performTouchInput { longClick() }
            compose.waitForIdle()
            // The wheel is a dialog: its own root is the last one.
            val roots = compose.onAllNodes(isRoot())
            roots[roots.fetchSemanticsNodes().lastIndex].captureRoboImage("build/outputs/roborazzi/242_library_wheel.png")
        }
    }
}
