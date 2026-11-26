package app.yomu.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** C1: providers by content type, what a missing MyAnimeList key means, and old saved links. */
class MetadataProviderTest {
    @Test
    fun mangaAndNovelsCanUseDifferentProviders() {
        val preferMal = MetadataProviders.preferred(ContentType.MANGA, MetadataProvider.MYANIMELIST, MetadataProvider.MANGABAKA)
        assertEquals(MetadataProvider.MYANIMELIST, preferMal)
        val preferBaka = MetadataProviders.preferred(ContentType.NOVEL, MetadataProvider.ANILIST, MetadataProvider.MANGABAKA)
        assertEquals(MetadataProvider.MANGABAKA, preferBaka)
    }

    @Test
    fun booksUseTheMangaChoice() {
        assertEquals(
            MetadataProvider.ANILIST,
            MetadataProviders.preferred(ContentType.BOOK, MetadataProvider.ANILIST, MetadataProvider.MANGABAKA),
        )
    }

    @Test
    fun animeListAndMangaBakaNeedNoKey() {
        assertEquals(MetadataAvailability.READY, MetadataProviders.state(MetadataProvider.ANILIST, malConfigured = false))
        assertEquals(MetadataAvailability.READY, MetadataProviders.state(MetadataProvider.MANGABAKA, malConfigured = false))
    }

    @Test
    fun myAnimeListWithoutAClientIdSaysWhatIsMissing() {
        assertEquals(MetadataAvailability.NEEDS_CLIENT_ID, MetadataProviders.state(MetadataProvider.MYANIMELIST, malConfigured = false))
        assertEquals(MetadataAvailability.READY, MetadataProviders.state(MetadataProvider.MYANIMELIST, malConfigured = true))
    }

    @Test
    fun oldSavedLinksStillResolveTheirProvider() {
        val anilist = ExternalMetadata(provider = "AniList", id = 1, title = "One")
        val mal = ExternalMetadata(provider = "MyAnimeList", id = 2, title = "Two")
        val baka = ExternalMetadata(provider = "MangaBaka", id = 3, title = "Three")
        assertEquals(MetadataProvider.ANILIST, anilist.providerKind)
        assertEquals(MetadataProvider.MYANIMELIST, mal.providerKind)
        assertEquals(MetadataProvider.MANGABAKA, baka.providerKind)
        assertNull(ExternalMetadata(provider = "Somewhere", id = 4, title = "X").providerKind)
    }

    @Test
    fun aMatchKnowsWhichFieldsTheProviderSupplied() {
        val metadata = ExternalMetadata(
            provider = "MangaBaka", id = 9, title = "Frieren", year = 2020,
            provenance = listOf("title", "cover", "year"),
        )
        assertTrue("cover" in metadata.provenance)
        assertEquals("MangaBaka · 2020", metadata.subtitle)
    }
}
