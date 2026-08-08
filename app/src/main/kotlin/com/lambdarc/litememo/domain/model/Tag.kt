package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TagColor
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.model.value.TimestampMillis

data class Tag(
    val id: TagId,
    val name: TagName,
    val color: TagColor,
    val createdAt: TimestampMillis
)
