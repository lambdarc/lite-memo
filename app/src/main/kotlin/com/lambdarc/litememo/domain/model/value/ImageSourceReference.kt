package com.lambdarc.litememo.domain.model.value

@JvmInline
value class ImageSourceReference private constructor(val value: String) {

    companion object {
        operator fun invoke(rawValue: String): ImageSourceReference {
            val value = rawValue.trim()
            require(value.isNotBlank()) { "ImageSourceReference must not be blank." }
            require(value.isAbsoluteUri()) {
                "ImageSourceReference must be a valid URI."
            }
            return ImageSourceReference(value)
        }
    }

}
