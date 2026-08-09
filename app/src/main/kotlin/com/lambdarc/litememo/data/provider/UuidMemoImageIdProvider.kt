package com.lambdarc.litememo.data.provider

import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.provider.MemoImageIdProvider
import java.util.UUID

class UuidMemoImageIdProvider : MemoImageIdProvider {

    override fun newMemoImageId(): MemoImageId = MemoImageId(UUID.randomUUID().toString())

}
