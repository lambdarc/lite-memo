package com.lambdarc.litememo.domain.model.value

data class TimestampRange(val fromInclusive: TimestampMillis, val toExclusive: TimestampMillis) {

    init {
        require(fromInclusive.value <= toExclusive.value) {
            "fromInclusive must not be later than toExclusive."
        }
    }

    val isEmpty: Boolean
        get() = fromInclusive == toExclusive

}
