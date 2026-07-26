package com.appvoyager.litememo.data.export

internal object MemoArchiveLayout {

    const val VERSION = 1
    const val MANIFEST_ENTRY_NAME = "manifest.json"

    private const val IMAGE_ENTRY_PREFIX = "images/"
    private const val IMAGE_ENTRY_NUMBER_LENGTH = 8
    private const val PATH_SEPARATOR = '/'

    private val imageEntryPattern =
        Regex("^$IMAGE_ENTRY_PREFIX\\d{$IMAGE_ENTRY_NUMBER_LENGTH}$")

    fun imageEntryName(index: Int): String {
        require(index > 0) { "Archive image index must be positive." }
        val number = index.toString().padStart(IMAGE_ENTRY_NUMBER_LENGTH, '0')
        require(number.length == IMAGE_ENTRY_NUMBER_LENGTH) {
            "Archive image index must fit in $IMAGE_ENTRY_NUMBER_LENGTH digits."
        }
        return IMAGE_ENTRY_PREFIX + number
    }

    fun isImageEntryName(name: String): Boolean = imageEntryPattern.matches(name)

    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.contains('\\') || name.contains(':')) return false
        if (name.any { it.isISOControl() }) return false

        val segments = name.split(PATH_SEPARATOR)
        return segments.none { it.isEmpty() || it == "." || it == ".." }
    }

}
