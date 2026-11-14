package app.yomu.feature.reader.epub

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yomu.data.book.EpubPaths
import app.yomu.domain.model.Highlight
import app.yomu.domain.model.HighlightStyle
import app.yomu.domain.model.TextReaderSettings
import app.yomu.domain.model.WordTapGesture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import kotlin.coroutines.resume

@Serializable
data class EpubProgress(
    val page: Int = 0,
    val pages: Int = 1,
    val progression: Float = 0f,
    val offset: Int = 0,
    val total: Int = 0,
    /** Chapter these offsets belong to; -1 when the runtime does not say (fixed-layout, old documents). */
    val chapter: Int = -1,
)

@Serializable
data class EpubSelection(
    val text: String = "",
    /** Chapter the offsets belong to; -1 when the selection is in the single document that was loaded. */
    val chapter: Int = -1,
    val start: Int = 0,
    val end: Int = 0,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val sentence: String = "",
)

@Serializable
data class EpubWord(
    val word: String = "",
    val chapter: Int = -1,
    val start: Int = 0,
    val end: Int = 0,
    val tail: String = "",
    val sentence: String = "",
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

@Serializable
data class EpubBlock(val i: Int, val start: Int, val end: Int, val text: String, val translated: Boolean = false)

@Serializable
data class EpubSentence(val start: Int, val end: Int, val text: String)

@Serializable
private data class TapRect(val id: Long = 0, val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)

@Serializable
private data class ReadyInfo(val pages: Int = 1, val total: Int = 0)

/** Callbacks from the reader runtime. All of them are delivered on the main thread. */
interface EpubListener {
    fun onReady(chapter: Int) {}
    fun onProgress(chapter: Int, progress: EpubProgress) {}
    fun onTap(x: Float, y: Float) {}
    fun onSelection(selection: EpubSelection?) {}
    fun onWord(word: EpubWord) {}
    fun onExternalLink(url: String) {}
    fun onImage(model: Any) {}
    fun onFootnote(html: String) {}
    fun onHighlightTap(id: Long, x: Float, y: Float) {}
    fun onEdgeReached(forward: Boolean) {}
}

/** Where to position the chapter after it is loaded. */
sealed interface EpubTarget {
    data class Offset(val offset: Int) : EpubTarget
    data class Progression(val value: Float) : EpubTarget
    data class Anchor(val id: String) : EpubTarget
}

/** Drives the WebView: loads chapters, applies settings and exposes the runtime API to Kotlin. */
class EpubController(
    private val context: Context,
    val document: TextDocument,
    private val fontsDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val main = Handler(Looper.getMainLooper())
    var listener: EpubListener? = null
    var webView: EpubWebView? = null
        private set

    var chapter by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var progress by mutableStateOf(EpubProgress())
        private set

    var settings: TextReaderSettings = TextReaderSettings()
        private set
    var columns = 1
    var wordTap = WordTapGesture.DOUBLE_TAP
    var accent: Long = 0xFF6750A4
    var reduceMotion = false

    /** Keeps the bottom margin free for the reader's status line (page, clock). */
    var footerSpace = false
    var prevLabel = "‹"
    var nextLabel = "›"
    private var pendingTarget: EpubTarget? = null

    /** The chapter the WebView actually shows: [chapter] may already point at the next one while it loads. */
    private var loadedChapter = 0
    /** Chapters the long-strip document holds inline right now (inclusive), or an empty range before the first report. */
    private var stripFrom = 0
    private var stripTo = 0
    private var highlights: List<Highlight> = emptyList()
    private var savedWords: List<String> = emptyList()
    private var nonce = 0
    /** Path of the document the reader itself loaded; the long strip's own fetched chapters are rendered without the
     *  chapter manifest, which only the live document needs. */
    private var mainPath = ""

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun attach(view: EpubWebView) {
        // A re-created composition attaches a new view before the old one is released: keep the newest.
        webView?.takeIf { it !== view }?.let { detach(it) }
        webView = view
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            loadWithOverviewMode = false
            useWideViewPort = false
            mediaPlaybackRequiresUserGesture = true
        }
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.setBackgroundColor(settings.background.toInt())
        view.addJavascriptInterface(Bridge(), "YomuBridge")
        view.webViewClient = Client()
    }

    /**
     * Releases [view] only if it is still the attached one. A late [onDispose] of an old composition must not
     * destroy a WebView that a newer composition has already attached.
     */
    fun detach(view: EpubWebView) {
        if (webView !== view) return
        webView = null
        view.stopLoading()
        view.removeJavascriptInterface("YomuBridge")
        view.destroy()
    }

    private fun customFonts(): List<File> = fontsDir.listFiles()?.filter { it.isFile }.orEmpty()

    fun css(): String = if (document.isFixedLayout(chapter)) {
        ":root{--yomu-accent:#%06X;--yomu-blend:multiply;}".format(accent and 0xFFFFFF)
    } else ReaderCss.build(settings, customFonts(), accent)

    private fun config(target: EpubTarget?, index: Int = chapter, stripChapters: Boolean = true): String = buildJsonObject {
        val fixed = document.isFixedLayout(index)
        put("fixed", fixed)
        put("paged", fixed || (settings.paged && !settings.verticalText && !settings.longStrip))
        put("strip", !fixed && settings.longStrip)
        put("vertical", !fixed && settings.verticalText)
        put("rtl", settings.rtl || document.rtl)
        put("columns", columns)
        put("marginH", settings.marginHorizontal)
        put("marginV", settings.marginVertical)
        put("footer", footerSpace)
        put("wordTap", wordTap.name)
        put("lang", document.language ?: "")
        put("reduceMotion", reduceMotion)
        put("pageAnimation", settings.pageAnimation.name)
        put("chapterButtons", settings.chapterButtons)
        put("chapter", index)
        put("chapterCount", document.chapterCount)
        put("hasPrev", chapter > 0)
        put("hasNext", chapter < document.chapterCount - 1)
        put("prevLabel", prevLabel)
        put("nextLabel", nextLabel)
        // The long strip fetches neighbouring chapters itself; it needs to know where they are and which ones can be
        // inlined (fixed-layout and image-only chapters stay pages of their own).
        if (stripChapters && settings.longStrip && !fixed && !settings.verticalText) {
            put(
                "chapters",
                buildJsonArray {
                    for (i in 0 until document.chapterCount) {
                        add(
                            buildJsonObject {
                                put("path", document.chapterPath(i))
                                put("fixed", document.isFixedLayout(i))
                                put("image", document.isImageChapter(i))
                            },
                        )
                    }
                },
            )
        }
        if (target != null) {
            put(
                "target",
                buildJsonObject {
                    when (target) {
                        is EpubTarget.Offset -> put("offset", target.offset)
                        is EpubTarget.Progression -> put("progression", target.value)
                        is EpubTarget.Anchor -> put("anchor", target.id)
                    }
                },
            )
        }
    }.toString()

    /** Loads [index] and positions it at [target]. */
    fun open(index: Int, target: EpubTarget? = null) {
        val view = webView ?: return
        chapter = index.coerceIn(0, (document.chapterCount - 1).coerceAtLeast(0))
        stripFrom = chapter
        stripTo = chapter
        pendingTarget = target
        loading = true
        nonce++
        mainPath = document.chapterPath(chapter)
        view.setBackgroundColor(settings.background.toInt())
        view.loadUrl("https://$HOST/${encodePath(mainPath)}?v=$nonce")
    }

    fun updateSettings(value: TextReaderSettings, relayout: Boolean = true) {
        val structural = value.paged != settings.paged || value.verticalText != settings.verticalText || value.longStrip != settings.longStrip
        settings = value
        webView?.setBackgroundColor(value.background.toInt())
        if (!relayout) return
        if (structural) {
            open(chapter, EpubTarget.Offset(progress.offset))
        } else {
            js("yomu.setConfig(${config(null)}, ${JsonPrimitive(css())})")
        }
    }

    fun refreshConfig() = js("yomu.setConfig(${config(null)}, ${JsonPrimitive(css())})")

    /** Number of text columns (2 for a landscape spread); reflows the current chapter at the same offset. */
    fun updateColumns(value: Int) {
        val v = value.coerceIn(1, 2)
        if (v == columns) return
        columns = v
        refreshConfig()
    }

    fun next() = js("yomu.next()")
    fun prev() = js("yomu.prev()")

    fun nextChapter() {
        // A long strip already holds the neighbouring chapters: moving is a scroll, not a page load.
        if (isStripMode()) {
            js("yomu.chapterStep(1)")
            return
        }
        if (chapter < document.chapterCount - 1) open(chapter + 1, EpubTarget.Progression(0f))
    }

    fun prevChapter(toEnd: Boolean = false) {
        if (isStripMode()) {
            js("yomu.chapterStep(-1)")
            return
        }
        if (chapter > 0) open(chapter - 1, EpubTarget.Progression(if (toEnd) 1f else 0f))
    }

    /** True when the chapter on screen is part of the long feed: fixed-layout pages are always loaded one at a time. */
    private fun isStripMode(): Boolean =
        settings.longStrip && !settings.verticalText && !loading && !document.isFixedLayout(chapter)

    fun goTo(index: Int, target: EpubTarget) {
        if (isStripMode() && index in stripFrom..stripTo) {
            val spec = buildJsonObject {
                when (target) {
                    is EpubTarget.Offset -> put("offset", target.offset)
                    is EpubTarget.Progression -> put("progression", target.value)
                    is EpubTarget.Anchor -> put("anchor", target.id)
                }
            }
            js("yomu.goToChapter($index, $spec)")
            return
        }
        if (index == chapter && !loading) {
            when (target) {
                is EpubTarget.Offset -> js("yomu.goToOffset(${target.offset})")
                is EpubTarget.Progression -> js("yomu.goToProgression(${target.value})")
                is EpubTarget.Anchor -> js("yomu.goToAnchor(${JsonPrimitive(target.id)})")
            }
        } else {
            open(index, target)
        }
    }

    /** Navigates to an href relative to the current chapter (TOC entries and internal links). */
    fun goToHref(href: String, base: String = document.chapterPath(chapter)) {
        val path = EpubPaths.resolve(base, href)
        val anchor = href.substringAfter('#', "").takeIf { it.isNotEmpty() }
        val index = document.chapterIndexOf(path)
        if (index >= 0) goTo(index, anchor?.let { EpubTarget.Anchor(it) } ?: EpubTarget.Progression(0f))
    }

    fun setHighlights(list: List<Highlight>) {
        highlights = list
        pushHighlights()
    }

    private fun pushHighlights() {
        // A strip document holds several chapters: send them all, the runtime maps each to its section. One chapter at
        // a time otherwise, as before.
        val strip = settings.longStrip
        val arr = buildJsonArray {
            highlights.filter { it.locator.offset != null && (strip || it.locator.chapter == chapter) }.forEach { h ->
                add(
                    buildJsonObject {
                        put("id", h.id)
                        put("chapter", h.locator.chapter)
                        put("start", h.locator.offset ?: 0)
                        put("end", h.locator.endOffset ?: ((h.locator.offset ?: 0) + h.text.length))
                        put("color", h.color.cssHex)
                        put(
                            "style",
                            when (h.style) {
                                HighlightStyle.HIGHLIGHT -> "highlight"
                                HighlightStyle.UNDERLINE -> "underline"
                                HighlightStyle.STRIKETHROUGH -> "strike"
                            },
                        )
                        put("note", !h.note.isNullOrBlank())
                    },
                )
            }
        }
        js("yomu.setHighlights($arr)")
    }

    fun setSavedWords(words: List<String>) {
        savedWords = words
        pushWords()
    }

    private fun pushWords() {
        val list = if (settings.highlightSavedWords) savedWords else emptyList()
        js("yomu.setSavedWords(${JsonArray(list.map { JsonPrimitive(it) })})")
    }

    fun mark(kind: String, start: Int?, end: Int?, scroll: Boolean = true) =
        if (start == null) js("yomu.mark('$kind', null)") else js("yomu.mark('$kind', $start, ${end ?: start}, $scroll)")

    fun clearSelection() = js("yomu.clearSelection()")
    fun select(start: Int, end: Int) = js("yomu.select($start, $end)")
    fun expand(paragraph: Boolean) = js("yomu.expand('${if (paragraph) "paragraph" else "sentence"}')")
    fun insertTranslation(index: Int, text: String) = js("yomu.insertTranslation($index, ${JsonPrimitive(text)})")
    fun commitTranslations() = js("yomu.commitTranslations()")
    fun removeTranslations() = js("yomu.removeTranslations()")
    fun scrollStep(px: Int) = js("yomu.scrollStep($px)")

    suspend fun blocks(from: Int? = null): List<EpubBlock> =
        eval("yomu.blocks(${from ?: "null"})")?.let { runCatching { json.decodeFromString<List<EpubBlock>>(it) }.getOrNull() }.orEmpty()

    suspend fun sentences(from: Int): List<EpubSentence> =
        eval("yomu.sentences($from)")?.let { runCatching { json.decodeFromString<List<EpubSentence>>(it) }.getOrNull() }.orEmpty()

    suspend fun visibleText(): String = eval("yomu.visibleText()").orEmpty()

    suspend fun text(start: Int, end: Int): String = eval("yomu.text($start, $end)").orEmpty()

    private fun js(code: String) {
        main.post { webView?.evaluateJavascript("if(window.yomu){$code}", null) }
    }

    /** Evaluates an expression returning a string and decodes the JSON string literal produced by the WebView. */
    private suspend fun eval(expr: String): String? = suspendCancellableCoroutine { cont ->
        main.post {
            val view = webView
            if (view == null) {
                cont.resume(null)
                return@post
            }
            view.evaluateJavascript("(function(){try{return window.yomu?$expr:null}catch(e){return null}})()") { raw ->
                val value = runCatching {
                    if (raw == null || raw == "null") null else json.decodeFromString<String>(raw)
                }.getOrNull()
                if (cont.isActive) cont.resume(value)
            }
        }
    }

    // ------------------------------------------------------------------ serving
    private fun encodePath(path: String): String = path.split('/').joinToString("/") { android.net.Uri.encode(it) }

    private fun render(raw: String, path: String): String {
        val doc: Document = Jsoup.parse(raw, "https://$HOST/$path")
        doc.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.html)
        sanitizeChapter(doc)
        val html = doc.selectFirst("html")
        if (html != null && html.attr("lang").isBlank() && html.attr("xml:lang").isBlank()) document.language?.let { html.attr("lang", it) }
        val head = doc.head()
        val index = document.chapterIndexOf(path)
        val fixed = index >= 0 && document.isFixedLayout(index)
        val viewport = head.select("meta[name]").firstOrNull { it.attr("name").equals("viewport", true) }?.attr("content").orEmpty()
        if (fixed) html?.attr("data-yomu-fixed", "true")
        head.select("meta[name]").filter { it.attr("name").equals("viewport", true) }.forEach { it.remove() }
        if (fixed) head.appendElement("meta").attr("name", "yomu-fixed-viewport").attr("content", viewport)
        // Only the reader's own script (with this document's nonce), its styles and the book's own resources may load;
        // everything else in the document is blocked by the policy as well as by serve().
        val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
        head.prependElement("meta")
            .attr("http-equiv", "Content-Security-Policy")
            .attr("content", CSP.replace(NONCE, nonce))
        head.prependElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no")
        head.appendElement("link").attr("rel", "stylesheet").attr("href", "/__yomu/reader.css")
        head.appendElement("style").attr("id", "yomu-user").appendChild(org.jsoup.nodes.DataNode(css()))
        head.appendElement("script").attr("nonce", nonce).appendChild(
            org.jsoup.nodes.DataNode("window.__yomuConfig=${config(pendingTarget, index, stripChapters = path == mainPath)};"),
        )
        head.appendElement("script").attr("src", "/__yomu/reader.js")
        return "<!DOCTYPE html>" + doc.outerHtml()
    }

    private fun mime(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "css" -> "text/css"
        "js" -> "application/javascript"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "xhtml", "html", "htm" -> "text/html"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }

    private fun serve(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        // A book may only reach its own packaged resources. External images, trackers and scripts of the document are
        // answered here instead of being handed to the network.
        if (url.host != HOST) return blocked(url.scheme)
        val path = URLDecoder.decode(url.encodedPath.orEmpty().removePrefix("/"), "UTF-8")
        return try {
            when {
                path.startsWith("__yomu/") -> {
                    val name = path.removePrefix("__yomu/")
                    WebResourceResponse(mime(name), "UTF-8", context.assets.open("yomu/$name"))
                }
                path.startsWith("__fonts/") -> {
                    val f = File(fontsDir, path.removePrefix("__fonts/").substringAfterLast('/'))
                    if (f.exists()) WebResourceResponse(mime(f.name), null, f.inputStream()) else notFound()
                }
                document.chapterIndexOf(path) >= 0 && (request.isForMainFrame || !document.isImageChapter(document.chapterIndexOf(path))) -> {
                    val index = document.chapterIndexOf(path)
                    val raw = runBlocking(Dispatchers.IO) { document.chapterHtml(index) }
                    WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(render(raw, path).toByteArray()))
                }
                else -> document.resource(path)?.let { WebResourceResponse(mime(path), null, ByteArrayInputStream(it)) } ?: notFound()
            }
        } catch (e: Throwable) {
            // Also errors (a huge or deeply nested chapter): WebView threads must not take the app down.
            Timber.w(e, "Cannot serve $path")
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    /** Answers http(s) requests that leave the book with an empty 403 instead of letting the WebView fetch them. */
    private fun blocked(scheme: String?): WebResourceResponse? =
        if (scheme == "http" || scheme == "https") {
            WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        } else {
            null
        }

    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = serve(request)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.host == HOST) {
                val path = URLDecoder.decode(url.encodedPath.orEmpty().removePrefix("/"), "UTF-8")
                val index = document.chapterIndexOf(path)
                if (index >= 0) goTo(index, url.fragment?.let { EpubTarget.Anchor(it) } ?: EpubTarget.Progression(0f))
                return true
            }
            listener?.onExternalLink(url.toString())
            return true
        }
    }

    private inner class Bridge {
        /** Runs a callback of reader.js on the main thread; a failing callback is logged instead of closing the app. */
        private fun ui(block: () -> Unit) = main.post { runCatching(block).onFailure { Timber.e(it, "Reader callback failed") } }

        @JavascriptInterface
        fun onReady(data: String) = ui {
            runCatching { json.decodeFromString<ReadyInfo>(data) }
            loadedChapter = chapter
            loading = false
            pushHighlights()
            pushWords()
            listener?.onReady(chapter)
        }

        @JavascriptInterface
        fun onProgress(data: String) = ui {
            runCatching { json.decodeFromString<EpubProgress>(data) }.getOrNull()?.let {
                // While a new chapter loads, the document being replaced still reports its own position: that must not
                // pull the app back to the chapter it is leaving.
                if (loading && it.chapter >= 0 && it.chapter != chapter) return@let
                progress = it
                // The long strip reports the chapter the reader is actually looking at; the page follows it, so the
                // title, the contents and "chapter N of M" stay true while scrolling across boundaries.
                if (settings.longStrip && it.chapter >= 0) {
                    if (it.chapter != chapter) chapter = it.chapter
                    listener?.onProgress(it.chapter, it)
                } else {
                    // A late report of the previous chapter must not be saved with the number of the new one.
                    listener?.onProgress(loadedChapter, it)
                }
            }
        }

        @JavascriptInterface
        fun onStripRange(from: Int, to: Int) = ui {
            if (!settings.longStrip) return@ui
            val changed = from != stripFrom || to != stripTo
            stripFrom = from.coerceAtLeast(0)
            stripTo = to.coerceAtLeast(from)
            // The inserted chapters can bring highlights that were sent before those sections existed.
            if (changed) pushHighlights()
        }

        @JavascriptInterface
        fun onTap(x: Float, y: Float) = ui { listener?.onTap(x, y) }

        @JavascriptInterface
        fun onSelection(data: String) = ui { listener?.onSelection(runCatching { json.decodeFromString<EpubSelection>(data) }.getOrNull()) }

        @JavascriptInterface
        fun onSelectionCleared() = ui { listener?.onSelection(null) }

        @JavascriptInterface
        fun onWord(data: String) = ui { runCatching { json.decodeFromString<EpubWord>(data) }.getOrNull()?.let { listener?.onWord(it) } }

        @JavascriptInterface
        fun onLink(url: String) = ui {
            val uri = android.net.Uri.parse(url)
            if (uri.host == HOST) {
                val path = URLDecoder.decode(uri.encodedPath.orEmpty().removePrefix("/"), "UTF-8")
                val index = document.chapterIndexOf(path)
                if (index >= 0) goTo(index, uri.fragment?.let { EpubTarget.Anchor(it) } ?: EpubTarget.Progression(0f))
            } else {
                listener?.onExternalLink(url)
            }
        }

        @JavascriptInterface
        fun onImage(src: String) = ui {
            val uri = android.net.Uri.parse(src)
            val model: Any = if (uri.host == HOST) {
                val path = URLDecoder.decode(uri.encodedPath.orEmpty().removePrefix("/"), "UTF-8")
                document.resource(path) ?: return@ui
            } else {
                src
            }
            listener?.onImage(model)
        }

        @JavascriptInterface
        fun onFootnote(html: String) = ui { listener?.onFootnote(html) }

        @JavascriptInterface
        fun footnote(href: String): String = runCatching { footnoteHtml(href) }.onFailure { Timber.w(it, "Footnote $href") }.getOrDefault("")

        private fun footnoteHtml(href: String): String {
            val path = EpubPaths.resolve(document.chapterPath(chapter), href)
            val id = href.substringAfter('#', "")
            val index = document.chapterIndexOf(path)
            if (index < 0 || id.isEmpty()) return ""
            val raw = runBlocking(Dispatchers.IO) { document.chapterHtml(index) }
            val doc = Jsoup.parse(raw)
            val el = doc.getElementById(id) ?: return ""
            val holder = if (el.tagName() == "a") el.closest("li,p,aside,div,dd") ?: el.parent() ?: el else el
            return holder.html()
        }

        @JavascriptInterface
        fun onEdge(direction: String) = ui { listener?.onEdgeReached(direction == "next") }

        /** A strip neighbour that cannot be inlined (fixed layout, image-only): open it as its own page. */
        @JavascriptInterface
        fun onChapterEdge(next: Boolean) = ui {
            val target = chapter + if (next) 1 else -1
            if (target in 0 until document.chapterCount) open(target, EpubTarget.Progression(if (next) 0f else 1f))
        }

        @JavascriptInterface
        fun onHighlightTap(data: String) = ui {
            runCatching { json.decodeFromString<TapRect>(data) }.getOrNull()?.let { listener?.onHighlightTap(it.id, it.left, it.top) }
        }

        @JavascriptInterface
        fun log(message: String) = Timber.d("reader.js: $message")
    }

    companion object {
        const val HOST = "book.yomu"
        private const val NONCE = "__yomu_nonce__"

        /** Content policy of a rendered chapter: own packaged resources only, plus this document's config script. */
        private const val CSP = "default-src 'none'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; " +
            "font-src 'self' data:; media-src 'self'; connect-src 'self'; script-src 'self' 'nonce-$NONCE'; " +
            "frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
    }
}

