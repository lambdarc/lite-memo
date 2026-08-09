package com.lambdarc.litememo.domain.repository

import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.model.MemoTrashUpdate
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.SearchQuery
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import kotlinx.coroutines.flow.Flow

interface MemoRepository {

    fun observeActiveMemos(): Flow<List<Memo>>

    fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummary>>

    fun observeActiveMemosBySearchQuery(query: SearchQuery): Flow<List<Memo>>

    fun observeActiveMemosCreatedBetween(range: TimestampRange): Flow<List<Memo>>

    fun observeTrashedMemos(): Flow<List<Memo>>

    suspend fun getActiveMemo(id: MemoId): Memo?

    suspend fun getActiveMemos(ids: List<MemoId>): List<Memo>

    suspend fun saveMemo(memo: Memo)

    suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>)

    suspend fun moveMemoToTrash(id: MemoId, deletedAt: TimestampMillis)

    suspend fun moveMemosToTrash(updates: List<MemoTrashUpdate>)

    suspend fun restoreMemoFromTrash(id: MemoId)

    suspend fun restoreMemosFromTrash(ids: List<MemoId>)

    suspend fun deleteMemoPermanently(id: MemoId)

    suspend fun deleteMemosPermanently(ids: List<MemoId>)

    suspend fun discardMemo(id: MemoId)

    suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: TimestampMillis)

    suspend fun getAllActiveMemos(): List<Memo>

}
