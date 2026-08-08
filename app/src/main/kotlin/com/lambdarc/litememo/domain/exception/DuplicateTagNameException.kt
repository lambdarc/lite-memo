package com.lambdarc.litememo.domain.exception

import com.lambdarc.litememo.domain.model.value.TagName

class DuplicateTagNameException(tagName: TagName) :
    IllegalArgumentException("Tag name already exists: ${tagName.value}")
