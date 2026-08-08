package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.repository.MemoRepository
import javax.inject.Inject

class PurgeExpiredTrashedMemosUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val currentTimeProvider: CurrentTimeProvider
) {

    suspend operator fun invoke() {
        val cutoffMillis = (currentTimeProvider.now().value - RETENTION_MILLIS).coerceAtLeast(0L)
        memoRepository.deleteTrashedMemosDeletedAtOrBefore(TimestampMillis(cutoffMillis))
    }

    private companion object {
        const val RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
    }

}
