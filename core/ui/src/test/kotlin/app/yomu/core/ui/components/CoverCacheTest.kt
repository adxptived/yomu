package app.yomu.core.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

/** Only covers from the internet take part in the cache refresh; local files are already on the device. */
class CoverCacheTest {
    @Test
    fun httpCoversAreRemote() {
        assertEquals("https://covers.test/a.jpg", CoverCache.remoteCoverUrl("https://covers.test/a.jpg"))
        assertEquals("http://covers.test/a.jpg", CoverCache.remoteCoverUrl("http://covers.test/a.jpg"))
    }

    @Test
    fun localModelsAreNotCoversToRefresh() {
        assertNull(CoverCache.remoteCoverUrl(null))
        assertNull(CoverCache.remoteCoverUrl(File("/data/user/0/app/files/covers/b1_0.jpg")))
        assertNull(CoverCache.remoteCoverUrl("/data/user/0/app/files/covers/b1_0.jpg"))
        assertNull(CoverCache.remoteCoverUrl("content://media/external/images/1"))
        assertNull(CoverCache.remoteCoverUrl(42))
    }
}
