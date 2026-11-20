package app.yomu.feature.reader

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The Wi-Fi-only preload gate is a pure predicate: only Wi-Fi only + metered blocks the ahead-of-reading preload. */
class PreloadGateTest {

    @Test
    fun `preload runs on any network when the setting is off`() {
        assertTrue(preloadAllowed(wifiOnly = false, metered = false))
        assertTrue(preloadAllowed(wifiOnly = false, metered = true))
    }

    @Test
    fun `preload runs on unmetered networks when the setting is on`() {
        assertTrue(preloadAllowed(wifiOnly = true, metered = false))
    }

    @Test
    fun `preload is blocked only when wifi only and metered`() {
        assertFalse(preloadAllowed(wifiOnly = true, metered = true))
    }
}