/**
 * Removes everything in a chapter that can run code or reach the network on its own: scripts, nested documents,
 * plugins, forms, inline event handlers and `javascript:` addresses. The book's own text, images and links stay.
 * Removing `<script>` alone is not enough: `<img onerror=…>` and `<iframe>` run code too.
 */
internal fun sanitizeChapter(doc: Document) {
    doc.select(
        "script, iframe, frame, frameset, object, embed, applet, base, form, " +
            "link[rel=import], link[rel=preload], link[rel=prefetch], link[rel=dns-prefetch], link[rel=manifest], " +
            "meta[http-equiv=refresh]",
    ).remove()
    for (el in doc.allElements) {
        for (name in el.attributes().asList().map { it.key }) {
            val key = name.lowercase()
            val value = el.attr(name).trim().lowercase()
            if (key.startsWith("on") || key == "srcdoc" || key == "formaction" ||
                value.startsWith("javascript:") || value.startsWith("vbscript:") || value.startsWith("data:text/html")
            ) {
                el.removeAttr(name)
            }
        }
    }
}

/** WebView that keeps the text selection but hides the system floating toolbar (Yomu shows its own). */
class EpubWebView(context: Context) : WebView(context) {
    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
        super.startActionMode(SilentCallback(callback), type)

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = super.startActionMode(SilentCallback(callback))

    private class SilentCallback(private val wrapped: ActionMode.Callback?) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val r = wrapped?.onCreateActionMode(mode, menu) ?: true
            menu.clear()
            return r
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val r = wrapped?.onPrepareActionMode(mode, menu) ?: true
            menu.clear()
            return r
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode) {
            wrapped?.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            (wrapped as? ActionMode.Callback2)?.onGetContentRect(mode, view, outRect) ?: super.onGetContentRect(mode, view, outRect)
        }
    }
}
