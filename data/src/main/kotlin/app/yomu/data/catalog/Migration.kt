package app.yomu.data.catalog

import app.yomu.domain.model.ChapterInfo
import app.yomu.source.SItem
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** What a source migration may carry to the target title. */
enum class MigrationCategory { PROGRESS, BOOKMARKS, TABS, FAVORITE }

/** How sure the matcher is that a chapter is the same one in the other source. */
enum class MigrationConfidence { EXACT, LIKELY, REVIEW, NONE }

/** A chapter of the target as the plan saw it, before anything was written. */
data class MigrationTargetChapter(val url: String, val name: String, val number: Float, val scanlator: String?)

/** One source chapter and its counterpart, with the reason for the confidence. */
data class MigrationChapterMatch(
    val from: ChapterInfo,
    val target: MigrationTargetChapter?,
    val confidence: MigrationConfidence,
    /** True only when it is literally the same release (same URL), so the page number means the same thing. */
    val pagePositionSafe: Boolean,
) {
    val matched: Boolean get() = target != null
}

/**
 * A migration preview. Nothing is written until [CatalogRepository.applyMigration]; the plan can be shown, corrected
 * in the UI or dropped, and the target title may not exist in the library yet.
 */
data class MigrationPlan(
    val planId: String,
    val fromEntryId: Long,
    val fromTitle: String,
    val fromRevision: String,
    val targetSourceId: Long,
    val targetItem: SItem,
    val targetTitle: String,
    val targetRevision: String,
    val matches: List<MigrationChapterMatch>,
    val categories: Set<MigrationCategory>,
    val targetChapterCount: Int? = null,
) {
    val exact: List<MigrationChapterMatch> get() = matches.filter { it.confidence == MigrationConfidence.EXACT }
    val likely: List<MigrationChapterMatch> get() = matches.filter { it.confidence == MigrationConfidence.LIKELY }
    val ambiguous: List<MigrationChapterMatch> get() = matches.filter { it.confidence == MigrationConfidence.REVIEW }
    val missing: List<MigrationChapterMatch> get() = matches.filter { !it.matched }
    val matched: List<MigrationChapterMatch> get() = matches.filter { it.matched }
}

/** What an apply actually changed. [alreadyApplied] means a journal for this exact plan existed. */
data class MigrationResult(
    val targetEntryId: Long,
    val applied: Int,
    val skipped: Int,
    val categories: Set<MigrationCategory>,
    val alreadyApplied: Boolean = false,
)

/** The plan no longer describes the library (the old or the new title changed while the preview was open). */
class StaleMigrationPlanException(message: String) : IllegalStateException(message)

/** Journal entry per target chapter: what both sides had before, and what apply wrote. */
@Serializable
data class JournalChapter(
    val targetId: Long,
    val fromRead: Boolean,
    val fromBookmarked: Boolean,
    val fromPage: Int,
    val targetRead: Boolean,
    val targetBookmarked: Boolean,
    val targetPage: Int,
    val appliedRead: Boolean,
    val appliedBookmarked: Boolean,
    val appliedPage: Int,
)

/** Everything [CatalogRepository.undoMigration] needs; written before the transaction so a crash can be undone. */
@Serializable
data class MigrationJournal(
    val version: Int = 1,
    val planId: String,
    val appliedAt: Long,
    val fromEntryId: Long,
    val targetEntryId: Long,
    val fromFavorite: Boolean,
    val fromCategoryId: Long?,
    val fromTabs: List<Long>,
    val targetFavorite: Boolean,
    val targetCategoryId: Long?,
    val targetTabs: List<Long>,
    val appliedFromFavorite: Boolean,
    val appliedTargetFavorite: Boolean,
    val categories: List<String>,
    val chapters: List<JournalChapter>,
)

/**
 * Chapter matching for a source migration.
 *
 * A valid chapter number and the same scanlation group is the strongest match; a number alone is likely; an unnumbered
 * chapter falls back to its title. Anything that has more than one candidate is [MigrationConfidence.REVIEW], never
 * silently picked as exact. A page position is transferred only when the URL is identical, because a different release
 * may paginate differently.
 */
object MigrationMatcher {
    fun match(from: List<ChapterInfo>, target: List<MigrationTargetChapter>): List<MigrationChapterMatch> = from.map { old ->
        val numbered = old.number >= 0f
        val sameGroup = if (numbered) target.filter { it.number == old.number && norm(it.scanlator) == norm(old.scanlator) } else emptyList()
        val sameNumber = if (numbered) target.filter { it.number == old.number } else emptyList()
        val sameName = target.filter { norm(it.name) == norm(old.name) }
        val (candidate, confidence) = when {
            sameGroup.size == 1 -> sameGroup[0] to MigrationConfidence.EXACT
            sameGroup.size > 1 -> sameGroup[0] to MigrationConfidence.REVIEW
            sameNumber.size == 1 -> sameNumber[0] to MigrationConfidence.LIKELY
            sameNumber.size > 1 -> sameNumber[0] to MigrationConfidence.REVIEW
            sameName.size == 1 -> sameName[0] to MigrationConfidence.LIKELY
            sameName.size > 1 -> sameName[0] to MigrationConfidence.REVIEW
            else -> null to MigrationConfidence.NONE
        }
        MigrationChapterMatch(
            from = old,
            target = candidate,
            confidence = confidence,
            pagePositionSafe = candidate != null && candidate.url == old.url,
        )
    }

    private fun norm(value: String?): String = value.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
}

internal fun fingerprint(vararg parts: Any?): String =
    MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|") { it?.toString().orEmpty() }.toByteArray())
        .joinToString("") { "%02x".format(it) }
