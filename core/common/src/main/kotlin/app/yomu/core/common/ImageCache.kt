package app.yomu.core.common

import android.content.Context
import java.io.File

/**
 * The pictures Coil keeps for covers. They live in the app's own folder instead of the cache directory: Android may
 * empty a cache when storage runs low, and then every cover would have to be downloaded again on the next start.
 * The application registers [clear] together with its image loader; the data layer only needs the folder and the size.
 */
object ImageCache {
    const val DIR = "image_cache"

    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun sizeOnDisk(context: Context): Long = Files.dirSize(dir(context))

    /** Clears every stored picture; null until the app sets it up. */
    @Volatile
    var clear: (() -> Unit)? = null
}
