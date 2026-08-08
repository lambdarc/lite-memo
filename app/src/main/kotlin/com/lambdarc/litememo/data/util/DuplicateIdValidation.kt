package com.lambdarc.litememo.data.util

fun <T, K> Iterable<T>.requireNoDuplicateIds(label: String, idSelector: (T) -> K) {
    val duplicateIds = groupingBy(idSelector).eachCount()
        .filterValues { it > 1 }.keys
    require(duplicateIds.isEmpty()) { "Duplicate $label ids: $duplicateIds" }
}
