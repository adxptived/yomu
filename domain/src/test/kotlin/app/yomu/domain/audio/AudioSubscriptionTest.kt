package app.yomu.domain.audio

import app.yomu.domain.model.AudioTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioSubscriptionTest {
    private fun episode(guid: String?, url: String = "https://cdn/$guid.mp3", title: String = "Episode $guid", at: Long = 0) =
        SubscriptionEpisode(EpisodeIdentity.of(guid, url, title, at), title, url, 1000, at)

    @Test
    fun `the feed guid wins over the address`() {
        val first = EpisodeIdentity.of("abc", "https://cdn/old.mp3", "One", 10)
        val second = EpisodeIdentity.of("abc", "https://cdn/new.mp3", "One", 10)
        assertEquals(first, second)
    }

    @Test
    fun `without a guid the address, title and date identify the episode`() {
        val identity = EpisodeIdentity.of(null, "https://cdn/a.mp3", "A", 5)
        assertEquals(identity, EpisodeIdentity.of("", "https://cdn/a.mp3", "A", 5))
        assertFalse(identity == EpisodeIdentity.of(null, "https://cdn/a.mp3", "A", 6))
        assertFalse(identity == EpisodeIdentity.of(null, "https://cdn/b.mp3", "A", 5))
    }

    @Test
    fun `a first refresh adds every episode in the feed order`() {
        val fetched = listOf(episode("1"), episode("2"), episode("3"))
        val result = AudioSubscriptionReconciler.reconcile(emptyList(), fetched)
        assertEquals(listOf("1", "2", "3"), result.tracks.map { it.guid })
        assertEquals(3, result.added)
        assertEquals(0, result.updated)
        assertEquals(0, result.removed)
        assertTrue(result.changed)
    }

    @Test
    fun `an unchanged feed changes nothing`() {
        val fetched = listOf(episode("1"), episode("2"))
        val current = AudioSubscriptionReconciler.reconcile(emptyList(), fetched).tracks
        val again = AudioSubscriptionReconciler.reconcile(current, fetched)
        assertEquals(0, again.added)
        assertEquals(0, again.updated)
        assertEquals(0, again.removed)
        assertFalse(again.changed)
        assertEquals(current, again.tracks)
    }

    @Test
    fun `a new episode is added and the old ones keep their place`() {
        val current = AudioSubscriptionReconciler.reconcile(emptyList(), listOf(episode("1"), episode("2"))).tracks
        val result = AudioSubscriptionReconciler.reconcile(current, listOf(episode("1"), episode("2"), episode("3")))
        assertEquals(listOf("1", "2", "3"), result.tracks.map { it.guid })
        assertEquals(1, result.added)
        assertEquals(0, result.removed)
    }

    @Test
    fun `a changed address keeps the downloaded copy and the chapter marks`() {
        val marks = listOf(app.yomu.domain.model.AudioChapter("Intro", 0))
        val current = listOf(
            AudioTrack("Old title", "https://cdn/old.mp3", 60_000, marks, file = "/local/old.mp3", guid = "ep-1", publishedAt = 5),
        )
        val result = AudioSubscriptionReconciler.reconcile(current, listOf(episode("ep-1").copy(url = "https://cdn/new.mp3", title = "New title")))
        assertEquals(0, result.added)
        assertEquals(1, result.updated)
        val track = result.tracks.single()
        assertEquals("https://cdn/new.mp3", track.uri)
        assertEquals("/local/old.mp3", track.file)
        assertEquals(marks, track.chapters)
        // The feed reports a length: the reported one wins.
        assertEquals(1000, track.durationMs)
    }

    @Test
    fun `a missing length does not erase the known one`() {
        val current = listOf(AudioTrack("Title", "https://cdn/a.mp3", 60_000, guid = "ep-1"))
        val result = AudioSubscriptionReconciler.reconcile(current, listOf(SubscriptionEpisode("ep-1", "Title", "https://cdn/a.mp3", durationMs = 0)))
        assertEquals(60_000, result.tracks.single().durationMs)
        assertEquals(0, result.updated)
    }

    @Test
    fun `a deleted episode leaves the list but its downloaded file is reported`() {
        val current = listOf(
            AudioTrack("One", "https://cdn/1.mp3", guid = "1", file = "/local/1.mp3"),
            AudioTrack("Two", "https://cdn/2.mp3", guid = "2"),
        )
        val result = AudioSubscriptionReconciler.reconcile(current, listOf(episode("2")))
        assertEquals(listOf("2"), result.tracks.map { it.guid })
        assertEquals(1, result.removed)
        assertEquals(listOf("/local/1.mp3"), result.retainedFiles)
    }

    @Test
    fun `a plain file track without identity does not match a feed episode`() {
        val current = listOf(AudioTrack("Local", "file:///books/a.mp3"))
        val result = AudioSubscriptionReconciler.reconcile(current, listOf(episode("1")))
        assertEquals(listOf("1"), result.tracks.map { it.guid })
        assertEquals(1, result.removed)
        // Nothing downloaded from a feed: the local file of an unrelated track is not ours to report.
        assertTrue(result.retainedFiles.isEmpty())
    }
}
