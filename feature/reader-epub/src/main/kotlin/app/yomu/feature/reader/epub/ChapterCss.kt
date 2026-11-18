package app.yomu.feature.reader.epub

/**
 * Confines a chapter's stylesheet to its own section of the whole-book document.
 *
 * Every chapter shares one `head` once the book is paginated as a single document, so `.note { display: none }` from
 * chapter two would otherwise hide `.note` in chapter one. Each selector is prefixed with the chapter's section, while
 * `html`, `body` and `:root` become the section itself (their classes are copied onto it), so the common
 * `body { margin: 0 }` still works. `@font-face`, `@keyframes`, `@page` and similar at-rules stay global — they carry
 * no element selectors — and the contents of `@media`, `@supports`, `@container` and `@layer` are scoped recursively.
 */
internal object ChapterCss {
    const val SCOPE_ATTRIBUTE = "data-yomu-chapter"

    fun scope(css: String, chapter: Int): String = scopeRules(css, selector(chapter))

    fun selector(chapter: Int): String = "section[$SCOPE_ATTRIBUTE=\"$chapter\"]"

    private fun scopeRules(css: String, scope: String): String {
        val out = StringBuilder(css.length + 64)
        var i = 0
        while (i < css.length) {
            val start = i
            val stop = scanPrelude(css, i)
            if (stop >= css.length) {
                out.append(css, start, css.length)
                break
            }
            if (css[stop] == ';') {
                // @charset, @import, @namespace or a stray declaration: nothing to scope.
                out.append(css, start, stop + 1)
                i = stop + 1
                continue
            }
            val close = matchingBrace(css, stop)
            if (close < 0) {
                out.append(css, start, css.length)
                break
            }
            val prelude = css.substring(start, stop).trim()
            val body = css.substring(stop + 1, close)
            when {
                prelude.isEmpty() -> out.append("{}")
                prelude.startsWith("@") -> {
                    when (atRuleName(prelude)) {
                        "media", "supports", "container", "layer", "document", "scope" ->
                            out.append(prelude).append('{').append(scopeRules(body, scope)).append('}')
                        else -> out.append(prelude).append('{').append(body).append('}')
                    }
                }
                // A comment between rules lands in the next prelude; it must not be taken for a selector.
                else -> out.append(scopedSelectorList(stripComments(prelude), scope)).append('{').append(body).append('}')
            }
            i = close + 1
        }
        return out.toString()
    }

    /** Index of the first `{` or `;` that is not inside a comment, a string or parentheses. */
    private fun scanPrelude(css: String, from: Int): Int {
        var i = from
        var depth = 0
        var quote = '\u0000'
        while (i < css.length) {
            val c = css[i]
            if (quote != '\u0000') {
                when {
                    c == '\\' -> i++
                    c == quote -> quote = '\u0000'
                }
            } else {
                when {
                    c == '"' || c == '\'' -> quote = c
                    c == '/' && i + 1 < css.length && css[i + 1] == '*' -> {
                        val end = css.indexOf("*/", i + 2)
                        if (end < 0) return css.length
                        i = end + 1
                    }
                    c == '(' || c == '[' -> depth++
                    c == ')' || c == ']' -> if (depth > 0) depth--
                    depth == 0 && (c == '{' || c == ';') -> return i
                }
            }
            i++
        }
        return css.length
    }

