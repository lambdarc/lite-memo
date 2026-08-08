package com.lambdarc.litememo.domain.model.value

@JvmInline
value class MemoTitle private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): MemoTitle = MemoTitle(rawValue.trim())
    }

}
