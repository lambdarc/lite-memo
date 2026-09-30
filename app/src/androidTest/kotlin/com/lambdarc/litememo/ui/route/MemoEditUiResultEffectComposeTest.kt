package com.lambdarc.litememo.ui.route

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.state.MemoEditUiResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoEditUiResultEffectComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stateTransitionInactiveLifecycleRetainsPendingResult() {
        // Arrange
        val result = MemoEditUiResult.SaveFailed(1)
        val fixture = createFixture(Lifecycle.State.CREATED, listOf(result))

        // Act
        // StateTransition: neither CREATED nor STARTED delivers a pending result.
        fixture.mount()
        fixture.moveTo(Lifecycle.State.STARTED)

        // Assert
        assertTrue(fixture.delivered.isEmpty())
        assertTrue(fixture.consumed.isEmpty())
        assertEquals(listOf(result), fixture.results.value)
    }

    @Test
    fun flowResumeDrainsPendingResultsOnceInOrder() {
        // Arrange
        val results = listOf(
            MemoEditUiResult.SaveFailed(1),
            MemoEditUiResult.MemoDeleted(2, MemoId("deleted-memo")),
            MemoEditUiResult.NavigateBack(3)
        )
        val fixture = createFixture(Lifecycle.State.STARTED, results)
        fixture.mount()

        // Act
        // Flow: resuming delivers and acknowledges every queued result in FIFO order.
        fixture.moveTo(Lifecycle.State.RESUMED)

        // Assert
        assertEquals(results, fixture.delivered)
        assertEquals(listOf(1L, 2L, 3L), fixture.consumed)
        assertTrue(fixture.results.value.isEmpty())
    }

    @Test
    fun stateTransitionReaddingCompositionDeliversUnconsumedResult() {
        // Arrange
        val result = MemoEditUiResult.DeleteFailed(1)
        val fixture = createFixture(Lifecycle.State.STARTED, listOf(result))
        fixture.mount()

        // Act
        // StateTransition: a result survives removal before consumption and delivers on reentry.
        fixture.showEffect(false)
        fixture.moveTo(Lifecycle.State.RESUMED)
        fixture.showEffect(true)

        // Assert
        assertEquals(listOf(result), fixture.delivered)
        assertEquals(listOf(1L), fixture.consumed)
        assertTrue(fixture.results.value.isEmpty())
    }

    @Test
    fun boundaryReaddingCompositionDoesNotRedeliverConsumedResult() {
        // Arrange
        val result = MemoEditUiResult.NavigateBack(1)
        val fixture = createFixture(Lifecycle.State.RESUMED, listOf(result))
        fixture.mount()

        // Act
        // Boundary: a consumed result stays consumed when the effect leaves and reenters composition.
        fixture.showEffect(false)
        fixture.showEffect(true)

        // Assert
        assertEquals(listOf(result), fixture.delivered)
        assertEquals(listOf(1L), fixture.consumed)
        assertTrue(fixture.results.value.isEmpty())
    }

    @Test
    fun stateTransitionPauseRetainsNewResultUntilResume() {
        // Arrange
        val first = MemoEditUiResult.SaveFailed(1)
        val second = MemoEditUiResult.ImageAttachFailed(2)
        val fixture = createFixture(Lifecycle.State.RESUMED, listOf(first))
        fixture.mount()

        // Act
        // StateTransition: a result added while paused is retained instead of delivered.
        fixture.moveTo(Lifecycle.State.STARTED)
        composeRule.runOnIdle { fixture.results.value = listOf(second) }
        composeRule.waitForIdle()

        // Assert
        assertEquals(listOf(first), fixture.delivered)
        assertEquals(listOf(second), fixture.results.value)
    }

    @Test
    fun boundaryRepeatedPauseAndResumeDeliversResultOnce() {
        // Arrange
        val result = MemoEditUiResult.ImageAttachFailed(1)
        val fixture = createFixture(Lifecycle.State.STARTED, listOf(result))
        fixture.mount()

        // Act
        // Boundary: repeated pause and resume do not repeat an acknowledged result.
        fixture.moveTo(Lifecycle.State.RESUMED)
        fixture.moveTo(Lifecycle.State.STARTED)
        fixture.moveTo(Lifecycle.State.RESUMED)

        // Assert
        assertEquals(listOf(result), fixture.delivered)
        assertEquals(listOf(1L), fixture.consumed)
        assertTrue(fixture.results.value.isEmpty())
    }

    @Test
    fun boundarySameKindQueuedAgainAfterConsumptionIsDeliveredAgain() {
        // Arrange
        val first = MemoEditUiResult.SaveFailed(1)
        val second = MemoEditUiResult.SaveFailed(2)
        val fixture = createFixture(Lifecycle.State.RESUMED, listOf(first))
        fixture.mount()

        // Act
        // Boundary: a new ID of the same kind is delivered after the previous one is consumed.
        composeRule.runOnIdle { fixture.results.value = listOf(second) }
        composeRule.waitForIdle()

        // Assert
        assertEquals(listOf(first, second), fixture.delivered)
        assertEquals(listOf(1L, 2L), fixture.consumed)
        assertTrue(fixture.results.value.isEmpty())
    }

    @Test
    fun interactionRecompositionUsesLatestResultAndConsumptionCallbacks() {
        // Arrange
        val result = MemoEditUiResult.SaveFailed(1)
        val fixture = createFixture(Lifecycle.State.RESUMED)
        val latestResults = mutableListOf<MemoEditUiResult>()
        val latestConsumed = mutableListOf<Long>()
        fixture.mount()

        // Act
        // Interaction: updating callbacks on an active effect takes effect for the next result.
        composeRule.runOnIdle {
            fixture.onResult.value = { result -> latestResults.add(result) }
            fixture.onConsumed.value = { id ->
                latestConsumed.add(id)
                fixture.acknowledge(id)
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { fixture.results.value = listOf(result) }
        composeRule.waitForIdle()

        // Assert
        assertEquals(listOf(result), latestResults)
        assertEquals(listOf(1L), latestConsumed)
        assertTrue(fixture.delivered.isEmpty())
        assertTrue(fixture.consumed.isEmpty())
        assertTrue(fixture.results.value.isEmpty())
    }

    @Test
    fun interactionResultCallbackRunsBeforeSynchronousAcknowledgment() {
        // Arrange
        val result = MemoEditUiResult.NavigateBack(1)
        val nextResult = MemoEditUiResult.SaveFailed(2)
        val fixture = createFixture(Lifecycle.State.RESUMED, listOf(result, nextResult))
        val calls = mutableListOf<String>()
        val pendingAtCallback = mutableListOf<List<MemoEditUiResult>>()
        composeRule.runOnIdle {
            fixture.onResult.value = {
                calls.add("result")
                pendingAtCallback.add(fixture.results.value)
                fixture.isShown.value = false
                fixture.lifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED
            }
            fixture.onConsumed.value = { id ->
                calls.add("acknowledgment")
                fixture.acknowledge(id)
            }
        }

        // Act
        // Interaction: pausing and leaving composition cannot interrupt acknowledgment or consume the next result.
        fixture.mount()

        // Assert
        assertEquals(listOf("result", "acknowledgment"), calls)
        assertEquals(listOf(listOf(result, nextResult)), pendingAtCallback)
        assertEquals(listOf(nextResult), fixture.results.value)
    }

    private fun createFixture(
        lifecycleState: Lifecycle.State,
        results: List<MemoEditUiResult> = emptyList()
    ): EffectFixture = composeRule.runOnIdle {
        EffectFixture(TestLifecycleOwner(lifecycleState), results)
    }

    private inner class EffectFixture(
        val lifecycleOwner: TestLifecycleOwner,
        results: List<MemoEditUiResult>
    ) {
        val results = MutableStateFlow(results)
        val delivered = mutableListOf<MemoEditUiResult>()
        val consumed = mutableListOf<Long>()
        val isShown = mutableStateOf(true)
        val onResult = mutableStateOf<
            (
                MemoEditUiResult
            ) -> Unit
            >({ result -> delivered.add(result) })
        val onConsumed = mutableStateOf<(Long) -> Unit>({ id ->
            consumed.add(id)
            acknowledge(id)
        })

        fun mount() {
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    if (isShown.value) {
                        MemoEditUiResultEffect(results, onResult.value, onConsumed.value)
                    }
                }
            }
            composeRule.waitForIdle()
        }

        fun moveTo(state: Lifecycle.State) {
            composeRule.runOnIdle { lifecycleOwner.lifecycle.currentState = state }
            composeRule.waitForIdle()
        }

        fun showEffect(shown: Boolean) {
            composeRule.runOnIdle { isShown.value = shown }
            composeRule.waitForIdle()
        }

        fun acknowledge(id: Long) {
            if (results.value.firstOrNull()?.id == id) results.value = results.value.drop(1)
        }
    }

    private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = initialState }
    }
}
