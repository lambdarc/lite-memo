package com.lambdarc.litememo.domain

import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.model.MemoTrashUpdate
import com.lambdarc.litememo.domain.model.StagedMemoImport
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.ExportFileReference
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.MemoImportSessionToken
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.SearchQuery
import com.lambdarc.litememo.domain.model.value.TagColor
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.provider.MemoIdProvider
import com.lambdarc.litememo.domain.provider.TagIdProvider
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoImportArchiveRepository
import com.lambdarc.litememo.domain.repository.MemoImportRepository
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

fun memoFixture(
    id: String = "memo-1",
    title: String = "Title",
    body: String = "Body",
    createdAt: Long = 1000L,
    updatedAt: Long = createdAt,
    tagIds: List<TagId> = emptyList(),
    images: List<MemoImage> = emptyList(),
    isFavorite: Boolean = false,
    deletedAt: Long? = null
) = Memo(
    id = MemoId(id),
    title = MemoTitle(title),
    body = MemoBody(body),
    createdAt = TimestampMillis(createdAt),
    updatedAt = TimestampMillis(updatedAt),
    tagIds = tagIds,
    images = images,
    isFavorite = isFavorite,
    deletedAt = deletedAt?.let { TimestampMillis(it) }
)

fun memoImageFixture(id: String = "image-1", fileName: String = "image-1.jpg") = MemoImage(
    id = MemoImageId(id),
    fileName = MemoImageFileName(fileName)
)

fun memoSummaryFixture(
    id: String = "memo-1",
    title: String = "Title",
    body: String = "Body",
    isFavorite: Boolean = false
) = MemoSummary(
    id = MemoId(id),
    title = MemoTitle(title),
    body = MemoBody(body),
    isFavorite = isFavorite
)

fun tagFixture(
    id: String = "tag-1",
    name: String = "Tag",
    color: Long = 0xFF6750A4,
    createdAt: Long = 1000L
) = Tag(
    id = TagId(id),
    name = TagName(name),
    color = TagColor(color),
    createdAt = TimestampMillis(createdAt)
)

fun epochMillis(value: String): Long = Instant.parse(value).toEpochMilli()

data class TrashMoveRecord(val memoId: MemoId, val deletedAt: TimestampMillis)

class FakeMemoRepository(initialMemos: List<Memo> = emptyList()) : MemoRepository {

    private val memos = MutableStateFlow(initialMemos)
    val savedMemos = mutableListOf<Memo>()
    val activeBulkSaveExpectedIdBatches = mutableListOf<List<MemoId>>()
    val movedToTrash = mutableListOf<TrashMoveRecord>()
    val restoredIds = mutableListOf<MemoId>()
    val permanentlyDeletedIds = mutableListOf<MemoId>()
    val discardedIds = mutableListOf<MemoId>()
    val purgeCutoffs = mutableListOf<TimestampMillis>()

    override fun observeActiveMemos(): Flow<List<Memo>> =
        memos.map { list -> list.filter { it.deletedAt == null } }

    override fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummary>> = memos.map { list ->
        list.filter { it.deletedAt == null }
            .sortedWith(
                compareByDescending<Memo> { it.isFavorite }
                    .thenByDescending { it.updatedAt.value }
                    .thenByDescending { it.createdAt.value }
            )
            .take(limit)
            .map { memo ->
                MemoSummary(
                    id = memo.id,
                    title = memo.title,
                    body = memo.body,
                    isFavorite = memo.isFavorite
                )
            }
    }

    override fun observeActiveMemosBySearchQuery(query: SearchQuery): Flow<List<Memo>> =
        memos.map { list ->
            list.filter { memo ->
                val matchesQuery = memo.title.value.contains(query.value, ignoreCase = true) ||
                    memo.body.value.contains(query.value, ignoreCase = true)
                memo.deletedAt == null && matchesQuery
            }
        }

    override fun observeActiveMemosCreatedBetween(range: TimestampRange): Flow<List<Memo>> {
        if (range.isEmpty) return flowOf(emptyList())
        return memos.map { list ->
            list.filter { memo ->
                memo.deletedAt == null &&
                    memo.createdAt.value >= range.fromInclusive.value &&
                    memo.createdAt.value < range.toExclusive.value
            }
        }
    }

