package app.yomu.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderBarActionsTest {
    @Test
    fun normalizeKeepsTheStoredOrderAndAppendsMissingCommands() {
        val stored = listOf(ReaderBarActionConfig(ReaderBarAction.DISPLAY), ReaderBarActionConfig(ReaderBarAction.SEARCH))

        val normalized = ReaderBarActions.normalize(stored)

        assertEquals(ReaderBarAction.entries.size, normalized.size)
        assertEquals(listOf(ReaderBarAction.DISPLAY, ReaderBarAction.SEARCH), normalized.take(2).map { it.action })
        assertEquals(ReaderBarAction.entries.toSet(), normalized.map { it.action }.toSet())
    }

    @Test
    fun normalizeDropsDuplicates() {
        val stored = listOf(
            ReaderBarActionConfig(ReaderBarAction.SEARCH),
            ReaderBarActionConfig(ReaderBarAction.SEARCH, enabled = false),
        )

        val normalized = ReaderBarActions.normalize(stored)

        assertEquals(1, normalized.count { it.action == ReaderBarAction.SEARCH })
        assertEquals(true, normalized.first { it.action == ReaderBarAction.SEARCH }.enabled)
    }

    @Test
    fun visibleKeepsOrderAndSkipsDisabledCommands() {
        val stored = listOf(
            ReaderBarActionConfig(ReaderBarAction.BOOKMARK, enabled = true),
            ReaderBarActionConfig(ReaderBarAction.CHAPTERS, enabled = true),
            ReaderBarActionConfig(ReaderBarAction.SEARCH, enabled = false),
            ReaderBarActionConfig(ReaderBarAction.DISPLAY, enabled = false),
            ReaderBarActionConfig(ReaderBarAction.AUTOSCROLL, enabled = false),
        )

        assertEquals(listOf(ReaderBarAction.BOOKMARK, ReaderBarAction.CHAPTERS), ReaderBarActions.visible(stored))
    }

    @Test
    fun anEmptyBarFallsBackToChapters() {
        val stored = ReaderBarAction.entries.map { ReaderBarActionConfig(it, enabled = false) }

        assertEquals(listOf(ReaderBarAction.CHAPTERS), ReaderBarActions.visible(stored))
    }

    @Test
    fun anUnsetBarShowsTheDefaults() {
        assertEquals(
            listOf(ReaderBarAction.CHAPTERS, ReaderBarAction.SEARCH, ReaderBarAction.DISPLAY),
            ReaderBarActions.visible(emptyList()),
        )
    }
}
