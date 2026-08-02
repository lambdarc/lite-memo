package com.appvoyager.litememo.data.repository

import com.appvoyager.litememo.data.local.dao.TagDao
import com.appvoyager.litememo.data.local.entity.TagEntity
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.tagFixture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RoomTagRepositoryTest {

    @Test
    fun observeTagsReturnsDomainTagsFromDao() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1")))
        val repository = RoomTagRepository(dao)

        // Act
        val tags = repository.observeTags().first()

        // Assert
        assertEquals(listOf(TagId("tag-1")), tags.map { it.id })
    }

    @Test
    fun getTagReturnsNullWhenDaoReturnsNull() = runTest {
        // Arrange
        val repository = RoomTagRepository(FakeTagDao())

        // Act
        val tag = repository.getTag(TagId("missing"))

        // Assert
        assertNull(tag)
    }

    @Test
    fun getTagsByIdsReturnsTagsInRequestedOrder() = runTest {
        // Arrange
        val dao = FakeTagDao(
            tags = listOf(
                tagEntity(id = "tag-1"),
                tagEntity(id = "tag-2")
            )
        )
        val repository = RoomTagRepository(dao)

        // Act
        val tags = repository.getTagsByIds(listOf(TagId("tag-2"), TagId("tag-1")))

        // Assert
        assertEquals(listOf(TagId("tag-2"), TagId("tag-1")), tags.map { it.id })
    }

    @Test
    fun getTagsByIdsReturnsEmptyListWhenIdsAreEmpty() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1")))
        val repository = RoomTagRepository(dao)

        // Act
        val tags = repository.getTagsByIds(emptyList())

        // Assert
        assertEquals(emptyList<Tag>(), tags)
    }

    @Test
    fun getTagsByIdsDoesNotCallDaoWhenIdsAreEmpty() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1")))
        val repository = RoomTagRepository(dao)

        // Act
        // Boundary/Interaction: an empty id list skips the DAO query entirely.
        repository.getTagsByIds(emptyList())

        // Assert
        assertEquals(0, dao.getTagsByIdsCallCount)
    }

    @Test
    fun saveTagWritesTagEntityToDao() = runTest {
        // Arrange
        val dao = FakeTagDao()
        val repository = RoomTagRepository(dao)

        // Act
        repository.saveTag(tagFixture(id = "tag-1", name = "Work"))

        // Assert
        assertEquals("tag-1", dao.savedTag?.id)
    }

    @Test
    fun saveTagInsertsWhenTagIdIsNew() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1", name = "Work")))
        val repository = RoomTagRepository(dao)

        // Act
        repository.saveTag(tagFixture(id = "tag-2", name = "Home"))

        // Assert
        assertAll(
            { assertEquals(listOf("tag-2"), dao.insertedTags.map { it.id }) },
            { assertEquals(emptyList<String>(), dao.updatedTags.map { it.id }) }
        )
    }

    @Test
    fun saveTagUpdatesWhenTagIdAlreadyExists() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1", name = "Work")))
        val repository = RoomTagRepository(dao)

        // Act
        repository.saveTag(tagFixture(id = "tag-1", name = "Renamed"))

        // Assert
        assertAll(
            { assertEquals(emptyList<String>(), dao.insertedTags.map { it.id }) },
            { assertEquals(listOf("tag-1"), dao.updatedTags.map { it.id }) },
            { assertEquals(listOf("Renamed"), dao.updatedTags.map { it.name }) }
        )
    }

    @Test
    fun deleteTagDelegatesTagIdValueToDao() = runTest {
        // Arrange
        val dao = FakeTagDao()
        val repository = RoomTagRepository(dao)

        // Act
        repository.deleteTag(TagId("tag-1"))

        // Assert
        assertEquals("tag-1", dao.deletedTagId)
    }

    @Test
    fun findTagByNameReturnsMatchingTag() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1", name = "Work")))
        val repository = RoomTagRepository(dao)

        // Act
        val tag = repository.findTagByName(TagName("Work"))

        // Assert
        assertEquals(TagId("tag-1"), tag?.id)
    }

    @Test
    fun findTagByNameReturnsNullWhenNameIsAbsent() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1", name = "Work")))
        val repository = RoomTagRepository(dao)

        // Act
        val tag = repository.findTagByName(TagName("Home"))

        // Assert
        assertNull(tag)
    }

    @Test
    fun boundaryFindTagByNameDoesNotMatchDifferentLetterCase() = runTest {
        // Arrange
        val dao = FakeTagDao(tags = listOf(tagEntity(id = "tag-1", name = "Work")))
        val repository = RoomTagRepository(dao)

        // Act
        val tag = repository.findTagByName(TagName("work"))

        // Assert
        assertNull(tag)
    }

    private fun tagEntity(id: String, name: String = "Tag") = TagEntity(
        id = id,
        name = name,
        colorArgb = 0xFF6750A4,
        createdAt = 1000L
    )

    private class FakeTagDao(tags: List<TagEntity> = emptyList()) : TagDao {

        private val tags = MutableStateFlow(tags)
        var getTagsByIdsCallCount = 0
        var savedTag: TagEntity? = null
        var savedTags: List<TagEntity> = emptyList()
        var insertedTags: List<TagEntity> = emptyList()
        var updatedTags: List<TagEntity> = emptyList()
        var deletedTagId: String? = null

        override fun observeTags(): Flow<List<TagEntity>> = tags

        override suspend fun getTag(id: String): TagEntity? = tags.value.firstOrNull { it.id == id }

        override suspend fun getTagsByIds(ids: List<String>): List<TagEntity> {
            getTagsByIdsCallCount += 1
            return tags.value.filter { it.id in ids }
        }

        override suspend fun findTagByName(name: String): TagEntity? =
            tags.value.firstOrNull { it.name == name }

        override suspend fun findTagsByNames(names: List<String>): List<TagEntity> =
            tags.value.filter { it.name in names }

        override suspend fun insertTags(tags: List<TagEntity>) {
            insertedTags = tags
            savedTags = tags
            savedTag = tags.lastOrNull()
        }

        override suspend fun updateTags(tags: List<TagEntity>) {
            updatedTags = tags
            savedTags = tags
            savedTag = tags.lastOrNull()
        }

        override suspend fun deleteTag(id: String) {
            deletedTagId = id
        }

        override suspend fun getAllTags(): List<TagEntity> = tags.value
    }

}
