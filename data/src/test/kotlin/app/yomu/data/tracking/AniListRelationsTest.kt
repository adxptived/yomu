package app.yomu.data.tracking

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListRelationsTest {
    @Test
    fun parsesEdgesAndSkipsIncompleteNodes() {
        val json = """
            {"Media":{"relations":{"edges":[
              {"relationType":"SEQUEL","node":{"id":2,"title":{"userPreferred":"Two"},"coverImage":{"large":"c2"},"siteUrl":"u2"}},
              {"relationType":"PREQUEL","node":{"id":1,"title":{"romaji":"One"},"coverImage":{"large":"c1"},"siteUrl":"u1"}},
              {"relationType":"SIDE_STORY","node":{"id":3,"title":{"userPreferred":"Three"}}},
              {"relationType":"OTHER","node":{"title":{"userPreferred":"No id"}}}
            ]}}}
        """.trimIndent()

        val related = parseRelations(Json.parseToJsonElement(json))

        assertEquals(3, related.size)
        assertEquals("SEQUEL", related[0].relation)
        assertEquals("Two", related[0].title)
        assertEquals("u2", related[0].url)
        assertEquals("One", related[1].title)
        assertEquals("https://anilist.co/manga/3", related[2].url)
    }

    @Test
    fun anEmptyAnswerGivesNoRelations() {
        assertEquals(emptyList<Any>(), parseRelations(null))
    }
}
