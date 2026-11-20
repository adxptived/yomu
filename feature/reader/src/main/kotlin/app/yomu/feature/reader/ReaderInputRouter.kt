package app.yomu.feature.reader

import app.yomu.domain.model.TapZoneLayout
import app.yomu.domain.reader.ReaderInput
import app.yomu.domain.reader.ReaderKeyAction
import app.yomu.domain.reader.ReaderKeys
import app.yomu.domain.reader.ReaderPoint
import app.yomu.domain.reader.ReaderTapAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/** One reader command produced by a tap, a hardware key or a gesture. */
sealed interface ReaderIntent {
    data object Previous : ReaderIntent
    data object Next : ReaderIntent
    data object Menu : ReaderIntent
}

/**
 * The single place that turns reader input into an intent and the owner of the coroutine that delivers it.
 *
 * Routing is pure (delegating the geometry to [ReaderInput] and [ReaderKeys]); the delivered intent is what actually
 * moves the page. A single channel consumer means one input wins at a time, and [dispose] drops everything still
 * queued — so a destroyed reader cannot act on a tap that was on its way to a WebView.
 */
class ReaderInputRouter(
    dispatcher: CoroutineDispatcher,
    private val onIntent: suspend (ReaderIntent) -> Unit,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + dispatcher)
    private val intents = Channel<ReaderIntent>(Channel.UNLIMITED)
    private val disposed = AtomicBoolean(false)

    init {
        scope.launch {
            for (intent in intents) runCatching { onIntent(intent) }.onFailure { Timber.w(it, "Reader input") }
        }
    }

    /** Resolves a tap at viewport fractions into the one action it fires. */
    fun routeTap(
        point: ReaderPoint,
        layout: TapZoneLayout,
        rtl: Boolean,
        tapToTurn: Boolean,
        invert: Boolean = false,
        sideFraction: Float = 0.25f,
    ): ReaderIntent {
        if (!tapToTurn) return ReaderIntent.Menu
        return when (ReaderInput.action(layout, invert, rtl, point, sideFraction)) {
            ReaderTapAction.PREV -> ReaderIntent.Previous
            ReaderTapAction.NEXT -> ReaderIntent.Next
            ReaderTapAction.MENU -> ReaderIntent.Menu
        }
    }

    /** Resolves a physical key; null means the key belongs to another app (media keys, for example). */
    fun routeKey(keyCode: Int, rtl: Boolean): ReaderIntent? = when (ReaderKeys.action(keyCode, rtl)) {
        ReaderKeyAction.NEXT -> ReaderIntent.Next
        ReaderKeyAction.PREVIOUS -> ReaderIntent.Previous
        ReaderKeyAction.IGNORE -> null
    }

    /** Queues an intent for the consumer. A disposed router drops it instead of acting after destruction. */
    fun submit(intent: ReaderIntent) {
        if (disposed.get()) return
        intents.trySend(intent)
    }

    val isDisposed: Boolean get() = disposed.get()

    /** Cancels the consumer and every still-queued intent. Safe to call any number of times. */
    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        // Cancel first: closing the channel can resume a waiting consumer, which would still deliver queued intents.
        job.cancel()
        intents.close()
    }
}
