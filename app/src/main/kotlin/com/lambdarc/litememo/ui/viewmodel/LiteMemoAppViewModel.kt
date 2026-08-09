package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.usecase.RestoreMemoFromTrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiteMemoAppViewModel @Inject constructor(
    private val restoreMemoFromTrashUseCase: RestoreMemoFromTrashUseCase
) : ViewModel() {

    private val _restoreMemoErrorEvent = Channel<Unit>(Channel.CONFLATED)
    val restoreMemoErrorEvent = _restoreMemoErrorEvent.receiveAsFlow()

    fun restoreMemo(memoId: MemoId) {
        viewModelScope.launch {
            try {
                restoreMemoFromTrashUseCase(memoId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _restoreMemoErrorEvent.trySend(Unit)
            }
        }
    }

}
