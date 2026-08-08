package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TagId

sealed class MemoBulkAction {

    data object MoveToTrash : MemoBulkAction()

    data class SetFavorite(val isFavorite: Boolean) : MemoBulkAction()

    data class AddTag(val tagId: TagId) : MemoBulkAction()

    data class RemoveTag(val tagId: TagId) : MemoBulkAction()

    companion object {
        fun moveToTrash(): MemoBulkAction = MoveToTrash

        fun setFavorite(isFavorite: Boolean): MemoBulkAction = SetFavorite(isFavorite)

        fun addTag(tagId: TagId): MemoBulkAction = AddTag(tagId)

        fun removeTag(tagId: TagId): MemoBulkAction = RemoveTag(tagId)
    }

}
