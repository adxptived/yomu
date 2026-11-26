package app.yomu.domain.tracking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** C2: the tracker match is a pure ranking; these are the cases that used to link the wrong work. */
class TrackMatchTest {
    private fun result(
        id: Long,
        title: String,
        alt: String? = null,
        format: String? = "manga",
        year: Int? = 2020,
        country: String? = null,
        tags: List<String> = emptyList(),
    ) = TrackSearchResult(
        remoteId = id, title = title, altTitle = alt, url = "https://example.com/$id",
        format = format, year = year, country = country, tags = tags,
    )

    private fun query(
        title: String,
        alt: List<String> = emptyList(),
        type: TrackQueryType = TrackQueryType.MANGA,
        year: Int? = null,
        country: String? = null,
        tags: Set<String> = emptySet(),
        id: Long? = null,
    ) = TrackQuery(title, alt, type, year, country, tags, id)

    @Test
    fun aVerifiedExternalIdWinsEvenOverABetterName() {
        val match = TrackMatcher.match(
            query("Some Title", id = 7),
            listOf(result(9, "Some Title"), result(7, "Unrelated Name")),
        )
        assertEquals(7, match.best?.result?.remoteId)
        assertEquals(listOf(MatchReason.EXTERNAL_ID), match.best?.reasons)
        assertTrue(match.automatic)
    }

    @Test
    fun exactNamesBeatPartialOnesAndTheBestIsConfident() {
        val match = TrackMatcher.match(
            query("Solo Leveling"),
            listOf(result(1, "Solo Leveling: Ragnarok"), result(2, "Solo Leveling"), result(3, "Solo Leveling Novel", format = "novel")),
        )
        assertEquals(2, match.best?.result?.remoteId)
        assertTrue(match.best!!.confident)
        assertTrue(match.automatic)
    }

    @Test
    fun originalAndLocalizedNamesAreUsed() {
        val q = query("Твоё имя", alt = listOf("Kimi no Na wa", "君の名は"))
        val match = TrackMatcher.match(q, listOf(result(1, "Kimi no Na wa"), result(2, "Kimi no Na wa Another")))
        assertEquals(1, match.best?.result?.remoteId)
        assertEquals(MatchReason.ALT_TITLE, match.best?.reasons?.first())
    }

    @Test
    fun theSameNameInAnotherYearIsNotLinkedAutomatically() {
        val q = query("Berserk", year = 1989)
        val match = TrackMatcher.match(q, listOf(result(1, "Berserk", year = 2016), result(2, "Berserk", year = 1989)))
        assertEquals(2, match.best?.result?.remoteId)
        assertEquals(MatchReason.SAME_YEAR, match.best?.reasons?.firstOrNull { it == MatchReason.SAME_YEAR })
        assertTrue(match.automatic)
    }

    @Test
    fun aYearGapLowersTheScoreAndCanLeaveTwoCloseRivals() {
        val q = query("Berserk", year = 1989)
        val match = TrackMatcher.match(q, listOf(result(1, "Berserk", year = 1997), result(2, "Berserk", year = 1989)))
        val scores = TrackMatcher.rank(q, listOf(result(1, "Berserk", year = 1997), result(2, "Berserk", year = 1989)))
        assertTrue(scores.first().score > scores.last().score)
        assertNotNull(match.best)
    }

    @Test
    fun aNovelAndAMangaWithOneNameAreSeparatedByType() {
        val match = TrackMatcher.match(
            query("Overlord", type = TrackQueryType.NOVEL),
            listOf(result(1, "Overlord", format = "manga"), result(2, "Overlord", format = "novel")),
        )
        assertEquals(2, match.best?.result?.remoteId)
        assertTrue(match.best!!.reasons.contains(MatchReason.SAME_TYPE))
    }

    @Test
    fun anUnknownTypeOnEitherSideIsNeutral() {
        val known = TrackMatcher.rank(query("Overlord", type = TrackQueryType.NOVEL), listOf(result(1, "Overlord", format = "novel")))
        val unknown = TrackMatcher.rank(query("Overlord", type = TrackQueryType.UNKNOWN), listOf(result(1, "Overlord", format = "novel")))
        assertEquals(known.first().score - 8, unknown.first().score)
    }

    @Test
    fun sameNameSameEverythingIsOfferedAloneOnlyWithAClearGap() {
        val match = TrackMatcher.match(
            query("Dandadan", year = 2021),
            listOf(result(1, "Dandadan", year = 2021), result(2, "Dandadan", year = 2021)),
        )
        assertFalse(match.automatic, "two identical candidates must be a choice, not a guess")
        assertEquals(1, match.rivals.size)
    }

    @Test
    fun aWeakNameHitIsNeverAutomatic() {
        val match = TrackMatcher.match(query("Blue"), listOf(result(1, "Blue Period"), result(2, "Blue Lock")))
        assertFalse(match.automatic)
    }

    @Test
    fun countriesAndTagsBreakTies() {
        val q = query("Given", country = "JP", tags = setOf("Boys Love", "Drama"))
        val match = TrackMatcher.match(
            q,
            listOf(result(1, "Given", country = "KR"), result(2, "Given", country = "JP", tags = listOf("Boys Love", "Drama"))),
        )
        assertEquals(2, match.best?.result?.remoteId)
        assertTrue(match.best!!.reasons.contains(MatchReason.SAME_COUNTRY))
        assertTrue(match.best!!.reasons.contains(MatchReason.SHARED_TAGS))
    }

    @Test
    fun namesAreComparedWithoutPunctuationVolumesAndCase() {
        assertEquals("kimi no na wa", TrackMatcher.normalize("Kimi no Na wa."))
        assertEquals("твое имя", TrackMatcher.normalize("Твоё имя"))
        assertEquals("solo leveling", TrackMatcher.normalize("Solo Leveling, Vol. 3"))
        assertEquals("berserk", TrackMatcher.normalize("Berserk (Manga)"))
    }
}
