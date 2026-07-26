package com.appvoyager.litememo.domain.model.value

@JvmInline
value class MemoImportSessionToken private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): MemoImportSessionToken {
            val value = rawValue.trim()
            require(value.isNotBlank()) { "MemoImportSessionToken must not be blank." }
            require(!value.contains('/') && !value.contains('\\')) {
                "MemoImportSessionToken must not contain path separators."
            }
            require(value.none { it.isISOControl() }) {
                "MemoImportSessionToken must not contain control characters."
            }
            require(value != "." && value != "..") {
                "MemoImportSessionToken must not be a relative path reference."
            }
            return MemoImportSessionToken(value)
        }
    }

}
