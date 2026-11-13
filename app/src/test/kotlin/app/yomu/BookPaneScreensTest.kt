package app.yomu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.yomu.data.book.EpubBuilder
import app.yomu.data.book.EpubMeta
import app.yomu.data.library.ImportOutcome
import app.yomu.data.library.ImportService
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The book page tinted with its cover colour: as a screen on a phone and as a side pane next to the library on a tablet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BookPaneScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<YomuActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 20_000)

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun cover(): ByteArray {
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, 0xFF0F766E.toInt(), 0xFF14B8A6.toInt(), Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, 300f, 450f, paint)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Skips onboarding (when this process has not passed it yet) and imports a book with a cover. */
    @OptIn(ExperimentalTestApi::class)
    private fun importBook(title: String, fileName: String) {
        compose.waitUntil(20_000) {
            listOf("Welcome to Yomu", "Library").any { compose.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
        if (compose.onAllNodes(hasText("Welcome to Yomu")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("Next")[0].performClick()
            awaitText("Where are your books?")
            compose.onAllNodesWithText("Skip")[0].performClick()
        }
        val epub = File(compose.activity.cacheDir, fileName)
        EpubBuilder(EpubMeta(title = title, authors = listOf("Mara Ellison"), language = "en")).apply {
            coverHref = addResource("cover.png", "image/png", cover())
            addChapter("Chapter 1", "<p>The sea kept its own records.</p>")
            addChapter("Chapter 2", "<p>Low tide.</p>")
        }.writeTo(epub)
        val outcome = runBlocking { GlobalContext.get().get<ImportService>().importLocalFile(epub, fileName) }
        assertTrue("import failed: $outcome", outcome is ImportOutcome.Added)
        awaitText(title)
        compose.mainClock.advanceTimeBy(1_000)
    }

    /** A long press opens the book's actions; Details opens the page. */
    private fun openDetails() {
        importBook("The Tide Archive", "tide.epub")
        compose.onAllNodesWithText("The Tide Archive")[0].performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodesWithText("Details")[0].performClick()
        awaitText("Mara Ellison")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletLibraryWithDetailsPane() {
        openDetails()
        shot("310_tablet_details_pane")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneDetailsTintedByCover() {
        openDetails()
        shot("311_details_cover_tint")
    }

    /** The title of a library tile opens the details; the cover is the way into the book. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun titleOpensDetails() {
        importBook("The Title Target", "title-target.epub")
        // The title also appears in the "Continue reading" card above the grid; the grid tile is the last one.
        val titles = compose.onAllNodesWithText("The Title Target")
        titles[titles.fetchSemanticsNodes().lastIndex].performClick()
        awaitText("Mara Ellison")
        shot("312_details_from_title")
    }
}
