package app.yomu.domain.tracking

import kotlin.math.floor

/**
 * One side of a linked title: Yomu's own read chapters or one service's entry (D2). [chapters] is the first chapter
 * that has NOT been finished: 12 means chapters 1–12 are read.
 */
data class ProgressParticipant(
    val key: String,
    val chapters: Float,
    /** The change has not reached the service yet: never send it twice, but count it in the maximum. */
    val pending: Boolean = false,
    /** Services that keep whole chapters (MAL, AniList) round a fractional maximum down. */
    val wholeChapters: Boolean = true,
)

/** Why a participant is shown but not written. */
enum class ProgressNote {
    /** A change is already waiting to be sent; the next sync checks it. */
    PENDING,
    /** Fractional chapters of the source cannot be mapped to the service's whole numbers. */
    FRACTIONAL,
    /** The title's numbering is unknown (no number on the chapters): nothing is inferred. */
    UNKNOWN_NUMBERING,
    /** The source does not have the furthest chapter yet: the remote value is shown, not applied. */
    NOT_IN_SOURCE,
    /** Volumes and chapters disagree between the editions: the reader has to confirm the mapping. */
    VOLUME_CONFLICT,
}

data class ProgressChange(val key: String, val from: Float, val to: Float)

/** The plan: the furthest comparable value, who moves to it, who waits and why, and what Yomu marks read. */
data class ProgressPlan(
    val target: Float,
    val changes: List<ProgressChange>,
    val notes: Map<String, List<ProgressNote>>,
    /** Chapters Yomu should mark read now; null when the local side already matches or must wait. */
    val applyLocal: Float?,
    /** Remote progress shown but not applied locally because the source lacks that chapter. */
    val deferred: Float?,
) {
    val isEmpty: Boolean get() = changes.isEmpty() && applyLocal == null
}

/**
 * "The furthest progress wins" (D2): the maximum of the local and linked service values is applied to the lagging
 * participants, only forwards, only to comparable numbers. A pending change is counted but not re-sent; fractions,
 * unknown numbering, volume conflicts and chapters the source does not have yet become notes for the reader instead
 * of guessed writes.
 */
object ProgressAlign {
    fun plan(
        local: ProgressParticipant,
        remote: List<ProgressParticipant>,
        /** Whether the local source has the chapter with this number (false = only show it for now). */
        localHasChapter: (Float) -> Boolean = { true },
    ): ProgressPlan {
        val notes = LinkedHashMap<String, MutableList<ProgressNote>>()
        fun note(key: String, n: ProgressNote) { notes.getOrPut(key) { mutableListOf() }.add(n) }

        val all = listOf(local) + remote
        val known = all.filter { it.chapters.isFinite() && it.chapters >= 0f }
        if (known.isEmpty() || known.all { it.chapters <= 0f }) {
            return ProgressPlan(0f, emptyList(), emptyMap(), null, null)
        }
        val max = known.maxOf { it.chapters }
        val target = floor(max) // a finished chapter is whole: half a chapter is not "chapter 13 done"
        if (target < 1f) return ProgressPlan(0f, emptyList(), emptyMap(), null, null)

        val changes = mutableListOf<ProgressChange>()
        for (p in known) {
            if (p.pending) note(p.key, ProgressNote.PENDING)
            if (p.chapters >= target) continue
            if (p.pending) continue
            changes += ProgressChange(p.key, p.chapters, target)
        }

        // The local side: mark read only what the source really has; otherwise show the remote value and wait.
        val applyLocal = when {
            local.chapters >= target -> null
            !localHasChapter(target) -> {
                note(local.key, ProgressNote.NOT_IN_SOURCE)
                null
            }
            else -> target
        }
        val deferred = if (local.chapters < target && !localHasChapter(target)) max else null

        if (max % 1f != 0f) {
            note(local.key, ProgressNote.FRACTIONAL)
            remote.filter { it.wholeChapters }.forEach { note(it.key, ProgressNote.FRACTIONAL) }
        }
        return ProgressPlan(
            target = target,
            changes = changes.filterNot { it.key == local.key },
            notes = notes,
            applyLocal = applyLocal,
            deferred = deferred,
        )
    }

    /** The value actually sent to [participant] for [target]: whole chapters where the service keeps them. */
    fun valueFor(participant: ProgressParticipant, target: Float): Float =
        if (participant.wholeChapters) floor(target) else target
}
