package com.lambdarc.litememo.domain.provider

import com.lambdarc.litememo.domain.model.value.TagId

interface TagIdProvider {

    fun newTagId(): TagId

}
