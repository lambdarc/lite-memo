package com.appvoyager.litememo.domain.model.value

@JvmInline
value class MemoExportToken(val value: String) {

    init {
        require(value.isNotBlank()) { "MemoExportToken must not be blank." }
    }

}
