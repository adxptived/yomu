package app.yomu.feature.reader

import app.yomu.domain.model.PageAction
import app.yomu.domain.reader.PageContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One cancellation owner for page actions (search, share, save, lookup, translation, bookmarks, notes).
 *
 * All of these actions used to launch on the ViewModel scope independently. Funnelling them through a single scope
 * means one [dispose] stops every action still running, and the capability decision lives beside the execution so an
 * action the engine cannot perform is never offered or run.
 */
class PageActionExecutor(
    dispatcher: CoroutineDispatcher,
    exceptionHandler: CoroutineExceptionHandler? = null,
) {
    private val job = SupervisorJob()
    private val scope = if (exceptionHandler != null) CoroutineScope(job + dispatcher + exceptionHandler) else CoroutineScope(job + dispatcher)
    private val disposed = AtomicBoolean(false)

    /** The capability resolver: an action is supported only when the page advertises what it needs. */
    fun isSupported(action: PageAction, context: PageContext): Boolean = when (action) {
        PageAction.TRANSLATE -> context.capabilities.translatable
        PageAction.SAVE, PageAction.SHARE -> context.capabilities.exportable
        PageAction.SET_COVER -> context.capabilities.coverable
        PageAction.COPY_TEXT -> context.capabilities.hasTextLayer || context.capabilities.ocr
        PageAction.BOOKMARK -> context.capabilities.bookmarkable
        PageAction.NOTE -> context.acceptsNote
    }

    /** The configured first action, restricted to what the engine can actually do. */
    fun defaultAction(context: PageContext, enabled: List<PageAction>): PageAction =
        enabled.firstOrNull { isSupported(it, context) }
            ?: if (context.capabilities.translatable) PageAction.TRANSLATE else PageAction.BOOKMARK

    /** Launches one page action on the owned scope; cancelling the owner stops it. */
    fun execute(block: suspend () -> Unit): Job = scope.launch { block() }

    val isDisposed: Boolean get() = disposed.get()

    /** Cancels every running and queued page action. Safe to call any number of times. */
    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        job.cancel()
    }
}
