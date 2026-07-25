package com.appvoyager.litememo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.MemoTagRefEntity
import com.appvoyager.litememo.data.local.model.MemoVersionProjection
import com.appvoyager.litememo.data.local.model.MemoWithRefs

@Dao
interface MemoBulkDao : MemoDao {

    @Transaction
    @Query("SELECT * FROM memos WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getActiveMemosWithRefsBatch(ids: List<String>): List<MemoWithRefs>

    @Query("SELECT id FROM memos WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getActiveMemoIdsBatch(ids: List<String>): List<String>

    @Query("SELECT id, updatedAt FROM memos WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getActiveMemoVersionsBatch(ids: List<String>): List<MemoVersionProjection>

    @Query("SELECT id FROM memos WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun getTrashedMemoIdsBatch(ids: List<String>): List<String>

    @Query("SELECT * FROM memo_images WHERE memoId IN (:memoIds)")
    suspend fun getImageRefsForMemosBatch(memoIds: List<String>): List<MemoImageEntity>

    @Query("UPDATE memos SET deletedAt = :deletedAt WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun moveMemosToTrashBatch(ids: List<String>, deletedAt: Long): Int

    @Query("UPDATE memos SET deletedAt = NULL WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun restoreMemosFromTrashBatch(ids: List<String>): Int

    @Query("DELETE FROM memos WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun deleteMemosPermanentlyBatch(ids: List<String>): Int

    @Transaction
    suspend fun getActiveMemosWithRefs(ids: List<String>): List<MemoWithRefs> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptyList()
        val memoById = distinctIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getActiveMemosWithRefsBatch(batch) }
            .associateBy { memo -> memo.memo.id }
        return distinctIds.mapNotNull(memoById::get)
    }

    @Transaction
    suspend fun upsertActiveMemosWithVersionCheckAndCollectRemovedFileNames(
        expectedVersions: Map<String, Long>,
        memos: List<MemoEntity>,
        tagRefsByMemoId: Map<String, List<MemoTagRefEntity>>,
        imageRefsByMemoId: Map<String, List<MemoImageEntity>>
    ): List<String> {
        if (expectedVersions.isEmpty()) return emptyList()
        require(memos.all { it.deletedAt == null }) {
            "Only active memos can be written through the active bulk write."
        }
        require(memos.all { it.id in expectedVersions }) {
            "Every written memo must be included in expectedVersions."
        }

        val currentVersionById = expectedVersions.keys
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getActiveMemoVersionsBatch(batch) }
            .associate { it.id to it.updatedAt }
        check(currentVersionById.keys == expectedVersions.keys) {
            "Some memos were not found or are no longer active."
        }
        check(expectedVersions.all { (id, version) -> currentVersionById[id] == version }) {
            "Some memos were modified since they were read."
        }

        val updatedMemoIds = memos.map { it.id }
        val before = updatedMemoIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getImageFileNamesForMemos(batch) }
        val currentImageRefsByMemoId = updatedMemoIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getImageRefsForMemosBatch(batch) }
            .groupBy { it.memoId }

        memos.forEach { memo ->
            val tagRefs = tagRefsByMemoId[memo.id].orEmpty()
            val imageRefs = imageRefsByMemoId[memo.id].orEmpty()
            require(tagRefs.all { it.memoId == memo.id }) {
                "All tagRefs must reference memoId=${memo.id}."
            }
            require(imageRefs.all { it.memoId == memo.id }) {
                "All imageRefs must reference memoId=${memo.id}."
            }
            upsertMemo(memo)
            deleteTagRefsForMemo(memo.id)
            if (tagRefs.isNotEmpty()) insertTagRefs(tagRefs)
            val currentImageRefs = currentImageRefsByMemoId[memo.id].orEmpty()
            if (!sameImageRefs(currentImageRefs, imageRefs)) {
                deleteImageRefsForMemo(memo.id)
                if (imageRefs.isNotEmpty()) insertImageRefs(imageRefs)
            }
        }
        val after = imageRefsByMemoId.values.flatten().map { it.fileName }.toSet()
        return before - after
    }

    @Transaction
    suspend fun moveMemosToTrash(updates: Map<String, Long>) {
        if (updates.isEmpty()) return

        val activeMemoIds = updates.keys.toList()
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getActiveMemoIdsBatch(batch) }
        if (activeMemoIds.isEmpty()) return

        activeMemoIds
            .groupBy { memoId -> updates.getValue(memoId) }
            .forEach { (deletedAt, memoIds) ->
                memoIds.chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE).forEach { batch ->
                    moveMemosToTrashBatch(batch, deletedAt)
                }
            }
    }

    @Transaction
    suspend fun restoreMemosFromTrash(ids: List<String>) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return

        val trashedMemoIds = trashedMemoIdsPreservingOrder(distinctIds)
        if (trashedMemoIds.isEmpty()) return

        trashedMemoIds.chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE).forEach { batch ->
            restoreMemosFromTrashBatch(batch)
        }
    }

    @Transaction
    suspend fun deleteMemosPermanentlyAndCollectImageFileNames(ids: List<String>): List<String> {
        val distinctIds = ids.distinct()
        val trashedMemoIds = trashedMemoIdsPreservingOrder(distinctIds)
        if (trashedMemoIds.isEmpty()) return emptyList()

        val fileNames = trashedMemoIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getImageFileNamesForMemos(batch) }
        trashedMemoIds.chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE).forEach { batch ->
            deleteMemosPermanentlyBatch(batch)
        }
        return fileNames
    }

    private suspend fun trashedMemoIdsPreservingOrder(distinctIds: List<String>): List<String> {
        val trashedIdSet = distinctIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getTrashedMemoIdsBatch(batch) }
            .toSet()
        return distinctIds.filter { it in trashedIdSet }
    }

    private fun sameImageRefs(
        current: List<MemoImageEntity>,
        incoming: List<MemoImageEntity>
    ): Boolean = current.sortedBy { it.position } == incoming.sortedBy { it.position }

}
