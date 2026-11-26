package app.yomu.domain.tracking

/**
 * When the "link a tracker" hint stays quiet (C2). It is offered once a title has a read chapter and no link; "later"
 * hides it for a week, "never" for this title until the user clears it, so the card never nags on every visit.
 * Records are stored as `"<itemId>:<time|forever>"` strings in the settings.
 */
object TrackPrompt {
    /** How long "Later" keeps the hint away. */
    const val SNOOZE_MS = 7L * 24 * 60 * 60 * 1000

    private const val FOREVER = "forever"

    fun hidden(records: Set<String>, itemId: Long, now: Long): Boolean = records.any { record ->
        val separator = record.indexOf(':')
        if (separator <= 0) return@any false
        if (record.substring(0, separator) != itemId.toString()) return@any false
        val value = record.substring(separator + 1)
        value == FOREVER || value.toLongOrNull()?.let { now - it < SNOOZE_MS } == true
    }

    fun snooze(records: Set<String>, itemId: Long, now: Long): Set<String> = replace(records, itemId, now.toString())

    fun hideForever(records: Set<String>, itemId: Long): Set<String> = replace(records, itemId, FOREVER)

    /** Drops expired snoozes so the set does not grow with every title read once. */
    fun prune(records: Set<String>, now: Long): Set<String> = records.filterNot { record ->
        val value = record.substringAfter(':', "")
        value != FOREVER && (value.toLongOrNull()?.let { now - it >= SNOOZE_MS } ?: true)
    }.toSet()

    private fun replace(records: Set<String>, itemId: Long, value: String): Set<String> =
        records.filterNot { it.substringBefore(':') == itemId.toString() }.toSet() + "$itemId:$value"
}
