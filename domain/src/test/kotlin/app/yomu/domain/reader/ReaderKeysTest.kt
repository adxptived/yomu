package app.yomu.domain.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderKeysTest {
    @Test
    fun `page-up and left go back in a left-to-right book`() {
        assertEquals(ReaderKeyAction.PREVIOUS, ReaderKeys.action(ReaderKeys.PAGE_UP, rtl = false))
        assertEquals(ReaderKeyAction.PREVIOUS, ReaderKeys.action(ReaderKeys.DPAD_LEFT, rtl = false))
    }

    @Test
    fun `page-down and right go forward in a left-to-right book`() {
        assertEquals(ReaderKeyAction.NEXT, ReaderKeys.action(ReaderKeys.PAGE_DOWN, rtl = false))
        assertEquals(ReaderKeyAction.NEXT, ReaderKeys.action(ReaderKeys.DPAD_RIGHT, rtl = false))
    }

    @Test
    fun `right-to-left reverses the direction`() {
        assertEquals(ReaderKeyAction.NEXT, ReaderKeys.action(ReaderKeys.PAGE_UP, rtl = true))
        assertEquals(ReaderKeyAction.NEXT, ReaderKeys.action(ReaderKeys.DPAD_LEFT, rtl = true))
        assertEquals(ReaderKeyAction.PREVIOUS, ReaderKeys.action(ReaderKeys.PAGE_DOWN, rtl = true))
        assertEquals(ReaderKeyAction.PREVIOUS, ReaderKeys.action(ReaderKeys.DPAD_RIGHT, rtl = true))
    }

    @Test
    fun `media and other keys are left alone`() {
        // 85 = MEDIA_PLAY_PAUSE, 87 = MEDIA_NEXT, 88 = MEDIA_PREVIOUS, 4 = BACK.
        listOf(85, 87, 88, 4, 0).forEach { assertEquals(ReaderKeyAction.IGNORE, ReaderKeys.action(it, rtl = false), "key $it") }
    }
}
