package app.yomu.feature.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class PageBatchExportTest {
    @Test
    fun `pages are written once in order and failures do not prevent the next export`() = runTest {
        val attempted = mutableListOf<Int>()
        val result = exportReaderPages(listOf(7, 2, 4, 2)) { page ->
            attempted += page
            if (page == 4) throw IOException("Disk full")
        }
        assertEquals(listOf(2, 4, 7), attempted)
        assertEquals(2, result.saved)
        assertEquals(listOf(4), result.failed)
    }

    @Test
    fun `cancellation stops the export without processing another page`() = runTest {
        val attempted = mutableListOf<Int>()
        val result = runCatching {
            exportReaderPages(listOf(1, 2)) { page ->
                attempted += page
                throw CancellationException()
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(listOf(1), attempted)
    }
}
