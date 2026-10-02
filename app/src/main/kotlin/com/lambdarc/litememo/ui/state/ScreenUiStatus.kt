package com.lambdarc.litememo.ui.state

enum class ScreenUiStatus {
    LOADING,
    ERROR,
    CONTENT;

    companion object {
        fun loaded(hasError: Boolean): ScreenUiStatus = if (hasError) ERROR else CONTENT
    }
}
