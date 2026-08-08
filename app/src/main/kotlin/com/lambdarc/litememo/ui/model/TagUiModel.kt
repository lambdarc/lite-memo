package com.lambdarc.litememo.ui.model

import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagId

data class TagUiModel(val id: TagId, val name: String, val colorArgb: Long) {

    companion object {
        fun fromDomain(tag: Tag) = TagUiModel(
            id = tag.id,
            name = tag.name.value,
            colorArgb = tag.color.argb
        )
    }

}
