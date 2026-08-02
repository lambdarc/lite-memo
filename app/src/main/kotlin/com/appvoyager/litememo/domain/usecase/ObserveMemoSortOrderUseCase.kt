package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.repository.DisplaySettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMemoSortOrderUseCase @Inject constructor(
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    operator fun invoke(): Flow<MemoSortOrder> = displaySettingsRepository.observeMemoSortOrder()

}
