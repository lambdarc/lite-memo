package com.lambdarc.litememo.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.lambdarc.litememo.data.local.dao.SQLITE_QUERY_PARAMETER_BATCH_SIZE
import com.lambdarc.litememo.data.local.dao.TagDao
import com.lambdarc.litememo.data.mapper.toDomain
import com.lambdarc.litememo.data.mapper.toEntity
import com.lambdarc.litememo.domain.exception.DuplicateTagNameException
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomTagRepository @Inject constructor(private val tagDao: TagDao) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeTags().map { tags -> tags.map { it.toDomain() } }

    override suspend fun getTag(id: TagId): Tag? = tagDao.getTag(id.value)?.toDomain()

    override suspend fun findTagByName(name: TagName): Tag? =
        tagDao.findTagByName(name.value)?.toDomain()

    override suspend fun getTagsByIds(ids: List<TagId>): List<Tag> {
        if (ids.isEmpty()) return emptyList()

        val tagsById = ids.map { it.value }
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMap { batch -> tagDao.getTagsByIds(batch) }
            .associateBy { it.id }
        return ids.mapNotNull { id -> tagsById[id.value]?.toDomain() }
    }

    override suspend fun saveTag(tag: Tag) {
        try {
            tagDao.insertOrUpdateAllTags(listOf(tag.toEntity()))
        } catch (_: SQLiteConstraintException) {
            throw DuplicateTagNameException(tag.name)
        }
    }

    override suspend fun deleteTag(id: TagId) {
        tagDao.deleteTag(id.value)
    }

    override suspend fun getAllTags(): List<Tag> = tagDao.getAllTags().map { it.toDomain() }

}
