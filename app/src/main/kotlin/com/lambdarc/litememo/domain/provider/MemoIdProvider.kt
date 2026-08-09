package com.lambdarc.litememo.domain.provider

import com.lambdarc.litememo.domain.model.value.MemoId

interface MemoIdProvider {

    fun newMemoId(): MemoId

}
