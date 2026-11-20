package app.yomu.feature.settings

import app.yomu.domain.sync.SyncStep
import app.yomu.domain.sync.SyncWizard
import app.yomu.domain.sync.SyncWizardState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The wizard INPUT fields call wizardInputReady(false) after every confirmed credential edit (and after the folder
 * picker writes a new folder). These pin the state-machine effect that wiring relies on: an edit clears a previously
 * passing probe and a confirmed preview, and re-ticking "Details are entered" is the way back before probing again.
 */
class SyncWizardInputResetTest {
    private fun probed() = SyncWizardState(
        step = SyncStep.INPUT,
        backend = "WEBDAV",
        inputReady = true,
        probePassed = true,
        previewConfirmed = true,
    )

    @Test
    fun `a credential edit clears the passing probe and confirmed preview`() {
        val edited = SyncWizard.withInput(probed(), false)
        assertFalse(edited.inputReady)
        assertFalse(edited.probePassed)
        assertFalse(edited.previewConfirmed)
    }

    @Test
    fun `the details checkbox re-confirms input but never resurrects a stale probe`() {
        val edited = SyncWizard.withInput(probed(), false)
        val rechecked = SyncWizard.withInput(edited, true)
        assertTrue(rechecked.inputReady)
        assertFalse(rechecked.probePassed)
        assertTrue(SyncWizard.canAdvance(rechecked)) // only INPUT is complete now
        val atProbe = SyncWizard.advance(rechecked)
        assertFalse(SyncWizard.canAdvance(atProbe)) // PROBE has to pass again
    }

    @Test
    fun `editing while already unconfirmed stays unconfirmed`() {
        val edited = SyncWizard.withInput(probed(), false)
        val again = SyncWizard.withInput(edited, false)
        assertFalse(again.probePassed)
        assertFalse(again.previewConfirmed)
    }
}
