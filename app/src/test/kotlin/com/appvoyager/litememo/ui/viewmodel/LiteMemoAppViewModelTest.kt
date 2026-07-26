package com.appvoyager.litememo.ui.viewmodel

import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.usecase.RestoreMemoFromTrashUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiteMemoAppViewModelTest {

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
    fun restoreMemoEmitsErrorEventWhenRestoreFails() = runTest(dispatcher) {
        // Arrange
        val repository = ThrowingRestoreMemoRepository(
            IllegalStateException("Restore failed.")
        )
        val viewModel = LiteMemoAppViewModel(
            restoreMemoFromTrashUseCase = RestoreMemoFromTrashUseCase(repository)
        )

        // Act
        viewModel.restoreMemo(MemoId("memo-1"))
        advanceUntilIdle()
        val event = viewModel.restoreMemoErrorEvent.first()

        // Assert
        assertEquals(Unit, event)
    }

    @Test
    fun restoreMemoDoesNotEmitErrorEventWhenRestoreIsCancelled() = runTest(dispatcher) {
        // Arrange
        val repository = ThrowingRestoreMemoRepository(
            CancellationException("Cancelled.")
        )
        val viewModel = LiteMemoAppViewModel(
            restoreMemoFromTrashUseCase = RestoreMemoFromTrashUseCase(repository)
        )

        // Act
        viewModel.restoreMemo(MemoId("memo-1"))
        advanceUntilIdle()
        val event = withTimeoutOrNull(100L) {
            viewModel.restoreMemoErrorEvent.first()
        }

        // Assert
        assertNull(event)
    }

    private class ThrowingRestoreMemoRepository(private val throwable: Throwable) :
        MemoRepository by FakeMemoRepository() {

        override suspend fun restoreMemoFromTrash(id: MemoId): Unit = throw throwable
    }
}
