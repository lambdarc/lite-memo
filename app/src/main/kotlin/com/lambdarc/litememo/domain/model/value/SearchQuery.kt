package com.lambdarc.litememo.domain.model.value

@JvmInline
value class SearchQuery private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): SearchQuery {
            val trimmed = rawValue.trim()
            require(trimmed.isNotEmpty()) { "SearchQuery must not be blank." }
            return SearchQuery(trimmed)
        }

        fun fromOrNull(rawValue: String): SearchQuery? {
            val trimmed = rawValue.trim()
            return if (trimmed.isEmpty()) null else SearchQuery(trimmed)
        }
    }

}
