package app.yomu.domain.library

/**
 * The result of parsing a library search box.
 *
 * [usesAdvanced] is true only when the expression actually used operators, prefixes or comparisons. Plain text keeps
 * the repository's existing substring/FTS path; advanced expressions are evaluated in memory by the library screen.
 * Malformed or oversized input is never an error to the user: it degrades to a plain substring match of the raw text.
 */
data class LibrarySearchQuery(val expression: LibrarySearchExpr, val usesAdvanced: Boolean) {
    companion object {
        fun parse(raw: String): LibrarySearchQuery = parseLibrarySearch(raw)
    }
}

private sealed interface Token {
    data class Word(val value: String) : Token
    data object And : Token
    data object Or : Token
    data object Not : Token
    data object LParen : Token
    data object RParen : Token
    data object Colon : Token
    data class Compare(val op: LibrarySearchComparison) : Token
}

/** Inputs past this are treated as plain text: a search box is not a place to spend unbounded CPU. */
private const val MAX_LENGTH = 512
private const val MAX_TOKENS = 200
private const val MAX_DEPTH = 12
private const val SPECIALS = "&|!():><=\""

/** A single linear scan; every branch advances `i`, so there is no repeating-regex blowup. */
private fun tokenize(raw: String): List<Token>? {
    if (raw.length > MAX_LENGTH) return null
    val out = ArrayList<Token>()
    val s = raw.trim()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isWhitespace() -> i++
            c == '&' && i + 1 < s.length && s[i + 1] == '&' -> { out += Token.And; i += 2 }
            c == '|' && i + 1 < s.length && s[i + 1] == '|' -> { out += Token.Or; i += 2 }
            c == '&' || c == '|' -> return null
            c == '!' -> { out += Token.Not; i++ }
            c == '-' && (i == 0 || s[i - 1].isWhitespace() || s[i - 1] in "&|!():><=") -> { out += Token.Not; i++ }
            c == '(' -> { out += Token.LParen; i++ }
            c == ')' -> { out += Token.RParen; i++ }
            c == ':' -> { out += Token.Colon; i++ }
            c == '>' -> {
                if (i + 1 < s.length && s[i + 1] == '=') { out += Token.Compare(LibrarySearchComparison.GTE); i += 2 }
                else { out += Token.Compare(LibrarySearchComparison.GT); i++ }
            }
            c == '<' -> {
                if (i + 1 < s.length && s[i + 1] == '=') { out += Token.Compare(LibrarySearchComparison.LTE); i += 2 }
                else { out += Token.Compare(LibrarySearchComparison.LT); i++ }
            }
            c == '=' -> { out += Token.Compare(LibrarySearchComparison.EQ); i++ }
            c == '"' -> {
                val end = s.indexOf('"', i + 1)
                if (end < 0) return null
                out += Token.Word(s.substring(i + 1, end))
                i = end + 1
            }
            else -> {
                val start = i
                while (i < s.length && !s[i].isWhitespace() && s[i] !in SPECIALS) i++
                if (i == start) return null
                val word = s.substring(start, i)
                val standalone = start == 0 || s[start - 1].isWhitespace()
                out += if (standalone && word.equals("NOT", ignoreCase = true)) Token.Not
                else if (standalone && word.equals("AND", ignoreCase = true)) Token.And
                else if (standalone && word.equals("OR", ignoreCase = true)) Token.Or
                else Token.Word(word)
            }
        }
        if (out.size > MAX_TOKENS) return null
    }
    return out
}

/**
 * Precedence, highest first: unary `-`/`NOT`, implicit AND and `&&`, then `||`. Parentheses override it. A parse that
 * leaves a token unconsumed (a stray `)`, a dangling operator, a field without a value) returns null and the caller
 * falls back to plain text.
 */
private class SearchParser(private val tokens: List<Token>) {
    private var pos = 0
    private var depth = 0

    fun parse(): LibrarySearchExpr? {
        val expr = parseOr() ?: return null
        return if (pos == tokens.size) expr else null
    }

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun parseOr(): LibrarySearchExpr? {
        val first = parseAnd() ?: return null
        var parts: MutableList<LibrarySearchExpr>? = null
        while (peek() is Token.Or) {
            pos++
            val right = parseAnd() ?: return null
            if (parts == null) parts = ArrayList<LibrarySearchExpr>().apply { add(first) }
            parts.add(right)
        }
        return parts?.let { LibrarySearchExpr.Or(it) } ?: first
    }

