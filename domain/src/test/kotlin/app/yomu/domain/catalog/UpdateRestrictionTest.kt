package app.yomu.domain.catalog

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** E2: the check filters are a table of explicit conditions; an unknown source status is never "ended". */
class UpdateRestrictionTest {
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun title(
        status: String = "READING",
        unread: Int = 0,
        publication: PublicationStatus = PublicationStatus.UNKNOWN,
    ) = UpdateTitleState(status = status, unreadChapters = unread, chapterCount = 10, shelfIds = emptySet(), publication = publication)

    @Test
    fun anEmptySelectionChecksEverything() {
        assertTrue(UpdatePolicy.decide(title(), now, 6, emptySet()).eligible)
    }

    @Test
    fun unreadMatchesOnlyTitlesWithUnreadChapters() {
        assertTrue(UpdatePolicy.decide(title(unread = 2), now, 6, setOf(UpdateRestriction.UNREAD)).eligible)
        assertFalse(UpdatePolicy.decide(title(unread = 0), now, 6, setOf(UpdateRestriction.UNREAD)).eligible)
    }

    @Test
    fun startedAndUnstartedAreOpposites() {
        val started = setOf(UpdateRestriction.STARTED)
        val unstarted = setOf(UpdateRestriction.UNSTARTED)
        assertTrue(UpdatePolicy.decide(title("READING"), now, 6, started).eligible)
        assertFalse(UpdatePolicy.decide(title("READING"), now, 6, unstarted).eligible)
        assertTrue(UpdatePolicy.decide(title("NEW"), now, 6, unstarted).eligible)
        assertFalse(UpdatePolicy.decide(title("NEW"), now, 6, started).eligible)
    }

    @Test
    fun theFinishedFilterUsesTheReadersStatus() {
        assertTrue(UpdatePolicy.decide(title("FINISHED"), now, 6, setOf(UpdateRestriction.COMPLETED)).eligible)
        assertFalse(UpdatePolicy.decide(title("READING"), now, 6, setOf(UpdateRestriction.COMPLETED)).eligible)
    }

    @Test
    fun theEndedPublicationFilterUsesTheSourceAndNeverGuesses() {
        val filter = setOf(UpdateRestriction.PUBLICATION_ENDED)
        assertTrue(UpdatePolicy.decide(title(publication = PublicationStatus.ENDED), now, 6, filter).eligible)
        assertFalse(UpdatePolicy.decide(title(publication = PublicationStatus.ONGOING), now, 6, filter).eligible)
        assertFalse(UpdatePolicy.decide(title(publication = PublicationStatus.UNKNOWN), now, 6, filter).eligible)
    }

    @Test
    fun everyFilterTogetherMeansAnyMatch() {
        val filters = setOf(UpdateRestriction.UNREAD, UpdateRestriction.UNSTARTED, UpdateRestriction.PUBLICATION_ENDED)
        assertTrue(UpdatePolicy.decide(title("NEW", 0, PublicationStatus.UNKNOWN), now, 6, filters).eligible)
        assertTrue(UpdatePolicy.decide(title("READING", 1, PublicationStatus.UNKNOWN), now, 6, filters).eligible)
        assertTrue(UpdatePolicy.decide(title("READING", 0, PublicationStatus.ENDED), now, 6, filters).eligible)
        assertFalse(UpdatePolicy.decide(title("READING", 0, PublicationStatus.UNKNOWN), now, 6, filters).eligible)
    }

    @Test
    fun aManualCheckBypassesTheFiltersButNotAnExclusion() {
        val skipped = title("READING").copy(excludedFromUpdates = true)
        assertFalse(UpdatePolicy.decide(skipped, now, 6, setOf(UpdateRestriction.UNREAD), force = true).eligible)
        assertTrue(UpdatePolicy.decide(title("NEW"), now, 6, setOf(UpdateRestriction.UNREAD), force = true).eligible)
    }

    @Test
    fun theIntervalStillAppliesAfterTheFilters() {
        val justChecked = title(unread = 1, status = "READING").copy(lastSuccessAt = now - hour)
        val decision = UpdatePolicy.decide(justChecked, now, 6, setOf(UpdateRestriction.UNREAD))
        assertFalse(decision.eligible)
        assertTrue(decision.reason == UpdateSkip.INTERVAL)
        assertTrue(decision.nextCheckAt != null)
    }
}
