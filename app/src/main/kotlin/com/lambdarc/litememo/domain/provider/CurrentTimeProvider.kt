package com.lambdarc.litememo.domain.provider

import com.lambdarc.litememo.domain.model.value.TimestampMillis

interface CurrentTimeProvider {

    fun now(): TimestampMillis

}
