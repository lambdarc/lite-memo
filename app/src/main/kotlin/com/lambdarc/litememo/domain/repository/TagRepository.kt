package com.lambdarc.litememo.domain.repository

import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import kotlinx.coroutines.flow.Flow

interface TagRepository {

    fun observeTags(): Flow<List<Tag>>

    suspend fun getTag(id: TagId): Tag?

    suspend fun findTagByName(name: TagName): Tag?

    suspend fun getTagsByIds(ids: List<TagId>): List<Tag>

    suspend fun saveTag(tag: Tag)

    suspend fun deleteTag(id: TagId)

    suspend fun getAllTags(): List<Tag>

}
