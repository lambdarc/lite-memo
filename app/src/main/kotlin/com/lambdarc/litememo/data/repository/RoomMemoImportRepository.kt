package com.lambdarc.litememo.data.repository

import androidx.room.withTransaction
import com.lambdarc.litememo.data.local.LiteMemoDatabase
import com.lambdarc.litememo.data.local.dao.MemoDao
import com.lambdarc.litememo.data.local.dao.TagDao
import com.lambdarc.litememo.data.local.entity.MemoEntity
import com.lambdarc.litememo.data.local.entity.TagEntity
import com.lambdarc.litememo.data.mapper.toEntity
import com.lambdarc.litememo.data.mapper.toImageRefsByMemoId
import com.lambdarc.litememo.data.mapper.toTagRefsByMemoId
import com.lambdarc.litememo.data.util.deleteImageFiles
import com.lambdarc.litememo.data.util.requireNoDuplicateIds
import com.lambdarc.litememo.domain.exception.ImportTagNameConflictException
import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoImportRepository
import javax.inject.Inject

private const val NAME_QUERY_CHUNK_SIZE = 900

class RoomMemoImportRepository @Inject constructor(
    private val memoDao: MemoDao,
    private val tagDao: TagDao,
    private val database: LiteMemoDatabase,
    private val memoImageStore: MemoImageStore
) : MemoImportRepository {

    override suspend fun import(data: ExportData) {
        data.tags.requireNoDuplicateIds(label = "tag") { it.id }
        data.memos.requireNoDuplicateIds(label = "memo") { it.id }

        val tagEntities: List<TagEntity> = data.tags.map { it.toEntity() }
        val memoEntities: List<MemoEntity> = data.memos.map { it.toEntity() }
        val tagRefsByMemoId = data.memos.toTagRefsByMemoId()
        val imageRefsByMemoId = data.memos.toImageRefsByMemoId()

        val removedFileNames = database.withTransaction {
            val conflictingTagNames = findConflictingTagNames(data)
            if (conflictingTagNames.isNotEmpty()) {
                throw ImportTagNameConflictException(conflictingTagNames)
            }

            tagDao.insertOrUpdateAllTags(tagEntities)
            memoDao.upsertAllMemosWithRefsAndCollectRemovedFileNames(
                memoEntities,
                tagRefsByMemoId,
                imageRefsByMemoId
            )
        }
        memoImageStore.deleteImageFiles(removedFileNames)
    }

    private suspend fun findConflictingTagNames(data: ExportData): List<TagName> {
        if (data.tags.isEmpty()) return emptyList()

        val importedTagIds = data.tags.mapTo(mutableSetOf()) { it.id.value }
        return data.tags.map { it.name.value }
            .distinct()
            .chunked(NAME_QUERY_CHUNK_SIZE)
            .flatMap { names -> tagDao.findTagsByNames(names) }
            .filterNot { it.id in importedTagIds }
            .map { TagName(it.name) }
            .sortedBy { it.value }
    }

}
