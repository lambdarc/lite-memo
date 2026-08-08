package com.lambdarc.litememo.domain.model.value

@JvmInline
value class TimestampMillis(val value: Long) {

    init {
        require(value >= 0L) { "TimestampMillis must not be negative." }
    }

}
