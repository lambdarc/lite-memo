package com.appvoyager.litememo.data.local.dao

import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.MemoTagRefEntity
import com.appvoyager.litememo.data.local.model.MemoSummaryProjection
import com.appvoyager.litememo.data.local.model.MemoVersionProjection
import com.appvoyager.litememo.data.local.model.MemoWithRefs
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.fail

internal class RecordingMemoBulkDao(
    private val failOnWrite: Boolean = false,
    private val activeMemoIds: Set<String>? = null,
    private val trashedMemoIds: Set<String>? = null,
    private val activeMemosById: Map<String, MemoWithRefs> = emptyMap()
) : MemoBulkDao {

    val calls = mutableListOf<String>()
    val activeMemoIdReads = mutableListOf<List<String>>()
    val imageFileNameBatchSizes = mutableListOf<Int>()
    val trashedMemoIdBatchSizes = mutableListOf<Int>()
    val restoredMemoBatchSizes = mutableListOf<Int>()
    private val emptyMemoFlow = flowOf(emptyList<MemoWithRefs>())

    override fun observeActiveMemosWithRefs() = emptyMemoFlow

    override fun observeRecentActiveMemos(limit: Int) = flowOf(emptyList<MemoSummaryProjection>())

    override fun observeActiveMemosWithRefsBySearchPattern(pattern: String) = emptyMemoFlow

    override fun observeActiveMemosWithRefsCreatedBetween(fromMillis: Long, toMillis: Long) =
        emptyMemoFlow

    override suspend fun getActiveMemoWithRefs(id: String): MemoWithRefs? = null

    override suspend fun getActiveMemosWithRefsBatch(ids: List<String>): List<MemoWithRefs> =
        ids.asReversed().mapNotNull(activeMemosById::get)

    override suspend fun getActiveMemoIdsBatch(ids: List<String>): List<String> {
        activeMemoIdReads += ids
        return ids.filter { id -> activeMemoIds?.contains(id) ?: true }
    }

    override suspend fun getActiveMemoVersionsBatch(
        ids: List<String>
    ): List<MemoVersionProjection> = emptyList()

    override suspend fun getTrashedMemoIdsBatch(ids: List<String>): List<String> {
        trashedMemoIdBatchSizes += ids.size
        return ids.filter { id -> trashedMemoIds?.contains(id) ?: true }
    }

    override suspend fun getImageRefsForMemosBatch(memoIds: List<String>): List<MemoImageEntity> =
        emptyList()

    override fun observeTrashedMemosWithRefs() = emptyMemoFlow

    override suspend fun upsertMemo(memo: MemoEntity) {
        if (failOnWrite) fail<Nothing>("upsertMemo should not be called.")
        calls += "upsertMemo:${memo.id}"
    }

    override suspend fun insertTagRefs(tagRefs: List<MemoTagRefEntity>) {
        if (failOnWrite) fail<Nothing>("insertTagRefs should not be called.")
        val refs = tagRefs.joinToString(",") { "${it.memoId}:${it.tagId}:${it.position}" }
        calls += "insertTagRefs:$refs"
    }

    override suspend fun insertImageRefs(imageRefs: List<MemoImageEntity>) {
        if (failOnWrite) fail<Nothing>("insertImageRefs should not be called.")
        val refs = imageRefs.joinToString(",") {
            "${it.id}:${it.memoId}:${it.fileName}:${it.position}"
        }
        calls += "insertImageRefs:$refs"
    }

    override suspend fun deleteTagRefsForMemo(memoId: String) {
        if (failOnWrite) fail<Nothing>("deleteTagRefsForMemo should not be called.")
        calls += "deleteTagRefsForMemo:$memoId"
    }

    override suspend fun deleteImageRefsForMemo(memoId: String) {
        if (failOnWrite) fail<Nothing>("deleteImageRefsForMemo should not be called.")
        calls += "deleteImageRefsForMemo:$memoId"
    }

    override suspend fun getImageFileNamesForMemo(memoId: String): List<String> = emptyList()

    override suspend fun getImageFileNamesForMemos(memoIds: List<String>): List<String> {
        imageFileNameBatchSizes += memoIds.size
        return emptyList()
    }

    override suspend fun findReferencedImageFileNames(fileNames: List<String>): List<String> =
        emptyList()

    override suspend fun getImageFileNamesForTrashedMemosDeletedAtOrBefore(
        cutoff: Long
    ): List<String> = emptyList()

    override suspend fun moveMemoToTrash(id: String, deletedAt: Long): Int {
        if (failOnWrite) fail<Nothing>("moveMemoToTrash should not be called.")
        calls += "moveMemoToTrash:$id:$deletedAt"
        return 1
    }

    override suspend fun moveMemosToTrashBatch(ids: List<String>, deletedAt: Long): Int {
        if (failOnWrite) fail<Nothing>("moveMemosToTrashBatch should not be called.")
        calls += "moveMemosToTrashBatch:${ids.joinToString(",")}:$deletedAt"
        return ids.size
    }

    override suspend fun restoreMemoFromTrash(id: String): Int = 1

    override suspend fun restoreMemosFromTrashBatch(ids: List<String>): Int {
        restoredMemoBatchSizes += ids.size
        return ids.size
    }

    override suspend fun deleteMemoPermanently(id: String): Int = 1

    override suspend fun deleteMemosPermanentlyBatch(ids: List<String>): Int = ids.size

    override suspend fun discardMemo(id: String): Int = 1

    override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: Long) = Unit

    override suspend fun getAllActiveMemosWithRefs(): List<MemoWithRefs> = emptyList()
}
