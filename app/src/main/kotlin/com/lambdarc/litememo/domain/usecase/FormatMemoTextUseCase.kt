package com.lambdarc.litememo.domain.usecase

import javax.inject.Inject

class FormatMemoTextUseCase @Inject constructor() {

    operator fun invoke(title: String, body: String): String? {
        val t = title.trim()
        val b = body.trim()
        return when {
            t.isNotEmpty() && b.isNotEmpty() -> "$t\n\n$b"
            t.isNotEmpty() -> t
            b.isNotEmpty() -> b
            else -> null
        }
    }

}
