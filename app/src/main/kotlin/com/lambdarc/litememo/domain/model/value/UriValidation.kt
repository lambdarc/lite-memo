package com.lambdarc.litememo.domain.model.value

import java.net.URI

fun String.isAbsoluteUri(): Boolean = runCatching { URI(this) }
    .getOrNull()
    ?.let { uri -> uri.isAbsolute && !uri.rawSchemeSpecificPart.isNullOrBlank() }
    ?: false
