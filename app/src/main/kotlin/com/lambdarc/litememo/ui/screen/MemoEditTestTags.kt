package com.lambdarc.litememo.ui.screen

object MemoEditTestTags {
    const val TITLE_INPUT = "memoEditTitleInput"
    const val BODY_INPUT = "memoEditBodyInput"
    const val ATTACH_IMAGE_BUTTON = "memoEditAttachImageButton"
    const val IMAGE_LIST = "memoEditImageList"

    fun imageItem(id: String) = "memoEditImageItem_$id"

    fun removeImageButton(id: String) = "memoEditRemoveImageButton_$id"
}
