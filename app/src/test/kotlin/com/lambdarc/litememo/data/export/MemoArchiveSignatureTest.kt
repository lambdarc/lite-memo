package com.lambdarc.litememo.data.export

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PushbackInputStream

class MemoArchiveSignatureTest {

    @Test
    fun normalMatchesAcceptsLocalFileHeaderFollowedByContent() {
        // Arrange
        val source = pushbackOf(VALID_HEADER + byteArrayOf(1, 2, 3))

        // Act
        // Normal: the local file header signature identifies a ZIP archive
        val matches = MemoArchiveSignature.matches(source)

        // Assert
        assertTrue(matches)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3])
    fun boundaryMatchesRejectsInputShorterThanSignature(length: Int) {
        // Arrange
        val source = pushbackOf(VALID_HEADER.copyOf(length))

        // Act
        // Boundary: every strict prefix of the valid header is too short to match
        val matches = MemoArchiveSignature.matches(source)

        // Assert
        assertFalse(matches)
    }

    @Test
    fun boundaryMatchesRejectsEndOfCentralDirectorySignature() {
        // Arrange
        val source = pushbackOf(byteArrayOf(0x50, 0x4B, 0x05, 0x06))

        // Act
        // Boundary: the end-of-central-directory signature is not a local file header
        val matches = MemoArchiveSignature.matches(source)

        // Assert
        assertFalse(matches)
    }

    @Test
    fun normalMatchesUnreadsHeaderWhenSignatureMatches() {
        // Arrange
        val bytes = VALID_HEADER + byteArrayOf(9, 8, 7, 6)
        val source = pushbackOf(bytes)

        // Act
        // Normal: the probed bytes are pushed back so the ZIP reader sees the full stream
        MemoArchiveSignature.matches(source)

        // Assert
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test
    fun boundaryMatchesUnreadsAllBytesWhenInputIsTooShort() {
        // Arrange
        val bytes = byteArrayOf(0x50, 0x4B, 0x03)
        val source = pushbackOf(bytes)

        // Act
        // Boundary: a partial header is pushed back in full when the match fails
        MemoArchiveSignature.matches(source)

        // Assert
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test
    fun boundaryMatchesAcceptsSourceReturningOneByteAtATime() {
        // Arrange
        val bytes = VALID_HEADER + byteArrayOf(1, 2, 3)
        val source = PushbackInputStream(SingleByteInputStream(bytes), MemoArchiveSignature.LENGTH)

        // Act
        // Boundary: short reads are accumulated until the signature length is filled
        val matches = MemoArchiveSignature.matches(source)

        // Assert
        assertTrue(matches)
    }

    private fun pushbackOf(bytes: ByteArray) =
        PushbackInputStream(ByteArrayInputStream(bytes), MemoArchiveSignature.LENGTH)
}

private val VALID_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

private class SingleByteInputStream(bytes: ByteArray) : InputStream() {
    private val delegate = ByteArrayInputStream(bytes)

    override fun read(): Int = delegate.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, minOf(len, 1))
}
