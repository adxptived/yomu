package app.yomu.data.catalog

import app.yomu.domain.model.MetadataProvider
import app.yomu.domain.tracking.TrackSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun myAnimeListResultsKeepTheirOwnIdentity() {
        val result = TrackSearchResult(
            remoteId = 126287,
            title = "Sousou no Frieren",
            altTitle = "Frieren: Beyond Journey's End",
            coverUrl = "https://cdn.myanimelist.net/images/manga/1.jpg",
            url = "https://myanimelist.net/manga/126287",
            format = "manga",
            publishing = "currently_publishing",
            totalChapters = 0,
            year = 2020,
            description = "An elf mage after the journey.",
        )

        val metadata = result.toExternalMetadata(MetadataProvider.MYANIMELIST)

        assertEquals("MyAnimeList", metadata.provider)
        assertEquals(MetadataProvider.MYANIMELIST, metadata.providerKind)
        assertEquals("Frieren: Beyond Journey's End", metadata.altTitle)
        assertEquals("manga", metadata.contentType)
        assertTrue("description" in metadata.provenance)
    }

    @Test
    fun aMatchWithoutACoverDoesNotClaimOne() {
        val metadata = TrackSearchResult(remoteId = 1, title = "One", url = "u").toExternalMetadata()
        assertEquals(listOf("title"), metadata.provenance)
    }
}
