package com.lambdarc.litememo.domain.model.value

@JvmInline
value class MemoImageId private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): MemoImageId {
            require(rawValue.isNotBlank()) { "MemoImageId must not be blank." }
            return MemoImageId(rawValue.trim())
        }
    }

}
