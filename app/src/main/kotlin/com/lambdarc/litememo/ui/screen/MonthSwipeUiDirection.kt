@file:JvmName("MonthSwipe")

package com.lambdarc.litememo.ui.screen

import kotlin.math.abs

enum class MonthSwipeUiDirection { PREVIOUS, NEXT }

fun resolveMonthSwipe(dragAmount: Float, thresholdPx: Float): MonthSwipeUiDirection? {
    if (abs(dragAmount) < thresholdPx) return null
    return if (dragAmount < 0f) MonthSwipeUiDirection.NEXT else MonthSwipeUiDirection.PREVIOUS
}