    /** Index of the `}` matching the `{` at [open], or -1 when the rule is unbalanced. */
    private fun matchingBrace(css: String, open: Int): Int {
        var i = open
        var depth = 0
        var quote = '\u0000'
        while (i < css.length) {
            val c = css[i]
            if (quote != '\u0000') {
                when {
                    c == '\\' -> i++
                    c == quote -> quote = '\u0000'
                }
            } else {
                when {
                    c == '"' || c == '\'' -> quote = c
                    c == '/' && i + 1 < css.length && css[i + 1] == '*' -> {
                        val end = css.indexOf("*/", i + 2)
                        if (end < 0) return -1
                        i = end + 1
                    }
                    c == '{' -> depth++
                    c == '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun atRuleName(prelude: String): String =
        prelude.substring(1).takeWhile { it.isLetterOrDigit() || it == '-' }.lowercase()

    /** Removes comments from a selector list, leaving strings alone. */
    private fun stripComments(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        var quote = '\u0000'
        while (i < text.length) {
            val c = text[i]
            if (quote != '\u0000') {
                out.append(c)
                when {
                    c == '\\' && i + 1 < text.length -> { out.append(text[i + 1]); i++ }
                    c == quote -> quote = '\u0000'
                }
            } else {
                when {
                    c == '"' || c == '\'' -> { quote = c; out.append(c) }
                    c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                        val end = text.indexOf("*/", i + 2)
                        i = if (end < 0) text.length else end + 1
                    }
                    else -> out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }

    private fun scopedSelectorList(list: String, scope: String): String =
        splitTopLevel(list, ',').joinToString(", ") { scopeSelector(it.trim(), scope) }

    private data class Part(val combinator: String, val compound: String)

    private fun scopeSelector(selector: String, scope: String): String {
        if (selector.isEmpty()) return scope
        var replaced = false
        val mapped = parseParts(selector).map { part ->
            val suffix = rootSuffix(part.compound)
            if (suffix != null) {
                replaced = true
                Part(part.combinator, scope + suffix)
            } else {
                part
            }
        }
        val collapsed = mutableListOf<Part>()
        for (part in mapped) {
            val previous = collapsed.lastOrNull()
            when {
                // `html body` and `html > body` are one section; keep the suffix of the later part.
                previous != null && previous.compound == scope && part.compound.startsWith(scope) ->
                    collapsed[collapsed.lastIndex] = part.copy(combinator = previous.combinator)
                // A bare second root adds nothing to a section that already has a suffix.
                previous != null && part.compound == scope && previous.compound.startsWith(scope) -> Unit
                else -> collapsed += part
            }
        }
        if (collapsed.isEmpty()) return scope
        val parts = if (replaced) {
            collapsed.toMutableList().also { it[0] = it[0].copy(combinator = "") }
        } else {
            // A selector with no leading combinator needs a descendant space between the section and itself.
            val first = collapsed.first()
            listOf(Part("", scope), first.copy(combinator = first.combinator.ifBlank { " " })) + collapsed.drop(1)
        }
        return parts.joinToString("") { it.combinator + it.compound }
    }

    private fun parseParts(selector: String): List<Part> {
        val out = mutableListOf<Part>()
        val combinator = StringBuilder()
        val compound = StringBuilder()
        fun flush() {
            if (compound.isNotEmpty()) {
                out += Part(combinator.toString(), compound.toString())
                combinator.clear()
                compound.clear()
            }
        }
        var depth = 0
        var quote = '\u0000'
        var i = 0
        while (i < selector.length) {
            val c = selector[i]
            if (quote != '\u0000') {
                compound.append(c)
                when {
                    c == '\\' && i + 1 < selector.length -> {
                        compound.append(selector[i + 1])
                        i++
                    }
                    c == quote -> quote = '\u0000'
                }
                i++
                continue
            }
            when {
                c == '"' || c == '\'' -> { quote = c; compound.append(c) }
                c == '(' || c == '[' -> { depth++; compound.append(c) }
                c == ')' || c == ']' -> { if (depth > 0) depth--; compound.append(c) }
                depth == 0 && (c == '>' || c == '+' || c == '~') -> { flush(); combinator.append(c) }
                depth == 0 && c.isWhitespace() -> {
                    if (compound.isNotEmpty()) flush()
                    if (combinator.isEmpty()) combinator.append(' ')
                }
                else -> compound.append(c)
            }
            i++
        }
        flush()
        return out
    }

    /** The suffix of a compound that starts with `html`, `body` or `:root`, or null for any other selector. */
    private fun rootSuffix(compound: String): String? {
        val lower = compound.lowercase()
        return when {
            lower.startsWith(":root") && (compound.length == 5 || compound[5] !in "abcdefghijklmnopqrstuvwxyz0123456789-_") ->
                compound.substring(5)
            lower.startsWith("html") && typeBoundary(compound, 4) -> compound.substring(4)
            lower.startsWith("body") && typeBoundary(compound, 4) -> compound.substring(4)
            else -> null
        }
    }

    /** A type selector ends at a class, id, attribute or pseudo; `bodyguard` is not `body`. */
    private fun typeBoundary(s: String, at: Int): Boolean = at >= s.length || s[at] in ".#[:"

    /** Splits a selector list on commas that are not inside parentheses, brackets or strings. */
    private fun splitTopLevel(text: String, separator: Char): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        var depth = 0
        var quote = '\u0000'
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote != '\u0000') {
                when {
                    c == '\\' -> i++
                    c == quote -> quote = '\u0000'
                }
            } else {
                when {
                    c == '"' || c == '\'' -> quote = c
                    c == '(' || c == '[' -> depth++
                    c == ')' || c == ']' -> if (depth > 0) depth--
                    depth == 0 && c == separator -> {
                        out += text.substring(start, i)
                        start = i + 1
                    }
                }
            }
            i++
        }
        out += text.substring(start)
        return out
    }
}
