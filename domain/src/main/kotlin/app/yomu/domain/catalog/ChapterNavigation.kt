package app.yomu.domain.catalog

import app.yomu.domain.model.ChapterInfo

/** What the user asked to open next. */
enum class ChapterIntent {
    /** The next chapter to read: other releases of the current one are skipped. */
    NEXT,

    /** The previous chapter, same rules. */
    PREVIOUS,

    /** Where reading left off: the first unread chapter after the last read one. */
    RESUME,

    /** Chapters to keep downloaded ahead of [afterId]. */
    DOWNLOAD_AHEAD,
}

/**
 * What navigation may skip.
 *
 * The reader, the chapter list, "continue reading", the downloader and the notification actions used to apply their
 * own filters, so a chapter hidden by the chosen scanlation group could still be the one a notification opened. The
 * policy makes the answer one function.
 */
data class ChapterNavigationPolicy(
    /** Skip chapters already read.[RESUME] always skips them: that is what resuming means. */
    val skipRead: Boolean = false,
    /** Skip releases of excluded scanlation groups (the title's chapter filters). */
    val excludedScanlators: Set<String> = emptySet(),
    /** Skip other releases of the same chapter: one chapter, one target. */
    val skipDuplicateReleases: Boolean = true,
) {
    /** Chapters the navigation may choose from, in the order given (already reading order). */
    fun candidates(chapters: List<ChapterInfo>): List<ChapterInfo> =
        if (excludedScanlators.isEmpty()) chapters
        else chapters.filter { it.scanlator.isNullOrBlank() || it.scanlator !in excludedScanlators }
}

object ChapterNavigation {
    /**
     * The chapter an intent points at, or null when there is none. [currentId] is ignored by [ChapterIntent.RESUME].
     *
     * A chapter whose releases are not reliably numbered is never skipped as a "duplicate" of another: the same
     * number from a restarted volume is a different chapter.
     */
    fun resolve(
        readingOrder: List<ChapterInfo>,
        currentId: Long?,
        intent: ChapterIntent,
        policy: ChapterNavigationPolicy = ChapterNavigationPolicy(),
    ): ChapterInfo? {
        val candidates = policy.candidates(readingOrder)
        if (candidates.isEmpty()) return null
        val reliable = ChapterOrder.numbersReliable(candidates)
        return when (intent) {
            // Resuming is defined by skipping what was read, whatever the policy says.
            ChapterIntent.RESUME -> ChapterOrder.continueWith(candidates)
            ChapterIntent.NEXT, ChapterIntent.PREVIOUS -> {
                val id = currentId ?: return null
                val target = if (policy.skipDuplicateReleases && reliable) {
                    ChapterOrder.adjacent(candidates, id, forward = intent == ChapterIntent.NEXT)
                } else {
                    val index = candidates.indexOfFirst { it.id == id }
                    if (index < 0) null else candidates.getOrNull(if (intent == ChapterIntent.NEXT) index + 1 else index - 1)
                }
                // "Skip read" is about the destination: a read chapter in between is still shown when it is the only
                // next one (a reader who jumps back wants to walk forward through what they read).
                target
            }
            ChapterIntent.DOWNLOAD_AHEAD -> null
        }
    }

    /**
     * Other releases of the chapter [chapterId] that mean the same chapter: same number from another group. Empty when
     * the numbering is not reliable — then an equal number is a different chapter (a restarted volume), not a duplicate.
     */
    fun alternativeReleases(readingOrder: List<ChapterInfo>, chapterId: Long): List<Long> {
        val current = readingOrder.firstOrNull { it.id == chapterId } ?: return emptyList()
        if (current.number < 0f) return emptyList()
        if (!ChapterOrder.numbersReliable(readingOrder)) return emptyList()
        return readingOrder
            .filter { it.id != chapterId && ChapterOrder.sameChapter(it, current) && !it.read }
            .map { it.id }
    }

    /**
     * Chapter numbers missing between the first and the last one, when the numbers can be trusted: a gap of at least
     * one whole chapter. Decimal numbers ("12.5") and unnumbered chapters are not counted as gaps. Null when the
     * numbering is not reliable — a guessed gap would be a lie.
     */
    fun missingNumbers(readingOrder: List<ChapterInfo>): List<Float>? {
        val numbered = readingOrder.filter { it.number >= 0f }
        if (numbered.size < 2 || !ChapterOrder.numbersReliable(readingOrder)) return null
        val present = numbered.mapTo(HashSet()) { it.number }
        val first = numbered.minOf { it.number }
        val last = numbered.maxOf { it.number }
        val missing = mutableListOf<Float>()
        var n = first
        while (n <= last) {
            if (n !in present) missing += n
            n += 1f
            if (missing.size >= 200) break
        }
        return missing
    }
}
