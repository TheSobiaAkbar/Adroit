package io.github.thesobiaakbar.adroit.billing.internal.play

import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState.Connected
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState.Connecting
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState.Disconnected
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
/** The manager runs in `backgroundScope`, which `advanceUntilIdle` ignores: time is advanced explicitly. */
class ConnectionManagerTest {
    private val port = FakePlayBillingPort()
    private val state = MutableStateFlow<BillingConnectionState>(Disconnected)
    private val disconnected = PlayResult.Failed(BillingError.ServiceDisconnected(-1))

    private fun TestScope.manager() =
        ConnectionManager(port, backgroundScope, state, BillingLog(BillingLogLevel.NONE), now = { testScheduler.currentTime })

    private fun TestScope.recordStates(): List<BillingConnectionState> = mutableListOf<BillingConnectionState>().also { states ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { states += it } }
    }

    @Test
    fun `connects on a trigger`() = runTest {
        val states = recordStates()
        manager().trigger()
        runCurrent()
        assertEquals(listOf(Disconnected, Connecting, Connected), states)
    }

    @Test
    fun `a disconnect or network error is retried after 1, 2, 4, 8 and 16 s, then never again without a trigger`() = runTest {
        repeat(20) { port.connectResults += disconnected }
        manager().trigger()
        runCurrent()
        val attemptsAt = mutableListOf(testScheduler.currentTime to port.connectCalls)
        assertEquals(BillingConnectionState.Error(disconnected.error, retryAtMillis = 1_000), state.value)
        for (wait in listOf(1_000L, 2_000, 4_000, 8_000, 16_000)) {
            advanceTimeBy(wait)
            runCurrent()
            attemptsAt += testScheduler.currentTime to port.connectCalls
        }
        assertEquals(listOf(0L to 1, 1_000L to 2, 3_000L to 3, 7_000L to 4, 15_000L to 5, 31_000L to 6), attemptsAt)
        assertEquals(BillingConnectionState.Error(disconnected.error, retryAtMillis = null), state.value)

        advanceTimeBy(24 * 3_600_000L)
        assertEquals(6, port.connectCalls)
    }

    @Test
    fun `billing unavailable is not retried`() = runTest {
        val unavailable = BillingError.Unavailable(3)
        port.connectResults += PlayResult.Failed(unavailable)
        manager().trigger()
        advanceTimeBy(3_600_000L)
        assertEquals(BillingConnectionState.Unavailable(unavailable), state.value)
        assertEquals(1, port.connectCalls)
    }

    @Test
    fun `a developer error is not retried`() = runTest {
        val error = BillingError.DeveloperError(5)
        port.connectResults += PlayResult.Failed(error)
        manager().trigger()
        advanceTimeBy(3_600_000L)
        assertEquals(BillingConnectionState.Error(error, retryAtMillis = null), state.value)
        assertEquals(1, port.connectCalls)
    }

    @Test
    fun `a foreground trigger reconnects after giving up`() = runTest {
        port.connectResults += PlayResult.Failed(BillingError.Unavailable(3))
        val manager = manager()
        manager.trigger()
        runCurrent()
        manager.trigger()
        runCurrent()
        assertEquals(Connected, state.value)
        assertEquals(2, port.connectCalls)
    }

    @Test
    fun `triggers while connecting or connected start nothing`() = runTest {
        port.connectResults += disconnected
        val manager = manager()
        manager.trigger()
        runCurrent()
        manager.trigger() // waiting for the retry
        advanceTimeBy(1_000)
        runCurrent()
        manager.trigger() // connected
        runCurrent()
        assertEquals(2, port.connectCalls)
        assertEquals(Connected, state.value)
    }

    @Test
    fun `a disconnect reconnects at once`() = runTest {
        manager().trigger()
        runCurrent()
        port.disconnect()
        runCurrent()
        assertEquals(Connected, state.value)
        assertEquals(2, port.connectCalls)
    }

    @Test
    fun `a call never waits more than 10 s for a connection`() = runTest {
        port.connectGate = CompletableDeferred()
        val manager = manager()
        manager.trigger()
        val result = manager.call("queryPurchases") { queryPurchases(PlayProductType.SUBS, includeSuspended = false) }
        assertEquals(10_000L, testScheduler.currentTime)
        assertTrue((result as PlayResult.Failed).error is BillingError.ServiceDisconnected)
        assertEquals(0, port.queryPurchasesCalls)
    }

    @Test
    fun `a call fails at once when billing is unavailable`() = runTest {
        val unavailable = BillingError.Unavailable(3)
        port.connectResults += PlayResult.Failed(unavailable)
        val manager = manager()
        manager.trigger()
        val result = manager.call("queryPurchases") { queryPurchases(PlayProductType.SUBS, includeSuspended = false) }
        assertEquals(PlayResult.Failed(unavailable), result)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `a call that hits a disconnect runs once more after reconnecting`() = runTest {
        port.purchasesResults += disconnected
        port.purchasesResults += PlayResult.Ok(emptyList())
        val manager = manager()
        manager.trigger()
        val result = manager.call("queryPurchases") { queryPurchases(PlayProductType.SUBS, includeSuspended = false) }
        assertEquals(PlayResult.Ok(emptyList<PlayPurchase>()), result)
        assertEquals(2, port.queryPurchasesCalls)
        assertEquals(2, port.connectCalls)
    }

    @Test
    fun `a call that hits a disconnect fails when reconnecting fails, without a third try`() = runTest {
        port.purchasesResults += disconnected
        port.purchasesResults += disconnected
        val manager = manager()
        manager.trigger()
        runCurrent()
        port.connectResults += PlayResult.Failed(BillingError.DeveloperError(5))
        val result = manager.call("queryPurchases") { queryPurchases(PlayProductType.SUBS, includeSuspended = false) }
        assertEquals(PlayResult.Failed(BillingError.DeveloperError(5)), result)
        assertEquals(1, port.queryPurchasesCalls)
    }

    @Test
    fun `shutdown ends the connection`() = runTest {
        val manager = manager()
        manager.trigger()
        runCurrent()
        manager.shutdown()
        assertEquals(Disconnected, state.value)
        assertEquals(1, port.endConnectionCalls)
    }
}
