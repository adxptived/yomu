package app.yomu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.yomu.core.datastore.AppSettings
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
import org.robolectric.RuntimeEnvironment
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
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun cover(): ByteArray {
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, 0xFF0F766E.toInt(), 0xFF14B8A6.toInt(), Shader.TileMode.CLAMP) }
        val canvas = Canvas(bmp)
        canvas.drawRect(0f, 0f, 300f, 450f, paint)
        paint.shader = null
        paint.isAntiAlias = true
        paint.color = 0xFFFFD992.toInt()
        canvas.drawCircle(206f, 150f, 53f, paint)
        paint.color = 0xFF154D58.toInt()
        canvas.drawOval(-230f, 170f, 425f, 540f, paint)
        paint.color = 0xFF0B3544.toInt()
        canvas.drawOval(30f, 212f, 540f, 660f, paint)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Opens the library and imports a book with a cover. */
    @OptIn(ExperimentalTestApi::class)
    private fun importBook(title: String, fileName: String) {
        val settings = GlobalContext.get().get<AppSettings>()
        runBlocking {
            settings.whatsNewSeen.set(BuildConfig.VERSION_CODE.toLong())
            settings.onboardingDone.set(true)
        }
        val library = hasText("Library") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.waitUntilAtLeastOneExists(library, 20_000)
        compose.onAllNodes(library)[0].performClick()
        compose.waitUntilAtLeastOneExists(hasText("Library") and !hasClickAction(), 20_000)
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
    private fun openDetails(title: String = "The Tide Archive") {
        importBook(title, "tide.epub")
        compose.onAllNodesWithText(title)[0].performTouchInput { longClick() }
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
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w411dp-h680dp-xxhdpi")
    fun phoneDetailsTintedByCover() {
        openDetails()
        shot("311_details_cover_tint")
        val title = compose.onNodeWithText("The Tide Archive")
        val beforeScroll = title.getUnclippedBoundsInRoot().top
        title.performTouchInput { swipeUp(endY = -height.toFloat() * 4) }
        compose.waitForIdle()
        assertTrue("book content must scroll behind the bars", title.getUnclippedBoundsInRoot().top < beforeScroll)
        shot("314_details_scrolled_bars")
        compose.onNode(hasContentDescription("Add to favorites")).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Remove from favorites"), 10_000)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun largeTextTitleUsesFullHeaderWidth() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.activityRule.scenario.recreate()
        val title = "The Tide Archive and the Lost Records of the Northern Archipelago"
        openDetails(title)
        val titleBounds = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val readBounds = compose.onNodeWithText("Read").getUnclippedBoundsInRoot()
        assertTrue("title must start at the full-width action's leading edge", titleBounds.left == readBounds.left)
        shot("313_details_large_text")
    }

    @Test
    @Config(qualifiers = "w360dp-h460dp-xhdpi")
    fun libraryScrollsBehindItsHeader() {
        val title = "The Tide Archive"
        importBook(title, "library-tide.epub")
        for (index in 1..6) importBook("Field Notes $index", "field-notes-$index.epub")
        compose.onAllNodesWithText("Got it").let { tips ->
            if (tips.fetchSemanticsNodes().isNotEmpty()) tips[0].performClick()
        }
        val header = compose.onNode(hasText("Library") and !hasClickAction())
        val headerTop = header.getUnclippedBoundsInRoot().top
        val bookTitles = compose.onAllNodes(hasText("Field Notes", substring = true))
        val beforeScroll = bookTitles.fetchSemanticsNodes().associate { it.id to it.positionInRoot.y }
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.70f, endY = height * 0.43f, durationMillis = 600)
        }
        assertTrue("at least one visible book must move up", bookTitles.fetchSemanticsNodes().any {
            val before = beforeScroll[it.id]
            before != null && it.positionInRoot.y < before
        })
        assertTrue("the library header must stay pinned", header.getUnclippedBoundsInRoot().top == headerTop)
        shot("315_library_scrolled_header")
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
