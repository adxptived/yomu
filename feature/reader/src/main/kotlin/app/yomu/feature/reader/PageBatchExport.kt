package app.yomu.feature.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PageBatchExportResult(val saved: Int, val failed: List<Int>)

internal data class PageBatchExportState(
    val running: Boolean = false,
    val result: PageBatchExportResult? = null,
    val chapter: Long? = null,
)

internal suspend fun exportReaderPages(
    pages: List<Int>,
    write: suspend (Int) -> Unit,
): PageBatchExportResult {
    var saved = 0
    val failed = mutableListOf<Int>()
    for (page in pages.toSortedSet()) {
        currentCoroutineContext().ensureActive()
        try {
            write(page)
            saved++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed += page
        }
    }
    return PageBatchExportResult(saved, failed)
}
