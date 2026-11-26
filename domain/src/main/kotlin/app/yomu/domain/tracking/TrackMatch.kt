package app.yomu.domain.tracking

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** What Yomu knows about a title when it looks for the same work on a tracking service. */
data class TrackQuery(
    val title: String,
    /** Other names the title is known by: original, romaji, localized, synonyms. */
    val altTitles: List<String> = emptyList(),
    val type: TrackQueryType = TrackQueryType.UNKNOWN,
    val year: Int? = null,
    val country: String? = null,
    /** Confirmed tags only (genre names from the source); unknown is not "no". */
    val tags: Set<String> = emptySet(),
    /** A verified cross-id of this exact work on the service, when one is known. */
    val knownRemoteId: Long? = null,
)

enum class TrackQueryType { MANGA, NOVEL, ANIME, UNKNOWN }

/** Why a candidate was ranked where it is; the first one is what the list explains to the reader. */
enum class MatchReason {
    EXTERNAL_ID,
    EXACT_TITLE,
    ALT_TITLE,
    TITLE_IN_TITLE,
    TITLE_WORDS,
    SAME_YEAR,
    SAME_TYPE,
    SAME_COUNTRY,
    SHARED_TAGS,
    TYPE_CONFLICT,
    YEAR_GAP,
    DIFFERENT_WORK,
}

/** One search hit with its score (0–100) and the reasons that moved it. */
data class TrackCandidate(
    val result: TrackSearchResult,
    val score: Int,
    val reasons: List<MatchReason>,
) {
    val confident: Boolean get() = score >= TrackMatcher.CONFIDENT_SCORE
}

/** The best hit and, when close, the rivals that must be offered as a choice instead of an automatic link. */
data class TrackMatch(
    val best: TrackCandidate?,
    val rivals: List<TrackCandidate>,
    /** True when [best] is high enough and clearly ahead of the second candidate. */
    val automatic: Boolean,
)

/**
 * Ranking of tracking-service search hits against a Yomu title (C2): a verified external id wins, then names
 * (main, original, localized, alternative), then the facts that separate remakes and same-name works — type, year,
 * country, confirmed tags. A missing field is neutral: never a reason against a candidate.
 *
 * The rules are pure and unit-tested, so the threshold and the gap are measured values, not guesses.
 */
object TrackMatcher {
    /** A match this strong may prefill the choice; the user still confirms it. */
    const val CONFIDENT_SCORE = 72

    /** The best must be this far ahead of the runner-up to be offered alone. */
    const val MIN_GAP = 10

    fun match(query: TrackQuery, results: List<TrackSearchResult>): TrackMatch {
        val candidates = rank(query, results)
        val best = candidates.firstOrNull()
        val rivals = candidates.drop(1).filter { it.score >= (best?.score ?: 0) - MIN_GAP }
        val automatic = best != null && best.confident && (candidates.size == 1 || best.score - candidates[1].score >= MIN_GAP)
        return TrackMatch(best, rivals, automatic)
    }

    fun rank(query: TrackQuery, results: List<TrackSearchResult>): List<TrackCandidate> {
        val names = (listOf(query.title) + query.altTitles).map(::normalize).filter { it.isNotEmpty() }.distinct()
        return results.map { result -> score(query, names, result) }
            .sortedWith(compareByDescending<TrackCandidate> { it.score }.thenBy { it.result.title })
    }

    private fun score(query: TrackQuery, names: List<String>, result: TrackSearchResult): TrackCandidate {
        val reasons = mutableListOf<MatchReason>()
        if (query.knownRemoteId != null && query.knownRemoteId == result.remoteId) {
            return TrackCandidate(result, 100, listOf(MatchReason.EXTERNAL_ID))
        }
        var score = 0
        val main = names.firstOrNull()
        val resultNames = listOfNotNull(result.title, result.altTitle).map(::normalize).filter { it.isNotEmpty() }

        // Names: an exact hit on the main title is worth more than one on an alternative name.
        val exactMain = main != null && resultNames.any { it == main }
        val exactAlt = resultNames.any { rn -> names.any { it == rn } }
        val contains = resultNames.any { rn -> names.any { n -> n.length >= 4 && (rn.contains(n) || n.contains(rn)) } }
        val shared = sharedWords(names, resultNames)
        when {
            exactMain -> { score += 74; reasons += MatchReason.EXACT_TITLE }
            exactAlt -> { score += 66; reasons += MatchReason.ALT_TITLE }
            shared >= 2 -> { score += 34; reasons += MatchReason.TITLE_WORDS }
            contains -> { score += 26; reasons += MatchReason.TITLE_IN_TITLE }
            shared == 1 -> score += 6
        }
        if (!exactMain && !exactAlt && !contains && shared == 0) reasons += MatchReason.DIFFERENT_WORK

        // Facts that separate works carrying the same name.
        val resultType = typeOf(result.format)
        if (query.type != TrackQueryType.UNKNOWN && resultType != TrackQueryType.UNKNOWN) {
            if (query.type == resultType) { score += 8; reasons += MatchReason.SAME_TYPE } else { score -= 30; reasons += MatchReason.TYPE_CONFLICT }
        }
        val year = result.year?.takeIf { it in 1900..2100 }
        if (query.year != null && year != null) {
            val gap = abs(query.year - year)
            when {
                gap == 0 -> { score += 8; reasons += MatchReason.SAME_YEAR }
                gap == 1 -> score += 3
                else -> { score -= 12 * minOf(gap, 4); reasons += MatchReason.YEAR_GAP }
            }
        }
        if (query.country != null && result.country != null && query.country.equals(result.country, ignoreCase = true)) {
            score += 4
            reasons += MatchReason.SAME_COUNTRY
        }
        if (query.tags.isNotEmpty() && result.tags.isNotEmpty()) {
            val sharedTags = query.tags.map(::normalize).toSet().intersect(result.tags.map(::normalize).toSet())
            if (sharedTags.isNotEmpty()) { score += 4 * minOf(sharedTags.size, 3); reasons += MatchReason.SHARED_TAGS }
        }
        // A hit that only shares words (or a fragment) is never "confident" on its own.
        if (!exactMain && !exactAlt && !contains && shared < 2) score = minOf(score, 55)
        return TrackCandidate(result, score.coerceIn(0, 100), reasons.distinct())
    }

    /** "Kimi no Na wa", "Твоё имя", "君の名は。" and "kimi no na wa." share this form. */
    fun normalize(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
        .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
        .replace(Regex("""\b(tom|volume|vol|chapter|ch|part|книга|том|глава)\b\.?\s*\d*"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun sharedWords(names: List<String>, resultNames: List<String>): Int {
        val a = names.flatMap { it.split(' ') }.filter { it.length > 1 }.toSet()
        val b = resultNames.flatMap { it.split(' ') }.filter { it.length > 1 }.toSet()
        return a.intersect(b).size
    }

    private fun typeOf(format: String?): TrackQueryType = when (format?.lowercase(Locale.ROOT)) {
        null, "" -> TrackQueryType.UNKNOWN
        "novel", "light novel", "web novel", "ранобэ", "новелла" -> TrackQueryType.NOVEL
        "anime", "ona", "ova", "tv", "tv short", "movie" -> TrackQueryType.ANIME
        else -> TrackQueryType.MANGA
    }
}
