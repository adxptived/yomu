package app.yomu.feature.reader

import app.yomu.domain.model.TapZoneLayout
import app.yomu.domain.reader.ReaderKeys
import app.yomu.domain.reader.ReaderPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Q01: the router is the only place that decides which action an input fires, and it owns its delivery. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderInputRouterTest {

    @Test
    fun `a tap resolves to exactly one intent using the tap zone and reading direction`() {
        val router = ReaderInputRouter(UnconfinedTestDispatcher(), onIntent = {})
        val left = ReaderPoint(0.1f, 0.5f)

        assertEquals(ReaderIntent.Previous, router.routeTap(left, TapZoneLayout.LEFT_RIGHT, rtl = false, tapToTurn = true))
        assertEquals(ReaderIntent.Next, router.routeTap(left, TapZoneLayout.LEFT_RIGHT, rtl = true, tapToTurn = true))
        assertEquals(ReaderIntent.Menu, router.routeTap(left, TapZoneLayout.LEFT_RIGHT, rtl = false, tapToTurn = false))
        router.dispose()
    }

    @Test
    fun `physical keys map to intents and foreign keys are ignored`() {
        val router = ReaderInputRouter(UnconfinedTestDispatcher(), onIntent = {})
        assertEquals(ReaderIntent.Next, router.routeKey(ReaderKeys.PAGE_DOWN, rtl = false))
        assertEquals(ReaderIntent.Previous, router.routeKey(ReaderKeys.PAGE_DOWN, rtl = true))
        assertNull(router.routeKey(99, rtl = false))
        router.dispose()
    }

    @Test
    fun `a submitted intent is delivered once`() = runTest {
        val delivered = mutableListOf<ReaderIntent>()
        val router = ReaderInputRouter(StandardTestDispatcher(testScheduler), onIntent = { delivered += it })

        router.submit(ReaderIntent.Next)
        advanceUntilIdle()

        assertEquals(listOf(ReaderIntent.Next), delivered)
        router.dispose()
    }

    @Test
    fun `dispose cancels work still queued and is idempotent`() = runTest {
        val delivered = mutableListOf<ReaderIntent>()
        val router = ReaderInputRouter(StandardTestDispatcher(testScheduler), onIntent = { delivered += it })

        // The consumer has not been dispatched yet; these intents are still pending in the channel.
        router.submit(ReaderIntent.Next)
        router.submit(ReaderIntent.Menu)
        router.dispose()
        router.dispose()
        advanceUntilIdle()

        assertFalse(delivered.isNotEmpty(), "pending intents must not fire after dispose")
        assertTrue(router.isDisposed)
        router.submit(ReaderIntent.Previous)
        advanceUntilIdle()
        assertEquals(emptyList<ReaderIntent>(), delivered)
    }

    @Test
    fun `recreating the router after rotation leaks no old consumer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val oldDelivered = mutableListOf<ReaderIntent>()
        val old = ReaderInputRouter(dispatcher, onIntent = { oldDelivered += it })
        old.submit(ReaderIntent.Next)
        old.dispose()

        val newDelivered = mutableListOf<ReaderIntent>()
        val fresh = ReaderInputRouter(dispatcher, onIntent = { newDelivered += it })
        fresh.submit(ReaderIntent.Previous)
        advanceUntilIdle()

        assertEquals(emptyList<ReaderIntent>(), oldDelivered)
        assertEquals(listOf(ReaderIntent.Previous), newDelivered)
        fresh.dispose()
    }
}
