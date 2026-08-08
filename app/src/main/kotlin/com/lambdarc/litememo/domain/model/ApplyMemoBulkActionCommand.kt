package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoId

data class ApplyMemoBulkActionCommand(val memoIds: List<MemoId>, val action: MemoBulkAction)
