package app.yomu.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicInfoTest {

    @Test
    fun aDownloadRoundTripsThroughTheImporterParser() {
        val source = ComicInfo(
            title = "Chapter 12 — 終章",
            series = "Ünïcode Series",
            number = "12",
            volume = "2",
            writer = "A. Writer, B. Writer",
            penciller = "C. Artist",
            genre = "Action, Drama",
            languageIso = "en",
            summary = "A <summary> & more",
            year = "2024",
            manga = true,
            pagesXml = "<Page Image=\"0\" Type=\"FrontCover\" />",
            uploadDate = 1_700_000_000_000L,
        )
        val xml = ComicInfoXml.write(source, pageCount = 24)
        val parsed = ComicInfoXml.parse(xml)
        assertEquals(source.title, parsed.title)
        assertEquals(source.series, parsed.series)
        assertEquals(source.number, parsed.number)
        assertEquals(source.volume, parsed.volume)
        assertEquals(source.writer, parsed.writer)
        assertEquals(source.penciller, parsed.penciller)
        assertEquals(source.genre, parsed.genre)
        assertEquals(source.languageIso, parsed.languageIso)
        assertEquals(source.summary, parsed.summary)
        assertEquals(source.year, parsed.year)
        assertTrue(parsed.manga)
        assertEquals(source.uploadDate, parsed.uploadDate)
        assertTrue(parsed.pagesXml.contains("FrontCover"))
        assertTrue(xml.contains("<PageCount>24</PageCount>"))
    }

    @Test
    fun unknownFieldsSurviveARewrite() {
        val xml = """
            <ComicInfo>
              <Title>Kept</Title>
              <Publisher>Publisher</Publisher>
              <Review>Review text</Review>
              <Pages><Page Image="0" Type="FrontCover" /></Pages>
            </ComicInfo>
        """.trimIndent()
        val parsed = ComicInfoXml.parse(xml)
        assertEquals("Publisher", parsed.extra["Publisher"])
        assertEquals("Review text", parsed.extra["Review"])
        val written = ComicInfoXml.write(parsed, pageCount = 1)
        assertTrue(written.contains("<Publisher>Publisher</Publisher>"))
        assertTrue(written.contains("<Review>Review text</Review>"))
    }

    @Test
    fun aCorruptOrEmptyDocumentIsNotACrash() {
        assertTrue(ComicInfoXml.parse("this is not xml").isEmpty)
        assertTrue(ComicInfoXml.parse("<html><body>not a comic</body></html>").isEmpty)
        assertFalse(ComicInfoXml.parse("").uploadDate > 0)
    }

    @Test
    fun noUploadDateIsWrittenWithoutTheElement() {
        val xml = ComicInfoXml.write(ComicInfo(title = "No date"), pageCount = 1)
        assertFalse(xml.contains("UploadDate"))
        assertEquals(0L, ComicInfoXml.parse(xml).uploadDate)
    }
}
