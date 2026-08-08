package com.lambdarc.litememo.domain.model.value

@JvmInline
value class MemoId private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): MemoId {
            require(rawValue.isNotBlank()) { "MemoId must not be blank." }
            return MemoId(rawValue.trim())
        }
    }

}
