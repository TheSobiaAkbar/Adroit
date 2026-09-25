package com.company.admob.internal.init

import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.UnavailableReason
import com.company.admob.internal.init.AdsStateTransitions.Kind
import com.company.admob.internal.runtime.RuntimeHarness
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReadinessTest {
    private val everyState: List<AdsState> = listOf(
        AdsState.NotInitialized,
        AdsState.AwaitingStart,
        AdsState.GatheringConsent,
        AdsState.InitializingSdk,
        AdsState.Ready,
    ) + UnavailableReason.entries.map { AdsState.Unavailable(it) } +
        listOf(AdsState.Failed(AdError.SdkInitFailed()), AdsState.Failed(AdError.InvalidConfiguration("x")))

    @Test
    fun `canLoad is true only in Ready`() = runTest {
        val h = RuntimeHarness(this)
        val init = h.runtime().init
        for (state in everyState) {
            h.state.value = state
            assertEquals("$state", state == AdsState.Ready, init.canLoad())
        }
    }

    @Test
    fun `awaitSettled - true on Ready, false on a settled state, false on timeout without throwing`() = runTest {
        val h = RuntimeHarness(this)
        val init = h.runtime().init
        h.settle()

        val timedOut = async { init.awaitSettled(5.seconds) }
        advanceTimeBy(6_000)
        assertFalse(timedOut.await())

        val ready = async { init.awaitSettled(5.seconds) }
        runCurrent()
        h.state.value = AdsState.Ready
        assertTrue(ready.await())

        h.state.value = AdsState.GatheringConsent
        val unavailable = async { init.awaitSettled(5.seconds) }
        runCurrent()
        h.state.value = AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED)
        assertFalse(unavailable.await())

        h.state.value = AdsState.Ready
        assertTrue(init.awaitSettled(0.seconds))
    }

    @Test
    fun `the engines' awaitReady returns only on Ready`() = runTest {
        val h = RuntimeHarness(this)
        val init = h.runtime().init
        val waiting = async { init.awaitReady() }
        runCurrent()
        h.state.value = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        runCurrent()
        assertFalse(waiting.isCompleted)
        h.state.value = AdsState.Ready
        runCurrent()
        assertTrue(waiting.isCompleted)
    }

    /** Pins the implemented table: the §11.1 diagram plus the documented extensions, and nothing else. */
    @Test
    fun `the transition table`() {
        val expected = mapOf(
            Kind.NOT_INITIALIZED to setOf(Kind.AWAITING_START, Kind.FAILED),
            Kind.AWAITING_START to setOf(Kind.GATHERING_CONSENT),
            Kind.GATHERING_CONSENT to setOf(Kind.INITIALIZING_SDK, Kind.UNAVAILABLE),
            Kind.INITIALIZING_SDK to setOf(Kind.READY, Kind.FAILED, Kind.UNAVAILABLE),
            Kind.READY to setOf(Kind.UNAVAILABLE),
            Kind.UNAVAILABLE to setOf(Kind.GATHERING_CONSENT, Kind.READY, Kind.INITIALIZING_SDK, Kind.UNAVAILABLE),
            Kind.FAILED to setOf(Kind.INITIALIZING_SDK, Kind.GATHERING_CONSENT),
        )
        assertEquals(expected, AdsStateTransitions.allowed)

        for (from in everyState) for (to in everyState) {
            val allowed = AdsStateTransitions.isAllowed(from, to)
            val byKind = AdsStateTransitions.kindOf(to) in expected.getValue(AdsStateTransitions.kindOf(from))
            val expectedAllowed = byKind &&
                !(from is AdsState.Failed && from.error !is AdError.SdkInitFailed) &&
                !(from == AdsState.NotInitialized && to is AdsState.Failed && to.error !is AdError.InvalidConfiguration)
            assertEquals("$from -> $to", expectedAllowed, allowed)
        }
        // Invalid configuration is terminal, and nothing goes back to NotInitialized or AwaitingStart.
        assertFalse(AdsStateTransitions.isAllowed(AdsState.Failed(AdError.InvalidConfiguration("x")), AdsState.InitializingSdk))
        assertTrue(everyState.none { AdsStateTransitions.isAllowed(it, AdsState.NotInitialized) })
        assertTrue(everyState.none { it != AdsState.NotInitialized && AdsStateTransitions.isAllowed(it, AdsState.AwaitingStart) })
    }
}
