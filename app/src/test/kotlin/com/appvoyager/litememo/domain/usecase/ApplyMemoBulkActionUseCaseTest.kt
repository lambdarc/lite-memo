package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.FakeTagRepository
import com.appvoyager.litememo.domain.MutableTimeProvider
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.ActiveMemoBulkWrite
import com.appvoyager.litememo.domain.model.ApplyMemoBulkActionCommand
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoBulkAction
import com.appvoyager.litememo.domain.model.MemoTrashUpdate
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.provider.CurrentTimeProvider
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.repository.TagRepository
import com.appvoyager.litememo.domain.tagFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyMemoBulkActionUseCaseTest {

    @Test
    fun invokeMovesMemosToTrashInInputOrder() = runTest {
        // Arrange
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "memo-1"),
                memoFixture(id = "memo-2")
            )
        )
        val useCase = applyMemoBulkActionUseCase(memoRepository = repository)

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-2"), MemoId("memo-1"), MemoId("memo-2")),
                action = MemoBulkAction.moveToTrash()
            )
        )

        // Assert
        val expectedIds = listOf(MemoId("memo-2"), MemoId("memo-1"))
        assertEquals(expectedIds, repository.movedToTrash.map { it.memoId })
    }

    @Test
    fun invokeKeepsExistingUpdatedAtForTrashWhenCurrentTimeIsEarlierThanUpdatedAt() = runTest {
        // Arrange
        val repository = FakeMemoRepository(
            listOf(memoFixture(id = "memo-1", createdAt = 1000L, updatedAt = 5000L))
        )
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            now = TimestampMillis(3000L)
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-1")),
                action = MemoBulkAction.moveToTrash()
            )
        )

        // Assert
        assertEquals(TimestampMillis(5000L), repository.movedToTrash.single().deletedAt)
    }

    @Test
    fun invokeSavesFavoriteUpdatesInInputOrder() = runTest {
        // Arrange
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "memo-1"),
                memoFixture(id = "memo-2")
            )
        )
        val useCase = applyMemoBulkActionUseCase(memoRepository = repository)

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-2"), MemoId("memo-1")),
                action = MemoBulkAction.setFavorite(true)
            )
        )

        // Assert
        val expectedIds = listOf(MemoId("memo-2"), MemoId("memo-1"))
        assertEquals(expectedIds, repository.savedMemos.map { it.id })
    }

    @Test
    fun boundarySetFavoriteDeduplicatesMemoIdsBeforeSaving() = runTest {
        // Arrange
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "memo-1"),
                memoFixture(id = "memo-2")
            )
        )
        val useCase = applyMemoBulkActionUseCase(memoRepository = repository)

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-2"), MemoId("memo-1"), MemoId("memo-2")),
                action = MemoBulkAction.setFavorite(true)
            )
        )

        // Assert
        assertEquals(
            listOf(MemoId("memo-2"), MemoId("memo-1")),
            repository.savedMemos.map {
                it.id
            }
        )
    }

    @Test
    fun invokeUsesCreatedAtForFavoriteUpdatedAtWhenCurrentTimeIsEarlierThanCreatedAt() = runTest {
        // Arrange
        val repository = FakeMemoRepository(listOf(memoFixture(id = "memo-1", createdAt = 3000L)))
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            now = TimestampMillis(2000L)
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-1")),
                action = MemoBulkAction.setFavorite(true)
            )
        )

        // Assert
        assertEquals(TimestampMillis(3000L), repository.savedMemos.single().updatedAt)
    }

    @Test
    fun invokeKeepsExistingUpdatedAtForFavoriteWhenCurrentTimeIsEarlierThanUpdatedAt() = runTest {
        // Arrange
        val repository = FakeMemoRepository(
            listOf(memoFixture(id = "memo-1", createdAt = 1000L, updatedAt = 5000L))
        )
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            now = TimestampMillis(3000L)
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-1")),
                action = MemoBulkAction.setFavorite(true)
            )
        )

        // Assert
        assertEquals(TimestampMillis(5000L), repository.savedMemos.single().updatedAt)
    }

    @Test
    fun invokeAddsTagInInputOrder() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "memo-1"),
                memoFixture(id = "memo-2")
            )
        )
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value)))
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-2"), MemoId("memo-1")),
                action = MemoBulkAction.addTag(tagId)
            )
        )

        // Assert
        val expectedIds = listOf(MemoId("memo-2"), MemoId("memo-1"))
        assertEquals(expectedIds, repository.savedMemos.map { it.id })
    }

    @Test
    fun invokeUsesCreatedAtForAddTagUpdatedAtWhenCurrentTimeIsEarlierThanCreatedAt() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val repository = FakeMemoRepository(listOf(memoFixture(id = "memo-1", createdAt = 3000L)))
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value))),
            now = TimestampMillis(2000L)
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-1")),
                action = MemoBulkAction.addTag(tagId)
            )
        )

        // Assert
        assertEquals(TimestampMillis(3000L), repository.savedMemos.single().updatedAt)
    }

    @Test
    fun invokeKeepsExistingUpdatedAtForAddTagWhenCurrentTimeIsEarlierThanUpdatedAt() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val repository = FakeMemoRepository(
            listOf(memoFixture(id = "memo-1", createdAt = 1000L, updatedAt = 5000L))
        )
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value))),
            now = TimestampMillis(3000L)
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-1")),
                action = MemoBulkAction.addTag(tagId)
            )
        )

        // Assert
        assertEquals(TimestampMillis(5000L), repository.savedMemos.single().updatedAt)
    }

    @Test
    fun invokeRemovesTagInInputOrder() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "memo-1", tagIds = listOf(tagId)),
                memoFixture(id = "memo-2", tagIds = listOf(tagId))
            )
        )
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value)))
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-2"), MemoId("memo-1")),
                action = MemoBulkAction.removeTag(tagId)
            )
        )

        // Assert
        val expectedIds = listOf(MemoId("memo-2"), MemoId("memo-1"))
        assertEquals(expectedIds, repository.savedMemos.map { it.id })
    }

    @Test
    fun invokeUsesCreatedAtForRemoveTagUpdatedAtWhenCurrentTimeIsEarlierThanCreatedAt() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val repository = FakeMemoRepository(
            listOf(memoFixture(id = "memo-1", createdAt = 3000L, tagIds = listOf(tagId)))
        )
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value))),
            now = TimestampMillis(2000L)
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-1")),
                action = MemoBulkAction.removeTag(tagId)
            )
        )

        // Assert
        assertEquals(TimestampMillis(3000L), repository.savedMemos.single().updatedAt)
    }

    @Test
    fun invokeKeepsExistingUpdatedAtForRemoveTagWhenCurrentTimeIsEarlierThanUpdatedAt() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(
                    id = "memo-1",
                    createdAt = 1000L,
                    updatedAt = 5000L,
                    tagIds = listOf(tagId)
                )
            )
        )
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value))),
            now = TimestampMillis(3000L)
        )

        // Act
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("memo-1")),
                action = MemoBulkAction.removeTag(tagId)
            )
        )

        // Assert
        assertEquals(TimestampMillis(5000L), repository.savedMemos.single().updatedAt)
    }

    @Test
    fun boundaryNoOpBulkChangesPersistNoChangedMemos() = runTest {
        // Arrange
        val tagId = TagId("tag-1")
        val alreadyFavoriteRepository = FakeMemoRepository(
            listOf(memoFixture(id = "favorite", isFavorite = true))
        )
        val alreadyTaggedRepository = FakeMemoRepository(
            listOf(memoFixture(id = "tagged", tagIds = listOf(tagId)))
        )
        val untaggedRepository = FakeMemoRepository(
            listOf(memoFixture(id = "untagged"))
        )
        val tagRepository = FakeTagRepository(listOf(tagFixture(id = tagId.value)))

        // Act
        // Boundary/Normal: no-op favorite/tag changes should not produce changed memos.
        applyMemoBulkActionUseCase(
            memoRepository = alreadyFavoriteRepository
        )(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("favorite")),
                action = MemoBulkAction.setFavorite(true)
            )
        )
        applyMemoBulkActionUseCase(
            memoRepository = alreadyTaggedRepository,
            tagRepository = tagRepository
        )(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("tagged")),
                action = MemoBulkAction.addTag(tagId)
            )
        )
        applyMemoBulkActionUseCase(
            memoRepository = untaggedRepository,
            tagRepository = tagRepository
        )(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(MemoId("untagged")),
                action = MemoBulkAction.removeTag(tagId)
            )
        )

        // Assert
        assertEquals(
            NoOpBulkChangeSnapshot(
                alreadyFavoriteSavedMemos = emptyList(),
                alreadyTaggedSavedMemos = emptyList(),
                untaggedSavedMemos = emptyList()
            ),
            NoOpBulkChangeSnapshot(
                alreadyFavoriteSavedMemos = alreadyFavoriteRepository.savedMemos,
                alreadyTaggedSavedMemos = alreadyTaggedRepository.savedMemos,
                untaggedSavedMemos = untaggedRepository.savedMemos
            )
        )
    }

    @Test
    fun interactionSetFavoriteValidatesAllSelectedMemosWhileSavingOnlyChangedMemos() = runTest {
        // Arrange
        val unchanged = memoFixture(id = "memo-unchanged", isFavorite = true)
        val changed = memoFixture(id = "memo-changed")
        val repository = FakeMemoRepository(listOf(unchanged, changed))
        val useCase = applyMemoBulkActionUseCase(memoRepository = repository)

        // Act
        // Interaction: every selected ID remains a transaction precondition without extra writes.
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = listOf(unchanged.id, changed.id),
                action = MemoBulkAction.setFavorite(true)
            )
        )

        // Assert
        assertEquals(
            listOf(unchanged.id, changed.id) to listOf(changed.id),
            repository.activeBulkSaveExpectedIdBatches.single() to
                repository.savedMemos.map { it.id }
        )
    }

    @Test
    fun invokeThrowsBeforeWritingWhenMemoIsMissing() = runTest {
        // Arrange
        val repository = FakeMemoRepository(listOf(memoFixture(id = "memo-1")))
        val useCase = applyMemoBulkActionUseCase(memoRepository = repository)

        // Act
        val error = runCatching {
            useCase(
                ApplyMemoBulkActionCommand(
                    memoIds = listOf(MemoId("memo-1"), MemoId("missing")),
                    action = MemoBulkAction.setFavorite(true)
                )
            )
        }.exceptionOrNull()

        // Assert
        val expected = true to emptyList<Memo>()
        val actual = (error is IllegalArgumentException) to repository.savedMemos
        assertEquals(expected, actual)
    }

    @Test
    fun invokeThrowsBeforeWritingWhenTagIsMissing() = runTest {
        // Arrange
        val repository = FakeMemoRepository(listOf(memoFixture(id = "memo-1")))
        val useCase = applyMemoBulkActionUseCase(
            memoRepository = repository,
            tagRepository = FakeTagRepository()
        )

        // Act
        val error = runCatching {
            useCase(
                ApplyMemoBulkActionCommand(
                    memoIds = listOf(MemoId("memo-1")),
                    action = MemoBulkAction.addTag(TagId("missing"))
                )
            )
        }.exceptionOrNull()

        // Assert
        val expected = true to emptyList<Memo>()
        val actual = (error is IllegalArgumentException) to repository.savedMemos
        assertEquals(expected, actual)
    }

    @Test
    fun boundaryEmptyMemoIdsDoesNotReadTimeOrDependencies() = runTest {
        // Arrange
        val memoRepository = mockk<MemoRepository>()
        val tagRepository = mockk<TagRepository>()
        val timeProvider = mockk<CurrentTimeProvider>()
        val useCase = ApplyMemoBulkActionUseCase(
            memoRepository = memoRepository,
            tagRepository = tagRepository,
            currentTimeProvider = timeProvider
        )

        // Act
        // Boundary/Interaction: empty input is a pure no-op.
        useCase(
            ApplyMemoBulkActionCommand(
                memoIds = emptyList(),
                action = MemoBulkAction.addTag(TagId("tag-1"))
            )
        )

        // Assert
        coVerify(exactly = 0) { memoRepository.getActiveMemos(any()) }
        coVerify(exactly = 0) { tagRepository.getTag(any()) }
        verify(exactly = 0) { timeProvider.now() }
        confirmVerified(memoRepository, tagRepository, timeProvider)
    }

    @Test
    fun interactionMissingMemoDoesNotWriteAnyBulkAction() = runTest {
        // Arrange
        val memoRepository = mockk<MemoRepository>()
        val tagRepository = mockk<TagRepository>(relaxed = true)
        val timeProvider = mockk<CurrentTimeProvider>()
        coEvery { memoRepository.getActiveMemos(listOf(MemoId("missing"))) } returns emptyList()
        val useCase = ApplyMemoBulkActionUseCase(
            memoRepository = memoRepository,
            tagRepository = tagRepository,
            currentTimeProvider = timeProvider
        )

        // Act
        val error = runCatching {
            useCase(
                ApplyMemoBulkActionCommand(
                    memoIds = listOf(MemoId("missing")),
                    action = MemoBulkAction.setFavorite(true)
                )
            )
        }.exceptionOrNull()

        // Assert
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        coVerify(exactly = 1) { memoRepository.getActiveMemos(listOf(MemoId("missing"))) }
        coVerify(exactly = 0) { memoRepository.saveActiveMemoBulkWrites(any()) }
        confirmVerified(memoRepository, tagRepository)
    }

    @Test
    fun interactionMissingTagDoesNotWriteAnyBulkAction() = runTest {
        // Arrange
        val tagId = TagId("missing")
        val memo = memoFixture(id = "memo-1")
        val memoRepository = mockk<MemoRepository>()
        val tagRepository = mockk<TagRepository>()
        val timeProvider = mockk<CurrentTimeProvider>()
        coEvery { memoRepository.getActiveMemos(listOf(memo.id)) } returns listOf(memo)
        coEvery { tagRepository.getTag(tagId) } returns null
        val useCase = ApplyMemoBulkActionUseCase(
            memoRepository = memoRepository,
            tagRepository = tagRepository,
            currentTimeProvider = timeProvider
        )

        // Act
        val error = runCatching {
            useCase(
                ApplyMemoBulkActionCommand(
                    memoIds = listOf(memo.id),
                    action = MemoBulkAction.addTag(tagId)
                )
            )
        }.exceptionOrNull()

        // Assert
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
        coVerify(exactly = 1) { memoRepository.getActiveMemos(listOf(memo.id)) }
        coVerify(exactly = 1) { tagRepository.getTag(tagId) }
        coVerify(exactly = 0) { memoRepository.saveActiveMemoBulkWrites(any()) }
        confirmVerified(memoRepository, tagRepository)
    }

    @Test
    fun errorBulkFavoriteSaveIsIssuedAsASingleAllOrNothingCall() = runTest {
        // Arrange
        val delegate = FakeMemoRepository(
            listOf(
                memoFixture(id = "memo-1"),
                memoFixture(id = "memo-2"),
                memoFixture(id = "memo-3")
            )
        )
        val repository = BulkWriteRecordingMemoRepository(
            delegate = delegate,
            poisonMemoId = MemoId("memo-3")
        )
        val useCase = applyMemoBulkActionUseCase(memoRepository = repository)

        // Act
        // Error: a per-item write regression would apply memo-1/memo-2 or issue multiple calls.
        runCatching {
            useCase(
                ApplyMemoBulkActionCommand(
                    memoIds = listOf(MemoId("memo-1"), MemoId("memo-2"), MemoId("memo-3")),
                    action = MemoBulkAction.setFavorite(true)
                )
            )
        }

        // Assert
        assertEquals(
            1 to listOf(false, false, false),
            repository.bulkWriteCallCount to
                delegate.currentMemos().sortedBy { it.id.value }.map { it.isFavorite }
        )
    }

    @Test
    fun errorBulkTrashIsIssuedAsASingleAllOrNothingCall() = runTest {
        // Arrange
        val delegate = FakeMemoRepository(
            listOf(
                memoFixture(id = "memo-1"),
                memoFixture(id = "memo-2"),
                memoFixture(id = "memo-3")
            )
        )
        val repository = BulkTrashRecordingMemoRepository(
            delegate = delegate,
            poisonMemoId = MemoId("memo-3")
        )
        val useCase = applyMemoBulkActionUseCase(memoRepository = repository)

        // Act
        // Error: a per-item trash regression would trash memo-1/memo-2 or issue multiple calls.
        runCatching {
            useCase(
                ApplyMemoBulkActionCommand(
                    memoIds = listOf(MemoId("memo-1"), MemoId("memo-2"), MemoId("memo-3")),
                    action = MemoBulkAction.moveToTrash()
                )
            )
        }

        // Assert
        assertEquals(
            1 to listOf<TimestampMillis?>(null, null, null),
            repository.bulkTrashCallCount to
                delegate.currentMemos().sortedBy { it.id.value }.map { it.deletedAt }
        )
    }

    private fun applyMemoBulkActionUseCase(
        memoRepository: MemoRepository = FakeMemoRepository(),
        tagRepository: FakeTagRepository = FakeTagRepository(),
        now: TimestampMillis = TimestampMillis(2_000L)
    ) = ApplyMemoBulkActionUseCase(
        memoRepository = memoRepository,
        tagRepository = tagRepository,
        currentTimeProvider = MutableTimeProvider(now)
    )

    private class BulkWriteRecordingMemoRepository(
        private val delegate: FakeMemoRepository,
        private val poisonMemoId: MemoId
    ) : MemoRepository by delegate {

        var bulkWriteCallCount = 0
            private set

        override suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>) {
            bulkWriteCallCount += 1
            if (writes.any { it.memoId == poisonMemoId }) error("Failed to save active memos.")
            delegate.saveActiveMemoBulkWrites(writes)
        }
    }

    private class BulkTrashRecordingMemoRepository(
        private val delegate: FakeMemoRepository,
        private val poisonMemoId: MemoId
    ) : MemoRepository by delegate {

        var bulkTrashCallCount = 0
            private set

        override suspend fun moveMemosToTrash(updates: List<MemoTrashUpdate>) {
            bulkTrashCallCount += 1
            if (updates.any { it.memoId == poisonMemoId }) error("Failed to move memos.")
            delegate.moveMemosToTrash(updates)
        }
    }

    private data class NoOpBulkChangeSnapshot(
        val alreadyFavoriteSavedMemos: List<Memo>,
        val alreadyTaggedSavedMemos: List<Memo>,
        val untaggedSavedMemos: List<Memo>
    )
}
