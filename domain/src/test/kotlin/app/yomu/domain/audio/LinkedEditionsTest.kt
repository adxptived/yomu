package app.yomu.domain.audio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A03e: the link is a small id in each book's settings and never disturbs the other fields. */
class LinkedEditionsTest {

    @Test
    fun `a link round trips and keeps the other reader settings`() {
        val json = LinkedEditions.withLink("""{"fontSize":18,"audio":{"speed":1.2}}""", 42)
        assertEquals(42L, LinkedEditions.linkedId(json))
        assertTrue("fontSize" in Json.parseToJsonElement(json).jsonObject)
        assertTrue("audio" in Json.parseToJsonElement(json).jsonObject)
    }

    @Test
    fun `clearing a link removes only that key`() {
        val linked = LinkedEditions.withLink("""{"fontSize":18}""", 42)
        val cleared = LinkedEditions.withLink(linked, null)
        assertNull(LinkedEditions.linkedId(cleared))
        assertTrue("fontSize" in Json.parseToJsonElement(cleared).jsonObject)
    }

    @Test
    fun `missing or invalid settings have no link`() {
        assertNull(LinkedEditions.linkedId(null))
        assertNull(LinkedEditions.linkedId("not json"))
        assertNull(LinkedEditions.linkedId("""{"linkedEdition":0}"""))
        assertNull(LinkedEditions.linkedId("""{"linkedEdition":"x"}"""))
        assertEquals("{}", LinkedEditions.withLink("not json", null))
    }
}
