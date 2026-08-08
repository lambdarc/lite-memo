package com.lambdarc.litememo.data.provider

import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider

class SystemCurrentTimeProvider : CurrentTimeProvider {

    override fun now(): TimestampMillis = TimestampMillis(System.currentTimeMillis())

}
