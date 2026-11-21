package app.yomu.feature.reader.pdf

internal fun adjacentPdfPage(page: Int, count: Int, dual: Boolean, forward: Boolean): Int {
    val step = if (dual) 2 else 1
    val last = (count - 1).coerceAtLeast(0)
    val spread = page.coerceIn(0, last) / step
    return (spread + if (forward) 1 else -1).coerceIn(0, last / step) * step
}
