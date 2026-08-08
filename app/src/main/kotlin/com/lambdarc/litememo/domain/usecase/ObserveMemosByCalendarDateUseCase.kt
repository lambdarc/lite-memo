package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.CalendarDate
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.sortedBy
import com.lambdarc.litememo.domain.repository.DisplaySettingsRepository
import com.lambdarc.litememo.domain.repository.MemoRepository
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
