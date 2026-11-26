package app.yomu

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.test.core.app.ApplicationProvider
import app.yomu.core.database.TrackEntity
import app.yomu.core.ui.theme.ThemeConfig
import app.yomu.core.ui.theme.ThemeMode
import app.yomu.core.ui.theme.YomuTheme
import app.yomu.data.catalog.TrackerPageSource
import app.yomu.domain.model.TrackerPage
import app.yomu.domain.model.TrackerPerson
import app.yomu.domain.model.TrackerRecommendation
import app.yomu.domain.model.TrackerSection
import app.yomu.domain.model.TrackerTitleDetails
import app.yomu.domain.tracking.TrackService
import app.yomu.feature.catalog.TrackerSectionState
import app.yomu.feature.catalog.TrackerTitleScreen
import app.yomu.feature.catalog.TrackerTitleViewModel
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C3 screens and rules: the public tracker page, its provider sections, a failing section that can be retried, and the
 * private entry that only appears with an account. A fake [TrackerPageSource] stands in for the services.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class TrackerPageScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun details() = TrackerTitleDetails(
        service = TrackService.ANILIST,
        id = 118586,
        title = "Sousou no Frieren",
        altTitles = listOf("Frieren: Beyond Journey's End"),
        coverUrl = null,
        description = "Эльфийская волшебница Фрирен переживает своих спутников и отправляется в новое путешествие.",
        url = "https://anilist.co/manga/118586",
        format = "MANGA",
        status = "RELEASING",
        year = 2020,
        score = 87f,
        tags = listOf("Adventure", "Drama", "Fantasy", "Magic"),
        sections = setOf(TrackerSection.CHARACTERS, TrackerSection.STAFF, TrackerSection.RECOMMENDATIONS),
    )

    private class FakeSource(
        private val title: TrackerTitleDetails?,
        private val localItemId: Long? = null,
        private val track: TrackEntity? = null,
        private val signedIn: Boolean = true,
        private val staffFails: Boolean = false,
        private val pageTwoFails: Boolean = false,
    ) : TrackerPageSource {
        var staffCalls = 0
        val characterRequests = mutableListOf<Int>()
        val pageTwoGate = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun trackerTitle(service: TrackService, id: Long) = title

        override suspend fun trackerPeople(service: TrackService, id: Long, section: TrackerSection, page: Int): TrackerPage<TrackerPerson> {
            if (section == TrackerSection.STAFF) {
                staffCalls++
                if (staffFails && staffCalls == 1) throw java.io.IOException("offline")
                return TrackerPage(listOf(TrackerPerson(2, "Kanehito Yamada", null, "Story"), TrackerPerson(3, "Tsukasa Abe", null, "Art")))
            }
            characterRequests += page
            if (page == 2 && pageTwoFails && characterRequests.count { it == 2 } == 1) {
                pageTwoGate.await()
                throw java.io.IOException("offline")
            }
            // Paged: 25 names on page 1, two on page 2, so the page can offer "More".
            val start = (page - 1) * 25
            val count = if (page == 1) 25 else 2
            return TrackerPage((0 until count).map { TrackerPerson((start + it + 100).toLong(), "Character ${start + it + 1}", null, "MAIN") }, hasMore = page == 1)
        }

        override suspend fun trackerRecommendations(service: TrackService, id: Long, page: Int): TrackerPage<TrackerRecommendation> =
            TrackerPage(listOf(TrackerRecommendation(9, "Witch Hat Atelier", null, "https://anilist.co/manga/9")))

        override suspend fun trackFor(service: TrackService, id: Long) = track

        override suspend fun localItemId(service: TrackService, id: Long) = localItemId

        override fun signedIn(service: TrackService) = signedIn
    }

    private fun tracked() = TrackEntity(
        itemId = 7, service = TrackService.ANILIST.id, remoteId = 118586,
        title = "Sousou no Frieren", url = "https://anilist.co/manga/118586",
        status = app.yomu.domain.tracking.TrackStatus.READING.code, score = 9f, lastChapterRead = 12f, totalChapters = 140,
    )

    private fun show(source: FakeSource): TrackerTitleViewModel {
        val vm = TrackerTitleViewModel(TrackService.ANILIST, 118586, source)
        compose.setContent {
            YomuTheme(ThemeConfig(mode = ThemeMode.DARK)) {
                TrackerTitleScreen(
                    viewModel = vm,
                    onBack = {},
                    onOpenLocal = {},
                    onOpenTrackerTitle = { _, _ -> },
                    onFindSource = {},
                    onOpenWeb = {},
                )
            }
        }
        return vm
    }

    @OptIn(ExperimentalTestApi::class)
    private fun awaitText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 60_000)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun thePublicPageShowsDetailsActionsAndSections() {
        val vm = show(FakeSource(details()))
        awaitText("Sousou no Frieren")
        awaitText("Персонажи")
        assertTrue(vm.characters.value is TrackerSectionState.Ready)
        assertEquals(25, (vm.characters.value as TrackerSectionState.Ready).items.size)
        assertTrue((vm.characters.value as TrackerSectionState.Ready).hasMore)
        shot("c3_tracker_page")
        // "More" adds the next page instead of replacing what is shown.
        vm.more(TrackerSection.CHARACTERS)
        compose.waitUntil(10_000) { (vm.characters.value as? TrackerSectionState.Ready)?.items?.size == 27 }
    }

    @Test
    fun pageTwoCanBeRetriedWithoutRemovingPageOne() {
        val source = FakeSource(details(), pageTwoFails = true)
        val vm = show(source)
        compose.waitUntil(10_000) { vm.characters.value is TrackerSectionState.Ready }
        compose.runOnIdle {
            vm.more(TrackerSection.CHARACTERS)
            vm.more(TrackerSection.CHARACTERS)
        }
        source.pageTwoGate.complete(Unit)
        compose.waitUntil(10_000) { (vm.characters.value as? TrackerSectionState.Ready)?.moreError != null }
        assertEquals(25, (vm.characters.value as TrackerSectionState.Ready).items.size)
        compose.runOnIdle { vm.more(TrackerSection.CHARACTERS) }
        compose.waitUntil(10_000) { (vm.characters.value as? TrackerSectionState.Ready)?.items?.size == 27 }
        assertEquals(listOf(1, 2, 2), source.characterRequests)
    }

    @Test
    fun thePersonalEntryAppearsWithAnAccount() {
        show(FakeSource(details(), track = tracked()))
        awaitText("Моя запись")
        awaitText("Глав")
        shot("c3_tracker_personal")
    }

    @Test
    fun aFailedSectionCanBeRetried() {
        val source = FakeSource(details(), staffFails = true)
        val vm = show(source)
        awaitText("Sousou no Frieren")
        compose.waitUntil(10_000) { vm.staff.value is TrackerSectionState.Failed }
        // The failure is per section: the rest of the page is still there.
        assertTrue(vm.characters.value is TrackerSectionState.Ready)
        shot("c3_tracker_section_error")
        val retry = compose.onAllNodesWithText("Повторить")[0]
        retry.performClick()
        compose.waitUntil(10_000) { vm.staff.value is TrackerSectionState.Ready }
        assertEquals(2, (vm.staff.value as TrackerSectionState.Ready).items.size)
    }
}
