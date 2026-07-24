package com.appvoyager.litememo.data.repository

import com.appvoyager.litememo.data.local.dao.MemoBulkDao
import com.appvoyager.litememo.data.local.dao.MemoDao
import com.appvoyager.litememo.data.mapper.toDomain
import com.appvoyager.litememo.data.mapper.toEntity
import com.appvoyager.litememo.data.mapper.toImageRefs
import com.appvoyager.litememo.data.mapper.toImageRefsByMemoId
import com.appvoyager.litememo.data.mapper.toTagRefs
import com.appvoyager.litememo.data.mapper.toTagRefsByMemoId
import com.appvoyager.litememo.data.util.deleteImageFiles
import com.appvoyager.litememo.data.util.requireNoDuplicateIds
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoSummary
import com.appvoyager.litememo.domain.model.MemoTrashUpdate
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.SearchQuery
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.model.value.TimestampRange
import com.appvoyager.litememo.domain.repository.MemoImageStore
import com.appvoyager.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        memoImageStore.deleteImageFiles(removedFileNames)
    }

    override suspend fun saveAllActiveMemos(expectedActiveIds: List<MemoId>, memos: List<Memo>) {
        val distinctExpectedActiveIds = expectedActiveIds.distinct()
        val distinctMemos = memos.distinctBy { it.id }
        val expectedActiveIdSet = distinctExpectedActiveIds.toSet()
        require(distinctMemos.all { it.id in expectedActiveIdSet }) {
            "Every saved memo must be included in expectedActiveIds."
        }
        require(distinctMemos.all { it.deletedAt == null }) {
            "Only active memos can be saved through saveAllActiveMemos."
        }
        if (distinctExpectedActiveIds.isEmpty()) return

        val removedFileNames = memoBulkDao.upsertAllActiveMemosWithRefsAndCollectRemovedFileNames(
            expectedActiveIds = distinctExpectedActiveIds.map { it.value },
            memos = distinctMemos.map { it.toEntity() },
            tagRefsByMemoId = distinctMemos.toTagRefsByMemoId(),
            imageRefsByMemoId = distinctMemos.toImageRefsByMemoId()
        )
        memoImageStore.deleteImageFiles(removedFileNames.distinct())
    }

    override suspend fun moveMemoToTrash(id: MemoId, deletedAt: TimestampMillis) {
        val affected = memoDao.moveMemoToTrash(id.value, deletedAt.value)
        check(affected > 0) { "Memo not found or already trashed: ${id.value}" }
    }

    override suspend fun moveMemosToTrash(updates: List<MemoTrashUpdate>) {
        val distinctUpdates = updates.distinctBy { it.memoId }
        if (distinctUpdates.isEmpty()) return

        memoBulkDao.moveMemosToTrash(
            distinctUpdates.associate { update ->
                update.memoId.value to update.deletedAt.value
            }
        )
    }

    override suspend fun restoreMemoFromTrash(id: MemoId) {
        val affected = memoDao.restoreMemoFromTrash(id.value)
        check(affected > 0) { "Memo not found or not in trash: ${id.value}" }
    }

    override suspend fun restoreMemosFromTrash(ids: List<MemoId>) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return
        memoBulkDao.restoreMemosFromTrash(distinctIds.map { it.value })
    }

    override suspend fun deleteMemoPermanently(id: MemoId) {
        val fileNames = memoDao.deleteMemoPermanentlyAndCollectImageFileNames(id.value)
        memoImageStore.deleteImageFiles(fileNames)
    }

    override suspend fun deleteMemosPermanently(ids: List<MemoId>) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return

        val fileNames = memoBulkDao.deleteMemosPermanentlyAndCollectImageFileNames(
            distinctIds.map { it.value }
        )
        memoImageStore.deleteImageFiles(fileNames.distinct())
    }

    override suspend fun discardMemo(id: MemoId) {
        val fileNames = memoDao.discardMemoAndCollectImageFileNames(id.value)
        memoImageStore.deleteImageFiles(fileNames)
    }

    override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: TimestampMillis) {
        val fileNames = memoDao.deleteTrashedMemosDeletedAtOrBeforeAndCollectImageFileNames(
            cutoff.value
        )
        memoImageStore.deleteImageFiles(fileNames)
    }

    override suspend fun getAllActiveMemos(): List<Memo> =
        memoDao.getAllActiveMemosWithRefs().map { it.toDomain() }

    override suspend fun saveAllMemos(memos: List<Memo>) {
        memos.requireNoDuplicateIds(label = "memo") { it.id }

        val entities = memos.map { it.toEntity() }
        val removedFileNames = memoDao.upsertAllMemosWithRefsAndCollectRemovedFileNames(
            entities,
            memos.toTagRefsByMemoId(),
            memos.toImageRefsByMemoId()
        )
        memoImageStore.deleteImageFiles(removedFileNames)
    }

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
