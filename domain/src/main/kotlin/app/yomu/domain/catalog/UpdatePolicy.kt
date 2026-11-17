package app.yomu.domain.catalog

/** What the user allows the automatic check to look at. A title outside every restriction is still checked. */
enum class UpdateRestriction {
    /** Titles the reader started (reading, on hold, dropped with progress). */
    STARTED,

    /** Titles with unread chapters. */
    UNREAD,

    /** Finished titles, for extra chapters or side stories. */
    COMPLETED,
}

/** Why a title was not asked this time. */
enum class UpdateSkip {
    /** The configured interval has not passed since the last successful check. */
    INTERVAL,

    /** The source of the title is gone or disabled. */
    NO_SOURCE,

    /** One of the title's shelves is excluded from updates. */
    EXCLUDED_SHELF,

    /** The user excluded this title itself from automatic updates. */
    EXCLUDED_TITLE,

    /** The title is outside the chosen restrictions. */
    RESTRICTION,
}

/** What the policy needs to know about one library title. */
data class UpdateTitleState(
    /** ReadingStatus name (NEW, READING, FINISHED, ON_HOLD, DROPPED, PLAN_TO_READ). */
    val status: String,
    val unreadChapters: Int,
    val chapterCount: Int,
    val shelfIds: Set<Long>,
    /** Last check that was attempted, whether it failed or not; 0 = never. */
    val lastAttemptAt: Long = 0,
    /** Last check that actually reached the source; 0 = never. */
    val lastSuccessAt: Long = 0,
    /** The title's own interval, set by the user; null = the app-wide interval. */
    val intervalOverrideHours: Int? = null,
    /** The user asked for this title to be left out of every automatic check. */
    val excludedFromUpdates: Boolean = false,
)

/** The answer of [UpdatePolicy]: ask now, or wait until [nextCheckAt] for [reason]. */
data class UpdateDecision(
    val eligible: Boolean,
    val reason: UpdateSkip? = null,
    val nextCheckAt: Long? = null,
)

/**
 * When an automatic update check should ask a source about a title.
 *
 * The decision is pure: a clock, the settings and the title's own history go in, an answer comes out. Two facts are kept
 * apart on purpose — the last *attempt* (which may have failed) and the last *success*: a failing source is retried on
 * the next pass, a healthy one waits for its interval.
 *
 * A title's own upload rhythm narrows the interval: if it publishes every two weeks, asking every six hours changes
 * nothing. The estimate never goes below the configured interval and never above [MAX_PREDICTED_HOURS], and with too
 * few valid dates the configured interval is used unchanged — a guess is not worth a request.
 */
object UpdatePolicy {
    const val MIN_PREDICTED_HOURS = 1L
    const val MAX_PREDICTED_HOURS = 24L * 30

    fun decide(
        title: UpdateTitleState,
        now: Long,
        intervalHours: Int,
        restrictions: Set<UpdateRestriction> = emptySet(),
        excludedShelves: Set<Long> = emptySet(),
        uploadDates: List<Long> = emptyList(),
        sourceAvailable: Boolean = true,
        /** "Check now" ignores the interval, the prediction and the restrictions — not a broken source. */
        force: Boolean = false,
    ): UpdateDecision {
        if (!sourceAvailable) return UpdateDecision(false, UpdateSkip.NO_SOURCE)
        // An explicit exclusion is the user saying "not this one": it holds even for a manual check of everything.
        if (title.excludedFromUpdates) return UpdateDecision(false, UpdateSkip.EXCLUDED_TITLE)
        if (title.shelfIds.any { it in excludedShelves }) return UpdateDecision(false, UpdateSkip.EXCLUDED_SHELF)
        if (!force && restrictions.isNotEmpty() && !allowedBy(title, restrictions)) {
            return UpdateDecision(false, UpdateSkip.RESTRICTION)
        }
        if (force) return UpdateDecision(true)
        val intervalMs = effectiveIntervalMs(intervalHours, uploadDates, now, title.intervalOverrideHours)
        val base = maxOf(title.lastSuccessAt, title.lastAttemptAt)
        if (base <= 0) return UpdateDecision(true)
        val due = base + intervalMs
        return if (now >= due) UpdateDecision(true) else UpdateDecision(false, UpdateSkip.INTERVAL, due)
    }

    /**
     * The interval actually used: the configured one, stretched to the title's upload rhythm when that rhythm is known
     * and stable. A title's own override is used as written — an explicit choice is not silently stretched by a guess.
     * Bounded so a dormant title is still checked monthly and a daily one is not checked more often than the user asked.
     */
    fun effectiveIntervalMs(
        intervalHours: Int,
        uploadDates: List<Long>,
        now: Long = System.currentTimeMillis(),
        overrideHours: Int? = null,
    ): Long {
        if (overrideHours != null) return overrideHours.coerceAtLeast(1).toLong() * 3_600_000L
        val configured = intervalHours.coerceAtLeast(0).toLong() * 3_600_000L
        val estimate = estimateIntervalMs(uploadDates, now) ?: return configured
        val predicted = estimate.coerceIn(MIN_PREDICTED_HOURS * 3_600_000L, MAX_PREDICTED_HOURS * 3_600_000L)
        return maxOf(configured, predicted)
    }

    /**
     * The typical distance between the newest upload dates, or null when there are not enough usable ones.
     *
     * Only dates in the past and at most a year old are used, sorted newest first; the median of the gaps between the
     * newest few is the estimate, so one delayed chapter does not stretch the whole rhythm.
     */
    fun estimateIntervalMs(uploadDates: List<Long>, now: Long = System.currentTimeMillis()): Long? {
        val year = 365L * 86_400_000L
        val sorted = uploadDates.filter { it in (now - year)..now }.sortedDescending().take(8)
        if (sorted.size < 3) return null
        val gaps = sorted.zipWithNext { a, b -> a - b }.filter { it > 0 }
        if (gaps.size < 2) return null
        return gaps.sorted()[gaps.size / 2]
    }

    private fun allowedBy(title: UpdateTitleState, restrictions: Set<UpdateRestriction>): Boolean =
        (UpdateRestriction.UNREAD in restrictions && title.unreadChapters > 0) ||
            (UpdateRestriction.STARTED in restrictions && title.status in setOf("READING", "ON_HOLD", "DROPPED")) ||
            (UpdateRestriction.COMPLETED in restrictions && title.status == "FINISHED")
}
