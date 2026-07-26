package com.appvoyager.litememo.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.FakeTagRepository
import com.appvoyager.litememo.domain.MutableTimeProvider
import com.appvoyager.litememo.domain.exception.ImportTagNameConflictException
import com.appvoyager.litememo.domain.exception.MemoImportException
import com.appvoyager.litememo.domain.exception.MemoImportFailureReason
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoExportToken
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.repository.FakeUserSettingsRepository
import com.appvoyager.litememo.domain.repository.MemoExportArchiveRepository
import com.appvoyager.litememo.domain.usecase.ExportMemosUseCase
import com.appvoyager.litememo.domain.usecase.ImportMemosFromFileUseCase
import com.appvoyager.litememo.domain.usecase.ObserveAppLockEnabledUseCase
import com.appvoyager.litememo.domain.usecase.ObserveMemoSortOrderUseCase
import com.appvoyager.litememo.domain.usecase.ObserveThemeModeUseCase
import com.appvoyager.litememo.domain.usecase.PrepareMemoExportUseCase
import com.appvoyager.litememo.domain.usecase.SetAppLockEnabledUseCase
import com.appvoyager.litememo.domain.usecase.SetMemoSortOrderUseCase
import com.appvoyager.litememo.domain.usecase.SetThemeModeUseCase
import com.appvoyager.litememo.ui.auth.AppLockAuthenticationUiResult
import com.appvoyager.litememo.ui.state.SettingsImportErrorDialogUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
    fun stateTransitionPrepareExportRequestsPickerOnlyAfterPreparationCompletes() =
        runTest(dispatcher) {
            // Arrange
            val gate = CompletableDeferred<Unit>()
            val repository = FakeMemoExportArchiveRepository(prepareGate = gate)
            val viewModel = viewModel(repository)

            // Act
            // StateTransition: picker state is withheld while the private ZIP is incomplete.
            viewModel.prepareExport()
            runCurrent()
            val beforeCompletion = viewModel.uiState.first().exportPickerRequestId
            gate.complete(Unit)
            advanceUntilIdle()
            val afterCompletion = viewModel.uiState.first { it.exportPickerRequestId != null }

            // Assert
            assertEquals(
                null to true,
                beforeCompletion to (afterCompletion.exportPickerRequestId != null)
            )
        }

    @Test
    fun interactionPrepareExportSuppressesDuplicateTaps() = runTest(dispatcher) {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val repository = FakeMemoExportArchiveRepository(prepareGate = gate)
        val viewModel = viewModel(repository)

        // Act
        // Interaction: an in-flight prepare owns the export operation.
        viewModel.prepareExport()
        viewModel.prepareExport()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        // Assert
        assertEquals(1, repository.preparedData.size)
    }

    @Test
    fun flowPrepareExportEmitsErrorWithoutPickerWhenImagePreparationFails() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository(
            prepareError = IllegalStateException("missing image")
        )
        val viewModel = viewModel(repository)

        // Act & Assert
        // Flow/Error: preparation failure never creates a destination document.
        viewModel.snackbarEvent.test {
            viewModel.prepareExport()
            advanceUntilIdle()
            assertEquals(SettingsSnackbarUiEvent.ExportError, awaitItem())
        }
        assertEquals(null, viewModel.uiState.first().exportPickerRequestId)
    }

    @Test
    fun stateTransitionPickerRequestIsConsumedOnce() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val viewModel = viewModel(repository)
        viewModel.prepareExport()
        advanceUntilIdle()
        val requestId = viewModel.uiState.first { it.exportPickerRequestId != null }
            .exportPickerRequestId!!

        // Act
        // StateTransition: the Route acknowledges one picker launch.
        viewModel.onExportPickerRequestHandled(requestId)
        advanceUntilIdle()

        // Assert
        assertEquals(null, viewModel.uiState.first().exportPickerRequestId)
    }

    @Test
    fun interactionPickerCancelDiscardsPreparedArchive() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val viewModel = viewModel(repository)
        viewModel.prepareExport()
        advanceUntilIdle()

        // Act
        // Interaction: cancel releases the private archive without a result snackbar.
        viewModel.cancelPreparedExport()
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(FakeMemoExportArchiveRepository.TOKEN), repository.discardedTokens)
    }

    @Test
    fun interactionDelayedPickerResultAfterCancelDoesNotWriteDiscardedArchive() =
        runTest(dispatcher) {
            // Arrange
            val discardGate = CompletableDeferred<Unit>()
            val repository = FakeMemoExportArchiveRepository(discardGate = discardGate)
            val viewModel = viewModel(repository)
            viewModel.prepareExport()
            advanceUntilIdle()

            // Act
            // Interaction: cancel releases ownership before asynchronous file deletion completes.
            viewModel.cancelPreparedExport()
            viewModel.writePreparedExport(DESTINATION)
            runCurrent()
            discardGate.complete(Unit)
            advanceUntilIdle()

            // Assert
            assertEquals(emptyList<Pair<MemoExportToken, ExportFileReference>>(), repository.writes)
        }

    @Test
    fun flowWritePreparedExportEmitsSuccessAndDiscardsArchive() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val viewModel = viewModel(repository)
        viewModel.prepareExport()
        advanceUntilIdle()

        // Act & Assert
        // Flow/Normal: successful destination copy is followed by cleanup and snackbar.
        viewModel.snackbarEvent.test {
            viewModel.writePreparedExport(DESTINATION)
            advanceUntilIdle()
            assertEquals(SettingsSnackbarUiEvent.ExportSuccess, awaitItem())
        }
        assertEquals(
            listOf(FakeMemoExportArchiveRepository.TOKEN to DESTINATION) to
                listOf(FakeMemoExportArchiveRepository.TOKEN),
            repository.writes to repository.discardedTokens
        )
    }

    @Test
    fun flowWriteFailureEmitsErrorAndDiscardsArchive() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository(
            writeError = IllegalStateException("write failed")
        )
        val viewModel = viewModel(repository)
        viewModel.prepareExport()
        advanceUntilIdle()

        // Act & Assert
        // Flow/Error: destination failure still releases the private archive.
        viewModel.snackbarEvent.test {
            viewModel.writePreparedExport(DESTINATION)
            advanceUntilIdle()
            assertEquals(SettingsSnackbarUiEvent.ExportDestinationWriteError, awaitItem())
        }
        assertEquals(listOf(FakeMemoExportArchiveRepository.TOKEN), repository.discardedTokens)
    }

    @Test
    fun flowWritePreparedExportWithoutTokenEmitsErrorInsteadOfSilentNoOp() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val viewModel = viewModel(repository)

        // Act & Assert
        // Flow/Error: a picker result arriving without a prepared archive still reports failure.
        viewModel.snackbarEvent.test {
            viewModel.writePreparedExport(DESTINATION)
            advanceUntilIdle()
            assertEquals(SettingsSnackbarUiEvent.ExportError, awaitItem())
        }
        assertEquals(emptyList<Pair<MemoExportToken, ExportFileReference>>(), repository.writes)
    }

    @Test
    fun stateTransitionAbandonedPickerRequestReleasesExportLock() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val viewModel = viewModel(repository)
        viewModel.prepareExport()
        advanceUntilIdle()

        // Act
        // StateTransition: a picker request nobody handled stops blocking later operations.
        viewModel.onExportPickerHostStopped()
        advanceUntilIdle()

        // Assert
        assertEquals(false, viewModel.uiState.first().isExporting)
    }

    @Test
    fun interactionAbandonmentDiscardsPreparedArchiveOnApplicationScope() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val viewModel = viewModel(repository)
        val store = ViewModelStore()
        store.put("settings", viewModel)
        viewModel.prepareExport()
        advanceUntilIdle()

        // Act
        // Interaction: ViewModel destruction hands cleanup to the application scope.
        store.clear()
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(FakeMemoExportArchiveRepository.TOKEN), repository.discardedTokens)
    }

    @Test
    fun interactionConfirmImportCallsZipImportOnce() = runTest(dispatcher) {
        // Arrange
        val importUseCase = mockk<ImportMemosFromFileUseCase>()
        coEvery { importUseCase(IMPORT_REFERENCE) } returns Unit
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), importUseCase)
        viewModel.onImportFileSelected(IMPORT_REFERENCE)

        // Act
        // Interaction: confirmed ZIP selection reaches the import use case once.
        viewModel.confirmImport()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { importUseCase(IMPORT_REFERENCE) }
    }

    @Test
    fun stateTransitionConfirmImportMaintainsProgressState() = runTest(dispatcher) {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val importUseCase = mockk<ImportMemosFromFileUseCase>()
        coEvery { importUseCase(IMPORT_REFERENCE) } coAnswers { gate.await() }
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), importUseCase)
        viewModel.onImportFileSelected(IMPORT_REFERENCE)

        // Act
        // StateTransition: import stays active until ZIP persistence completes.
        viewModel.confirmImport()
        runCurrent()
        val inProgress = viewModel.uiState.first { it.isImporting }.isImporting
        gate.complete(Unit)
        advanceUntilIdle()
        val completed = viewModel.uiState.first { !it.isImporting }.isImporting

        // Assert
        assertEquals(true to false, inProgress to completed)
    }

    @Test
    fun interactionPrepareExportDoesNotStartWhenImportIsRunning() = runTest(dispatcher) {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val importUseCase = mockk<ImportMemosFromFileUseCase>()
        coEvery { importUseCase(IMPORT_REFERENCE) } coAnswers { gate.await() }
        val repository = FakeMemoExportArchiveRepository()
        val viewModel = viewModel(repository, importUseCase)
        viewModel.onImportFileSelected(IMPORT_REFERENCE)
        viewModel.confirmImport()
        runCurrent()

        // Act
        // Interaction: import ownership blocks export preparation.
        viewModel.prepareExport()
        runCurrent()

        // Assert
        assertEquals(emptyList<ExportData>(), repository.preparedData)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun interactionConfirmImportDoesNotStartWhenExportIsRunning() = runTest(dispatcher) {
        // Arrange
        val prepareGate = CompletableDeferred<Unit>()
        val importUseCase = mockk<ImportMemosFromFileUseCase>(relaxed = true)
        val viewModel = viewModel(
            FakeMemoExportArchiveRepository(prepareGate = prepareGate),
            importUseCase
        )
        viewModel.onImportFileSelected(IMPORT_REFERENCE)
        viewModel.prepareExport()

        // Act
        // Interaction: export ownership blocks confirmed import.
        viewModel.confirmImport()
        runCurrent()

        // Assert
        coVerify(exactly = 0) { importUseCase(any()) }
        prepareGate.complete(Unit)
        advanceUntilIdle()
        viewModel.cancelPreparedExport()
        advanceUntilIdle()
    }

    @Test
    fun flowConfirmImportEmitsSuccessSnackbar() = runTest(dispatcher) {
        // Arrange
        val importUseCase = mockk<ImportMemosFromFileUseCase>()
        coEvery { importUseCase(IMPORT_REFERENCE) } returns Unit
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), importUseCase)
        viewModel.onImportFileSelected(IMPORT_REFERENCE)

        // Act & Assert
        // Flow/Normal: successful ZIP import closes confirmation and emits success.
        viewModel.snackbarEvent.test {
            viewModel.confirmImport()
            advanceUntilIdle()
            assertEquals(SettingsSnackbarUiEvent.ImportSuccess, awaitItem())
        }
        assertEquals(false, viewModel.uiState.value.showImportConfirmDialog)
    }

    @Test
    fun flowConfirmImportShowsGenericErrorDialogWithoutSnackbar() = runTest(dispatcher) {
        // Arrange
        val importUseCase = mockk<ImportMemosFromFileUseCase>()
        coEvery { importUseCase(IMPORT_REFERENCE) } throws IllegalStateException("read failed")
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), importUseCase)
        viewModel.onImportFileSelected(IMPORT_REFERENCE)

        // Act & Assert
        // Flow/Error: unknown import failures are retained as dialog state.
        viewModel.snackbarEvent.test {
            viewModel.confirmImport()
            advanceUntilIdle()
            expectNoEvents()
        }

        // Assert
        assertEquals(
            SettingsImportErrorDialogUiState.Generic,
            viewModel.uiState.first { it.importErrorDialog != null }.importErrorDialog
        )
    }

    @Test
    fun flowConfirmImportShowsAllConflictingTagNames() = runTest(dispatcher) {
        // Arrange
        val importUseCase = mockk<ImportMemosFromFileUseCase>()
        coEvery { importUseCase(IMPORT_REFERENCE) } throws ImportTagNameConflictException(
            listOf(TagName("Home"), TagName("Work"))
        )
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), importUseCase)
        viewModel.onImportFileSelected(IMPORT_REFERENCE)

        // Act
        // Flow/Error: all conflicting names are retained for the dialog.
        viewModel.confirmImport()
        advanceUntilIdle()

        // Assert
        assertEquals(
            SettingsImportErrorDialogUiState.TagNameConflict(listOf("Home", "Work")),
            viewModel.uiState.first { it.importErrorDialog != null }.importErrorDialog
        )
    }

    @Test
    fun flowConfirmImportShowsUnsupportedVersionDialog() = runTest(dispatcher) {
        // Arrange
        val viewModel = importFailingViewModel(MemoImportFailureReason.UNSUPPORTED_VERSION)

        // Act
        // Flow/Error: unsupported ZIP versions have a dedicated dialog.
        viewModel.confirmImport()
        advanceUntilIdle()

        // Assert
        assertEquals(
            SettingsImportErrorDialogUiState.UnsupportedVersion,
            viewModel.uiState.first { it.importErrorDialog != null }.importErrorDialog
        )
    }

    @Test
    fun flowConfirmImportShowsInvalidImageDialog() = runTest(dispatcher) {
        // Arrange
        val viewModel = importFailingViewModel(MemoImportFailureReason.INVALID_IMAGE)

        // Act
        // Flow/Error: corrupt archive images have a dedicated dialog.
        viewModel.confirmImport()
        advanceUntilIdle()

        // Assert
        assertEquals(
            SettingsImportErrorDialogUiState.InvalidImage,
            viewModel.uiState.first { it.importErrorDialog != null }.importErrorDialog
        )
    }

    @Test
    fun stateTransitionDismissImportErrorDialogClearsState() = runTest(dispatcher) {
        // Arrange
        val viewModel = importFailingViewModel(MemoImportFailureReason.INVALID_ARCHIVE)
        viewModel.confirmImport()
        advanceUntilIdle()
        viewModel.uiState.first { it.importErrorDialog != null }

        // Act
        // StateTransition: dismiss releases the retained import failure.
        viewModel.dismissImportErrorDialog()
        advanceUntilIdle()

        // Assert
        assertEquals(
            null,
            viewModel.uiState.first {
                it.importErrorDialog == null
            }.importErrorDialog
        )
    }

    @Test
    fun boundaryConfirmImportDoesNothingWithoutPendingFile() = runTest(dispatcher) {
        // Arrange
        val importUseCase = mockk<ImportMemosFromFileUseCase>(relaxed = true)
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), importUseCase)

        // Act
        // Boundary: confirmation without a selected ZIP is a no-op.
        viewModel.confirmImport()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { importUseCase(any()) }
    }

    @Test
    fun boundaryDismissImportDialogClearsPendingReference() = runTest(dispatcher) {
        // Arrange
        val importUseCase = mockk<ImportMemosFromFileUseCase>(relaxed = true)
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), importUseCase)
        viewModel.onImportFileSelected(IMPORT_REFERENCE)

        // Act
        // Boundary: dismissed selection cannot be confirmed later.
        viewModel.dismissImportConfirmDialog()
        viewModel.confirmImport()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { importUseCase(any()) }
    }

    @Test
    fun normalAppLockAuthenticationSuccessEnablesAppLock() = runTest(dispatcher) {
        // Arrange
        val settings = FakeUserSettingsRepository()
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), settings = settings)

        // Act
        // Normal: successful authentication applies the setting.
        viewModel.onAppLockEnableAuthenticationResult(AppLockAuthenticationUiResult.SUCCEEDED)
        advanceUntilIdle()

        // Assert
        assertEquals(true, settings.observeAppLockEnabled().first())
    }

    @Test
    fun flowAppLockNoCredentialEmitsExpectedSnackbar() = runTest(dispatcher) {
        assertAppLockFailure(
            AppLockAuthenticationUiResult.NO_DEVICE_CREDENTIAL,
            SettingsSnackbarUiEvent.AppLockNoDeviceCredential
        )
    }

    @Test
    fun flowAppLockUnavailableEmitsExpectedSnackbar() = runTest(dispatcher) {
        assertAppLockFailure(
            AppLockAuthenticationUiResult.UNAVAILABLE,
            SettingsSnackbarUiEvent.AppLockUnavailable
        )
    }

    @Test
    fun flowAppLockFailedEmitsExpectedSnackbar() = runTest(dispatcher) {
        assertAppLockFailure(
            AppLockAuthenticationUiResult.FAILED,
            SettingsSnackbarUiEvent.AppLockAuthenticationFailed
        )
    }

    @Test
    fun flowAppLockCanceledEmitsExpectedSnackbar() = runTest(dispatcher) {
        assertAppLockFailure(
            AppLockAuthenticationUiResult.CANCELED,
            SettingsSnackbarUiEvent.AppLockAuthenticationCanceled
        )
    }

    @Test
    fun stateTransitionExpandThemeCollapsesSortOrder() = runTest(dispatcher) {
        // Arrange
        val viewModel = viewModel(FakeMemoExportArchiveRepository())
        viewModel.expandSortOrder()

        // Act
        // StateTransition: only one display dropdown remains expanded.
        viewModel.expandThemeDropdown()
        val state = viewModel.uiState.first { it.themeDropdownExpanded }

        // Assert
        assertEquals(true to false, state.themeDropdownExpanded to state.sortOrderExpanded)
    }

    @Test
    fun stateTransitionExpandSortOrderCollapsesTheme() = runTest(dispatcher) {
        // Arrange
        val viewModel = viewModel(FakeMemoExportArchiveRepository())
        viewModel.expandThemeDropdown()

        // Act
        // StateTransition: only one display dropdown remains expanded.
        viewModel.expandSortOrder()
        val state = viewModel.uiState.first { it.sortOrderExpanded }

        // Assert
        assertEquals(false to true, state.themeDropdownExpanded to state.sortOrderExpanded)
    }

    private fun importFailingViewModel(reason: MemoImportFailureReason): SettingsViewModel {
        val importUseCase = mockk<ImportMemosFromFileUseCase>()
        coEvery { importUseCase(IMPORT_REFERENCE) } throws MemoImportException(reason, "failed")
        return viewModel(FakeMemoExportArchiveRepository(), importUseCase).also {
            it.onImportFileSelected(IMPORT_REFERENCE)
        }
    }

    private suspend fun assertAppLockFailure(
        result: AppLockAuthenticationUiResult,
        expected: SettingsSnackbarUiEvent
    ) {
        // Arrange
        val settings = FakeUserSettingsRepository()
        val viewModel = viewModel(FakeMemoExportArchiveRepository(), settings = settings)

        // Act & Assert
        // Flow: a failed authentication result keeps app lock disabled.
        viewModel.snackbarEvent.test {
            viewModel.onAppLockEnableAuthenticationResult(result)
            assertEquals(expected, awaitItem())
        }
        assertEquals(false, settings.observeAppLockEnabled().first())
    }

    private fun viewModel(
        exportRepository: MemoExportArchiveRepository,
        importUseCase: ImportMemosFromFileUseCase = mockk(relaxed = true),
        settings: FakeUserSettingsRepository = FakeUserSettingsRepository()
    ): SettingsViewModel {
        val exportMemosUseCase = ExportMemosUseCase(
            FakeMemoRepository(),
            FakeTagRepository(),
            MutableTimeProvider(TimestampMillis(1_000L))
        )
        return SettingsViewModel(
            observeThemeModeUseCase = ObserveThemeModeUseCase(settings),
            observeMemoSortOrderUseCase = ObserveMemoSortOrderUseCase(settings),
            observeAppLockEnabledUseCase = ObserveAppLockEnabledUseCase(settings),
            setThemeModeUseCase = SetThemeModeUseCase(settings),
            setMemoSortOrderUseCase = SetMemoSortOrderUseCase(settings),
            setAppLockEnabledUseCase = SetAppLockEnabledUseCase(settings),
            prepareMemoExportUseCase = PrepareMemoExportUseCase(
                exportMemosUseCase,
                exportRepository
            ),
            memoExportArchiveRepository = exportRepository,
            importMemosFromFileUseCase = importUseCase,
            applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
            appVersion = "1.0.0"
        )
    }

    private class FakeMemoExportArchiveRepository(
        private val prepareGate: CompletableDeferred<Unit>? = null,
        private val prepareError: Throwable? = null,
        private val writeError: Throwable? = null,
        private val discardGate: CompletableDeferred<Unit>? = null
    ) : MemoExportArchiveRepository {

        val preparedData = mutableListOf<ExportData>()
        val writes = mutableListOf<Pair<MemoExportToken, ExportFileReference>>()
        val discardedTokens = mutableListOf<MemoExportToken>()

        override suspend fun prepare(data: ExportData): MemoExportToken {
            preparedData += data
            prepareGate?.await()
            prepareError?.let { throw it }
            return TOKEN
        }

        override suspend fun write(token: MemoExportToken, destination: ExportFileReference) {
            writes += token to destination
            writeError?.let { throw it }
        }

        override suspend fun discard(token: MemoExportToken) {
            discardedTokens += token
            discardGate?.await()
        }

        override suspend fun deleteAbandonedPreparedExports() = Unit

        companion object {
            val TOKEN = MemoExportToken("prepared-1")
        }
    }

    private companion object {
        val DESTINATION = ExportFileReference("content://export.zip")
        val IMPORT_REFERENCE = ExportFileReference("content://import.zip")
    }

}
