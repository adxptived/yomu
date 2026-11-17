package app.yomu.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentPreferenceTest {
    @Test
    fun `safe hides adult content, the other two list it`() {
        assertFalse(ContentPreference.SAFE.allows(adult = true))
        assertTrue(ContentPreference.MIXED.allows(adult = true))
        assertTrue(ContentPreference.NSFW.allows(adult = true))
    }

    @Test
    fun `every preference allows ordinary content`() {
        ContentPreference.entries.forEach { assertTrue(it.allows(adult = false), "$it hid a regular source") }
    }

    @Test
    fun `the old boolean keeps its meaning`() {
        // The old switch was off by default, so an untouched installation becomes the safe preference.
        assertEquals(ContentPreference.SAFE, ContentPreference.fromLegacyShowNsfw(false))
        assertEquals(ContentPreference.MIXED, ContentPreference.fromLegacyShowNsfw(true))
    }
}
