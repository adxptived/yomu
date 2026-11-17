package app.yomu.domain.catalog

/**
 * How adult content is treated across the catalog, independently of one list's filter.
 *
 * The old switch was a boolean: "show NSFW or not". A third answer is what users actually want — some keep the
 * catalog clean, some want the sources listed but not opened by default, and some want everything.
 *
 * - [SAFE] hides every source and extension marked as adult content.
 * - [MIXED] lists them like any other source.
 * - [NSFW] lists them and does not hide them again behind a filter.
 *
 * Language, pinning and hiding stay independent of this preference: it never changes which languages are shown.
 */
enum class ContentPreference {
    SAFE,
    MIXED,
    NSFW;

    /** Whether a source or extension marked [adult] is listed under this preference. */
    fun allows(adult: Boolean): Boolean = !adult || this != SAFE

    /** The value the old boolean meant, for a settings file written before this preference existed. */
    companion object {
        fun fromLegacyShowNsfw(showNsfw: Boolean): ContentPreference = if (showNsfw) MIXED else SAFE
    }
}
