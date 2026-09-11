package com.lambdarc.litememo.ui.widget.data

import app.cash.turbine.test
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.repository.FakeAppLockSettingsRepository
import com.lambdarc.litememo.domain.usecase.ObserveAppLockEnabledUseCase
import com.lambdarc.litememo.domain.usecase.ObserveRecentMemosUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class WidgetMemoLoaderTest {

    @Test
    fun normalObserveRecentMapsUseCaseOrder() = runTest {
        // Arrange
        val loader = loader(
            memoRepository = memoRepository(
                memoFixture(id = "a", title = "A"),
                memoFixture(id = "b", title = "B"),
                memoFixture(id = "c", title = "C")
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
        val memoRepository = memoRepository()
        val loader = loader(memoRepository = memoRepository)

        // Act
        // Interaction: observing uses the same fixed limit as the widget scroll content
        loader.observeRecent().first()

        // Assert
        assertEquals(listOf(8), memoRepository.recentLimits)
    }

    @Test
    fun normalObserveRecentReturnsEmptyForNoMemos() = runTest {
        // Arrange
        val loader = loader()

        // Act
        val items = loader.observeRecent().first()

        // Assert
        assertTrue(items.isEmpty())
    }

    @Test
    fun normalObserveRecentEmitsMappedItems() = runTest {
        // Arrange
        val loader = loader(
            memoRepository = memoRepository(
                memoFixture(id = "a", title = "A"),
                memoFixture(id = "b", title = "B")
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
        val appLockSettings = FakeAppLockSettingsRepository()
        val loader = loader(
            memoRepository = memoRepository(memoFixture(id = "a", title = "A")),
            appLockSettings = appLockSettings
        )

        // Act & Assert
        // Flow: enabling app lock hides content even while the app process remains active
        loader.observeRecent().test {
            assertEquals(listOf(MemoId("a")), awaitItem().map { it.id })
            appLockSettings.setAppLockEnabled(true)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun flowObserveRecentRestoresItemsWhenAppLockChangesFromEnabledToDisabled() = runTest {
        // Arrange
        val appLockSettings = FakeAppLockSettingsRepository()
        appLockSettings.setAppLockEnabled(true)
        val loader = loader(
            memoRepository = memoRepository(memoFixture(id = "a", title = "A")),
            appLockSettings = appLockSettings
        )

        // Act & Assert
        // Flow: disabling app lock restores the current widget content
        loader.observeRecent().test {
            assertTrue(awaitItem().isEmpty())
            appLockSettings.setAppLockEnabled(false)
            assertEquals(listOf(MemoId("a")), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun interactionObserveRecentDoesNotReadMemosWhileAppLockIsEnabled() = runTest {
        // Arrange
        val appLockSettings = FakeAppLockSettingsRepository()
        appLockSettings.setAppLockEnabled(true)
        val memoRepository = memoRepository(memoFixture(id = "a", title = "A"))
        val loader = loader(memoRepository = memoRepository, appLockSettings = appLockSettings)

        // Act
        // Interaction: locked widgets emit an empty state without waiting for memo storage
        val items = loader.observeRecent().first()

        // Assert
        assertAll(
            { assertTrue(items.isEmpty()) },
            { assertEquals(emptyList<Int>(), memoRepository.recentLimits) }
        )
    }

    @Test
    fun flowObserveRecentDoesNotEmitItemsBeforeAppLockSettingIsKnown() = runTest {
        // Arrange
        val appLockSettings = FakeAppLockSettingsRepository(deferInitialEmission = true)
        val memoRepository = memoRepository(memoFixture(id = "a", title = "A"))
        val loader = loader(memoRepository = memoRepository, appLockSettings = appLockSettings)

        // Act & Assert
        // Flow: memo content remains hidden until the app lock setting emits
        loader.observeRecent().test {
            expectNoEvents()
            assertEquals(emptyList<Int>(), memoRepository.recentLimits)
            appLockSettings.setAppLockEnabled(false)
            assertEquals(listOf(MemoId("a")), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun errorObserveRecentHidesItemsWhenAppLockSettingReadFails() = runTest {
        // Arrange
        val appLockSettings = FakeAppLockSettingsRepository()
        appLockSettings.observeError = IOException("read failed")
        val loader = loader(
            memoRepository = memoRepository(memoFixture(id = "a", title = "A")),
            appLockSettings = appLockSettings
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
        val loader = loader(
            memoRepository = memoRepository(
                memoFixture(id = "m", title = "A", isFavorite = true)
            )
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertTrue(item.isFavorite)
    }

    @Test
    fun normalTitledMemoUsesTitleAsPrimaryAndBodyAsSnippet() = runTest {
        // Arrange
        val loader = loader(
            memoRepository = memoRepository(
                memoFixture(id = "m", title = "見出し", body = "本文1\n本文2")
            )
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
        val loader = loader(
            memoRepository = memoRepository(
                memoFixture(id = "m", title = "", body = "先頭行\n2行目")
            )
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
        val loader = loader(
            memoRepository = memoRepository(memoFixture(id = "m", title = "", body = body))
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
        val loader = loader(
            memoRepository = memoRepository(
                memoFixture(id = "m", title = "", body = "x".repeat(100))
            )
        )

        // Act
        // Boundary: untitled single line longer than the title limit must not lose the tail
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
        val loader = loader(
            memoRepository = memoRepository(memoFixture(id = "m", title = longTitle, body = ""))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertEquals(50, item.title.length)
    }

    @Test
    fun boundaryLongSnippetTruncatedToMax() = runTest {
        // Arrange
        val loader = loader(
            memoRepository = memoRepository(
                memoFixture(id = "m", title = "T", body = "c".repeat(200))
            )
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertEquals(80, item.snippet.length)
    }

    @Test
    fun boundarySurrogatePairTitleIsNotSplit() = runTest {
        // Arrange
        val title = "a".repeat(49) + "😀"
        val loader = loader(
            memoRepository = memoRepository(memoFixture(id = "m", title = title, body = ""))
        )

        // Act
        // Boundary: an emoji straddling the 50-char limit must not leave a lone surrogate
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
        val loader = loader(
            memoRepository = memoRepository(memoFixture(id = "m", title = "T", body = body))
        )

        // Act
        val item = loader.observeRecent().first().single()

        // Assert
        assertAll(
            { assertEquals("b".repeat(79), item.snippet) },
            { assertFalse(item.snippet.last().isHighSurrogate()) }
        )
    }

    private fun memoRepository(vararg memos: Memo) = FakeMemoRepository(memos.toList())

    private fun loader(
        memoRepository: FakeMemoRepository = memoRepository(),
        appLockSettings: FakeAppLockSettingsRepository = FakeAppLockSettingsRepository()
    ) = WidgetMemoLoader(
        observeRecentMemosUseCase = ObserveRecentMemosUseCase(memoRepository),
        observeAppLockEnabledUseCase = ObserveAppLockEnabledUseCase(appLockSettings)
    )
}
