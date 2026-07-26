package com.appvoyager.litememo.data.export

import java.io.PushbackInputStream

internal object MemoArchiveSignature {

    const val LENGTH = 4

    fun matches(source: PushbackInputStream): Boolean {
        val header = ByteArray(LENGTH)
        var filled = 0
        while (filled < header.size) {
            val read = source.read(header, filled, header.size - filled)
            if (read < 0) break
            filled += read
        }
        if (filled > 0) source.unread(header, 0, filled)

        return filled == header.size && bigEndianInt(header) == LOCAL_FILE_HEADER
    }

    private fun bigEndianInt(bytes: ByteArray): Int {
        var value = 0
        bytes.forEach { byte -> value = (value shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK) }
        return value
    }

}

private const val LOCAL_FILE_HEADER = 0x504B0304
private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF
