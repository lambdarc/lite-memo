package com.lambdarc.litememo.domain.model.value

@JvmInline
value class MemoBody private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): MemoBody = MemoBody(rawValue)
    }

}
