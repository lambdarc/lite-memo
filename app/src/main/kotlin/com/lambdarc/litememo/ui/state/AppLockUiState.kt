package com.lambdarc.litememo.ui.state

data class AppLockUiState(
    val status: AppLockUiStatus = AppLockUiStatus.LOADING,
    val message: AppLockUiMessage? = null
) {
    val canShowAppContent: Boolean
        get() = status == AppLockUiStatus.UNLOCKED
}

enum class AppLockUiStatus {
    LOADING,
    UNLOCKED,
    LOCKED,
    AUTHENTICATING,
    UNAVAILABLE
}

enum class AppLockUiMessage {
    AUTHENTICATION_FAILED,
    AUTHENTICATION_CANCELED,
    NO_DEVICE_CREDENTIAL,
    AUTHENTICATION_UNAVAILABLE
}
