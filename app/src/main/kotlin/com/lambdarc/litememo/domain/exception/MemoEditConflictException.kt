package com.lambdarc.litememo.domain.exception

import com.lambdarc.litememo.domain.model.value.MemoId

class MemoEditConflictException(memoId: MemoId) :
    IllegalStateException("Memo was modified since it was read: ${memoId.value}")
