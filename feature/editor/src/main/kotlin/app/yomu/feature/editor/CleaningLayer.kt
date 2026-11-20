package app.yomu.feature.editor

import app.yomu.data.book.Xhtml
import app.yomu.domain.text.Typography
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** One reversible cleaning stroke applied to the staging copy of a document. */
data class BrushStroke(val path: String, val before: String, val after: String, val label: String)

/**
 * A reversible cleaning layer: brush strokes are applied to an in-memory staging copy only. The original
 * documents are never modified until the caller commits [changes] to disk (as one undoable History step).
 * Strokes have their own undo/redo stack independent of the editor's History.
 */
class CleaningLayer {
    private val originals = LinkedHashMap<String, String>()
    private val staged = LinkedHashMap<String, String>()
    private val undoStack = ArrayDeque<BrushStroke>()
    private val redoStack = ArrayDeque<BrushStroke>()

    fun stage(path: String, original: String) {
        originals.putIfAbsent(path, original)
        staged.putIfAbsent(path, original)
    }

    fun stagedPaths(): Set<String> = originals.keys.toSet()

    fun text(path: String): String = staged[path] ?: originals[path].orEmpty()

    /** Applies [transform] to the staged text and records a reversible stroke. Returns true when it changed. */
    fun apply(path: String, label: String, transform: (String) -> String): Boolean {
        val before = text(path)
        val after = transform(before)
        if (after == before) return false
        staged[path] = after
        undoStack.addLast(BrushStroke(path, before, after, label))
        redoStack.clear()
        return true
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo(): BrushStroke? {
        val stroke = undoStack.removeLastOrNull() ?: return null
        staged[stroke.path] = stroke.before
        redoStack.addLast(stroke)
        return stroke
    }

    fun redo(): BrushStroke? {
        val stroke = redoStack.removeLastOrNull() ?: return null
        staged[stroke.path] = stroke.after
        undoStack.addLast(stroke)
        return stroke
    }

    /** Only the documents whose staged text differs from the original: path -> (original, staged). */
    fun changes(): Map<String, Pair<String, String>> =
        staged.filter { (path, text) -> originals[path] != text }.mapValues { (path, text) -> originals[path].orEmpty() to text }

    /** Drops every stroke and restores the staging copy to the originals. */
    fun discard() {
        staged.clear()
        staged.putAll(originals)
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        /**
         * The pure text transformation used by the brush: the same typographic clean-up as the workspace,
         * applied to the text nodes of an XHTML document without touching its markup.
         */
        fun cleanXhtml(text: String, options: CleanupOptions, lang: String?): String {
            val doc = Jsoup.parse(text, "", Parser.xmlParser())
            Xhtml.configure(doc)
            var changed = false
            NodeTraversor.traverse(object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    if (node !is TextNode) return
                    val parent = node.parent()
                    if (parent is Element && parent.local() in SKIP_TEXT) return
                    var value = node.wholeText
                    if (options.spaces) value = Typography.collapseSpaces(value)
                    if (options.dashes) value = Typography.fixDashes(value)
                    if (options.quotes) value = Typography.fixQuotes(value, lang)
                    if (value != node.wholeText) {
                        node.text(value)
                        changed = true
                    }
                }
            }, doc)
            if (options.emptyParagraphs) {
                doc.allByLocal("p").filter { p ->
                    p.text().replace('\u00A0', ' ').isBlank() && p.getAllElements().none { it.local() in MEDIA }
                }.forEach {
                    it.remove()
                    changed = true
                }
            }
            return if (changed) doc.outerHtml() else text
        }

        private val SKIP_TEXT = setOf("pre", "code", "script", "style", "title")
        private val MEDIA = setOf("img", "image", "svg", "video", "audio", "object", "iframe", "math", "hr")
    }
}
