package com.lambdarc.litememo.data.provider

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.provider.MemoIdProvider
import java.util.UUID

class UuidMemoIdProvider : MemoIdProvider {

    override fun newMemoId(): MemoId = MemoId(UUID.randomUUID().toString())

}
