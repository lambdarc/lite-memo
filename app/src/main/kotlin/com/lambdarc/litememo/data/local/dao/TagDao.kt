package com.lambdarc.litememo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lambdarc.litememo.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY createdAt ASC, id ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTag(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id IN (:ids)")
    suspend fun getTagsByIds(ids: List<String>): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE name IN (:names)")
    suspend fun findTagsByNames(names: List<String>): List<TagEntity>

    @Insert
    suspend fun insertTags(tags: List<TagEntity>)

    @Update
    suspend fun updateTags(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)

    @Query("SELECT * FROM tags ORDER BY createdAt ASC, id ASC")
    suspend fun getAllTags(): List<TagEntity>

    @Transaction
    suspend fun insertOrUpdateAllTags(tags: List<TagEntity>) {
        if (tags.isEmpty()) return

        val existingIds = tags.map { it.id }
            .chunked(SQLITE_QUERY_PARAMETER_BATCH_SIZE)
            .flatMapTo(mutableSetOf()) { ids -> getTagsByIds(ids).map { it.id } }
        val (existing, added) = tags.partition { it.id in existingIds }
        if (added.isNotEmpty()) insertTags(added)
        if (existing.isNotEmpty()) updateTags(existing)
    }

}
