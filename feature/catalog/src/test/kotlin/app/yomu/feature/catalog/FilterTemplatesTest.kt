package app.yomu.feature.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterTemplatesTest {
    @Test
    fun roundTripKeepsNamesAndStates() {
        val templates = listOf(
            FilterTemplate("Completed in Russian", "{\"version\":1,\"status\":[2]}"),
            FilterTemplate("Fresh", "{}"),
        )

        assertEquals(templates, FilterTemplateCodec.decode(FilterTemplateCodec.encode(templates)))
    }

    @Test
    fun aDamagedFileGivesNoTemplates() {
        assertTrue(FilterTemplateCodec.decode("not json").isEmpty())
        assertTrue(FilterTemplateCodec.decode("").isEmpty())
    }

    @Test
    fun entriesWithoutANameOrStateAreDropped() {
        val json = """[{"name":"ok","state":"{}"},{"name":"","state":"{}"},{"name":"no state"},{"state":"{}"}]"""

        assertEquals(listOf(FilterTemplate("ok", "{}")), FilterTemplateCodec.decode(json))
    }
}
