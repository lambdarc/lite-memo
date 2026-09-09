package com.lambdarc.litememo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockWidgetRefreshRetryTest {

    @Test
    fun coroutineRetryUsesCappedExponentialDelayAndReportsFailureOnce() = runTest {
        // Arrange
        var subscriptions = 0
        var failureReports = 0
        val failingFlow = flow<Boolean> {
            subscriptions += 1
            throw IOException("read failed")
        }

        // Act
        // Coroutine: a continuous failure retries at 500, 1000, then capped 1000 ms delays
        val job = backgroundScope.launch {
            failingFlow.retryAppLockObservation(
                initialDelayMillis = 500,
                maxDelayMillis = 1_000
            ) { failureReports += 1 }.collect {}
        }
        runCurrent()
        assertEquals(1, subscriptions)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(1, subscriptions)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, subscriptions)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(2, subscriptions)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, subscriptions)
        advanceTimeBy(1_000)
        runCurrent()

        // Assert
        assertEquals(4, subscriptions)
        assertEquals(1, failureReports)
        job.cancel()
    }

    @Test
    fun flowSameSettingAfterFailureIsEmittedAgainForRecoveryRefresh() = runTest {
        // Arrange
        var subscriptions = 0
        var failureReports = 0
        val recoveringFlow = flow {
            subscriptions += 1
            emit(false)
            if (subscriptions == 1) throw IOException("read failed")
        }
        val values = mutableListOf<Boolean>()

        // Act
        // Flow: retry starts a fresh distinct session so recovery refresh is not suppressed
        val job = backgroundScope.launch {
            recoveringFlow.retryAppLockObservation(
                initialDelayMillis = 500,
                maxDelayMillis = 1_000
            ) { failureReports += 1 }.toList(values)
        }
        runCurrent()
        advanceTimeBy(500)
        runCurrent()

        // Assert
        assertEquals(listOf(false, false), values)
        assertEquals(1, failureReports)
        assertTrue(job.isCompleted)
    }

    @Test
    fun flowSuccessfulEmissionResetsFailureEpisodeAndRetryDelay() = runTest {
        // Arrange
        var subscriptions = 0
        var failureReports = 0
        val recoveringFlow = flow {
            subscriptions += 1
            when (subscriptions) {
                1 -> throw IOException("first failure")

                2 -> {
                    emit(false)
                    throw IOException("failure after recovery")
                }

                else -> emit(true)
            }
        }
        val values = mutableListOf<Boolean>()

        // Act
        // Flow: a successful setting emission starts a new failure episode and resets backoff
        val job = backgroundScope.launch {
            recoveringFlow.retryAppLockObservation(
                initialDelayMillis = 500,
                maxDelayMillis = 2_000
            ) { failureReports += 1 }.toList(values)
        }
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(false), values)
        assertEquals(2, failureReports)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(2, subscriptions)
        advanceTimeBy(1)
        runCurrent()

        // Assert
        assertEquals(listOf(false, true), values)
        assertEquals(3, subscriptions)
        job.cancel()
    }

    @Test
    fun coroutineCancellationStopsRetryWithoutReportingFailure() = runTest {
        // Arrange
        var failureReports = 0
        val canceledFlow = flow<Boolean> {
            throw CancellationException("stopped")
        }

        // Act
        // Coroutine: cancellation is propagated instead of entering the retry loop
        val job = launch {
            canceledFlow.retryAppLockObservation {
                failureReports += 1
            }.collect {}
        }
        runCurrent()

        // Assert
        assertTrue(job.isCancelled)
        assertEquals(0, failureReports)
    }
}
