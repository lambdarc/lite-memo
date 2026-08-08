package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.MemoSortOrder
import com.lambdarc.litememo.domain.repository.DisplaySettingsRepository
import javax.inject.Inject

class SetMemoSortOrderUseCase @Inject constructor(
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    suspend operator fun invoke(order: MemoSortOrder) =
        displaySettingsRepository.setMemoSortOrder(order)

}
