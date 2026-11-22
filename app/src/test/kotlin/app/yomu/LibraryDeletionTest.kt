package app.yomu

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.yomu.core.database.BookEntity
import app.yomu.core.database.YomuDatabase
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.feature.library.LibraryScreen
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

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LibraryDeletionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun tearDown() = stopKoin()

    @Test
    fun swipeCanBeCancelledAndUndoRestoresTheSameBook() {
        val koin = GlobalContext.get()
        val books = koin.get<YomuDatabase>().books()
        val title = "Swipe test book"
        val id = runBlocking {
            koin.get<AppSettings>().libraryGrid.set(false)
            koin.get<AppSettings>().showContinueReading.set(false)
            books.insert(BookEntity(
                title = title, sortTitle = title, authorsText = "", seriesName = null, seriesId = null,
                seriesIndex = null, language = null, description = null, publisher = null, format = "EPUB",
                uri = "file:///swipe-test.epub", isLinked = false, fileSize = 10, fileHash = "swipe-test",
                coverPath = null, status = "READING", isManga = false, addedAt = 1, lastReadAt = null,
                progress = .5f, readerSettingsJson = null,
            ))
        }
        compose.setContent {
            YomuTheme {
                LibraryScreen(koinViewModel(), onOpenBook = {}, onOpenDetails = {}, onFindBooksOnline = {}, onOpenSettings = {})
            }
        }
        val book = compose.onNodeWithText(title)
        compose.waitUntilAtLeastOneExists(hasText(title), 20_000)
        book.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Cancel").performClick()
        book.assertExists()
        book.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Remove from library").performClick()
        compose.waitUntilDoesNotExist(hasText(title), 20_000)
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntilAtLeastOneExists(hasText(title), 20_000)
        assertEquals(.5f, runBlocking { books.get(id)?.progress })
    }
}
