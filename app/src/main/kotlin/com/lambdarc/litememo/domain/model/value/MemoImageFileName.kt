package com.lambdarc.litememo.domain.model.value

@JvmInline
value class MemoImageFileName private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): MemoImageFileName {
            val value = rawValue.trim()
            require(value.isNotBlank()) { "MemoImageFileName must not be blank." }
            require(!value.contains('/') && !value.contains('\\')) {
                "MemoImageFileName must not contain path separators."
            }
            require(value.none { it.isISOControl() }) {
                "MemoImageFileName must not contain control characters."
            }
            require(value != "." && value != "..") {
                "MemoImageFileName must not be a relative path reference."
            }
            return MemoImageFileName(value)
        }
    }

}