    override fun observeTrashedMemos(): Flow<List<Memo>> = memos.map { list ->
        list.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt?.value }
    }

    override suspend fun getActiveMemo(id: MemoId): Memo? =
        memos.value.firstOrNull { it.id == id && it.deletedAt == null }

    override suspend fun getActiveMemos(ids: List<MemoId>): List<Memo> {
        val activeMemosById = memos.value
            .filter { it.deletedAt == null }
            .associateBy { it.id }
        return ids.distinct().mapNotNull(activeMemosById::get)
    }

    override suspend fun saveMemo(memo: Memo) {
        savedMemos += memo
        memos.value = memos.value.filterNot { it.id == memo.id } + memo
    }

    override suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>) {
        if (writes.isEmpty()) return
        val updatedMemos = writes
            .filterIsInstance<ActiveMemoBulkWrite.Update>()
            .map { it.updatedMemo }
        require(updatedMemos.all { it.deletedAt == null }) {
            "Only active memos can be written through the active bulk write."
        }

        val currentMemos = this.memos.value
        val activeMemoById = currentMemos
            .filter { it.deletedAt == null }
            .associateBy { it.id }
        writes.forEach { write ->
            val current = checkNotNull(activeMemoById[write.memoId]) {
                "Memo not found or not active: ${write.memoId.value}"
            }
            check(current.updatedAt == write.expectedUpdatedAt) {
                "Memo was modified since it was read: ${write.memoId.value}"
            }
        }

        activeBulkSaveExpectedIdBatches += writes.map { it.memoId }
        savedMemos += updatedMemos
        val updatesById = updatedMemos.associateBy { it.id }
        this.memos.value = currentMemos.map { memo -> updatesById[memo.id] ?: memo }
    }

    override suspend fun moveMemoToTrash(id: MemoId, deletedAt: TimestampMillis) {
        movedToTrash += TrashMoveRecord(memoId = id, deletedAt = deletedAt)
        memos.value = memos.value.map { memo ->
            if (memo.id == id && memo.deletedAt == null) memo.copy(deletedAt = deletedAt) else memo
        }
    }

    override suspend fun moveMemosToTrash(updates: List<MemoTrashUpdate>) {
        require(updates.map { it.memoId }.toSet().size == updates.size) {
            "Duplicate memo IDs are not allowed."
        }
        val currentMemos = memos.value
        val activeMemoIds = currentMemos
            .filter { it.deletedAt == null }
            .mapTo(mutableSetOf()) { it.id }
        val applicableUpdates = updates.filter { it.memoId in activeMemoIds }

        movedToTrash += applicableUpdates.map { update ->
            TrashMoveRecord(memoId = update.memoId, deletedAt = update.deletedAt)
        }
        val updatesById = applicableUpdates.associate { it.memoId to it.deletedAt }
        memos.value = currentMemos.map { memo ->
            updatesById[memo.id]?.let { deletedAt -> memo.copy(deletedAt = deletedAt) } ?: memo
        }
    }

    override suspend fun restoreMemoFromTrash(id: MemoId) {
        restoredIds += id
        memos.value = memos.value.map { memo ->
            if (memo.id == id && memo.deletedAt != null) memo.copy(deletedAt = null) else memo
        }
    }

    override suspend fun restoreMemosFromTrash(ids: List<MemoId>) {
        require(ids.toSet().size == ids.size) {
            "Duplicate memo IDs are not allowed."
        }
        val currentMemos = memos.value
        val trashedMemoIds = currentMemos
            .filter { it.deletedAt != null }
            .mapTo(mutableSetOf()) { it.id }
        val restorableIds = ids.distinct().filter { it in trashedMemoIds }

        restoredIds += restorableIds
        val restoredIdSet = restorableIds.toSet()
        memos.value = currentMemos.map { memo ->
            if (memo.id in restoredIdSet) memo.copy(deletedAt = null) else memo
        }
    }

    override suspend fun deleteMemoPermanently(id: MemoId) {
        val memo = requireNotNull(memos.value.firstOrNull { it.id == id && it.deletedAt != null }) {
            "Memo not found or not in trash: ${id.value}"
        }
        permanentlyDeletedIds += id
        memos.value = memos.value.filterNot { it.id == memo.id }
    }

    override suspend fun deleteMemosPermanently(ids: List<MemoId>) {
        require(ids.toSet().size == ids.size) {
            "Duplicate memo IDs are not allowed."
        }
        val currentMemos = memos.value
        val trashedMemoIds = currentMemos
            .filter { it.deletedAt != null }
            .mapTo(mutableSetOf()) { it.id }
        val deletableIds = ids.distinct().filter { it in trashedMemoIds }

        permanentlyDeletedIds += deletableIds
        val deletedIdSet = deletableIds.toSet()
        memos.value = currentMemos.filterNot { it.id in deletedIdSet }
    }

    override suspend fun discardMemo(id: MemoId) {
        discardedIds += id
        memos.value = memos.value.filterNot { it.id == id }
    }

    override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: TimestampMillis) {
        purgeCutoffs += cutoff
        memos.value = memos.value.filterNot { memo ->
            val deletedAt = memo.deletedAt ?: return@filterNot false
            deletedAt.value <= cutoff.value
        }
    }

    override suspend fun getAllActiveMemos(): List<Memo> =
        memos.value.filter { it.deletedAt == null }

    fun currentMemos(): List<Memo> = memos.value

}

