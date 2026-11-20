package app.yomu.data.audio

import app.yomu.domain.model.AudioTrack
import app.yomu.domain.model.AudiobookManifest
import app.yomu.domain.model.Book
import app.yomu.domain.model.BookFormat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path

/** A02: selective removal and the free-space guard; no network and no WorkManager are involved. */
class AudiobookDownloaderTest {

    @TempDir
    lateinit var tmp: Path

    private val book = Book(id = 1, title = "Book", format = BookFormat.AUDIO, uri = "file:///book.yaudio")

    private fun manifest(vararg files: String?) = AudiobookManifest(
        title = "Book",
        tracks = files.mapIndexed { i, file -> AudioTrack(title = "Track ${i + 1}", uri = "https://example.test/$i.mp3", file = file) },
    )

    private fun downloader(store: AudiobookStore) = AudiobookDownloader(mockk(relaxed = true), store, { mockk() })

    @Test
    fun `removing selected tracks keeps the manifest, the stream addresses and the other files`() = runBlocking {
        val dir = tmp.resolve("book.files").toFile().apply { mkdirs() }
        val first = File(dir, "0001.mp3").apply { writeText("a") }
        val second = File(dir, "0002.mp3").apply { writeText("b") }
        val original = manifest("file://${first.path}", "file://${second.path}", null)
        val store = mockk<AudiobookStore>()
        every { store.downloadDir(book) } returns dir
        every { store.manifest(book) } returns original
        val written = slot<AudiobookManifest>()
        every { store.writeManifest(book, capture(written)) } just runs

        downloader(store).remove(book, setOf(0))

        assertFalse(first.exists())
        assertTrue(second.exists())
        assertEquals(listOf(null, "file://${second.path}", null), written.captured.tracks.map { it.file })
        assertEquals(listOf("Track 1", "Track 2", "Track 3"), written.captured.tracks.map { it.title })
        // The stream address is untouched, so the book keeps playing from the network at the same place.
        assertEquals("https://example.test/0.mp3", written.captured.tracks[0].uri)
        assertTrue(dir.exists())
        verify(exactly = 1) { store.writeManifest(book, any()) }
    }

    @Test
    fun `removing everything deletes the folder but not the manifest`() = runBlocking {
        val dir = tmp.resolve("book.files").toFile().apply { mkdirs() }
        val file = File(dir, "0001.mp3").apply { writeText("a") }
        val store = mockk<AudiobookStore>()
        every { store.downloadDir(book) } returns dir
        every { store.manifest(book) } returns manifest("file://${file.path}")
        every { store.writeManifest(book, any()) } just runs

        downloader(store).remove(book)

        assertFalse(file.exists())
        assertFalse(dir.exists())
        verify { store.writeManifest(book, any()) }
    }

    @Test
    fun `track states tell downloaded, remote and local tracks apart`() {
        val dir = tmp.resolve("book.files").toFile().apply { mkdirs() }
        val first = File(dir, "0001.mp3").apply { writeText("abc") }
        val store = mockk<AudiobookStore>()
        every { store.downloadDir(book) } returns dir
        every { store.manifest(book) } returns AudiobookManifest(
            title = "Book",
            tracks = listOf(
                AudioTrack(title = "Track 1", uri = "https://example.test/0.mp3", file = "file://${first.path}"),
                AudioTrack(title = "Track 2", uri = "https://example.test/1.mp3"),
                AudioTrack(title = "Track 3", uri = "file:///local/third.mp3"),
            ),
        )
        val states = downloader(store).trackStates(book)
        assertEquals(3, states.size)
        assertTrue(states[0].downloaded)
        assertTrue(states[0].available)
        assertEquals(3L, states[0].bytes)
        assertFalse(states[1].downloaded)
        assertFalse(states[1].available)
        assertFalse(states[2].remote)
        assertTrue(states[2].available)
    }

    @Test
    fun `a nearly full volume refuses a download, an unknown one is not guessed about`() {
        val d = downloader(mockk(relaxed = true))
        assertThrows(IOException::class.java) { d.requireSpace(1024) }
        d.requireSpace(0)
        d.requireSpace(2L * 1024 * 1024 * 1024)
    }
}
