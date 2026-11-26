package app.yomu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.yomu.core.datastore.AppSettings
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.data.source.BrokenReason
import app.yomu.data.source.ContentWarning
import app.yomu.data.source.ExtensionFormat
import app.yomu.data.source.ExtensionManager
import app.yomu.data.source.ExtensionStatus
import app.yomu.data.source.InstalledExtension
import app.yomu.data.source.SourceManager
import app.yomu.domain.catalog.ContentPreference
import app.yomu.feature.catalog.BrowseArgs
import app.yomu.feature.catalog.CatalogScreen
import app.yomu.feature.catalog.ExtensionsScreen
import app.yomu.feature.catalog.SourceBrowseScreen
import app.yomu.feature.catalog.SourceBrowseViewModel
import app.yomu.source.CatalogSource
import app.yomu.source.FilterList
import app.yomu.source.ItemsPage
import app.yomu.source.SItem
import app.yomu.source.SourceContent
import app.yomu.source.SourceIds
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The state the user reported: several Tachiyomi extensions installed, 18+ content on, and the catalog that used to
 * hide the loaded ones behind a "not in the list" card. The screenshots prove the fixed state of 2.16.0: the sources
 * of installed extensions are ordinary rows of the catalog, they open, and the extensions screen explains the rest.
 * Screenshots go to build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ExtensionCatalogScreensTest {
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
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    /** A stand-in for a loaded Tachiyomi extension source: no network, fixed popular list. */
    private class FixtureSource(name: String, lang: String, titles: List<String>) : CatalogSource {
        private val items = titles.mapIndexed { i, t -> SItem(url = "/manga/$i", title = t, author = "Fixture Author") }
        override val id = SourceIds.generate(name, lang)
        override val name = name
        override val lang = lang
        override val content = SourceContent.MANGA
        override val supportsLatest = true

        override suspend fun getPopular(page: Int): ItemsPage = ItemsPage(items, page < 3)
        override suspend fun getLatest(page: Int): ItemsPage = ItemsPage(items.asReversed(), page < 3)
        override suspend fun search(query: String, filters: FilterList, page: Int): ItemsPage =
            ItemsPage(items.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    private fun ext(
        name: String,
        lang: String,
        status: ExtensionStatus,
        warning: ContentWarning = ContentWarning.SAFE,
        sources: List<CatalogSource> = emptyList(),
        icon: Drawable? = null,
    ) = InstalledExtension(
        pkg = "eu.kanade.tachiyomi.extension.$lang.${name.lowercase().replace(" ", "")}",
        name = name, versionName = "1.4.16", versionCode = 1, format = ExtensionFormat.TACHIYOMI, libVersion = 1.4,
        lang = lang, contentWarning = warning, signatures = listOf("9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"),
        isPrivate = false, isBundled = false, status = status,
        brokenReason = if (status == ExtensionStatus.BROKEN) BrokenReason.FAILED else null,
        error = if (status == ExtensionStatus.BROKEN) "java.lang.NoSuchMethodError: No static method headers" else null,
        sources = sources, icon = icon,
    )

    private fun icon(color: Long, letters: String): Drawable {
        val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawRoundRect(0f, 0f, 192f, 192f, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() })
        canvas.drawText(letters, 96f, 124f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt(); textSize = 86f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        })
        return BitmapDrawable(compose.activity.resources, bmp)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun runExtensionScreens(dark: Boolean, catalogShot: String, browseShot: String, extensionsShot: String) {
        val settings = GlobalContext.get().get<AppSettings>()
        val manager = GlobalContext.get().get<ExtensionManager>()
        runBlocking {
            // The device state with 18+ content on (safe content would hide the two adult rows by design); every
            // language of the catalog shown.
            settings.contentPreference.set(ContentPreference.MIXED)
            settings.catalogLanguages.set(setOf(SourceManager.ALL_LANGUAGES))
            // The startup scan finished before the screen composes; the fakes cannot be overwritten after this.
            GlobalContext.get().get<SourceManager>().ready.first { it }
        }
        @Suppress("UNCHECKED_CAST")
        val installed = ExtensionManager::class.java.getDeclaredField("_installed").apply { isAccessible = true }
            .get(manager) as MutableStateFlow<List<InstalledExtension>>

        val weeb = ext("Weeb Central", "en", ExtensionStatus.LOADED,
            sources = listOf(FixtureSource("Weeb Central", "en", listOf(
                "Solo Leveling", "Omniscient Reader's Viewpoint", "The Beginning After The End", "Eleceed",
                "Nano Machine", "Overgeared", "SSS-Class Suicide Hunter", "Second Life Ranker", "The Great Mage",
            ))),
            icon = icon(0xFF2E7D32, "WC"))
        val mangaToday = ext("Manga Today", "en", ExtensionStatus.LOADED, warning = ContentWarning.NSFW,
            sources = listOf(FixtureSource("Manga Today", "en", listOf("Midnight Library", "After Hours", "Late Night Tales"))),
            icon = icon(0xFFC62828, "MT"))
        val comick = ext("Comick", "all", ExtensionStatus.LOADED,
            sources = listOf(FixtureSource("Comick", "all", listOf("One Piece", "Chainsaw Man", "Blue Lock", "Sakamoto Days"))),
            icon = icon(0xFF6A1B9A, "MC"))
        val asm = ext("AsmHentai", "all", ExtensionStatus.LOADED, warning = ContentWarning.NSFW,
            sources = listOf(FixtureSource("AsmHentai", "all", listOf("Studio Works"))),
            icon = icon(0xFFAD1457, "AH"))
        val untrusted = ext("Uncensored Manhwa", "en", ExtensionStatus.UNTRUSTED)
        val broken = ext("Galaxy Manga", "en", ExtensionStatus.BROKEN, icon = icon(0xFF1565C0, "GM"))

        var browseId by mutableStateOf<Long?>(null)
        var showExtensions by mutableStateOf(false)
        compose.setContent {
            YomuTheme(forceDark = dark) {
                val id = browseId
                when {
                    id != null -> SourceBrowseScreen(
                        viewModel = koinViewModel<SourceBrowseViewModel> { parametersOf(BrowseArgs(sourceId = id)) },
                        onBack = { browseId = null }, onOpenTitle = {}, onOpenNavigation = { _, _, _ -> }, onOpenWebView = {},
                    )
                    showExtensions -> ExtensionsScreen(koinViewModel(), onBack = { showExtensions = false })
                    else -> CatalogScreen(
                        viewModel = koinViewModel(),
                        onOpenSource = { browseId = it }, onOpenTitle = {}, onReadChapter = {},
                        onGlobalSearch = {}, onOpenExtensions = { showExtensions = true }, onOpenDownloads = {},
                    )
                }
            }
        }
        awaitText("MangaDex")
        // The device state: six installed extensions, four of them loaded with working sources. A scan that a
        // previous screen started can still overwrite the list, so put the state back until it stays.
        val fakes = listOf(weeb, mangaToday, comick, asm, untrusted, broken)
        fun inject() = compose.runOnUiThread { installed.value = fakes }
        inject()
        var catalogStable = 0
        repeat(40) {
            compose.mainClock.advanceTimeBy(300)
            compose.waitForIdle()
            val shown = compose.onAllNodesWithText("Manga Today").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("Weeb Central").fetchSemanticsNodes().isNotEmpty()
            catalogStable = if (shown) catalogStable + 1 else 0
            if (catalogStable == 0) inject()
            if (catalogStable >= 3) return@repeat
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        shot(catalogShot)
        compose.runOnIdle {
            val model = GlobalContext.get().get<SourceManager>()
            val infos = model.infos.value
            // The four loaded extension sources sit in the registry next to the built-ins; the two adult ones carry
            // the 18+ badge.
            assertTrue(weeb.sources.single().id in infos.map { it.id })
            assertTrue(infos.single { it.id == mangaToday.sources.single().id }.isNsfw)
        }

        // The source of an installed extension opens like a built-in one: popular titles right in the browse screen.
        compose.onNodeWithText("Weeb Central").performClick()
        awaitText("Solo Leveling")
        compose.mainClock.advanceTimeBy(2_000)
        shot(browseShot)

        compose.runOnIdle { browseId = null }
        // "Manage" is the extensions icon of the action row; its label lives in the content description (B1).
        val manageExtensions = hasContentDescription("Extensions") and hasClickAction()
        compose.waitUntilAtLeastOneExists(manageExtensions, 60_000)
        compose.onNode(manageExtensions).performClick()
        awaitText("Installed")
        compose.mainClock.advanceTimeBy(1_000)
        // The screen's own scan keeps replacing the list (the startup reload and the store refresh): put the device
        // state back until it stays.
        var stable = 0
        repeat(40) {
            if (stable == 0) compose.runOnUiThread { installed.value = fakes }
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
            val shown = compose.onAllNodesWithText("Uncensored Manhwa").fetchSemanticsNodes().isNotEmpty()
            stable = if (shown) stable + 1 else 0
            if (stable >= 3) return@repeat
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        shot(extensionsShot)
    }

    @Test
    fun lightShots() = runExtensionScreens(
        dark = false,
        catalogShot = "40_catalog_extension_sources",
        browseShot = "41_extension_source_browse",
        extensionsShot = "42_extensions_installed",
    )

    /** The user's phone runs the dark theme; the same three screens on it. */
    @Test
    fun darkShots() = runExtensionScreens(
        dark = true,
        catalogShot = "43_catalog_extension_sources_dark",
        browseShot = "44_extension_source_browse_dark",
        extensionsShot = "45_extensions_installed_dark",
    )
}
