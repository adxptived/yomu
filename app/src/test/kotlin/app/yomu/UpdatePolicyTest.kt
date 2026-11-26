package app.yomu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yomu.core.datastore.AppSettings
import app.yomu.feature.settings.ApkAvailability
import app.yomu.feature.settings.AppUpdates
import app.yomu.feature.settings.ReleaseInfo
import app.yomu.feature.settings.UpdateCheckOutcome
import app.yomu.feature.settings.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The rules of the update check (A1): one request at a time, a fresh answer is reused, a failure keeps the last
 * trustworthy release and waits before the next automatic try, while a manual tap may always retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdatePolicyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings: AppSettings get() = GlobalContext.get().get()

    /** Update objects of this class: their scopes stop before Koin goes away, so no late write hits the next test. */
    private val created = mutableListOf<AppUpdates>()

    private fun track(updates: AppUpdates): AppUpdates = updates.also { created += it }

    @After
    fun tearDown() {
        created.forEach { it.shutdown() }
        created.clear()
        stopKoin()
    }

    private fun release(version: String = "9.9.0") = ReleaseInfo(
        version = version,
        pageUrl = "https://example.com/r",
        apkUrl = "https://example.com/yomu-v$version-arm64-v8a.apk",
    )

    private suspend fun clean() {
        settings.updateAutoCheck.set(false)
        settings.updateLatest.set("")
        settings.updateCheckedAt.set(0)
        settings.updateLastSuccessAt.set(0)
        settings.updateNextAttemptAt.set(0)
        settings.updateDismissedVersion.set("")
    }

    @Test
    fun aStartUpCheckAndATapShareOneRequest() = runBlocking {
        clean()
        val calls = AtomicInteger()
        val clock = System.currentTimeMillis()
        val updates = track(AppUpdates(
            context, settings,
            fetch = { calls.incrementAndGet(); delay(250); release() },
            now = { clock },
        ))
        settings.updateAutoCheck.set(true)
        updates.checkIfDue()
        updates.check()
        withTimeout(60_000) { updates.state.first { it is UpdateState.Available } }
        delay(300)
        assertEquals(1, calls.get())
        clean()
    }

    @Test
    fun aManualCheckReloadsDetailsAfterRestartingWithOnlyATag() = runBlocking {
        clean()
        val clock = System.currentTimeMillis()
        settings.updateLatest.set("9.9.0")
        settings.updateLastSuccessAt.set(clock - 10_000)
        settings.updateNextAttemptAt.set(clock + 50_000)
        val calls = AtomicInteger()
        val updates = track(AppUpdates(context, settings, fetch = { calls.incrementAndGet(); release() }, now = { clock }))
        updates.check()
        val available = withTimeout(60_000) { updates.state.first { it is UpdateState.Available } } as UpdateState.Available
        assertEquals(1, calls.get())
        assertEquals(release().apkUrl, available.release.apkUrl)
        clean()
    }

    @Test
    fun aSuccessIsReusedInsideItsInterval() = runBlocking {
        clean()
        val calls = AtomicInteger()
        val clock = System.currentTimeMillis()
        val updates = track(AppUpdates(context, settings, fetch = { calls.incrementAndGet(); release() }, now = { clock }))
        updates.check()
        withTimeout(60_000) { updates.state.first { it is UpdateState.Available } }
        updates.check()
        withTimeout(60_000) { updates.state.first { it is UpdateState.Available } }
        assertEquals(1, calls.get())
        clean()
    }

    @Test
    fun aFailureKeepsTheLastReleaseAndWaitsBeforeTheAutomaticRetry() = runBlocking {
        clean()
        val calls = AtomicInteger()
        var clock = System.currentTimeMillis()
        var broken = false
        val updates = track(AppUpdates(
            context, settings,
            fetch = {
                calls.incrementAndGet()
                if (broken) throw IOException("offline") else release()
            },
            now = { clock },
        ))
        updates.check()
        withTimeout(60_000) { updates.state.first { it is UpdateState.Available } }

        // Later the network is down: the manual check reports the failure but keeps the release found before.
        broken = true
        clock += AppUpdates.CHECK_EVERY_MS
        updates.check()
        val failed = withTimeout(60_000) { updates.state.first { it is UpdateState.Failed } } as UpdateState.Failed
        assertEquals("9.9.0", failed.release?.version)
        assertTrue(failed.lastKnownAt > 0)

        // The short backoff holds off another automatic attempt, so a flapping network does not hammer GitHub.
        val afterFailure = calls.get()
        clock += 1_000
        settings.updateCheckedAt.set(0)
        settings.updateAutoCheck.set(true)
        updates.checkIfDue()
        delay(400)
        assertEquals(afterFailure, calls.get())

        // A tap on "Check for updates" is allowed to try again at once, even inside the backoff.
        updates.check()
        withTimeout(60_000) { updates.state.first { it is UpdateState.Failed } }
        delay(200)
        assertEquals(afterFailure + 1, calls.get())
        clean()
    }

    @Test
    fun theBannerIsHiddenForOneVersionOnly() = runBlocking {
        clean()
        val updates = track(AppUpdates(context, settings, fetch = { null }, now = { System.currentTimeMillis() }))
        settings.updateLatest.set("9.9.0")
        assertEquals("9.9.0", withTimeout(60_000) { updates.available.first { it != null } })
        assertNull(withTimeout(60_000) { updates.dismissedVersion.first() })

        updates.dismissBanner("9.9.0")
        assertEquals("9.9.0", withTimeout(60_000) { updates.dismissedVersion.first { it == "9.9.0" } })
        // The dot on Settings stays: the dismissal is about the catalog banner, not about knowing the version.
        assertEquals("9.9.0", withTimeout(60_000) { updates.available.first { it != null } })

        settings.updateLatest.set("9.10.0")
        assertEquals("9.10.0", withTimeout(60_000) { updates.available.first { it == "9.10.0" } })
        assertEquals("9.9.0", withTimeout(60_000) { updates.dismissedVersion.first { it == "9.9.0" } })
        clean()
    }

    @Test
    fun aReleaseWithOnlyATagIsAnnouncedWithoutDownloading() = runBlocking {
        clean()
        val updates = track(AppUpdates(
            context, settings,
            fetchCheck = {
                UpdateCheckOutcome(ReleaseInfo("9.9.0", "https://example.com/r", apkUrl = null, metadataUnavailable = true))
            },
            now = { System.currentTimeMillis() },
        ))
        updates.check()
        val available = withTimeout(60_000) { updates.state.first { it is UpdateState.Available } } as UpdateState.Available
        assertNull(available.release.apkUrl)
        assertEquals(ApkAvailability.METADATA_UNAVAILABLE, available.release.apkAvailability)
        clean()
    }
}
