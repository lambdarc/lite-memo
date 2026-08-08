package com.lambdarc.litememo.domain.model.value

@JvmInline
value class TagColor(val argb: Long) {

    init {
        require(argb in MIN_ARGB..MAX_ARGB) { "TagColor must be a 32-bit ARGB value." }
    }

    companion object {
        private const val MIN_ARGB = 0x00000000L
        private const val MAX_ARGB = 0xFFFFFFFFL
    }

}
