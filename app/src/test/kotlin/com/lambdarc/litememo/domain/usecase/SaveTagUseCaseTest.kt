package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.QueueTagIdProvider
import com.lambdarc.litememo.domain.model.SaveTagCommand
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagColor
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.tagFixture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SaveTagUseCaseTest {

    @Test
    fun invokeCreatesTagWithGeneratedId() = runTest {
        // Arrange
        val useCase =
            saveTagUseCase(tagIdProvider = QueueTagIdProvider(listOf(TagId("generated-tag"))))

        // Act
        val tag = useCase(SaveTagCommand(name = TagName("Work"), color = TagColor(0xFF6750A4)))

        // Assert
        assertEquals(TagId("generated-tag"), tag.id)
    }

    @Test
    fun invokeCreatesTagWithCurrentTime() = runTest {
        // Arrange
        val useCase = saveTagUseCase(timeProvider = MutableTimeProvider(TimestampMillis(2000L)))

        // Act
        val tag = useCase(SaveTagCommand(name = TagName("Work"), color = TagColor(0xFF6750A4)))

        // Assert
        assertEquals(TimestampMillis(2000L), tag.createdAt)
    }

    @Test
    fun invokePreservesCreatedAtWhenUpdatingExistingTag() = runTest {
        // Arrange
        val existing = tagFixture(id = "tag-1", createdAt = 1000L)
        val useCase = saveTagUseCase(
            tagRepository = FakeTagRepository(listOf(existing)),
            timeProvider = MutableTimeProvider(TimestampMillis(3000L))
        )

        // Act
        val tag =
            useCase(
                SaveTagCommand(
                    id = existing.id,
                    name = TagName("New"),
                    color = TagColor(0xFF006D3B)
                )
            )

        // Assert
        assertEquals(TimestampMillis(1000L), tag.createdAt)
    }

    @Test
    fun invokeThrowsWhenTagIdDoesNotExist() {
        // Arrange
        val useCase = saveTagUseCase()

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    SaveTagCommand(
                        id = TagId("client-id"),
                        name = TagName("New"),
                        color = TagColor(0xFF006D3B)
                    )
                )
            }
        }
    }

    @Test
    fun invokeDoesNotSaveTagWhenTagIdDoesNotExist() = runTest {
        // Arrange
        val repository = FakeTagRepository()
        val useCase = saveTagUseCase(tagRepository = repository)

        // Act
        try {
            useCase(
                SaveTagCommand(
                    id = TagId("client-id"),
                    name = TagName("New"),
                    color = TagColor(0xFF006D3B)
                )
            )
        } catch (_: IllegalArgumentException) {
        }

        // Assert
        assertEquals(emptyList<Any>(), repository.savedTags)
    }

    @Test
    fun invokeThrowsWhenTagNameAlreadyExists() {
        // Arrange
        val existing = tagFixture(id = "tag-1", name = "Work")
        val useCase = saveTagUseCase(tagRepository = FakeTagRepository(listOf(existing)))

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(SaveTagCommand(name = TagName("Work"), color = TagColor(0xFF6750A4)))
            }
        }
    }

    @Test
    fun invokeAllowsSameNameWhenUpdatingSameTag() = runTest {
        // Arrange
        val existing = tagFixture(id = "tag-1", name = "Work")
        val useCase = saveTagUseCase(tagRepository = FakeTagRepository(listOf(existing)))

        // Act
        val tag = useCase(
            SaveTagCommand(
                id = existing.id,
                name = TagName("Work"),
                color = TagColor(0xFF006D3B)
            )
        )

        // Assert
        assertEquals(existing.id, tag.id)
    }

    @Test
    fun boundaryInvokeAllowsSameNameWithDifferentLetterCase() = runTest {
        // Arrange
        val existing = tagFixture(id = "tag-1", name = "Work")
        val useCase = saveTagUseCase(tagRepository = FakeTagRepository(listOf(existing)))

        // Act
        // Boundary: name uniqueness stays case-sensitive.
        val tag = useCase(SaveTagCommand(name = TagName("work"), color = TagColor(0xFF6750A4)))

        // Assert
        assertEquals(TagName("work"), tag.name)
    }

    @Test
    fun boundaryInvokeThrowsWhenTrimmedNameAlreadyExists() {
        // Arrange
        val existing = tagFixture(id = "tag-1", name = "Work")
        val useCase = saveTagUseCase(tagRepository = FakeTagRepository(listOf(existing)))

        // Act & Assert
        // Boundary: the trimmed TagName value is what gets looked up.
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(SaveTagCommand(name = TagName("  Work  "), color = TagColor(0xFF6750A4)))
            }
        }
    }

    @Test
    fun interactionInvokeDetectsDuplicateWithoutLoadingAllTags() = runTest {
        // Arrange
        val repository = GetAllTagsFailingTagRepository(listOf(tagFixture(id = "tag-1")))
        val useCase = SaveTagUseCase(
            tagRepository = repository,
            tagIdProvider = QueueTagIdProvider(listOf(TagId("generated-tag"))),
            currentTimeProvider = MutableTimeProvider()
        )

        // Act
        // Interaction: duplicate detection uses the name query, never a full tag load.
        val tag = useCase(SaveTagCommand(name = TagName("Other"), color = TagColor(0xFF6750A4)))

        // Assert
        assertEquals(TagId("generated-tag"), tag.id)
    }

    private class GetAllTagsFailingTagRepository(initialTags: List<Tag>) : TagRepository {

        private val repository = FakeTagRepository(initialTags)

        override fun observeTags(): Flow<List<Tag>> = repository.observeTags()

        override suspend fun getTag(id: TagId): Tag? = repository.getTag(id)

        override suspend fun findTagByName(name: TagName): Tag? = repository.findTagByName(name)

        override suspend fun getTagsByIds(ids: List<TagId>): List<Tag> =
            repository.getTagsByIds(ids)

        override suspend fun saveTag(tag: Tag) = repository.saveTag(tag)

        override suspend fun deleteTag(id: TagId) = repository.deleteTag(id)

        override suspend fun getAllTags(): List<Tag> =
            error("SaveTagUseCase must not load all tags.")
    }

    private fun saveTagUseCase(
        tagRepository: FakeTagRepository = FakeTagRepository(),
        tagIdProvider: QueueTagIdProvider = QueueTagIdProvider(),
        timeProvider: MutableTimeProvider = MutableTimeProvider()
    ) = SaveTagUseCase(
        tagRepository = tagRepository,
        tagIdProvider = tagIdProvider,
        currentTimeProvider = timeProvider
    )

}
