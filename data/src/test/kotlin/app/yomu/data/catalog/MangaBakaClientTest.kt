package app.yomu.data.catalog

import app.yomu.domain.model.MetadataProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1: MangaBaka's public v2 search answer, cut to the fields the card uses. The fixture keeps the real shapes — titles
 * per language with `is_primary`, `cover` sizes, `published.start_date`, `state` — so a change of the contract breaks
 * this test instead of the card.
 */
class MangaBakaClientTest {
    @Test
    fun aSearchResultBecomesCardMetadata() {
        val list = MangaBakaClient.parse(fixture())
        assertEquals(1, list.size)
        val m = list.single()
        assertEquals(MetadataProvider.MANGABAKA.displayName, m.provider)
        assertEquals(MetadataProvider.MANGABAKA, m.providerKind)
        assertEquals(1995L, m.id)
        assertEquals("Frieren: Beyond Journey's End", m.title)
        assertEquals("Sousou no Frieren", m.altTitle)
        assertEquals("https://cdn.mangabaka.dev/x250/frieren", m.coverUrl)
        assertEquals(2020, m.year)
        assertEquals("MANGA", m.format)
        assertEquals("manga", m.contentType)
        assertEquals("https://mangabaka.org/manga/1995/Frieren-Beyond-Journey-s-End", m.url)
        assertTrue(m.description!!.startsWith("The adventure is over"))
        assertTrue("cover" in m.provenance)
    }

    @Test
    fun theEnglishMainTitleWinsAndTheNativeOneIsTheAlternative() {
        val json = series(
            id = 7,
            titles = """
                [{"language":"ja","title":"葬送のフリーレン","is_primary":true},
                 {"language":"ja-Latn","title":"Sousou no Frieren","is_primary":true},
                 {"language":"en","title":"Frieren","is_primary":true}]
            """.trimIndent(),
        )
        val m = MangaBakaClient.parse("""{"data":[$json]}""").single()
        assertEquals("Frieren", m.title)
        assertEquals("Sousou no Frieren", m.altTitle)
    }

    @Test
    fun aMergedRecordIsNeverLinked() {
        val merged = series(id = 5, state = "merged")
        assertTrue(MangaBakaClient.parse("""{"data":[$merged]}""").isEmpty())
    }

    @Test
    fun aRecordWithoutATitleOrIdIsSkipped() {
        assertTrue(MangaBakaClient.parse("""{"data":[{"id":0,"titles":[{"language":"en","title":"X","is_primary":true}]}]}""").isEmpty())
        assertTrue(MangaBakaClient.parse("""{"data":[{"id":5,"titles":[]}]}""").isEmpty())
    }

    @Test
    fun aBrokenAnswerIsEmptyInsteadOfCrashing() {
        assertTrue(MangaBakaClient.parse("not json").isEmpty())
        assertTrue(MangaBakaClient.parse("""{"data":"nope"}""").isEmpty())
        assertNull(MangaBakaClient.parse("""{"data":[]}""").firstOrNull())
    }

    private fun fixture(): String = """{"status":200,"data":[${series(id = 1995)}]}"""

    private fun series(id: Long, state: String = "active", titles: String = DEFAULT_TITLES): String = """
        {
          "id": $id,
          "state": "$state",
          "canonical_url": "https://mangabaka.org/manga/$id/Frieren-Beyond-Journey-s-End",
          "description": "The adventure is over but life goes on for an elf mage.",
          "published": {"start_date": "2020-04-28", "end_date": "2025-10-15"},
          "status": "hiatus",
          "type": "manga",
          "content_rating": "safe",
          "titles": $titles,
          "cover": {
            "raw": "https://images.mangabaka.dev/raw/frieren",
            "x150": "https://cdn.mangabaka.dev/x150/frieren",
            "x250": "https://cdn.mangabaka.dev/x250/frieren"
          }
        }
    """.trimIndent()

    private companion object {
        val DEFAULT_TITLES = """
            [{"language":"ja","title":"葬送のフリーレン","is_primary":true},
             {"language":"ja-Latn","title":"Sousou no Frieren","is_primary":true},
             {"language":"en","title":"Frieren: Beyond Journey's End","is_primary":true},
             {"language":"fr","title":"Frieren","is_primary":true}]
        """.trimIndent()
    }
}