    private fun parseAnd(): LibrarySearchExpr? {
        val first = parseUnary() ?: return null
        var parts: MutableList<LibrarySearchExpr>? = null
        while (true) {
            when (peek()) {
                is Token.And -> pos++
                is Token.Word, is Token.Not, is Token.LParen -> Unit // implicit AND
                else -> break
            }
            val right = parseUnary() ?: return null
            if (parts == null) parts = ArrayList<LibrarySearchExpr>().apply { add(first) }
            parts.add(right)
        }
        return parts?.let { LibrarySearchExpr.And(it) } ?: first
    }

    private fun parseUnary(): LibrarySearchExpr? = when (peek()) {
        is Token.Not -> {
            pos++
            if (++depth > MAX_DEPTH) return null
            val child = parseUnary() ?: return null
            depth--
            LibrarySearchExpr.Not(child)
        }
        is Token.LParen -> {
            pos++
            if (++depth > MAX_DEPTH) return null
            val inner = parseOr() ?: return null
            depth--
            if (peek() !is Token.RParen) return null
            pos++
            inner
        }
        else -> parseTerm()
    }

    private fun parseTerm(): LibrarySearchExpr? {
        val name = (peek() as? Token.Word)?.value ?: return null
        pos++
        return when (peek()) {
            is Token.Colon -> {
                pos++
                val cmp = (peek() as? Token.Compare)?.also { pos++ }
                val value = (peek() as? Token.Word)?.value ?: return null
                pos++
                val literal = "$name:${cmp?.op?.symbol ?: ""}$value"
                val field = LibrarySearchField.fromToken(name)
                when {
                    field == null -> LibrarySearchExpr.Text(literal)
                    cmp != null && !field.numeric -> LibrarySearchExpr.Text(literal)
                    field.numeric -> {
                        val number = parseNumber(value) ?: return LibrarySearchExpr.Text(literal)
                        LibrarySearchExpr.Compare(field, cmp?.op ?: LibrarySearchComparison.EQ, number)
                    }
                    else -> LibrarySearchExpr.FieldText(field, value)
                }
            }
            is Token.Compare -> {
                val op = peek() as Token.Compare
                pos++
                val value = (peek() as? Token.Word)?.value ?: return null
                pos++
                val literal = "$name${op.op.symbol}$value"
                val field = LibrarySearchField.fromToken(name)
                if (field == null || !field.numeric) return LibrarySearchExpr.Text(literal)
                val number = parseNumber(value) ?: return LibrarySearchExpr.Text(literal)
                LibrarySearchExpr.Compare(field, op.op, number)
            }
            else -> LibrarySearchExpr.Text(name)
        }
    }
}

/** Epoch millis for `123`, or for a `yyyy-MM-dd` date; null when the token is neither. No regex, no java.time. */
private fun parseNumber(value: String): Long? {
    value.toLongOrNull()?.let { return it }
    val parts = value.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (year < 1 || month !in 1..12 || day !in 1..31) return null
    // Days from 1970-01-01 with the proleptic Gregorian calendar (civil_from_days, Howard Hinnant's algorithm).
    val y = year - if (month <= 2) 1 else 0
    val era = if (y >= 0) y / 400 else (y - 399) / 400
    val yoe = y - era * 400
    val mp = month + if (month > 2) -3 else 9
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return (era * 146097 + doe - 719468) * 86_400_000L
}

private fun parseLibrarySearch(raw: String): LibrarySearchQuery {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return LibrarySearchQuery(LibrarySearchExpr.All, false)

    val tokens = tokenize(trimmed) ?: return LibrarySearchQuery(LibrarySearchExpr.Text(trimmed), false)
    if (tokens.none { it !is Token.Word }) {
        // Plain words: the repository already does a case-insensitive FTS/LIKE search and treats spaces as AND.
        val words = tokens.map { (it as Token.Word).value }
        val expr = if (words.size == 1) LibrarySearchExpr.Text(words[0]) else LibrarySearchExpr.And(words.map { LibrarySearchExpr.Text(it) })
        return LibrarySearchQuery(expr, false)
    }
    val expr = SearchParser(tokens).parse() ?: return LibrarySearchQuery(LibrarySearchExpr.Text(trimmed), false)
    return LibrarySearchQuery(expr, true)
}
