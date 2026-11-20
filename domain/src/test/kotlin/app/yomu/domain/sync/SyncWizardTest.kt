package app.yomu.domain.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** B03: the wizard cannot skip a step, and a failure names its recovery. */
class SyncWizardTest {

    @Test
    fun `a fresh wizard starts at the backend and cannot skip it`() {
        var state = SyncWizard.start(configured = false)
        assertEquals(SyncStep.BACKEND, state.step)
        assertEquals(SyncStep.BACKEND, SyncWizard.advance(state).step)
        state = SyncWizard.withBackend(state, "WEBDAV")
        assertEquals(SyncStep.INPUT, state.step)
        assertEquals(SyncStep.INPUT, SyncWizard.advance(state).step)
        state = SyncWizard.withInput(state, true)
        assertEquals(SyncStep.PROBE, SyncWizard.advance(state).step)
    }

    @Test
    fun `categories are offered only after a passing probe`() {
        var state = SyncWizard.start(configured = true)
        state = SyncWizard.withBackend(state, "FOLDER")
        state = SyncWizard.withInput(state, true)
        state = SyncWizard.advance(state)
        assertEquals(SyncStep.PROBE, SyncWizard.advance(SyncWizard.withProbe(state, false)).step)
        state = SyncWizard.advance(SyncWizard.withProbe(state, true))
        assertEquals(SyncStep.CATEGORIES, state.step)
    }

    @Test
    fun `the last category cannot be turned off and a preview must be confirmed`() {
        var state = SyncWizard.start(configured = true).copy(step = SyncStep.CATEGORIES, categories = setOf(SyncCategory.WORDS))
        state = SyncWizard.toggleCategory(state, SyncCategory.WORDS)
        assertEquals(setOf(SyncCategory.WORDS), state.categories)
        state = SyncWizard.advance(state)
        assertEquals(SyncStep.SCHEDULE, state.step)
        state = SyncWizard.advance(state)
        assertEquals(SyncStep.PREVIEW, state.step)
        assertEquals(SyncStep.PREVIEW, SyncWizard.advance(state).step)
        state = SyncWizard.advance(SyncWizard.confirmPreview(state))
        assertEquals(SyncStep.READY, state.step)
    }

    @Test
    fun `back walks the steps without losing the settings`() {
        val ready = SyncWizardState(
            step = SyncStep.READY,
            backend = "WEBDAV",
            inputReady = true,
            probePassed = true,
            categories = setOf(SyncCategory.PROGRESS),
            previewConfirmed = true,
        )
        val back = SyncWizard.back(ready)
        assertEquals(SyncStep.PREVIEW, back.step)
        assertFalse(back.previewConfirmed)
        assertEquals("WEBDAV", back.backend)
        assertEquals(setOf(SyncCategory.PROGRESS), back.categories)
    }

    @Test
    fun `a failure says whether a retry can help`() {
        assertFalse(SyncWizard.recovery(SyncFailure.AUTH).retryNow)
        assertTrue(SyncWizard.recovery(SyncFailure.NETWORK).retryNow)
        assertFalse(SyncWizard.recovery(SyncFailure.STORAGE).retryNow)
        assertFalse(SyncWizard.recovery(SyncFailure.BATTERY).retryNow)
        assertTrue(SyncWizard.recovery(SyncFailure.OTHER).retryNow)
    }

    @Test
    fun `a summary distinguishes health from a partial result`() {
        assertTrue(SyncStatusSummary(lastSuccessAt = 1).healthy)
        assertFalse(SyncStatusSummary(lastSuccessAt = 1, conflicts = 2).healthy)
        assertTrue(SyncStatusSummary(lastSuccessAt = 1, conflicts = 2).partial)
        assertFalse(SyncStatusSummary(error = "boom").healthy)
    }

    @Test
    fun `canAdvance agrees with advance for every step`() {
        val incomplete = SyncWizardState(step = SyncStep.BACKEND)
        assertFalse(SyncWizard.canAdvance(incomplete))
        assertEquals(incomplete, SyncWizard.advance(incomplete))

        val steps = listOf(
            SyncWizardState(step = SyncStep.BACKEND, backend = "FOLDER") to SyncStep.INPUT,
            SyncWizardState(step = SyncStep.INPUT, inputReady = true) to SyncStep.PROBE,
            SyncWizardState(step = SyncStep.PROBE, probePassed = true) to SyncStep.CATEGORIES,
            SyncWizardState(step = SyncStep.CATEGORIES) to SyncStep.SCHEDULE,
            SyncWizardState(step = SyncStep.SCHEDULE) to SyncStep.PREVIEW,
            SyncWizardState(step = SyncStep.PREVIEW, previewConfirmed = true) to SyncStep.READY,
        )
        steps.forEach { (state, next) ->
            assertTrue(SyncWizard.canAdvance(state), "step ${state.step} should be complete")
            assertEquals(next, SyncWizard.advance(state).step)
        }
        assertFalse(SyncWizard.canAdvance(SyncWizardState(step = SyncStep.READY)))
    }

    @Test
    fun `changing credentials after a probe forces a new probe`() {
        var state = SyncWizard.start(configured = false)
        state = SyncWizard.withBackend(state, "WEBDAV")
        state = SyncWizard.withInput(state, true)
        state = SyncWizard.advance(state)
        state = SyncWizard.withProbe(state, true)
        assertEquals(SyncStep.CATEGORIES, SyncWizard.advance(state).step)

        // Back to INPUT: the old "connection works" result must not be reused after editing credentials.
        state = SyncWizard.back(SyncWizard.back(SyncWizardState(
            step = SyncStep.CATEGORIES, backend = "WEBDAV", inputReady = true, probePassed = true,
        )))
        assertEquals(SyncStep.INPUT, state.step)
        assertFalse(state.probePassed)
        assertEquals(SyncStep.PROBE, SyncWizard.advance(state).step)
        assertEquals(SyncStep.PROBE, SyncWizard.advance(state).step) // cannot skip the fresh probe

        state = SyncWizard.withProbe(state, true)
        state = SyncWizard.withInput(state, false) // unticking "details entered" also invalidates it
        assertFalse(state.probePassed)
    }

    @Test
    fun `failure classification picks the action the user can actually take`() {
        assertEquals(SyncFailure.AUTH, classifySyncFailure("HTTP 401 Unauthorized: bad token"))
        assertEquals(SyncFailure.AUTH, classifySyncFailure("Ошибка авторизации: неверный токен"))
        assertEquals(SyncFailure.STORAGE, classifySyncFailure("java.io.IOException: No space left on device"))
        assertEquals(SyncFailure.STORAGE, classifySyncFailure("Нет места на диске"))
        assertEquals(SyncFailure.BATTERY, classifySyncFailure("Battery saver blocks background sync"))
        assertEquals(SyncFailure.NETWORK, classifySyncFailure("java.net.UnknownHostException: cloud.example.com"))
        assertEquals(SyncFailure.NETWORK, classifySyncFailure("Нет соединения с сетью"))
        // 403 is auth even though it is also an HTTP error.
        assertEquals(SyncFailure.AUTH, classifySyncFailure("403 Forbidden"))
        assertEquals(SyncFailure.OTHER, classifySyncFailure("Something unexpected happened"))
        assertEquals(SyncFailure.OTHER, classifySyncFailure(null))
    }
}
