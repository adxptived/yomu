package app.yomu.sources.opds

import app.yomu.source.ConfigurableSource
import app.yomu.source.FilterList
import app.yomu.source.HttpSource
import app.yomu.source.ItemsPage
import app.yomu.source.SBookLink
import app.yomu.source.SItem
import app.yomu.source.SourceContent
import app.yomu.source.SourceIds
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic OPDS 1.2 / 2.0 catalog. The user adds any catalog by address (Project Gutenberg, Standard Ebooks,
 * Internet Archive, a personal Calibre server, …). Pagination follows rel="next" links.
 */
class OpdsSource(
    override val name: String,
    feedUrl: String,
    override val lang: String = "all",
    private var username: String? = null,
    private var password: String? = null,
) : HttpSource(), ConfigurableSource {
    override var baseUrl: String = feedUrl
        private set
    override val id: Long = SourceIds.generate("opds:$feedUrl", lang)
    override val content = SourceContent.BOOK
    override val supportsLatest = true
    override val configKeys = listOf("url", "username", "password")

    /** page-number -> URL for each browsed feed, since OPDS paginates with links. */
    private val pages = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()
    private var searchTemplate: String? = null

    override fun configure(values: Map<String, String>) {
        values["url"]?.let { baseUrl = it }
        pages.clear()
        searchTemplate = null
        username = values["username"]?.ifBlank { null }
        password = values["password"]?.ifBlank { null }
    }

    private fun headersFor(url: String): Headers = super.headersBuilder().apply {
        val root = baseUrl.toHttpUrlOrNull()
        val target = url.toHttpUrlOrNull()
        val sameOrigin = root != null && target != null &&
            root.scheme == target.scheme && root.host == target.host && root.port == target.port
        val user = username
        if (sameOrigin && user != null) add("Authorization", Credentials.basic(user, password.orEmpty()))
        add("Accept", "application/atom+xml, application/opds+json, application/json;q=0.9, */*;q=0.8")
    }.build()

    fun downloadHeaders(url: String): Map<String, String> =
        headersFor(url)["Authorization"]?.let { mapOf("Authorization" to it) }.orEmpty()

    private suspend fun load(url: String): OpdsFeed = get(url, headersFor(url)).use { response ->
        OpdsParser.parse(response.body.string(), response.request.url.toString())
    }

    private fun rememberNext(key: String, url: String, page: Int, feed: OpdsFeed): ItemsPage {
        val next = feed.nextUrl?.takeUnless { it == url }
        val map = pages.getOrPut(key) { ConcurrentHashMap() }
        if (page == 1) map.clear()
        if (next == null) map.remove(page + 1) else map[page + 1] = next
        val items = feed.items.map { item ->
            if (item.links.isEmpty()) item else item.copy(memo = Json.encodeToString(AcquisitionMemo(item.links)))
        }
        return ItemsPage(items, next != null)
    }

    private suspend fun paged(key: String, firstUrl: String, page: Int): ItemsPage {
        val url = if (page == 1) firstUrl else pages[key]?.get(page) ?: return ItemsPage(emptyList(), false)
        return rememberNext(key, url, page, load(url))
    }

    private suspend fun browseRoot(key: String, page: Int, section: (OpdsFeed) -> String?): ItemsPage {
        if (page > 1) return paged(key, baseUrl, page)
        val root = load(baseUrl)
        val url = section(root)?.takeUnless { it == baseUrl }
        return if (url == null) rememberNext(key, baseUrl, page, root) else paged(key, url, page)
    }

    override suspend fun getPopular(page: Int): ItemsPage = browseRoot("popular", page) { it.popularUrl }

    override suspend fun getLatest(page: Int): ItemsPage = browseRoot("new", page) { it.newUrl }

    override suspend fun browse(url: String, page: Int): ItemsPage = paged("browse:$url", url, page)

    override suspend fun search(query: String, filters: FilterList, page: Int): ItemsPage {
        val template = searchTemplate ?: run {
            val r = load(baseUrl)
            val s = r.searchUrl ?: return ItemsPage(emptyList(), false)
            (if (r.searchIsOpenSearchDescription) get(s, headersFor(s)).use { OpdsParser.parseOpenSearch(it.body.string(), it.request.url.toString()) } else s).also { searchTemplate = it }
        } ?: return ItemsPage(emptyList(), false)
        return paged("search:$query", OpdsParser.fillTemplate(template, query), page)
    }

    override suspend fun getDetails(item: SItem): SItem = item

    // The catalog stores source-specific data in memo when it saves a book from the feed.
    override suspend fun getDownloadLinks(book: SItem): List<SBookLink> {
        if (book.links.isNotEmpty()) return book.links
        val memo = book.memo ?: return emptyList()
        return try {
            Json.decodeFromString<AcquisitionMemo>(memo).links
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    @Serializable
    private data class AcquisitionMemo(val links: List<SBookLink>)

    companion object {
        /** Well-known public catalogs offered as presets. */
        val presets = listOf(
            "Project Gutenberg" to "https://www.gutenberg.org/ebooks.opds/",
            "Standard Ebooks" to "https://standardebooks.org/feeds/opds",
            "Feedbooks (public domain)" to "https://catalog.feedbooks.com/catalog/public_domain.atom",
            "ManyBooks" to "https://manybooks.net/opds/index.php",
        )
    }
}
