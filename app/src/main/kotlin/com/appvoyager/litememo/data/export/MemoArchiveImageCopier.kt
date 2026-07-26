package com.appvoyager.litememo.data.export

import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal object MemoArchiveImageCopier {

    fun copyVerified(source: InputStream, target: OutputStream, metadata: MemoImageExportDto) {
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L

        while (true) {
            val read = source.read(buffer)
            if (read < 0) break

            totalBytes += read
            if (totalBytes > metadata.sizeBytes) {
                archiveFailure(
                    MemoArchiveFailureReason.SIZE_MISMATCH,
                    "Archive image is larger than the declared ${metadata.sizeBytes} bytes."
                )
            }
            digest.update(buffer, 0, read)
            target.write(buffer, 0, read)
        }

        if (totalBytes != metadata.sizeBytes) {
            archiveFailure(
                MemoArchiveFailureReason.SIZE_MISMATCH,
                "Archive image is smaller than the declared ${metadata.sizeBytes} bytes."
            )
        }
        if (!toHex(digest.digest()).equals(metadata.sha256, ignoreCase = true)) {
            archiveFailure(
                MemoArchiveFailureReason.CHECKSUM_MISMATCH,
                "Archive image does not match the declared SHA-256 digest."
            )
        }
    }

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt()
            append(HEX_DIGITS[(value shr HEX_DIGIT_BITS) and HEX_DIGIT_MASK])
            append(HEX_DIGITS[value and HEX_DIGIT_MASK])
        }
    }

}

private const val DIGEST_ALGORITHM = "SHA-256"
private const val HEX_DIGITS = "0123456789abcdef"
private const val HEX_DIGIT_BITS = 4
private const val HEX_DIGIT_MASK = 0x0F
