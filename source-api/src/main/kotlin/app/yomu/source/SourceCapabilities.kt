package app.yomu.source

/**
 * What a source can actually do.
 *
 * The browsing UI used to infer this from flags scattered on the source interface; a source that cannot search still
 * showed a search tab, and an empty result was indistinguishable from an unsupported feature. These are *values* with
 * safe defaults, so an older extension that never heard of this type keeps working.
 *
 * The defaults are deliberately conservative for reading and permissive for browsing, which is the shape of the
 * existing interface: `CatalogSource` always has popular/latest/search entry points, so those default to true, while
 * optional capabilities default to false and are only reported when a source really implements them.
 */
data class SourceCapabilities(
    val search: Boolean = true,
    val latest: Boolean = true,
    val popular: Boolean = true,
    /** Navigation entries (sub-catalogues) are browsable through [CatalogSource.browse]. */
    val navigation: Boolean = false,
    /** Whole files can be downloaded (EPUB/PDF/CBZ, OPDS acquisitions). */
    val files: Boolean = false,
    /** Chapters can be streamed (audio, or pages that are fetched on demand). */
    val streaming: Boolean = false,
    /** The source has a subscription that is refreshed for new episodes. */
    val subscriptions: Boolean = false,
    /** The source needs a login or a browser check before it returns content. */
    val login: Boolean = false,
    /** Text chapters are read as content rather than page images. */
    val textContent: Boolean = false,
    /** Administratively hidden or disabled by the user. */
    val hidden: Boolean = false,
) {
    /** Tabs that should be drawn for this source, in the order a reader expects them. */
    val browsingTabs: List<SourceTab>
        get() = buildList {
            if (popular) add(SourceTab.POPULAR)
            if (latest) add(SourceTab.LATEST)
            if (search) add(SourceTab.SEARCH)
            if (navigation) add(SourceTab.NAVIGATION)
        }

    /** True when nothing can be browsed, so the screen shows an explanation instead of empty tabs. */
    val isBrowsable: Boolean get() = browsingTabs.isNotEmpty()

    /**
     * Why a tab is missing, for the empty state. A source that cannot search says so; the user must not conclude that
     * the search returned nothing.
     */
    fun unsupportedReason(tab: SourceTab): String? = when {
        !isSupported(tab) -> "unsupported"
        else -> null
    }

    fun isSupported(tab: SourceTab): Boolean = when (tab) {
        SourceTab.POPULAR -> popular
        SourceTab.LATEST -> latest
        SourceTab.SEARCH -> search
        SourceTab.NAVIGATION -> navigation
    }

    companion object {
        /**
         * The capabilities implied by a source's own declarations, used when it does not report any. A `CatalogSource`
         * keeps exactly the tabs its flags allow, so nothing new appears for an extension that was not updated.
         */
        fun infer(source: Source): SourceCapabilities {
            val catalog = source as? CatalogSource
            val content = source.content
            return SourceCapabilities(
                search = catalog?.supportsSearch ?: source.supportsSearch,
                latest = catalog?.supportsLatest ?: source.supportsLatest,
                popular = catalog != null,
                navigation = false,
                files = content == SourceContent.BOOK || content == SourceContent.AUDIO,
                streaming = content == SourceContent.AUDIO,
                subscriptions = content == SourceContent.AUDIO,
                login = false,
                textContent = content == SourceContent.NOVEL,
            )
        }
    }
}

/** The browsing entry points of a source. */
enum class SourceTab { POPULAR, LATEST, SEARCH, NAVIGATION }

/**
 * What a source reports about itself. Implemented optionally, next to [CatalogSource]; the reader uses
 * [SourceCapabilities.infer] when a source does not implement it.
 */
interface CapabilitySource {
    val capabilities: SourceCapabilities
}
