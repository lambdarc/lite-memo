package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.QueueTagIdProvider
import com.lambdarc.litememo.domain.exception.DuplicateTagNameException
import com.lambdarc.litememo.domain.model.SaveTagCommand
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagColor
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.tagFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SaveTagUseCaseTest {

    @Test
    fun normalCreatePersistsAndReturnsGeneratedTag() = runTest {
        // Arrange
        val repository = FakeTagRepository()
        val useCase = saveTagUseCase(
            tagRepository = repository,
            tagIdProvider = QueueTagIdProvider(listOf(TagId("generated-tag"))),
            timeProvider = MutableTimeProvider(TimestampMillis(2000L))
        )
        val expected = tagFixture(id = "generated-tag", name = "Work", createdAt = 2000L)

        // Act
        // Normal: the returned and persisted tag have the generated id and current time.
        val tag = useCase(SaveTagCommand(name = TagName("Work"), color = TagColor(0xFF6750A4)))

        // Assert
        assertAll(
            { assertEquals(expected, tag) },
            { assertEquals(listOf(expected), repository.savedTags) }
        )
    }

    @Test
    fun normalUpdatePreservesIdentityAndCreationTime() = runTest {
        // Arrange
        val existing = tagFixture(id = "tag-1", createdAt = 1000L)
        val repository = FakeTagRepository(listOf(existing))
        val expected = existing.copy(name = TagName("New"), color = TagColor(0xFF006D3B))
        val useCase = saveTagUseCase(
            tagRepository = repository,
            tagIdProvider = QueueTagIdProvider(emptyList()),
            timeProvider = MutableTimeProvider(TimestampMillis(3000L))
        )

        // Act
        // Normal: updating preserves identity and creation time without generating an id.
        val tag =
            useCase(
                SaveTagCommand(
                    id = existing.id,
                    name = TagName("New"),
                    color = TagColor(0xFF006D3B)
                )
            )

        // Assert
        assertAll(
            { assertEquals(expected, tag) },
            { assertEquals(listOf(expected), repository.savedTags) }
        )
    }

    @Test
    fun errorMissingTagRejectsUpdateWithoutSaving() = runTest {
        // Arrange
        val repository = FakeTagRepository()
        val useCase = saveTagUseCase(tagRepository = repository)

        // Act & Assert
        // Error: a missing update target fails without creating a new tag.
        assertThrows<IllegalArgumentException> {
            useCase(
                SaveTagCommand(
                    id = TagId("client-id"),
                    name = TagName("New"),
                    color = TagColor(0xFF006D3B)
                )
            )
        }
        assertTrue(repository.savedTags.isEmpty())
    }

    @Test
    fun errorCreateRejectsDuplicateName() = runTest {
        // Arrange
        val existing = tagFixture(id = "tag-1", name = "Work")
        val repository = FakeTagRepository(listOf(existing))
        val useCase = saveTagUseCase(tagRepository = repository)

        // Act & Assert
        // Error: an existing name cannot be used for a new tag.
        assertThrows<DuplicateTagNameException> {
            useCase(SaveTagCommand(name = TagName("Work"), color = TagColor(0xFF6750A4)))
        }
        assertAll(
            { assertTrue(repository.savedTags.isEmpty()) },
            { assertEquals(listOf(existing), repository.currentTags()) }
        )
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
        val repository = FakeTagRepository(listOf(existing))
        val useCase = saveTagUseCase(
            tagRepository = repository,
            tagIdProvider = QueueTagIdProvider(listOf(TagId("new-tag")))
        )

        // Act
        // Boundary: name uniqueness stays case-sensitive.
        val tag = useCase(SaveTagCommand(name = TagName("work"), color = TagColor(0xFF6750A4)))

        // Assert
        assertAll(
            { assertEquals(TagName("work"), tag.name) },
            { assertEquals(listOf("Work", "work"), repository.currentTags().map { it.name.value }) }
        )
    }

    @Test
    fun boundaryInvokeThrowsWhenTrimmedNameAlreadyExists() = runTest {
        // Arrange
        val existing = tagFixture(id = "tag-1", name = "Work")
        val repository = FakeTagRepository(listOf(existing))
        val useCase = saveTagUseCase(tagRepository = repository)

        // Act & Assert
        // Boundary: the trimmed TagName value is what gets looked up.
        assertThrows<DuplicateTagNameException> {
            useCase(SaveTagCommand(name = TagName("  Work  "), color = TagColor(0xFF6750A4)))
        }
        assertAll(
            { assertTrue(repository.savedTags.isEmpty()) },
            { assertEquals(listOf(existing), repository.currentTags()) }
        )
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

    private class GetAllTagsFailingTagRepository(initialTags: List<Tag>) :
        TagRepository by FakeTagRepository(initialTags) {
        override suspend fun getAllTags(): List<Tag> =
            error("SaveTagUseCase must not load all tags.")
    }

    @Test
    fun errorDuplicateNameRejectsRenameWithoutChangingTags() = runTest {
        // Arrange
        val existing = tagFixture(id = "tag-1", name = "Work")
        val other = tagFixture(id = "tag-2", name = "Home")
        val repository = FakeTagRepository(listOf(existing, other))
        val useCase = saveTagUseCase(tagRepository = repository)

        // Act & Assert
        // Error: a conflicting rename preserves both tags and never persists an update.
        assertThrows<DuplicateTagNameException> {
            useCase(SaveTagCommand(id = existing.id, name = other.name, color = existing.color))
        }
        assertAll(
            { assertTrue(repository.savedTags.isEmpty()) },
            { assertEquals(listOf(existing, other), repository.currentTags()) }
        )
    }

    @Test
    fun errorSaveFailurePropagatesToCaller() = runTest {
        // Arrange
        val failure = IllegalStateException("Storage unavailable")
        val repository = object : TagRepository by FakeTagRepository() {
            override suspend fun saveTag(tag: Tag) = throw failure
        }
        val useCase = saveTagUseCase(tagRepository = repository)

        // Act & Assert
        // Error: persistence failure must not be reported as a successful save.
        val thrown = assertThrows<IllegalStateException> {
            useCase(SaveTagCommand(name = TagName("Work"), color = TagColor(0xFF6750A4)))
        }
        assertSame(failure, thrown)
    }

    @Test
    fun coroutineCancelledLookupDoesNotSaveOrGenerateId() = runTest {
        // Arrange
        val failure = CancellationException("Cancelled lookup")
        val delegate = FakeTagRepository()
        val repository = object : TagRepository by delegate {
            override suspend fun findTagByName(name: TagName) = throw failure
        }
        val useCase = saveTagUseCase(
            tagRepository = repository,
            tagIdProvider = QueueTagIdProvider(emptyList())
        )

        // Act & Assert
        // Coroutine: cancellation escapes lookup before a tag is created.
        val thrown = assertThrows<CancellationException> {
            useCase(SaveTagCommand(name = TagName("Work"), color = TagColor(0xFF6750A4)))
        }
        assertAll(
            { assertSame(failure, thrown) },
            { assertTrue(delegate.savedTags.isEmpty()) }
        )
    }

    private fun saveTagUseCase(
        tagRepository: TagRepository = FakeTagRepository(),
        tagIdProvider: QueueTagIdProvider = QueueTagIdProvider(),
        timeProvider: MutableTimeProvider = MutableTimeProvider()
    ) = SaveTagUseCase(
        tagRepository = tagRepository,
        tagIdProvider = tagIdProvider,
        currentTimeProvider = timeProvider
    )

}
