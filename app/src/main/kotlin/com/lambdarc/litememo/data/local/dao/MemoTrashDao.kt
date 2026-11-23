package com.lambdarc.litememo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.lambdarc.litememo.data.local.model.MemoWithRefs
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoTrashDao : MemoDao {

    @Transaction
    @Query("SELECT * FROM memos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedMemosWithRefs(): Flow<List<MemoWithRefs>>

    @Query(
        """
        SELECT fileName FROM memo_images
        WHERE memoId IN (SELECT id FROM memos WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff)
        """
    )
    suspend fun getImageFileNamesForTrashedMemosDeletedAtOrBefore(cutoff: Long): List<String>

    @Query(
        "UPDATE memos SET deletedAt = MAX(:deletedAt, updatedAt) " +
            "WHERE id = :id AND deletedAt IS NULL"
    )
    suspend fun moveMemoToTrash(id: String, deletedAt: Long): Int

    @Query("UPDATE memos SET deletedAt = NULL WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun restoreMemoFromTrash(id: String): Int

    @Query("DELETE FROM memos WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun deleteMemoPermanently(id: String): Int

    @Query("DELETE FROM memos WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: Long)

    @Transaction
    suspend fun moveMemoToTrashIfUnchanged(expectedMemo: MemoWithRefs, deletedAt: Long): Boolean {
        val id = expectedMemo.memo.id
        if (!getActiveMemoWithRefs(id).isSameSnapshotAs(expectedMemo)) return false
        return moveMemoToTrash(id, deletedAt) > 0
    }

    @Transaction
    suspend fun deleteMemoPermanentlyAndCollectImageFileNames(id: String): List<String> {
        val fileNames = getImageFileNamesForMemo(id)
        val affected = deleteMemoPermanently(id)
        check(affected > 0) { "Memo not found or not in trash: $id" }
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
