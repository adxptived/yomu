package app.yomu

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The second package of the UI plan: the route through the tabs. A scrolled shelf survives a switch to another tab
 * and back, the last row scrolls above the floating navigation bar and still takes taps, and on a wide window the
 * details pane marks the book it shows in the list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NavigationScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<YomuActivity>()

    @Before
    @OptIn(ExperimentalTestApi::class)
    fun openLibrary() {
        val settings = GlobalContext.get().get<AppSettings>()
        runBlocking {
            settings.whatsNewSeen.set(BuildConfig.VERSION_CODE.toLong())
            settings.onboardingDone.set(true)
        }
        val library = hasText("Library") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.waitUntilAtLeastOneExists(library, 20_000)
        clickTab("Library")
        settle()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** Runs the platform animations too (the tab switch animates on the main looper). */
    private fun settle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** Item of the bottom navigation bar (in-screen tabs use the same role). */
    private fun clickTab(label: String) {
        compose.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).let { nodes ->
            val count = nodes.fetchSemanticsNodes().size
            nodes[count - 1].performClick()
        }
    }

    /** Imports a book into the active library. */
    private fun importBook(title: String, fileName: String) {
        val epub = File(compose.activity.cacheDir, fileName)
        EpubBuilder(EpubMeta(title = title, authors = listOf("Mara Ellison"), language = "en")).apply {
            addChapter("Chapter 1", "<p>The sea kept its own records.</p>")
            addChapter("Chapter 2", "<p>Low tide.</p>")
        }.writeTo(epub)
        val outcome = runBlocking { GlobalContext.get().get<ImportService>().importLocalFile(epub, fileName) }
        assertTrue("import failed: $outcome", outcome is ImportOutcome.Added)
        awaitText(title)
    }

    /** A shelf long enough to scroll, plus the one-time tip out of the way. */
    private fun importShelf() {
        importBook("The Tide Archive", "nav-tide.epub")
        for (index in 1..6) importBook("Field Notes $index", "nav-field-notes-$index.epub")
        compose.onAllNodes(hasText("Got it")).let { tips ->
            if (tips.fetchSemanticsNodes().isNotEmpty()) tips[0].performClick()
        }
    }

    /** Swipes up until [text] is on screen (the grid composes rows lazily), at most [limit] times. */
    @OptIn(ExperimentalTestApi::class)
    private fun scrollTo(text: String, limit: Int = 10) {
        repeat(limit) {
            if (compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()) {
                val node = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().last()
                if (node.positionInRoot.y < compose.onRoot().fetchSemanticsNode().size.height) return
            }
            compose.onRoot().performTouchInput { swipeUp(startY = height * 0.72f, endY = height * 0.30f, durationMillis = 400) }
            settle()
        }
        awaitText(text)
    }

    /** The top of the floating navigation bar: the topmost node of the bottom-most cluster of bar tabs. */
    private fun navBarTop(): Float {
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
        assertTrue("no tab nodes on screen", tabs.isNotEmpty())
        return tabs.groupBy { it.boundsInRoot.bottom }.maxBy { it.key }.value.minOf { it.boundsInRoot.top }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun tabSwitchKeepsTheScrolledShelf() {
        importShelf()
        scrollTo("Field Notes 6")
        val tile = compose.onAllNodes(hasText("Field Notes 6")).fetchSemanticsNodes().last()
        val topBefore = tile.positionInRoot.y
        compose.onAllNodes(hasText("Field Notes 6"))[0].assertIsDisplayed()

        clickTab("Catalog")
        settle()
        clickTab("Library")
        settle()

        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Field Notes 6")).fetchSemanticsNodes().isNotEmpty() }
        val tileAfter = compose.onAllNodes(hasText("Field Notes 6")).fetchSemanticsNodes().last()
        compose.runOnIdle { assertTrue("the shelf must return scrolled", tileAfter.positionInRoot.y == topBefore) }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun lastShelfRowClearsTheFloatingBar() {
        importShelf()
        scrollTo("Field Notes 6")
        shot("78_nav_bar_glass")
        val barTop = navBarTop()
        val lastTitle = compose.onAllNodes(hasText("Field Notes 6")).fetchSemanticsNodes().last()
        compose.runOnIdle {
            assertTrue(
                "the last row must end above the floating bar (bottom ${lastTitle.boundsInRoot.bottom}, bar at $barTop)",
                lastTitle.boundsInRoot.bottom <= barTop,
            )
        }
        // The last row still takes the tap: its title opens the details card.
        compose.onAllNodesWithText("Field Notes 6").let { nodes -> nodes[nodes.fetchSemanticsNodes().lastIndex].performClick() }
        awaitText("Mara Ellison")
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun allShowsOnlineMangaWithTheShelfHidden() {
        val koin = GlobalContext.get()
        runBlocking { koin.get<AppSettings>().libraryShelfHidden.set(true) }
        runBlocking {
            koin.get<app.yomu.core.database.YomuDatabase>().catalog().insert(
                app.yomu.core.database.CatalogItemEntity(
                    sourceId = 424_242L, url = "/all/online-manga", title = "Online Manga Only",
                    author = "Remote Author", artist = null, description = null, genres = "Action", status = 1,
                    coverUrl = null, type = "MANGA", favorite = true, categoryId = null,
                    addedAt = System.currentTimeMillis(), lastUpdateAt = 0, initialized = true,
                ),
            )
        }
        // The app opens on the Library tab; the catalog shelf is off, so the title can only come from the ordinary
        // All grid.
        settle()
        awaitText("Online Manga Only")
        compose.onAllNodesWithText("Online Manga Only")[0].assertIsDisplayed()
        shot("79_library_all_online")
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun detailsPaneHighlightsTheOpenBook() {
        importBook("The Tide Archive", "pane-tide.epub")
        compose.onAllNodes(hasText("Got it")).let { tips ->
            if (tips.fetchSemanticsNodes().isNotEmpty()) tips[0].performClick()
        }
        // The title of the tile opens the details; on a wide window it becomes a pane beside the list.
        compose.onAllNodesWithText("The Tide Archive").let { nodes -> nodes[nodes.fetchSemanticsNodes().lastIndex].performClick() }
        awaitText("Mara Ellison")
        // The list stays beside the pane, and the open book keeps its place in it.
        compose.onAllNodesWithText("The Tide Archive")[0].assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        shot("77_tablet_pane_highlight")
    }
}
