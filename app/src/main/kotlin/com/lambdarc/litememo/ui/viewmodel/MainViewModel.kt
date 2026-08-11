package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lambdarc.litememo.domain.model.ThemeMode
import com.lambdarc.litememo.domain.usecase.CompleteTutorialUseCase
import com.lambdarc.litememo.domain.usecase.ObserveAppLockEnabledUseCase
import com.lambdarc.litememo.domain.usecase.ObserveThemeModeUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTutorialCompletedUseCase
import com.lambdarc.litememo.domain.usecase.PurgeExpiredTrashedMemosUseCase
import com.lambdarc.litememo.ui.auth.AppLockAuthenticationUiResult
import com.lambdarc.litememo.ui.navigation.WidgetNavRequest
import com.lambdarc.litememo.ui.state.AppLockUiMessage
import com.lambdarc.litememo.ui.state.AppLockUiState
import com.lambdarc.litememo.ui.state.AppLockUiStatus
import com.lambdarc.litememo.ui.state.TutorialUiState
import com.lambdarc.litememo.ui.state.TutorialUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val observeAppLockEnabledUseCase: ObserveAppLockEnabledUseCase,
    private val observeTutorialCompletedUseCase: ObserveTutorialCompletedUseCase,
    private val completeTutorialUseCase: CompleteTutorialUseCase,
    private val purgeExpiredTrashedMemosUseCase: PurgeExpiredTrashedMemosUseCase
) : ViewModel() {

    val themeMode: Flow<ThemeMode> = observeThemeModeUseCase()

    private val _appLockUiState = MutableStateFlow(AppLockUiState())
    val appLockUiState: StateFlow<AppLockUiState> = _appLockUiState.asStateFlow()

    private val _authenticationRequestEvent = Channel<Unit>(Channel.CONFLATED)
    val authenticationRequestEvent = _authenticationRequestEvent.receiveAsFlow()

    private val _secureScreenEnabled = MutableStateFlow(false)
    val secureScreenEnabled: StateFlow<Boolean> = _secureScreenEnabled.asStateFlow()

    private val _tutorialUiState = MutableStateFlow(TutorialUiState())
    val tutorialUiState: StateFlow<TutorialUiState> = _tutorialUiState.asStateFlow()

    private val _widgetNavEvent = Channel<WidgetNavRequest>(Channel.CONFLATED)
    val widgetNavEvent = _widgetNavEvent.receiveAsFlow()

    private var appLockEnabled: Boolean? = null
    private var expiredTrashedMemosPurged = false

    init {
        observeAppLockEnabled()
        observeTutorialCompleted()
    }

    fun onAppStarted() {
        if (appLockEnabled != true) return
        val status = _appLockUiState.value.status
        if (status == AppLockUiStatus.LOCKED || status == AppLockUiStatus.UNAVAILABLE) {
            requestUnlock()
        }
    }

    fun onAppStopped() {
        if (
            appLockEnabled == true &&
            _appLockUiState.value.status != AppLockUiStatus.AUTHENTICATING
        ) {
            _appLockUiState.value = AppLockUiState(status = AppLockUiStatus.LOCKED)
        }
    }

    fun requestUnlock() {
        if (appLockEnabled != true) return
        if (_appLockUiState.value.status == AppLockUiStatus.AUTHENTICATING) return

        _appLockUiState.value = AppLockUiState(status = AppLockUiStatus.AUTHENTICATING)
        _authenticationRequestEvent.trySend(Unit)
    }

    fun onAuthenticationResult(result: AppLockAuthenticationUiResult) {
        _appLockUiState.value = when (result) {
            AppLockAuthenticationUiResult.SUCCEEDED -> AppLockUiState(
                status = AppLockUiStatus.UNLOCKED
            )

            AppLockAuthenticationUiResult.FAILED -> AppLockUiState(
                status = AppLockUiStatus.LOCKED,
                message = AppLockUiMessage.AUTHENTICATION_FAILED
            )

            AppLockAuthenticationUiResult.CANCELED -> AppLockUiState(
                status = AppLockUiStatus.LOCKED,
                message = AppLockUiMessage.AUTHENTICATION_CANCELED
            )

            AppLockAuthenticationUiResult.NO_DEVICE_CREDENTIAL -> AppLockUiState(
                status = AppLockUiStatus.UNAVAILABLE,
                message = AppLockUiMessage.NO_DEVICE_CREDENTIAL
            )

            AppLockAuthenticationUiResult.UNAVAILABLE -> AppLockUiState(
                status = AppLockUiStatus.UNAVAILABLE,
                message = AppLockUiMessage.AUTHENTICATION_UNAVAILABLE
            )
        }
        if (result == AppLockAuthenticationUiResult.SUCCEEDED) {
            purgeExpiredTrashedMemosOnce()
        }
    }

    fun requestWidgetNav(request: WidgetNavRequest) {
        _widgetNavEvent.trySend(request)
    }

    fun completeTutorial() {
        _tutorialUiState.value = TutorialUiState(status = TutorialUiStatus.HIDDEN)
        viewModelScope.launch {
            try {
                completeTutorialUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private fun observeAppLockEnabled() {
        viewModelScope.launch {
            observeAppLockEnabledUseCase().collect { enabled ->
                val previous = appLockEnabled
                appLockEnabled = enabled
                _secureScreenEnabled.value = enabled

                when {
                    !enabled -> {
                        _appLockUiState.value = AppLockUiState(status = AppLockUiStatus.UNLOCKED)
                        purgeExpiredTrashedMemosOnce()
                    }

                    previous == null -> {
                        requestUnlock()
                    }

                    previous == false -> {
                        _appLockUiState.update { state ->
                            state.copy(status = AppLockUiStatus.UNLOCKED, message = null)
                        }
                        purgeExpiredTrashedMemosOnce()
                    }
                }
            }
        }
    }

    private fun observeTutorialCompleted() {
        viewModelScope.launch {
            observeTutorialCompletedUseCase().collect { completed ->
                _tutorialUiState.update { state -> state.next(completed) }
            }
        }
    }

    private fun purgeExpiredTrashedMemosOnce() {
        if (expiredTrashedMemosPurged) return
        expiredTrashedMemosPurged = true
        viewModelScope.launch {
            try {
                purgeExpiredTrashedMemosUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

}
