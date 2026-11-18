package app.yomu.domain.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IncognitoPolicyTest {

    @Test
    fun `automatic persistence is off in incognito`() {
        listOf(
            PersistenceEvent.AUTO_PROGRESS,
            PersistenceEvent.AUTO_READ_MARK,
            PersistenceEvent.HISTORY,
            PersistenceEvent.SESSION_STATS,
            PersistenceEvent.TRACKER_UPDATE,
            PersistenceEvent.TITLED_NOTIFICATION,
            PersistenceEvent.MEDIA_METADATA,
        ).forEach { assertFalse(IncognitoPolicy.persists(it, incognito = true), it.name) }
    }

    @Test
    fun `a manual edit is still the user's own data`() {
        assertTrue(IncognitoPolicy.persists(PersistenceEvent.USER_EDIT, incognito = true))
    }

    @Test
    fun `outside incognito every event is kept`() {
        PersistenceEvent.entries.forEach { assertTrue(IncognitoPolicy.persists(it, incognito = false), it.name) }
    }

    @Test
    fun `an incognito notification loses its title and text`() {
        assertEquals(null to null, IncognitoPolicy.notificationText("A chapter", "A book", incognito = true))
        assertEquals("A chapter" to "A book", IncognitoPolicy.notificationText("A chapter", "A book", incognito = false))
        // Nothing to hide stays nothing: a blank title is not turned into a made-up one.
        assertEquals("" to "", IncognitoPolicy.notificationText("", "", incognito = true))
    }
}
