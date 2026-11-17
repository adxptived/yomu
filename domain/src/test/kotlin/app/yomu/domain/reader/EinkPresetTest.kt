package app.yomu.domain.reader

import app.yomu.domain.model.MangaSettings
import app.yomu.domain.model.PageTurnAnimation
import app.yomu.domain.model.TextAlignment
import app.yomu.domain.model.TextReaderSettings
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
}