class FakeMemoImportRepository : MemoImportRepository {

    val importedData = mutableListOf<ExportData>()

    override suspend fun import(data: ExportData) {
        importedData += data
    }

}

class FakeMemoImportArchiveRepository(private val stagedData: ExportData? = null) :
    MemoImportArchiveRepository {

    val stagedReferences = mutableListOf<ExportFileReference>()
    val completedTokens = mutableListOf<MemoImportSessionToken>()
    val rolledBackTokens = mutableListOf<MemoImportSessionToken>()
    var stageError: Throwable? = null
    var deleteUnreferencedCallCount = 0

    override suspend fun stageImportImages(reference: ExportFileReference): StagedMemoImport {
        stagedReferences += reference
        stageError?.let { throw it }
        return StagedMemoImport(token = TOKEN, data = requireNotNull(stagedData))
    }

    override suspend fun completeStagedImport(token: MemoImportSessionToken) {
        completedTokens += token
    }

    override suspend fun rollbackStagedImport(token: MemoImportSessionToken) {
        rolledBackTokens += token
    }

    override suspend fun deleteUnreferencedImportImages() {
        deleteUnreferencedCallCount++
    }

    companion object {
        val TOKEN = MemoImportSessionToken("session-1")
    }

}

class FakeTagRepository(initialTags: List<Tag> = emptyList()) : TagRepository {

    private val tags = MutableStateFlow(initialTags)
    val savedTags = mutableListOf<Tag>()
    val deletedIds = mutableListOf<TagId>()

    override fun observeTags(): Flow<List<Tag>> = tags

    override suspend fun getTag(id: TagId): Tag? = tags.value.firstOrNull { it.id == id }

    override suspend fun findTagByName(name: TagName): Tag? =
        tags.value.firstOrNull { it.name == name }

    override suspend fun getTagsByIds(ids: List<TagId>): List<Tag> =
        tags.value.filter { it.id in ids }

    override suspend fun saveTag(tag: Tag) {
        savedTags += tag
        tags.value = tags.value.filterNot { it.id == tag.id } + tag
    }

    override suspend fun deleteTag(id: TagId) {
        deletedIds += id
        tags.value = tags.value.filterNot { it.id == id }
    }

    override suspend fun getAllTags(): List<Tag> = tags.value

    fun currentTags(): List<Tag> = tags.value

}

class FakeMemoImageStore : MemoImageStore {

    val savedSources = mutableListOf<ImageSourceReference>()
    val deletedFileNames = mutableListOf<MemoImageFileName>()
    var saveError: Throwable? = null
    var deleteError: Throwable? = null
    private var nextImageNumber = 1

    override suspend fun saveImage(source: ImageSourceReference): MemoImage {
        saveError?.let { throw it }
        savedSources += source
        val number = nextImageNumber++
        return memoImageFixture(id = "image-$number", fileName = "image-$number.jpg")
    }

    override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
        deleteError?.let { throw it }
        deletedFileNames += fileNames
    }

    override fun resolveImagePath(fileName: MemoImageFileName): String = "/images/${fileName.value}"

}

class QueueMemoIdProvider(ids: List<MemoId> = listOf(MemoId("memo-1"))) : MemoIdProvider {

    private val memoIds = ids.toMutableList()

    override fun newMemoId(): MemoId {
        if (memoIds.isEmpty()) {
            error("No more MemoId available in QueueMemoIdProvider.")
        }

        return memoIds.removeAt(0)
    }
}

class QueueTagIdProvider(ids: List<TagId> = listOf(TagId("tag-1"))) : TagIdProvider {

    private val tagIds = ids.toMutableList()

    override fun newTagId(): TagId {
        if (tagIds.isEmpty()) {
            error("No more TagId available in QueueTagIdProvider.")
        }

        return tagIds.removeAt(0)
    }

}

class MutableTimeProvider(var current: TimestampMillis = TimestampMillis(1000L)) :
    CurrentTimeProvider {

    override fun now(): TimestampMillis = current

}
