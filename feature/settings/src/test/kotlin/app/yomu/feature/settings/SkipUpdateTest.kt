package app.yomu.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pure decision behind the update dot: skipped versions stay quiet, newer ones bring it back. */
class SkipUpdateTest {
    private val current = "1.7.0"

    @Test fun `nothing is skipped by default`() {
        assertFalse(SkipUpdate.isSkipped("1.7.1", "", current))
        assertFalse(SkipUpdate.isSkipped("1.7.1", "   ", current))
    }

    @Test fun `the skipped release is quiet`() {
        assertTrue(SkipUpdate.isSkipped("1.7.1", "1.7.1", current))
        assertTrue(SkipUpdate.isSkipped("1.7.1", "v1.7.1", current))
    }

    @Test fun `a newer release brings the dot back`() {
        assertFalse(SkipUpdate.isSkipped("1.8.0", "1.7.1", current))
        assertFalse(SkipUpdate.isSkipped("1.7.2", "v1.7.1", current))
    }

    @Test fun `a version that is not an update is never skipped`() {
        assertFalse(SkipUpdate.isSkipped("1.7.0", "1.7.1", current))
        assertFalse(SkipUpdate.isSkipped("1.6.9", "1.7.1", current))
    }

    @Test fun `build suffixes are ignored when comparing tags`() {
        assertEqualsTag("v1.7.1", "1.7.1")
        assertTrue(SkipUpdate.isSkipped("1.7.1-nightly.2", "v1.7.1", current))
    }

    private fun assertEqualsTag(raw: String, bare: String) = assertEquals(bare, SkipUpdate.normalize(raw))
}
