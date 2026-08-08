package com.lambdarc.litememo.domain.exception

import com.lambdarc.litememo.domain.model.value.TagName

class ImportTagNameConflictException(val tagNames: List<TagName>) :
    IllegalArgumentException(
        "Import contains conflicting tag names: ${tagNames.size} name(s)."
    )
