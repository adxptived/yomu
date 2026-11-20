package app.yomu.feature.reader

/**
 * Whether pages *ahead* of the reading position may be preloaded. The page currently on screen is loaded regardless
 * of the network — this gate only decides the speculative work done for the following pages.
 *
 * [wifiOnly] is the "preload only on Wi-Fi" setting; [metered] is the active network's metered state (mobile data or
 * a hotspot). Preloading is allowed unless the user asked for Wi-Fi only *and* the network is metered.
 */
internal fun preloadAllowed(wifiOnly: Boolean, metered: Boolean): Boolean = !wifiOnly || !metered
