package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.updatedAtFrom
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.repository.MemoRepository
import javax.inject.Inject

class SetMemoFavoriteUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val currentTimeProvider: CurrentTimeProvider
) {

    suspend operator fun invoke(id: MemoId, isFavorite: Boolean): Memo {
        val memo = requireNotNull(memoRepository.getActiveMemo(id)) {
            "Memo not found: ${id.value}"
        }
        if (memo.isFavorite == isFavorite) return memo

        val now = currentTimeProvider.now()
        val updatedMemo = memo.copy(
            updatedAt = memo.updatedAtFrom(now),
            isFavorite = isFavorite
        )
        memoRepository.saveMemo(updatedMemo)
        return updatedMemo
    }

}
