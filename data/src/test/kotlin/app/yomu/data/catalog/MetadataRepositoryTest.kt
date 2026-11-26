package app.yomu.data.catalog

import app.yomu.domain.tracking.TrackSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataRepositoryTest {
    @Test
    fun mapsASearchResultToExternalMetadata() {
        val result = TrackSearchResult(
            remoteId = 42,
            title = "One",
            altTitle = "One alt",
            coverUrl = "https://example.org/one.jpg",
            url = "https://anilist.co/manga/42",
            format = "MANGA",
            publishing = "FINISHED",
            totalChapters = 12,
            year = 2020,
            description = "A story",
        )

        val metadata = result.toExternalMetadata()

        assertEquals("AniList", metadata.provider)
        assertEquals(42L, metadata.id)
        assertEquals("One", metadata.title)
        assertEquals("https://example.org/one.jpg", metadata.coverUrl)
        assertEquals("A story", metadata.description)
        assertEquals("https://anilist.co/manga/42", metadata.url)
        assertEquals(2020, metadata.year)
        assertEquals("MANGA", metadata.format)
        assertEquals("AniList · 2020 · MANGA", metadata.subtitle)
    }

    @Test
    fun aTitleWithoutAYearOrFormatStillHasASubtitle() {
        val metadata = TrackSearchResult(remoteId = 1, title = "One", url = "u").toExternalMetadata()

        assertEquals("AniList", metadata.subtitle)
    }
}
