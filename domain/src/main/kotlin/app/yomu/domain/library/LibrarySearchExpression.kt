package app.yomu.domain.library

/** A field a library search can name with a `field:value` prefix or a comparison. */
enum class LibrarySearchField {
    TITLE, AUTHOR, ARTIST, DESCRIPTION, GENRE, SOURCE, NOTES, LANGUAGE,
    UNREAD, ADDED, NEXT_UPDATE, PROGRESS, RATING;

    val numeric: Boolean
        get() = this == UNREAD || this == ADDED || this == NEXT_UPDATE || this == PROGRESS || this == RATING

    companion object {
        /** Aliases keep the syntax forgiving; a name that is not here is a literal, never an error. */
        fun fromToken(raw: String): LibrarySearchField? = when (raw.lowercase()) {
            "title", "name" -> TITLE
            "author", "authors" -> AUTHOR
            "artist" -> ARTIST
            "description", "desc", "summary" -> DESCRIPTION
            "genre", "genres", "tag", "tags" -> GENRE
            "source" -> SOURCE
            "note", "notes" -> NOTES
            "language", "lang" -> LANGUAGE
            "unread" -> UNREAD
            "added" -> ADDED
            "nextupdate", "next", "update", "lastupdate" -> NEXT_UPDATE
            "progress" -> PROGRESS
            "rating", "score" -> RATING
            else -> null
        }
    }
}

/** The comparison attached to a numeric or date field. */
enum class LibrarySearchComparison(val symbol: String) {
    EQ("="), GT(">"), GTE(">="), LT("<"), LTE("<=");

    fun matches(actual: Long, expected: Long): Boolean = when (this) {
        EQ -> actual == expected
        GT -> actual > expected
        GTE -> actual >= expected
        LT -> actual < expected
        LTE -> actual <= expected
    }

    companion object {
        fun fromToken(raw: String): LibrarySearchComparison? = entries.firstOrNull { it.symbol == raw }
    }
}

/**
 * The parsed boolean expression of a library search.
 *
 * It is deliberately independent of the database and of any UI model: the library screen supplies a
 * [LibrarySearchFields] adapter and evaluates the tree in memory. Structured filters keep running in SQL, so this
 * only ever narrows what those filters already returned.
 */
sealed interface LibrarySearchExpr {
    data object All : LibrarySearchExpr

    /** A bare word or quoted phrase: matched against all searchable text. */
    data class Text(val value: String) : LibrarySearchExpr

    /** A known text field with a `field:value` prefix. */
    data class FieldText(val field: LibrarySearchField, val value: String) : LibrarySearchExpr

    /** A known numeric/date field with a comparison. */
    data class Compare(val field: LibrarySearchField, val op: LibrarySearchComparison, val value: Long) : LibrarySearchExpr

    data class Not(val child: LibrarySearchExpr) : LibrarySearchExpr
    data class And(val children: List<LibrarySearchExpr>) : LibrarySearchExpr
    data class Or(val children: List<LibrarySearchExpr>) : LibrarySearchExpr
}

/** The per-item view an expression needs. Implementations live next to the item type they adapt. */
interface LibrarySearchFields {
    /** All searchable text concatenated, for bare terms. */
    val searchText: String

    /** The value of a named text field, or null when the item has no such field. */
    fun text(field: LibrarySearchField): String?

    /** The value of a named numeric/date field, or null when the item has no such field. */
    fun number(field: LibrarySearchField): Long?
}

/** Recursive evaluation. Each node is a single pass over its children; no backtracking, no reflection. */
fun LibrarySearchExpr.matches(fields: LibrarySearchFields): Boolean = when (this) {
    LibrarySearchExpr.All -> true
    is LibrarySearchExpr.Text -> fields.searchText.contains(value, ignoreCase = true)
    is LibrarySearchExpr.FieldText -> fields.text(field)?.contains(value, ignoreCase = true) == true
    is LibrarySearchExpr.Compare -> fields.number(field)?.let { op.matches(it, value) } == true
    is LibrarySearchExpr.Not -> !child.matches(fields)
    is LibrarySearchExpr.And -> children.all { it.matches(fields) }
    is LibrarySearchExpr.Or -> children.any { it.matches(fields) }
}

/** The adapter for the domain's own item model; its `searchText` is the only text view it exposes. */
fun LibraryQueryItem.toSearchFields(): LibrarySearchFields = object : LibrarySearchFields {
    override val searchText: String get() = this@toSearchFields.searchText

    override fun text(field: LibrarySearchField): String? = when (field) {
        LibrarySearchField.LANGUAGE -> this@toSearchFields.language ?: this@toSearchFields.searchText
        else -> this@toSearchFields.searchText
    }

    override fun number(field: LibrarySearchField): Long? = when (field) {
        LibrarySearchField.UNREAD -> this@toSearchFields.unreadChapters?.toLong()
        else -> null
    }
}
