package com.lambdarc.litememo.ui.state

data class TutorialUiState(val status: TutorialUiStatus = TutorialUiStatus.LOADING) {
    fun next(completed: Boolean): TutorialUiState = when {
        completed -> copy(status = TutorialUiStatus.HIDDEN)
        status == TutorialUiStatus.LOADING -> copy(status = TutorialUiStatus.VISIBLE)
        else -> this
    }
}

enum class TutorialUiStatus {
    LOADING,
    VISIBLE,
    HIDDEN
}
