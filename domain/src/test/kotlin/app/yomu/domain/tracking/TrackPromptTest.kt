package app.yomu.domain.tracking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** C2: the "link a tracker" hint appears once, waits after "Later" and stays away after "Never". */
class TrackPromptTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun nothingIsHiddenByDefault() {
        assertFalse(TrackPrompt.hidden(emptySet(), 42, now))
        assertFalse(TrackPrompt.hidden(setOf("41:forever"), 42, now))
    }

    @Test
    fun laterHidesForAWeekAndThenComesBack() {
        val records = TrackPrompt.snooze(emptySet(), 42, now)
        assertTrue(TrackPrompt.hidden(records, 42, now))
        assertTrue(TrackPrompt.hidden(records, 42, now + 6 * day))
        assertFalse(TrackPrompt.hidden(records, 42, now + 8 * day))
    }

    @Test
    fun neverKeepsTheHintAwayAndCanBeReplaced() {
        val records = TrackPrompt.hideForever(TrackPrompt.snooze(emptySet(), 42, now), 42)
        assertEquals(1, records.size)
        assertTrue(TrackPrompt.hidden(records, 42, now + 3650 * day))
    }

    @Test
    fun expiredSnoozesArePruned() {
        val records = TrackPrompt.snooze(TrackPrompt.hideForever(emptySet(), 1), 2, now)
        val kept = TrackPrompt.prune(records, now + 8 * day)
        assertEquals(setOf("1:forever"), kept)
    }

    @Test
    fun aBrokenRecordIsIgnored() {
        assertFalse(TrackPrompt.hidden(setOf("nonsense", ":later", "42:"), 42, now))
    }
}
