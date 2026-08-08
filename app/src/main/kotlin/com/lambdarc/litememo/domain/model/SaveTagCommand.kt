package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TagColor
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName

data class SaveTagCommand(val id: TagId? = null, val name: TagName, val color: TagColor)
