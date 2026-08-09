package com.lambdarc.litememo.domain.model

data class CalendarDaySummary(val date: CalendarDate, val memoCount: Int) {

    init {
        require(memoCount >= 0) { "CalendarDaySummary memoCount must not be negative." }
    }

}
