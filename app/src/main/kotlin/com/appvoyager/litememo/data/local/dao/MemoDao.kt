package com.appvoyager.litememo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.MemoTagRefEntity
import com.appvoyager.litememo.data.local.model.MemoSummaryProjection
import com.appvoyager.litememo.data.local.model.MemoWithRefs
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {

    @Transaction
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL")
    fun observeActiveMemosWithRefs(): Flow<List<MemoWithRefs>>

    @Query(
        """
        SELECT id, title, body, isFavorite FROM memos
        WHERE deletedAt IS NULL
        ORDER BY isFavorite DESC, updatedAt DESC, createdAt DESC, id ASC
        LIMIT :limit
        """
    )
    fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummaryProjection>>

    @Transaction
    @Query(
        """
        SELECT * FROM memos
        WHERE deletedAt IS NULL
            AND (
                title LIKE :pattern ESCAPE '\'
                OR body LIKE :pattern ESCAPE '\'
            )
        """
    )
    fun observeActiveMemosWithRefsBySearchPattern(pattern: String): Flow<List<MemoWithRefs>>

    @Transaction
    @Query(
        """
        SELECT * FROM memos
        WHERE deletedAt IS NULL
            AND createdAt >= :fromMillis
            AND createdAt < :toMillis
        """
    )
    fun observeActiveMemosWithRefsCreatedBetween(
        fromMillis: Long,
        toMillis: Long
    ): Flow<List<MemoWithRefs>>

    @Transaction
    @Query("SELECT * FROM memos WHERE id = :id AND deletedAt IS NULL")
    suspend fun getActiveMemoWithRefs(id: String): MemoWithRefs?

    @Transaction
    @Query("SELECT * FROM memos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedMemosWithRefs(): Flow<List<MemoWithRefs>>

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

    @Query("SELECT fileName FROM memo_images WHERE memoId = :memoId")
    suspend fun getImageFileNamesForMemo(memoId: String): List<String>

    @Query("SELECT fileName FROM memo_images WHERE memoId IN (:memoIds)")
    suspend fun getImageFileNamesForMemos(memoIds: List<String>): List<String>

    @Query("SELECT fileName FROM memo_images WHERE fileName IN (:fileNames)")
    suspend fun findReferencedImageFileNames(fileNames: List<String>): List<String>

    @Query(
        """
        SELECT fileName FROM memo_images
        WHERE memoId IN (SELECT id FROM memos WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff)
        """
    )
    suspend fun getImageFileNamesForTrashedMemosDeletedAtOrBefore(cutoff: Long): List<String>

    @Query("UPDATE memos SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveMemoToTrash(id: String, deletedAt: Long): Int

    @Query("UPDATE memos SET deletedAt = NULL WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun restoreMemoFromTrash(id: String): Int

    @Query("DELETE FROM memos WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteMemoPermanently(id: String): Int

    // 放棄された新規編集行の破棄専用。deletedAt を問わず物理削除する（MemoRepository.discardMemo 参照）。
    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun discardMemo(id: String): Int

    @Query("DELETE FROM memos WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: Long)

    @Transaction
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL")
    suspend fun getAllActiveMemosWithRefs(): List<MemoWithRefs>

    @Transaction
    suspend fun upsertAllMemosWithRefs(
        memos: List<MemoEntity>,
        tagRefsByMemoId: Map<String, List<MemoTagRefEntity>>,
        imageRefsByMemoId: Map<String, List<MemoImageEntity>>
    ) {
        memos.forEach { memo ->
            val tagRefs = tagRefsByMemoId[memo.id] ?: emptyList()
            val imageRefs = imageRefsByMemoId[memo.id] ?: emptyList()
            upsertMemoWithRefs(memo, tagRefs, imageRefs)
        }
    }

    @Transaction
    suspend fun upsertAllMemosWithRefsAndCollectRemovedFileNames(
        memos: List<MemoEntity>,
        tagRefsByMemoId: Map<String, List<MemoTagRefEntity>>,
        imageRefsByMemoId: Map<String, List<MemoImageEntity>>
    ): List<String> {
        val before = memos.map { it.id }
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { memoIds -> getImageFileNamesForMemos(memoIds) }
        upsertAllMemosWithRefs(memos, tagRefsByMemoId, imageRefsByMemoId)
        val after = imageRefsByMemoId.values.flatten().map { it.fileName }.toSet()
        return before - after
    }

    @Transaction
    suspend fun upsertMemoWithRefs(
        memo: MemoEntity,
        tagRefs: List<MemoTagRefEntity>,
        imageRefs: List<MemoImageEntity>
    ) {
        require(tagRefs.all { it.memoId == memo.id }) {
            "All tagRefs must reference memoId=${memo.id}."
        }
        require(imageRefs.all { it.memoId == memo.id }) {
            "All imageRefs must reference memoId=${memo.id}."
        }

        upsertMemo(memo)
        deleteTagRefsForMemo(memo.id)
        if (tagRefs.isNotEmpty()) {
            insertTagRefs(tagRefs)
        }
        deleteImageRefsForMemo(memo.id)
        if (imageRefs.isNotEmpty()) {
            insertImageRefs(imageRefs)
        }
    }

    @Transaction
    suspend fun upsertMemoWithRefsAndCollectRemovedFileNames(
        memo: MemoEntity,
        tagRefs: List<MemoTagRefEntity>,
        imageRefs: List<MemoImageEntity>
    ): List<String> {
        val before = getImageFileNamesForMemo(memo.id)
        upsertMemoWithRefs(memo, tagRefs, imageRefs)
        return before - imageRefs.map { it.fileName }.toSet()
    }

    @Transaction
    suspend fun deleteMemoPermanentlyAndCollectImageFileNames(id: String): List<String> {
        val fileNames = getImageFileNamesForMemo(id)
        val affected = deleteMemoPermanently(id)
        check(affected > 0) { "Memo not found or not in trash: $id" }
        return fileNames
    }

    @Transaction
    suspend fun discardMemoAndCollectImageFileNames(id: String): List<String> {
        val fileNames = getImageFileNamesForMemo(id)
        discardMemo(id)
        return fileNames
    }

    @Transaction
    suspend fun deleteTrashedMemosDeletedAtOrBeforeAndCollectImageFileNames(
        cutoff: Long
    ): List<String> {
        val fileNames = getImageFileNamesForTrashedMemosDeletedAtOrBefore(cutoff)
        deleteTrashedMemosDeletedAtOrBefore(cutoff)
        return fileNames
    }

}
