package com.lambdarc.litememo.ui.widget.data

import app.cash.turbine.test
import com.lambdarc.litememo.domain.memoSummaryFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.usecase.ObserveAppLockEnabledUseCase
import com.lambdarc.litememo.domain.usecase.ObserveRecentMemosUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class WidgetMemoLoaderTest {

    private val observeRecentMemosUseCase = mockk<ObserveRecentMemosUseCase>()
    private val observeAppLockEnabledUseCase = mockk<ObserveAppLockEnabledUseCase>()
    private val loader = WidgetMemoLoader(
        observeRecentMemosUseCase = observeRecentMemosUseCase,
        observeAppLockEnabledUseCase = observeAppLockEnabledUseCase
    )

    init {
        every { observeAppLockEnabledUseCase.invoke() } returns flowOf(false)
    }

    @Test
    fun normalObserveRecentMapsUseCaseOrder() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(
                memoSummaryFixture(id = "a", title = "A"),
                memoSummaryFixture(id = "b", title = "B"),
                memoSummaryFixture(id = "c", title = "C")
            )
        )

        // Act
        val items = loader.observeRecent().first()

        // Assert
        assertEquals(listOf(MemoId("a"), MemoId("b"), MemoId("c")), items.map { it.id })
    }

    @Test
    fun interactionObserveRecentRequestsWidgetDisplayLimit() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(emptyList())

        // Act
        // Interaction: observing uses the same fixed limit as the widget scroll content
        loader.observeRecent().first()

        // Assert
        verify { observeRecentMemosUseCase.invoke(8) }
    }

    @Test
    fun normalObserveRecentReturnsEmptyForNoMemos() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(emptyList())

        // Act
        val items = loader.observeRecent().first()

        // Assert
        assertTrue(items.isEmpty())
    }

    @Test
    fun normalObserveRecentEmitsMappedItems() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(
                memoSummaryFixture(id = "a", title = "A"),
                memoSummaryFixture(id = "b", title = "B")
            )
        )

        // Act
        val items = loader.observeRecent().first()

        // Assert
        assertEquals(listOf(MemoId("a"), MemoId("b")), items.map { it.id })
    }

    @Test
    fun flowObserveRecentHidesItemsWhenAppLockChangesFromDisabledToEnabled() = runTest {
        // Arrange
        val appLockEnabled = MutableStateFlow(false)
        every { observeAppLockEnabledUseCase.invoke() } returns appLockEnabled
        every { observeRecentMemosUseCase.invoke(any()) } returns MutableStateFlow(
            listOf(memoSummaryFixture(id = "a", title = "A"))
        )

        // Act & Assert
        // Flow: enabling app lock hides content even while the app process remains active
        loader.observeRecent().test {
            assertEquals(listOf(MemoId("a")), awaitItem().map { it.id })
            appLockEnabled.value = true
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun flowObserveRecentRestoresItemsWhenAppLockChangesFromEnabledToDisabled() = runTest {
        // Arrange
        val appLockEnabled = MutableStateFlow(true)
        every { observeAppLockEnabledUseCase.invoke() } returns appLockEnabled
        every { observeRecentMemosUseCase.invoke(any()) } returns MutableStateFlow(
            listOf(memoSummaryFixture(id = "a", title = "A"))
        )

        // Act & Assert
        // Flow: disabling app lock restores the current widget content
        loader.observeRecent().test {
            assertTrue(awaitItem().isEmpty())
            appLockEnabled.value = false
            assertEquals(listOf(MemoId("a")), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun interactionObserveRecentDoesNotReadMemosWhileAppLockIsEnabled() = runTest {
        // Arrange
        every { observeAppLockEnabledUseCase.invoke() } returns flowOf(true)

        // Act
        // Interaction: locked widgets emit an empty state without waiting for memo storage
        val items = loader.observeRecent().first()

        // Assert
        assertTrue(items.isEmpty())
        verify(exactly = 0) { observeRecentMemosUseCase.invoke(any()) }
    }

    @Test
    fun flowObserveRecentDoesNotEmitItemsBeforeAppLockSettingIsKnown() = runTest {
        // Arrange
        val appLockEnabled = MutableSharedFlow<Boolean>()
        every { observeAppLockEnabledUseCase.invoke() } returns appLockEnabled
        every { observeRecentMemosUseCase.invoke(any()) } returns MutableStateFlow(
            listOf(memoSummaryFixture(id = "a", title = "A"))
        )

        // Act & Assert
        // Flow: memo content remains hidden until the app lock setting emits
        loader.observeRecent().test {
            expectNoEvents()
            verify(exactly = 0) { observeRecentMemosUseCase.invoke(any()) }
            appLockEnabled.emit(false)
            assertEquals(listOf(MemoId("a")), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun errorObserveRecentHidesItemsWhenAppLockSettingReadFails() = runTest {
        // Arrange
        every { observeAppLockEnabledUseCase.invoke() } returns flow {
            throw IOException("read failed")
        }
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "a", title = "A"))
        )

        // Act
        // Error: a settings read failure fails closed
        val items = loader.observeRecent().first()

        // Assert
        assertTrue(items.isEmpty())
    }

    @Test
    fun normalFavoriteFlagIsMapped() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = "A", isFavorite = true))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertTrue(item.isFavorite)
    }

    @Test
    fun normalTitledMemoUsesTitleAsPrimaryAndBodyAsSnippet() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = "見出し", body = "本文1\n本文2"))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertAll(
            { assertEquals("見出し", item.title) },
            { assertEquals("本文1 本文2", item.snippet) }
        )
    }

    @Test
    fun boundaryBodyOnlyMemoUsesFirstBodyLineAsPrimary() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = "", body = "先頭行\n2行目"))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertAll(
            { assertEquals("先頭行", item.title) },
            { assertEquals("2行目", item.snippet) }
        )
    }

    @Test
    fun boundaryLeadingWhitespaceDoesNotConsumeBodyScanLimit() = runTest {
        // Arrange
        val body = " ".repeat(600) + "\n\nVisible body"
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = "", body = body))
        )

        // Act
        // Boundary: leading whitespace is removed before applying the body scan limit
        val item = loader.observeRecent().first().single()

        // Assert
        assertEquals("Visible body", item.title)
    }

    @Test
    fun boundaryUntitledSingleLongLineKeepsRemainderInSnippet() = runTest {
        // Arrange
        // Boundary: untitled single line longer than the title limit must not lose the tail
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = "", body = "x".repeat(100)))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertAll(
            { assertEquals("x".repeat(50), item.title) },
            { assertEquals("x".repeat(50), item.snippet) }
        )
    }

    @Test
    fun boundaryLongTitleTruncatedToMax() = runTest {
        // Arrange
        val longTitle = "あ".repeat(100)
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = longTitle, body = ""))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertEquals(50, item.title.length)
    }

    @Test
    fun boundaryLongSnippetTruncatedToMax() = runTest {
        // Arrange
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = "T", body = "c".repeat(200)))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertEquals(80, item.snippet.length)
    }

    @Test
    fun boundarySurrogatePairTitleIsNotSplit() = runTest {
        // Arrange
        // Boundary: an emoji straddling the 50-char limit must not leave a lone surrogate
        val title = "a".repeat(49) + "😀"
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = title, body = ""))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertAll(
            { assertEquals("a".repeat(49), item.title) },
            { assertFalse(item.title.last().isHighSurrogate()) }
        )
    }

    @Test
    fun boundarySurrogatePairSnippetIsNotSplit() = runTest {
        // Arrange
        val body = "b".repeat(79) + "😀"
        every { observeRecentMemosUseCase.invoke(any()) } returns flowOf(
            listOf(memoSummaryFixture(id = "m", title = "T", body = body))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertAll(
            { assertEquals("b".repeat(79), item.snippet) },
            { assertFalse(item.snippet.last().isHighSurrogate()) }
        )
    }
}
