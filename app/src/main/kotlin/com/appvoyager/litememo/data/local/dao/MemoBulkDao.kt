package com.appvoyager.litememo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.MemoTagRefEntity
import com.appvoyager.litememo.data.local.model.MemoWithRefs

@Dao
interface MemoBulkDao {

    @Transaction
    @Query("SELECT * FROM memos WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getActiveMemosWithRefsBatch(ids: List<String>): List<MemoWithRefs>

    @Query("SELECT id FROM memos WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getActiveMemoIdsBatch(ids: List<String>): List<String>

    @Query("SELECT id FROM memos WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun getTrashedMemoIdsBatch(ids: List<String>): List<String>

    @Query("SELECT fileName FROM memo_images WHERE memoId IN (:memoIds)")
    suspend fun getImageFileNamesForMemosBatch(memoIds: List<String>): List<String>

    @Upsert
    suspend fun upsertMemo(memo: MemoEntity)

    @Insert
    suspend fun insertTagRefs(tagRefs: List<MemoTagRefEntity>)

    @Insert
    suspend fun insertImageRefs(imageRefs: List<MemoImageEntity>)

    @Query("DELETE FROM memo_tag_refs WHERE memoId = :memoId")
    suspend fun deleteTagRefsForMemo(memoId: String)

    @Query("DELETE FROM memo_images WHERE memoId = :memoId")
    suspend fun deleteImageRefsForMemo(memoId: String)

    @Query("UPDATE memos SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveMemoToTrash(id: String, deletedAt: Long): Int

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
    suspend fun upsertAllActiveMemosWithRefsAndCollectRemovedFileNames(
        expectedActiveIds: List<String>,
        memos: List<MemoEntity>,
        tagRefsByMemoId: Map<String, List<MemoTagRefEntity>>,
        imageRefsByMemoId: Map<String, List<MemoImageEntity>>
    ): List<String> {
        val distinctExpectedActiveIds = expectedActiveIds.distinct()
        val distinctMemos = memos.distinctBy { it.id }
        val expectedActiveIdSet = distinctExpectedActiveIds.toSet()
        require(distinctMemos.all { it.id in expectedActiveIdSet }) {
            "Every saved memo must be included in expectedActiveIds."
        }
        if (distinctExpectedActiveIds.isEmpty()) return emptyList()

        val activeMemoIds = distinctExpectedActiveIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getActiveMemoIdsBatch(batch) }
            .toSet()
        check(activeMemoIds == expectedActiveIdSet) {
            "Some memos were not found or are no longer active."
        }

        val memoIds = distinctMemos.map { it.id }
        val before = memoIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getImageFileNamesForMemosBatch(batch) }
        distinctMemos.forEach { memo ->
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
            deleteImageRefsForMemo(memo.id)
            if (imageRefs.isNotEmpty()) insertImageRefs(imageRefs)
        }
        val after = imageRefsByMemoId.values.flatten().map { it.fileName }.toSet()
        return before - after
    }

    @Transaction
    suspend fun moveMemosToTrash(updates: Map<String, Long>) {
        if (updates.isEmpty()) return

        val memoIds = updates.keys.toList()
        val activeMemoIds = memoIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getActiveMemoIdsBatch(batch) }
            .toSet()
        check(activeMemoIds == memoIds.toSet()) {
            "Some memos were not found or are already trashed."
        }

        updates.forEach { (memoId, deletedAt) ->
            val affected = moveMemoToTrash(memoId, deletedAt)
            check(affected == 1) { "Memo could not be moved to trash: $memoId" }
        }
    }

    @Transaction
    suspend fun restoreMemosFromTrash(ids: List<String>) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return

        val trashedMemoIds = distinctIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getTrashedMemoIdsBatch(batch) }
            .toSet()
        check(trashedMemoIds == distinctIds.toSet()) {
            "Some memos were not found or are not in trash."
        }

        distinctIds.chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE).forEach { batch ->
            val affected = restoreMemosFromTrashBatch(batch)
            check(affected == batch.size) { "Some memos could not be restored from trash." }
        }
    }

    @Transaction
    suspend fun deleteMemosPermanentlyAndCollectImageFileNames(ids: List<String>): List<String> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptyList()

        val trashedMemoIds = distinctIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getTrashedMemoIdsBatch(batch) }
            .toSet()
        check(trashedMemoIds == distinctIds.toSet()) {
            "Some memos were not found or are not in trash."
        }

        val fileNames = distinctIds
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> getImageFileNamesForMemosBatch(batch) }
        distinctIds.chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE).forEach { batch ->
            val affected = deleteMemosPermanentlyBatch(batch)
            check(affected == batch.size) { "Some memos could not be permanently deleted." }
        }
        return fileNames
    }

    private companion object {
        const val SQLITE_QUERY_PARAMETER_BATCH_SIZE = 900
    }

}
