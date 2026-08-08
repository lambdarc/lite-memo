package com.lambdarc.litememo.domain.model.value

@JvmInline
value class TagName private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): TagName {
            val value = rawValue.trim()
            require(value.isNotEmpty()) { "TagName must not be blank." }
            return TagName(value)
        }
    }

}
