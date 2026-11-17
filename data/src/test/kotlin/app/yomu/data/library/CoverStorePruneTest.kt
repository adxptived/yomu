package app.yomu.data.library

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Cover pictures live in filesDir forever unless someone collects the ones no book points at. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverStorePruneTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun cover(name: String, at: Long): File {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        return File(dir, name).apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(at)
        }
    }

    @Test
    fun orphanedCoversAreDeletedButReferencedAndFreshOnesStay() {
        val now = System.currentTimeMillis()
        val old = now - 7 * 86_400_000L
        val referenced = cover("b1_1000.jpg", old)
        val orphan = cover("b2_1000.jpg", old)
        // An import saves the picture before it inserts the row: a cover written a minute ago must survive.
        val fresh = cover("b3_1000.jpg", now)

        val deleted = CoverStore(context).pruneOrphans(listOf(referenced.absolutePath))

        assertEquals(1, deleted)
        assertTrue("a referenced cover was deleted", referenced.exists())
        assertTrue("a cover of a book that no longer exists stays", !orphan.exists())
        assertTrue("a cover written by an import in progress was deleted", fresh.exists())
    }

    @Test
    fun aCoverPathIsMatchedByItsFileName() {
        val now = System.currentTimeMillis()
        val old = now - 7 * 86_400_000L
        val cover = cover("b9_1000.jpg", old)
        // The stored path is absolute; only its name identifies the file in the covers directory.
        assertEquals(0, CoverStore(context).pruneOrphans(listOf("/some/other/place/${cover.name}")))
        assertTrue(cover.exists())
    }
}
