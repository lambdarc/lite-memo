package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.CalendarDate
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import java.time.ZoneId
import javax.inject.Inject

class GetCurrentCalendarDateUseCase @Inject constructor(
    private val currentTimeProvider: CurrentTimeProvider,
    private val zoneId: ZoneId
) {

    operator fun invoke(): CalendarDate = CalendarDate.from(currentTimeProvider.now(), zoneId)

}
