package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId

data class MemoImage(val id: MemoImageId, val fileName: MemoImageFileName)
