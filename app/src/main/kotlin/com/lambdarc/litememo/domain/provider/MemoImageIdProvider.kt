package com.lambdarc.litememo.domain.provider

import com.lambdarc.litememo.domain.model.value.MemoImageId

interface MemoImageIdProvider {

    fun newMemoImageId(): MemoImageId

}
