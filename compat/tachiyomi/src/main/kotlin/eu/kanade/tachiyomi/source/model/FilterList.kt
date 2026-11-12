package eu.kanade.tachiyomi.source.model

data class FilterList(val list: List<Filter<*>>) : List<Filter<*>> by list {

    constructor(vararg fs: Filter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    // Filters are mutable and change in place: two lists must never compare equal, or Compose would skip updates.
    @Suppress("EqualsAlwaysReturnsTrueOrFalse")
    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }
}
