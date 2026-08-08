package com.lambdarc.litememo.data.provider

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.provider.TagIdProvider
import java.util.UUID

class UuidTagIdProvider : TagIdProvider {

    override fun newTagId(): TagId = TagId(UUID.randomUUID().toString())

}
