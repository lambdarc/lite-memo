package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.QueueMemoIdProvider
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.SaveMemoCommand
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.tagFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SaveMemoUseCaseTest {

    @Test
    fun invokeCreatesMemoWithGeneratedId() = runTest {
        // Arrange
        val useCase =
            saveMemoUseCase(memoIdProvider = QueueMemoIdProvider(listOf(MemoId("generated-id"))))

        // Act
        val memo = useCase(SaveMemoCommand(title = MemoTitle("Title"), body = MemoBody("Body")))

        // Assert
        assertEquals(MemoId("generated-id"), memo.id)
    }

    @Test
    fun invokeCreatesMemoWithMatchingCreatedAtAndUpdatedAt() = runTest {
        // Arrange
        val useCase = saveMemoUseCase(timeProvider = MutableTimeProvider(TimestampMillis(2000L)))

        // Act
        val memo = useCase(SaveMemoCommand(title = MemoTitle("Title"), body = MemoBody("Body")))

        // Assert
        assertEquals(memo.createdAt, memo.updatedAt)
    }

    @Test
    fun invokePreservesCreatedAtWhenUpdatingExistingMemo() = runTest {
        // Arrange
        val existing = memoFixture(id = "memo-1", createdAt = 1000L, updatedAt = 1500L)
        val useCase = saveMemoUseCase(
            memoRepository = FakeMemoRepository(listOf(existing)),
            timeProvider = MutableTimeProvider(TimestampMillis(3000L))
        )

        // Act
        val memo =
            useCase(
                SaveMemoCommand(
                    id = existing.id,
                    title = MemoTitle("New"),
                    body = MemoBody("Body")
                )
            )

        // Assert
        assertEquals(TimestampMillis(1000L), memo.createdAt)
    }

    @Test
    fun invokeUpdatesUpdatedAtWhenUpdatingExistingMemo() = runTest {
        // Arrange
        val existing = memoFixture(id = "memo-1", createdAt = 1000L, updatedAt = 1500L)
        val useCase = saveMemoUseCase(
            memoRepository = FakeMemoRepository(listOf(existing)),
            timeProvider = MutableTimeProvider(TimestampMillis(3000L))
        )

        // Act
        val memo =
            useCase(
                SaveMemoCommand(
                    id = existing.id,
                    title = MemoTitle("New"),
                    body = MemoBody("Body")
                )
            )

        // Assert
        assertEquals(TimestampMillis(3000L), memo.updatedAt)
    }

    @Test
    fun invokeKeepsExistingUpdatedAtWhenCurrentTimeIsEarlierThanUpdatedAt() = runTest {
        // Arrange
        val existing = memoFixture(id = "memo-1", createdAt = 1000L, updatedAt = 5000L)
        val useCase = saveMemoUseCase(
            memoRepository = FakeMemoRepository(listOf(existing)),
            timeProvider = MutableTimeProvider(TimestampMillis(3000L))
        )

        // Act
        val memo =
            useCase(
                SaveMemoCommand(
                    id = existing.id,
                    title = MemoTitle("New"),
                    body = MemoBody("Body")
                )
            )

        // Assert
        assertEquals(TimestampMillis(5000L), memo.updatedAt)
    }

    @Test
    fun normalInvokeCreatesMemoWithCommandIdWhenMemoIdDoesNotExist() = runTest {
        // Arrange
        val useCase = saveMemoUseCase()

        // Act
        // Normal: a missing command id is treated as a stable new memo id.
        val memo = useCase(
            SaveMemoCommand(
                id = MemoId("missing-id"),
                title = MemoTitle("Title"),
                body = MemoBody("Body")
            )
        )

        // Assert
        assertEquals(MemoId("missing-id"), memo.id)
    }

    @Test
    fun invokeThrowsWhenTitleAndBodyAreBlank() {
        // Arrange
        val useCase = saveMemoUseCase()

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(SaveMemoCommand(title = MemoTitle(" "), body = MemoBody(" ")))
            }
        }
    }

    @Test
    fun invokeDoesNotSaveMemoWhenTitleAndBodyAreBlank() = runTest {
        // Arrange
        val repository = FakeMemoRepository()
        val useCase = saveMemoUseCase(memoRepository = repository)

        // Act
        try {
            useCase(SaveMemoCommand(title = MemoTitle(" "), body = MemoBody(" ")))
        } catch (_: IllegalArgumentException) {
        }

        // Assert
        assertEquals(emptyList<Any>(), repository.savedMemos)
    }

    @Test
    fun normalInvokeSavesMemoWithCommandImages() = runTest {
        // Arrange
        val repository = FakeMemoRepository()
        val images = listOf(memoImageFixture())
        val useCase = saveMemoUseCase(memoRepository = repository)

        // Act
        // Normal: command images are preserved on the saved memo.
        useCase(
            SaveMemoCommand(
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                images = images
            )
        )

        // Assert
        assertEquals(images, repository.savedMemos.single().images)
    }

    @Test
    fun normalInvokeSavesImageOnlyMemoWhenTitleAndBodyAreBlank() = runTest {
        // Arrange
        val repository = FakeMemoRepository()
        val images = listOf(memoImageFixture())
        val useCase = saveMemoUseCase(memoRepository = repository)

        // Act
        // Normal: an attached image counts as memo content.
        useCase(
            SaveMemoCommand(
                title = MemoTitle(" "),
                body = MemoBody(" "),
                images = images
            )
        )

        // Assert
        assertEquals(images, repository.savedMemos.single().images)
    }

    @Test
    fun normalInvokeSavesMemoWithDistinctImagesWhenDuplicateImageIdsAreProvided() = runTest {
        // Arrange
        val image = memoImageFixture()
        val useCase = saveMemoUseCase()

        // Act
        // Normal: duplicate image ids are normalized like duplicate tag ids.
        val memo = useCase(
            SaveMemoCommand(
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                images = listOf(image, image)
            )
        )

        // Assert
        assertEquals(listOf(image), memo.images)
    }

    @Test
    fun normalInvokeReplacesExistingImagesWithCommandImagesWhenUpdatingMemo() = runTest {
        // Arrange
        val oldImage = memoImageFixture(id = "image-old", fileName = "old.jpg")
        val newImage = memoImageFixture(id = "image-new", fileName = "new.jpg")
        val existing = memoFixture(id = "memo-1", images = listOf(oldImage))
        val repository = FakeMemoRepository(listOf(existing))
        val useCase = saveMemoUseCase(memoRepository = repository)

        // Act
        // Normal: update commands fully replace memo images.
        useCase(
            SaveMemoCommand(
                id = existing.id,
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                images = listOf(newImage)
            )
        )

        // Assert
        assertEquals(listOf(newImage), repository.savedMemos.single().images)
    }

    @Test
    fun invokeReturnsMemoWithUniqueTagIdsWhenDuplicateTagIdsAreProvided() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val useCase =
            saveMemoUseCase(tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value))))

        // Act
        val memo =
            useCase(
                SaveMemoCommand(
                    title = MemoTitle("Title"),
                    body = MemoBody("Body"),
                    tagIds = listOf(tagId, tagId)
                )
            )

        // Assert
        assertEquals(listOf(tagId), memo.tagIds)
    }

    @Test
    fun invokeThrowsWhenTagIdDoesNotExist() {
        // Arrange
        val repository = FakeMemoRepository()
        val useCase = saveMemoUseCase(memoRepository = repository)

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    SaveMemoCommand(
                        title = MemoTitle("Title"),
                        body = MemoBody("Body"),
                        tagIds = listOf(TagId("missing"))
                    )
                )
            }
        }
    }

    @Test
    fun invokeUsesCommandCreatedAtWhenProvided() = runTest {
        // Arrange
        val useCase = saveMemoUseCase(timeProvider = MutableTimeProvider(TimestampMillis(5000L)))

        // Act
        val memo = useCase(
            SaveMemoCommand(
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                createdAt = TimestampMillis(1000L)
            )
        )

        // Assert
        assertEquals(TimestampMillis(1000L), memo.createdAt)
    }

    @Test
    fun invokeUsesCurrentTimeWhenCommandCreatedAtIsNull() = runTest {
        // Arrange
        val useCase = saveMemoUseCase(timeProvider = MutableTimeProvider(TimestampMillis(5000L)))

        // Act
        val memo = useCase(
            SaveMemoCommand(
                title = MemoTitle("Title"),
                body = MemoBody("Body")
            )
        )

        // Assert
        assertEquals(TimestampMillis(5000L), memo.createdAt)
    }

    @Test
    fun invokePreservesExistingCreatedAtEvenWhenCommandCreatedAtIsProvided() = runTest {
        // Arrange
        val existing = memoFixture(id = "memo-1", createdAt = 1000L, updatedAt = 1500L)
        val useCase = saveMemoUseCase(
            memoRepository = FakeMemoRepository(listOf(existing)),
            timeProvider = MutableTimeProvider(TimestampMillis(5000L))
        )

        // Act
        val memo = useCase(
            SaveMemoCommand(
                id = existing.id,
                title = MemoTitle("New"),
                body = MemoBody("Body"),
                createdAt = TimestampMillis(9000L)
            )
        )

        // Assert
        assertEquals(TimestampMillis(1000L), memo.createdAt)
    }

    @Test
    fun invokeSetUpdatedAtToCreatedAtWhenCreatedAtIsFuture() = runTest {
        // Arrange
        val useCase = saveMemoUseCase(timeProvider = MutableTimeProvider(TimestampMillis(1000L)))

        // Act
        val memo = useCase(
            SaveMemoCommand(
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                createdAt = TimestampMillis(5000L)
            )
        )

        // Assert
        assertAll(
            { assertEquals(TimestampMillis(5000L), memo.createdAt) },
            { assertEquals(TimestampMillis(5000L), memo.updatedAt) }
        )
    }

    @Test
    fun invokeDoesNotSaveMemoWhenTagIdDoesNotExist() = runTest {
        // Arrange
        val repository = FakeMemoRepository()
        val useCase = saveMemoUseCase(memoRepository = repository)

        // Act
        try {
            useCase(
                SaveMemoCommand(
                    title = MemoTitle("Title"),
                    body = MemoBody("Body"),
                    tagIds = listOf(TagId("missing"))
                )
            )
        } catch (_: IllegalArgumentException) {
        }

        // Assert
        assertEquals(emptyList<Any>(), repository.savedMemos)
    }

    @Test
    fun interactionMissingTagDoesNotSaveMemoOrGenerateId() = runTest {
        // Arrange
        val missingTagId = TagId("missing")
        val memoRepository = FakeMemoRepository()
        val tagRepository = mockk<TagRepository>()
        val memoIdProvider = QueueMemoIdProvider()
        coEvery { tagRepository.getTagsByIds(listOf(missingTagId)) } returns emptyList()
        val useCase = SaveMemoUseCase(
            memoRepository = memoRepository,
            tagRepository = tagRepository,
            memoIdProvider = memoIdProvider,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(1000L))
        )

        // Act
        val error = runCatching {
            useCase(
                SaveMemoCommand(
                    title = MemoTitle("Title"),
                    body = MemoBody("Body"),
                    tagIds = listOf(missingTagId)
                )
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalArgumentException::class.java, error?.javaClass) },
            { assertEquals(emptyList<Any>(), memoRepository.savedMemos) },
            { assertEquals(emptyList<MemoId>(), memoIdProvider.issuedIds) }
        )
        coVerify(exactly = 1) { tagRepository.getTagsByIds(listOf(missingTagId)) }
        confirmVerified(tagRepository)
    }

    @Test
    fun boundaryEmptyTagIdsSkipsTagValidation() = runTest {
        // Arrange
        val tagRepository = mockk<TagRepository>()
        val useCase = SaveMemoUseCase(
            memoRepository = FakeMemoRepository(),
            tagRepository = tagRepository,
            memoIdProvider = QueueMemoIdProvider(listOf(MemoId("generated-id"))),
            currentTimeProvider = MutableTimeProvider(TimestampMillis(1000L))
        )

        // Act
        // Boundary/Interaction: empty tag ids do not touch TagRepository.
        useCase(SaveMemoCommand(title = MemoTitle("Title"), body = MemoBody("Body")))

        // Assert
        coVerify(exactly = 0) { tagRepository.getTagsByIds(any()) }
        confirmVerified(tagRepository)
    }

    @Test
    fun interactionUpdatingExistingMemoDoesNotGenerateNewMemoId() = runTest {
        // Arrange
        val existing = memoFixture(id = "memo-1")
        val memoIdProvider = QueueMemoIdProvider()
        val useCase = SaveMemoUseCase(
            memoRepository = FakeMemoRepository(listOf(existing)),
            tagRepository = FakeTagRepository(),
            memoIdProvider = memoIdProvider,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(2000L))
        )

        // Act
        // Interaction: existing memo update preserves id and skips id generation.
        val memo = useCase(
            SaveMemoCommand(
                id = existing.id,
                title = MemoTitle("Updated"),
                body = MemoBody("Body")
            )
        )

        // Assert
        assertAll(
            { assertEquals(existing.id, memo.id) },
            { assertEquals(emptyList<MemoId>(), memoIdProvider.issuedIds) }
        )
    }

    @Test
    fun interactionMissingCommandIdValidatesTagsAndSavesWithoutGeneratingId() = runTest {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val tagRepository = FakeTagRepository(listOf(tagFixture(id = "tag-1")))
        val memoIdProvider = QueueMemoIdProvider()
        val useCase = SaveMemoUseCase(
            memoRepository = memoRepository,
            tagRepository = tagRepository,
            memoIdProvider = memoIdProvider,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(1000L))
        )

        // Act
        // Interaction: a stable command id skips id generation even when no active memo exists.
        val memo = useCase(
            SaveMemoCommand(
                id = MemoId("missing"),
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                tagIds = listOf(TagId("tag-1"))
            )
        )

        // Assert
        assertAll(
            { assertEquals(MemoId("missing"), memo.id) },
            { assertEquals(listOf(listOf(TagId("tag-1"))), tagRepository.getTagsByIdsCalls) },
            { assertEquals(listOf(MemoId("missing")), memoRepository.savedMemos.map { it.id }) },
            { assertEquals(emptyList<MemoId>(), memoIdProvider.issuedIds) }
        )
    }

    @Test
    fun invokeThrowsWhenOnlyTagIdsAreProvided() {
        // Arrange
        val useCase =
            saveMemoUseCase(tagRepository = FakeTagRepository(listOf(tagFixture(id = "tag-1"))))

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    SaveMemoCommand(
                        title = MemoTitle(""),
                        body = MemoBody(""),
                        tagIds = listOf(TagId("tag-1"))
                    )
                )
            }
        }
    }

    @Test
    fun invokeThrowsWhenOnlyIsFavoriteIsTrue() {
        // Arrange
        val useCase = saveMemoUseCase()

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    SaveMemoCommand(
                        title = MemoTitle(""),
                        body = MemoBody(""),
                        isFavorite = true
                    )
                )
            }
        }
    }

    private fun saveMemoUseCase(
        memoRepository: FakeMemoRepository = FakeMemoRepository(),
        tagRepository: FakeTagRepository = FakeTagRepository(),
        memoIdProvider: QueueMemoIdProvider = QueueMemoIdProvider(),
        timeProvider: MutableTimeProvider = MutableTimeProvider()
    ) = SaveMemoUseCase(
        memoRepository = memoRepository,
        tagRepository = tagRepository,
        memoIdProvider = memoIdProvider,
        currentTimeProvider = timeProvider
    )

}
