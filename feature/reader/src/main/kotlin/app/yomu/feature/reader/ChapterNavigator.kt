package app.yomu.feature.reader

import app.yomu.domain.catalog.ChapterIntent
import app.yomu.domain.catalog.ChapterNavigation
import app.yomu.domain.catalog.ChapterOrder
import app.yomu.domain.model.ChapterInfo
import app.yomu.domain.reader.ReaderWindow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owner of online chapter navigation: the reading-order/window decision and the in-flight chapter open.
 *
 * The window (current chapter plus its neighbours) is model state, not a ViewModel field, so it can be snapshotted
 * and restored when a screen is recreated. Navigation runs on a scope this class owns: a newer request cancels the
 * older one, and [dispose] cancels whatever is still running so a destroyed reader never finishes opening a chapter.
 */
class ChapterNavigator(
    dispatcher: CoroutineDispatcher,
    exceptionHandler: CoroutineExceptionHandler? = null,
) {
    private val job = SupervisorJob()
    private val scope = if (exceptionHandler != null) CoroutineScope(job + dispatcher + exceptionHandler) else CoroutineScope(job + dispatcher)
    private var navigationJob: Job? = null
    private val disposed = AtomicBoolean(false)

    /** The chapter window currently held in memory, or null for a local book. */
    var window: ReaderWindow? = null
        private set

    data class Snapshot(val currentId: Long, val entryId: Long, val ids: List<Long>)

    fun snapshot(): Snapshot? = window?.let { Snapshot(it.currentId, it.entryId, it.ids) }

    /** Restores a window after recreation; a stale id simply yields no window. */
    fun restore(snapshot: Snapshot) {
        window = ReaderWindow.open(snapshot.entryId, snapshot.ids, snapshot.currentId)
    }

    /** One release per chapter in reading order, keeping the current chapter's group. */
    fun readingOrder(chapters: List<ChapterInfo>, chapterId: Long): List<Long> {
        val current = chapters.firstOrNull { it.id == chapterId }
        val scanlator = current?.scanlator ?: ChapterOrder.preferredScanlator(chapters)
        return ChapterOrder.slots(chapters)
            .map { slot -> if (slot.any { it.id == chapterId }) chapterId else ChapterOrder.pick(slot, scanlator).id }
            .distinct()
    }

    /** Opens (and remembers) the bounded window centred on a chapter. */
    fun openWindow(entryId: Long, order: List<Long>, chapterId: Long): ReaderWindow? {
        val opened = ReaderWindow.open(entryId, order, chapterId)
        window = opened
        return opened
    }

    fun resolve(chapters: List<ChapterInfo>, currentId: Long, intent: ChapterIntent): ChapterInfo? =
        ChapterNavigation.resolve(chapters, currentId, intent)

    /** Starts a navigation, cancelling the previous one so two quick taps never load two chapters. */
    fun navigate(block: suspend () -> Unit): Job {
        navigationJob?.cancel()
        navigationJob = scope.launch { block() }
        return navigationJob!!
    }

    fun cancelNavigation() {
        navigationJob?.cancel()
        navigationJob = null
    }

    val isNavigating: Boolean get() = navigationJob?.isActive == true

    /** Cancels the in-flight navigation and the whole scope. Safe to call any number of times. */
    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        navigationJob = null
        job.cancel()
    }
}
