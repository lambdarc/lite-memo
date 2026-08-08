package com.lambdarc.litememo.ui.viewmodel

import app.cash.turbine.test
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.QueueTagIdProvider
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.provider.TagIdProvider
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.tagFixture
import com.lambdarc.litememo.domain.usecase.DeleteTagUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTagsUseCase
import com.lambdarc.litememo.domain.usecase.SaveTagUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagManageViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiStateKeepsEditingTagWhenObserveTagsFails() = runTest(dispatcher) {
        // Arrange
        val viewModel = tagManageViewModel(
            tagRepository = ObserveFailingTagRepository(listOf(tagFixture(id = "tag-1")))
        )
        viewModel.uiState.first { it.hasError }

        // Act
        viewModel.startCreate()
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.hasError && it.editingTag != null }

        // Assert
        assertAll(
            { assertEquals(true, state.hasError) },
            { assertEquals("", state.editingTag?.name) }
        )
    }

    @Test
    fun coroutineRapidSaveEditCreatesTagOnlyOnce() = runTest(dispatcher) {
        // Arrange
        val tagRepository = FakeTagRepository()
        val viewModel = tagManageViewModel(
            tagRepository = tagRepository,
            tagIdProvider = QueueTagIdProvider(listOf(TagId("tag-1"), TagId("tag-2")))
        )
        viewModel.uiState.first { !it.isLoading }
        viewModel.startCreate()
        viewModel.updateEditName("New tag")

        // Act
        // Coroutine/Boundary: an in-flight save blocks the second rapid call.
        viewModel.saveEdit()
        viewModel.saveEdit()
        advanceUntilIdle()

        // Assert
        assertEquals(1, tagRepository.savedTags.size)
    }

    @Test
    fun saveEditSetsDuplicateNameErrorWhenTagNameAlreadyExists() = runTest(dispatcher) {
        // Arrange
        val viewModel = tagManageViewModel(
            tagRepository = FakeTagRepository(listOf(tagFixture(id = "tag-1", name = "Work")))
        )
        viewModel.uiState.first { !it.isLoading }

        // Act
        viewModel.startCreate()
        viewModel.updateEditName("Work")
        viewModel.saveEdit()
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.editingTag?.duplicateNameError == true }

        // Assert
        assertEquals(true, state.editingTag?.duplicateNameError)
    }

    @Test
    fun boundarySaveEditDoesNotTreatCurrentTagNameAsDuplicate() = runTest(dispatcher) {
        // Arrange
        val tagRepository = FakeTagRepository(listOf(tagFixture(id = "tag-1", name = "Work")))
        val viewModel = tagManageViewModel(tagRepository = tagRepository)
        viewModel.uiState.first { it.tags.isNotEmpty() }

        // Act
        viewModel.startEdit(TagId("tag-1"))
        viewModel.saveEdit()
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.editingTag == null }

        // Assert
        assertAll(
            { assertEquals(null, state.editingTag) },
            { assertEquals(listOf("Work"), tagRepository.currentTags().map { it.name.value }) }
        )
    }

    @Test
    fun boundarySaveEditSetsNameErrorWhenNameIsBlank() = runTest(dispatcher) {
        // Arrange
        val viewModel = tagManageViewModel()
        viewModel.startCreate()

        // Act
        viewModel.updateEditName("   ")
        viewModel.saveEdit()
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.editingTag?.nameError == true }

        // Assert
        assertAll(
            { assertEquals(true, state.editingTag?.nameError) },
            { assertEquals("   ", state.editingTag?.name) }
        )
    }

    @Test
    fun normalSaveEditClearsEditingTagWhenCreateSucceeds() = runTest(dispatcher) {
        // Arrange
        val tagRepository = FakeTagRepository()
        val viewModel = tagManageViewModel(tagRepository = tagRepository)
        viewModel.startCreate()

        // Act
        viewModel.updateEditName("Work")
        viewModel.saveEdit()
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.editingTag == null && it.tags.isNotEmpty() }

        // Assert
        assertAll(
            { assertEquals(null, state.editingTag) },
            { assertEquals(listOf("Work"), tagRepository.currentTags().map { it.name.value }) }
        )
    }

    @Test
    fun saveEditMapsDuplicateNameExceptionToDuplicateNameError() = runTest(dispatcher) {
        // Arrange
        val viewModel = tagManageViewModel(
            tagRepository = StaleObserveTagRepository(
                listOf(
                    tagFixture(id = "tag-1", name = "Work")
                )
            )
        )
        viewModel.uiState.first { !it.isLoading }

        // Act
        viewModel.startCreate()
        viewModel.updateEditName("Work")
        viewModel.saveEdit()
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.editingTag?.duplicateNameError == true }

        // Assert
        assertAll(
            { assertEquals(false, state.editingTag?.saveError) },
            { assertEquals(true, state.editingTag?.duplicateNameError) }
        )
    }

    @Test
    fun flowConfirmDeleteEmitsDeleteErrorWhenDeleteFails() = runTest(dispatcher) {
        // Arrange
        val tag = tagFixture(id = "tag-1", name = "Work")
        val viewModel = tagManageViewModel(
            tagRepository = DeleteFailingTagRepository(listOf(tag))
        )
        val tagUiModel = viewModel.uiState.first { it.tags.isNotEmpty() }.tags.single()

        // Act & Assert
        viewModel.deleteErrorEvent.test {
            viewModel.requestDelete(tagUiModel)
            viewModel.confirmDelete()
            advanceUntilIdle()
            assertEquals(Unit, awaitItem())
            assertEquals(null, viewModel.uiState.value.showDeleteDialog)
        }
    }

    private fun tagManageViewModel(
        tagRepository: TagRepository = FakeTagRepository(),
        tagIdProvider: TagIdProvider = QueueTagIdProvider()
    ) = TagManageViewModel(
        observeTagsUseCase = ObserveTagsUseCase(tagRepository),
        saveTagUseCase = SaveTagUseCase(
            tagRepository = tagRepository,
            tagIdProvider = tagIdProvider,
            currentTimeProvider = MutableTimeProvider()
        ),
        deleteTagUseCase = DeleteTagUseCase(tagRepository)
    )

    private class ObserveFailingTagRepository(initialTags: List<Tag>) : TagRepository {

        private val repository = FakeTagRepository(initialTags)

        override fun observeTags(): Flow<List<Tag>> = flow {
            emit(repository.currentTags())
            error("Failed to observe tags.")
        }

        override suspend fun getTag(id: TagId): Tag? = repository.getTag(id)

        override suspend fun findTagByName(name: TagName): Tag? = repository.findTagByName(name)

        override suspend fun getTagsByIds(ids: List<TagId>): List<Tag> =
            repository.getTagsByIds(ids)

        override suspend fun saveTag(tag: Tag) = repository.saveTag(tag)

        override suspend fun deleteTag(id: TagId) = repository.deleteTag(id)

        override suspend fun getAllTags(): List<Tag> = repository.getAllTags()
    }

    private class DeleteFailingTagRepository(initialTags: List<Tag>) : TagRepository {

        private val repository = FakeTagRepository(initialTags)

        override fun observeTags(): Flow<List<Tag>> = repository.observeTags()

        override suspend fun getTag(id: TagId): Tag? = repository.getTag(id)

        override suspend fun findTagByName(name: TagName): Tag? = repository.findTagByName(name)

        override suspend fun getTagsByIds(ids: List<TagId>): List<Tag> =
            repository.getTagsByIds(ids)

        override suspend fun saveTag(tag: Tag) = repository.saveTag(tag)

        override suspend fun deleteTag(id: TagId): Unit = error("Failed to delete tag.")

        override suspend fun getAllTags(): List<Tag> = repository.getAllTags()
    }

    private class StaleObserveTagRepository(initialTags: List<Tag>) : TagRepository {

        private val repository = FakeTagRepository(initialTags)

        override fun observeTags(): Flow<List<Tag>> = flowOf(emptyList())

        override suspend fun getTag(id: TagId): Tag? = repository.getTag(id)

        override suspend fun findTagByName(name: TagName): Tag? = repository.findTagByName(name)

        override suspend fun getTagsByIds(ids: List<TagId>): List<Tag> =
            repository.getTagsByIds(ids)

        override suspend fun saveTag(tag: Tag) = repository.saveTag(tag)

        override suspend fun deleteTag(id: TagId) = repository.deleteTag(id)

        override suspend fun getAllTags(): List<Tag> = repository.getAllTags()
    }
}
