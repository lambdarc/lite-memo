package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.CalendarDate
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.sortedBy
import com.appvoyager.litememo.domain.repository.DisplaySettingsRepository
import com.appvoyager.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId
import javax.inject.Inject

class ObserveMemosByCalendarDateUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val displaySettingsRepository: DisplaySettingsRepository,
    private val zoneId: ZoneId
) {

    operator fun invoke(date: CalendarDate): Flow<List<Memo>> = combine(
        memoRepository.observeActiveMemosCreatedBetween(date.toTimestampRange(zoneId)),
        displaySettingsRepository.observeMemoSortOrder()
    ) { memos, sortOrder ->
        memos.sortedBy(sortOrder)
    }

}
