package app.yomu.domain.reader

import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.PageTurnAnimation
import app.yomu.domain.model.TextAlignment
import app.yomu.domain.model.TextReaderSettings
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EinkPresetTest {

    @Test
    fun thePresetIsAReadableNormalChange() {
        val before = TextReaderSettings(paged = false, rtl = true)
        val after = EinkPreset.text(before)
        assertEquals(PageTurnAnimation.NONE, after.pageAnimation)
        assertEquals(TextAlignment.START, after.align)
        assertTrue(after.fontSizePercent >= 125)
        assertTrue(after.lineHeight >= 1.65f)
        // Page mode and writing direction are the reader's, not the preset's.
        assertFalse(after.paged)
        assertTrue(after.rtl)
    }

    @Test
    fun applyingItTwiceDoesNotShrinkAnAlreadyLargerChoice() {
        val larger = TextReaderSettings(fontSizePercent = 150, lineHeight = 1.9f)
        val once = EinkPreset.text(larger)
        assertEquals(150, once.fontSizePercent)
        assertEquals(1.9f, once.lineHeight)
        assertEquals(once, EinkPreset.text(once))
    }

    @Test
    fun mangaLosesTheAnimationAndGainsASmallContrast() {
        val before = MangaSettings(pageAnimation = true, contrast = 1f)
        val after = EinkPreset.manga(before)
        assertFalse(after.pageAnimation)
        assertTrue(after.contrast >= 1.15f)
        assertEquals(before.scale, after.scale)
    }

    @Test
    fun theFlashRunsOnlyOnTheChosenCadence() {
        assertTrue(EinkPreset.flashDue(5, 5))
        assertTrue(EinkPreset.flashDue(10, 5))
        assertFalse(EinkPreset.flashDue(4, 5))
        assertFalse(EinkPreset.flashDue(0, 5))
        assertFalse(EinkPreset.flashDue(5, 0))
    }

    @Test
    fun `globally the preset prepares both engines, per book only the open one`() {
        assertEquals(EinkPreset.Targets(text = true, manga = true), EinkPreset.targets(perBook = false, textBook = true, mangaBook = false))
        assertEquals(EinkPreset.Targets(text = true, manga = false), EinkPreset.targets(perBook = true, textBook = true, mangaBook = false))
        assertEquals(EinkPreset.Targets(text = false, manga = true), EinkPreset.targets(perBook = true, textBook = false, mangaBook = true))
        // A PDF has no group of its own in the preset: a per-book PDF leaves no override behind.
        assertEquals(EinkPreset.Targets(text = false, manga = false), EinkPreset.targets(perBook = true, textBook = false, mangaBook = false))
    }

    @Test
    fun `a flash cancelled by a quick page turn still clears the layer`() = runTest {
        var on = false
        val job = launch { EinkPreset.flash { on = it } }
        advanceTimeBy(1)
        assertTrue(on, "the layer is shown while the flash runs")
        job.cancelAndJoin()
        assertFalse(on, "the cancelled flash must remove the layer")
    }
}
