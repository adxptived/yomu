package app.yomu.core.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Covers seen once stay in Coil's disk cache, so the library and the catalog open from the device instead of the
 * network — the pull to refresh is what asks for the pictures again when a source has new artwork.
 *
 * [warm] fills the cache for titles that are not on screen yet (the rest of a shelf, the next pages of a catalog), and
 * [refresh] drops the pictures a screen has just reloaded and bumps [revision] so the covers in composition fetch them
 * anew.
 */
object CoverCache {
    /** Increases in [refresh]; [CoverImage] reads it, so a remote cover on screen is requested again. */
    var revision by mutableIntStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // A whole shelf at once would be a burst of requests: a few are fetched at a time.
    private val permits = Semaphore(4)

    // Covers warmed in this process (or being fetched now): a paging list that reports the same page twice is free.
    private val warmed = Collections.synchronizedSet(HashSet<String>())

    /**
     * Loads [models] into the cache for later: the next visit shows them without asking the network. Remote covers
     * only — local files are already on the device. The data saver blocks the downloads by itself (the request then
     * fails and is retried next time).
     */
    fun warm(context: Context, models: Iterable<Any?>, size: Size = THUMBNAIL) {
        val urls = models.mapNotNull(::remoteCoverUrl).distinct().take(MAX_WARMED)
        if (urls.isEmpty()) return
        val loader = SingletonImageLoader.get(context)
        urls.forEach { url ->
            if (!warmed.add(url)) return@forEach
            scope.launch {
                permits.withPermit {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(size)
                        // The cache on disk is the point; keeping a second copy in memory would only push out
                        // the covers actually on screen.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                    val result = try {
                        loader.execute(request)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        null
                    }
                    // Offline, blocked or a broken link: try the cover again on a later pass.
                    if (result !is SuccessResult) warmed.remove(url)
                }
            }
        }
    }

    /**
     * Refreshes the pictures of [urls] after their titles were reloaded: the entries are dropped and every remote
     * cover in composition is asked for again. Local covers are files and are left alone.
     */
    fun refresh(context: Context, urls: Iterable<String?>) {
        val list = urls.mapNotNull(::remoteCoverUrl).distinct()
        if (list.isEmpty()) return
        val loader = SingletonImageLoader.get(context)
        scope.launch {
            list.forEach { url -> loader.diskCache?.remove(url) }
            warmed.removeAll(list.toSet())
            // State for the covers on screen: written on the main thread, like every other view state.
            withContext(Dispatchers.Main) {
                loader.memoryCache?.clear()
                revision++
            }
        }
    }

    /** The address of a remote cover in [model], or null for a local file, a drawable or no cover at all. */
    internal fun remoteCoverUrl(model: Any?): String? = when (model) {
        is String -> model.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        is coil3.Uri -> model.toString().takeIf { model.scheme == "http" || model.scheme == "https" }
        else -> null
    }

    /** Big enough for a grid tile on a high-density screen; the disk keeps the original file either way. */
    private val THUMBNAIL = Size(300, 450)

    private const val MAX_WARMED = 80
}
