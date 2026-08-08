package com.lambdarc.litememo.data.repository

import com.lambdarc.litememo.data.local.dao.MemoBulkDao
import com.lambdarc.litememo.data.local.dao.MemoDao
import com.lambdarc.litememo.data.mapper.toDomain
import com.lambdarc.litememo.data.mapper.toEntity
import com.lambdarc.litememo.data.mapper.toImageRefs
import com.lambdarc.litememo.data.mapper.toImageRefsByMemoId
import com.lambdarc.litememo.data.mapper.toTagRefs
import com.lambdarc.litememo.data.mapper.toTagRefsByMemoId
import com.lambdarc.litememo.data.util.deleteImageFiles
import com.lambdarc.litememo.data.util.requireNoDuplicateIds
import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.model.MemoTrashUpdate
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.SearchQuery
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RoomMemoRepository @Inject constructor(
    private val memoDao: MemoDao,
    private val memoBulkDao: MemoBulkDao,
    private val memoImageStore: MemoImageStore
) : MemoRepository {

    override fun observeActiveMemos(): Flow<List<Memo>> =
        memoDao.observeActiveMemosWithRefs().map { memos ->
            memos.map { memo -> memo.toDomain() }
        }

    override fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummary>> =
        memoDao.observeRecentActiveMemos(limit).map { projections ->
            projections.map { projection -> projection.toDomain() }
        }

    override fun observeActiveMemosBySearchQuery(query: SearchQuery): Flow<List<Memo>> =
        memoDao.observeActiveMemosWithRefsBySearchPattern(
            query.value.toEscapedLikePattern()
        ).map { memos ->
            memos.map { memo -> memo.toDomain() }
        }

    override fun observeActiveMemosCreatedBetween(range: TimestampRange): Flow<List<Memo>> {
        if (range.isEmpty) return flowOf(emptyList())
        return memoDao
            .observeActiveMemosWithRefsCreatedBetween(
                range.fromInclusive.value,
                range.toExclusive.value
            )
            .map { memos ->
                memos.map { memo -> memo.toDomain() }
            }
    }

    override fun observeTrashedMemos(): Flow<List<Memo>> =
        memoDao.observeTrashedMemosWithRefs().map { memos ->
            memos.map { memo -> memo.toDomain() }
        }

    override suspend fun getActiveMemo(id: MemoId): Memo? {
        val memo = memoDao.getActiveMemoWithRefs(id.value) ?: return null
        return memo.toDomain()
    }

    override suspend fun getActiveMemos(ids: List<MemoId>): List<Memo> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptyList()

        val memoById = memoBulkDao.getActiveMemosWithRefs(distinctIds.map { it.value })
            .associateBy { it.memo.id }
        return distinctIds.mapNotNull { id -> memoById[id.value]?.toDomain() }
    }

    override suspend fun saveMemo(memo: Memo) {
        val removedFileNames = memoDao.upsertMemoWithRefsAndCollectRemovedFileNames(
            memo = memo.toEntity(),
            tagRefs = memo.toTagRefs(),
            imageRefs = memo.toImageRefs()
        )
        withContext(NonCancellable) {
            memoImageStore.deleteImageFiles(removedFileNames)
        }
    }

    override suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>) {
        if (writes.isEmpty()) return
        writes.requireNoDuplicateIds(label = "memo") { it.memoId }

        val updatedMemos = writes
            .filterIsInstance<ActiveMemoBulkWrite.Update>()
            .map { it.updatedMemo }
        val removedFileNames = memoBulkDao
            .upsertActiveMemosWithVersionCheckAndCollectRemovedFileNames(
                expectedVersions = writes.associate {
                    it.memoId.value to it.expectedUpdatedAt.value
                },
                memos = updatedMemos.map { it.toEntity() },
                tagRefsByMemoId = updatedMemos.toTagRefsByMemoId(),
                imageRefsByMemoId = updatedMemos.toImageRefsByMemoId()
            )
        withContext(NonCancellable) {
            memoImageStore.deleteImageFiles(removedFileNames.distinct())
        }
    }

    override suspend fun moveMemoToTrash(id: MemoId, deletedAt: TimestampMillis) {
        val affected = memoDao.moveMemoToTrash(id.value, deletedAt.value)
        check(affected > 0) { "Memo not found or already trashed: ${id.value}" }
    }

    override suspend fun moveMemosToTrash(updates: List<MemoTrashUpdate>) {
        if (updates.isEmpty()) return
        updates.requireNoDuplicateIds(label = "memo") { it.memoId }

        memoBulkDao.moveMemosToTrash(
            updates.associate { update ->
                update.memoId.value to update.deletedAt.value
            }
        )
    }

    override suspend fun restoreMemoFromTrash(id: MemoId) {
        val affected = memoDao.restoreMemoFromTrash(id.value)
        check(affected > 0) { "Memo not found or not in trash: ${id.value}" }
    }

    override suspend fun restoreMemosFromTrash(ids: List<MemoId>) {
        if (ids.isEmpty()) return
        ids.requireNoDuplicateIds(label = "memo") { it }
        memoBulkDao.restoreMemosFromTrash(ids.map { it.value })
    }

    override suspend fun deleteMemoPermanently(id: MemoId) {
        val fileNames = memoDao.deleteMemoPermanentlyAndCollectImageFileNames(id.value)
        withContext(NonCancellable) {
            memoImageStore.deleteImageFiles(fileNames)
        }
    }

    override suspend fun deleteMemosPermanently(ids: List<MemoId>) {
        if (ids.isEmpty()) return
        ids.requireNoDuplicateIds(label = "memo") { it }

        val fileNames = memoBulkDao.deleteMemosPermanentlyAndCollectImageFileNames(
            ids.map { it.value }
        )
        withContext(NonCancellable) {
            memoImageStore.deleteImageFiles(fileNames.distinct())
        }
    }

    override suspend fun discardMemo(id: MemoId) {
        val fileNames = memoDao.discardMemoAndCollectImageFileNames(id.value)
        withContext(NonCancellable) {
            memoImageStore.deleteImageFiles(fileNames)
        }
    }

    override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: TimestampMillis) {
        val fileNames = memoDao.deleteTrashedMemosDeletedAtOrBeforeAndCollectImageFileNames(
            cutoff.value
        )
        withContext(NonCancellable) {
            memoImageStore.deleteImageFiles(fileNames)
        }
    }

    override suspend fun getAllActiveMemos(): List<Memo> =
        memoDao.getAllActiveMemosWithRefs().map { it.toDomain() }

    private fun String.toEscapedLikePattern(): String = buildString {
        append(LIKE_MULTI_CHARACTER_WILDCARD)
        this@toEscapedLikePattern.forEach { char ->
            when (char) {
                LIKE_ESCAPE_CHARACTER,
                LIKE_MULTI_CHARACTER_WILDCARD,
                LIKE_SINGLE_CHARACTER_WILDCARD -> append(LIKE_ESCAPE_CHARACTER)
            }
            append(char)
        }
        append(LIKE_MULTI_CHARACTER_WILDCARD)
    }

    private companion object {
        const val LIKE_ESCAPE_CHARACTER = '\\'
        const val LIKE_MULTI_CHARACTER_WILDCARD = '%'
        const val LIKE_SINGLE_CHARACTER_WILDCARD = '_'
    }

}
