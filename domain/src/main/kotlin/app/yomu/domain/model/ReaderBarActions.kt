package app.yomu.domain.model

import kotlinx.serialization.Serializable

/**
 * Commands that can sit in the reader's bottom bar. The order and visibility are the user's; every command has a
 * handler in the reader, so hiding one never removes the function itself (the "More" sheet keeps all of them).
 */
enum class ReaderBarAction {
    /** Chapters (the table of contents). */
    CHAPTERS,

    /** Search inside the book. */
    SEARCH,

    /** The compact display panel (text size, theme, direction). */
    DISPLAY,

    /** Add or remove a bookmark at the current position. */
    BOOKMARK,

    /** Start or stop automatic scrolling. */
    AUTOSCROLL,
}

@Serializable
data class ReaderBarActionConfig(val action: ReaderBarAction, val enabled: Boolean = true)

object ReaderBarActions {
    /**
     * The three commands the bar always had are on; bookmark and auto-scroll are opt-in so the default bar stays as
     * short as before.
     */
    val defaults: List<ReaderBarActionConfig> = listOf(
        ReaderBarActionConfig(ReaderBarAction.CHAPTERS),
        ReaderBarActionConfig(ReaderBarAction.SEARCH),
        ReaderBarActionConfig(ReaderBarAction.DISPLAY),
        ReaderBarActionConfig(ReaderBarAction.BOOKMARK, enabled = false),
        ReaderBarActionConfig(ReaderBarAction.AUTOSCROLL, enabled = false),
    )

    /** Stored list with commands added in later versions appended and unknown ones dropped. */
    fun normalize(stored: List<ReaderBarActionConfig>): List<ReaderBarActionConfig> {
        val known = stored.distinctBy { it.action }
        val defaultEnabled = defaults.associate { it.action to it.enabled }
        return known + ReaderBarAction.entries.filter { action -> known.none { it.action == action } }
            .map { ReaderBarActionConfig(it, enabled = defaultEnabled[it] ?: true) }
    }

    /** Enabled commands in the stored order; an empty result falls back to chapters so the bar is never empty. */
    fun visible(stored: List<ReaderBarActionConfig>): List<ReaderBarAction> {
        val enabled = normalize(stored).filter { it.enabled }.map { it.action }
        return enabled.ifEmpty { listOf(ReaderBarAction.CHAPTERS) }
    }
}
