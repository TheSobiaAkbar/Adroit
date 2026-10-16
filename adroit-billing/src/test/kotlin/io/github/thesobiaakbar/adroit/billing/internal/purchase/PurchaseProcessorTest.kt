package io.github.thesobiaakbar.adroit.billing.internal.purchase

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.ConsumableGrant
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.PurchaseToVerify
import io.github.thesobiaakbar.adroit.billing.PurchaseUpdate
import io.github.thesobiaakbar.adroit.billing.PurchaseVerifier
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.VerificationResult
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.sha256Hex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PurchaseProcessorTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val port = FakePlayBillingPort()
    private val logged = mutableListOf<String>()
    private val log = BillingLog(BillingLogLevel.VERBOSE) { _, message -> logged += message }
    private val events = mutableListOf<BillingEvent>()
    private val updates = MutableSharedFlow<PurchaseUpdate>(replay = 16)
    private val consumables = MutableStateFlow<List<ConsumableGrant>>(emptyList())
    private val verified = mutableListOf<PurchaseToVerify>()
    private val attribution = Attribution("a-1", "home", "Onboarding", null, "premium_sub", "monthly", null, recovered = false)

    private fun TestScope.processor(
        verifier: PurchaseVerifier? = null,
        ledger: TokenLedger = TokenLedger(application),
        grants: MutableStateFlow<List<ConsumableGrant>> = consumables,
    ): PurchaseProcessor {
        val settings = BillingSettings(TestProducts.entries, verifier = verifier, events = listOf(BillingEventListener { events += it }))
        val connection = ConnectionManager(port, backgroundScope, MutableStateFlow(BillingConnectionState.Connected), log)
        return PurchaseProcessor(
            connection, settings, ledger, updates, grants, EventBus(settings.events, log), log, "com.example", { "user-1" },
        )
    }

    private fun purchase(
        vararg products: String = arrayOf("premium_sub"),
        state: PlayPurchaseState = PlayPurchaseState.PURCHASED,
        token: String = "secret-token",
        acknowledged: Boolean = false,
        suspended: Boolean = false,
    ) = PlayPurchase(
        products.toList(), token, "GPA.1", 7, state, 3, acknowledged, isAutoRenewing = true, isSuspended = suspended,
        obfuscatedAccountId = null, obfuscatedProfileId = null, originalJson = "{}", signature = "",
    )

    private fun verifier(result: VerificationResult) = PurchaseVerifier { verified += it; result }

    private inline fun <reified T : BillingEvent> count() = events.count { it is T }

    private fun ledger(token: String = "secret-token") = TokenLedger(application)[token]

    @Test
    fun `a pending purchase grants nothing until Play says it is purchased`() = runTest {
        val processor = processor()
        assertEquals(PurchaseResult.Pending(attribution), processor.process(purchase(state = PlayPurchaseState.PENDING), attribution))
        assertEquals(emptyList<String>(), port.acknowledged)
        assertNull(ledger())
        assertEquals(0, count<BillingEvent.PurchaseCompleted>())
        assertEquals(1, count<BillingEvent.PurchasePending>())

        val result = processor.process(purchase(), attribution) as PurchaseResult.Purchased
        assertEquals(GrantState.ACTIVE, result.grant!!.state)
        assertEquals(setOf("premium", "ad_free"), result.grant!!.entitlements)
        assertEquals(listOf("secret-token"), port.acknowledged)
        assertEquals(TokenState.ACKNOWLEDGED, ledger())
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
    }

    @Test
    fun `a listener that throws doesn't break processing`() = runTest {
        val settings = BillingSettings(TestProducts.entries, events = listOf(BillingEventListener { error("listener bug") }))
        val connection = ConnectionManager(port, backgroundScope, MutableStateFlow(BillingConnectionState.Connected), log)
        val processor = PurchaseProcessor(
            connection, settings, TokenLedger(application), updates, consumables, EventBus(settings.events, log), log, "com.example", { null },
        )
        assertTrue(processor.process(purchase(), attribution) is PurchaseResult.Purchased)
        assertEquals(listOf("secret-token"), port.acknowledged)
        assertEquals(TokenState.ACKNOWLEDGED, ledger())
        assertEquals(1, logged.count { it.startsWith("Event listener") })
    }

    @Test
    fun `a suspended subscription grants nothing and is reported`() = runTest {
        assertNull(processor().process(purchase(suspended = true), attribution))
        assertEquals(listOf(PurchaseUpdate.Suspended("premium_sub")), updates.replayCache)
        assertEquals(emptyList<String>(), port.acknowledged)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `an unspecified purchase is ignored`() = runTest {
        assertNull(processor().process(purchase(state = PlayPurchaseState.UNSPECIFIED), attribution))
        assertTrue(events.isEmpty())
        assertNull(ledger())
    }

    @Test
    fun `a verified purchase is granted and acknowledged, with the account only as a hash`() = runTest {
        val result = processor(verifier(VerificationResult.Verified())).process(purchase(), attribution)
        assertTrue(result is PurchaseResult.Purchased)
        val request = verified.single()
        assertEquals(sha256Hex("user-1"), request.accountIdHash)
        assertEquals("com.example", request.packageName)
        assertEquals(1, count<BillingEvent.PurchaseVerified>())
        assertEquals(listOf("secret-token"), port.acknowledged)
    }

    @Test
    fun `a purchase the backend acknowledged isn't acknowledged again`() = runTest {
        processor(verifier(VerificationResult.Verified(acknowledgedByBackend = true))).process(purchase(), attribution)
        assertEquals(emptyList<String>(), port.acknowledged)
        assertEquals(TokenState.ACKNOWLEDGED, ledger())
    }

    @Test
    fun `a rejected purchase is never granted nor acknowledged, and stays rejected`() = runTest {
        val processor = processor(verifier(VerificationResult.Rejected("forged")))
        assertEquals(
            PurchaseResult.Failed(BillingError.VerificationFailed("forged"), attribution),
            processor.process(purchase(), attribution),
        )
        assertTrue(processor.process(purchase(), attribution) is PurchaseResult.Failed)
        assertEquals(1, verified.size)
        assertEquals(TokenState.REJECTED, ledger())
        assertEquals(emptyList<String>(), port.acknowledged)
        assertEquals(0, count<BillingEvent.PurchaseCompleted>())
    }

    @Test
    fun `an unreachable, slow or failing verifier grants nothing and is asked again on the next sync`() = runTest {
        val unreachable = processor(verifier(VerificationResult.Unreachable("offline"))).process(purchase(), attribution)
        assertEquals(PurchaseResult.Failed(BillingError.BackendError("offline"), attribution), unreachable)

        val slow = processor(PurchaseVerifier { awaitCancellation() }).process(purchase(), attribution)
        assertTrue((slow as PurchaseResult.Failed).error is BillingError.BackendError)
        assertEquals(15_000L, currentTime)

        val throwing = processor(PurchaseVerifier { error("boom") }).process(purchase(), attribution)
        assertTrue((throwing as PurchaseResult.Failed).error is BillingError.BackendError)

        assertNull(ledger())
        assertEquals(emptyList<String>(), port.acknowledged)
        assertEquals(0, count<BillingEvent.PurchaseCompleted>())

        assertTrue(processor(verifier(VerificationResult.Verified())).process(purchase(), attribution) is PurchaseResult.Purchased)
        assertEquals(listOf("secret-token"), port.acknowledged)
    }

    @Test
    fun `acknowledging is retried with backoff`() = runTest {
        repeat(2) { port.acknowledgeResults += PlayResult.Failed(BillingError.NetworkError()) }
        processor().process(purchase(), attribution)
        assertEquals(3, port.acknowledged.size)
        assertEquals(3_000L, currentTime)
        assertEquals(TokenState.ACKNOWLEDGED, ledger())
        assertEquals(0, count<BillingEvent.AcknowledgeFailed>())
    }

    @Test
    fun `an acknowledge failing three times is reported and retried on the next sync without a second grant`() = runTest {
        repeat(3) { port.acknowledgeResults += PlayResult.Failed(BillingError.NetworkError()) }
        val processor = processor()
        assertTrue(processor.process(purchase(), attribution) is PurchaseResult.Purchased)
        assertEquals(TokenState.GRANTED, ledger())
        assertEquals(1, count<BillingEvent.AcknowledgeFailed>())

        // The next sync, here after a restart: still unacknowledged in Play, already granted in the ledger.
        assertTrue(processor().process(purchase(), attribution) is PurchaseResult.Purchased)
        assertEquals(4, port.acknowledged.size)
        assertEquals(TokenState.ACKNOWLEDGED, ledger())
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
    }

    @Test
    fun `the listener and a sync delivering the same purchase together give one grant and one acknowledge`() = runTest {
        val processor = processor()
        val gate = CompletableDeferred<Unit>().also { port.acknowledgeGate = it }
        val listener = async { processor.process(purchase(), attribution) }
        val sync = async { processor.process(purchase(), attribution) }
        runCurrent()
        gate.complete(Unit)
        assertTrue(listener.await() is PurchaseResult.Purchased)
        assertTrue(sync.await() is PurchaseResult.Purchased)
        assertEquals(listOf("secret-token"), port.acknowledged)
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
    }

    @Test
    fun `an acknowledged purchase seen again changes nothing`() = runTest {
        val processor = processor()
        processor.process(purchase(), attribution)
        processor.process(purchase(acknowledged = true), attribution)
        assertEquals(1, port.acknowledged.size)
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
    }

    @Test
    fun `a multi-product purchase activates each product`() = runTest {
        processor().process(purchase("premium_sub", "premium_lifetime"), attribution)
        assertEquals(
            listOf("premium_sub", "premium_lifetime"),
            events.filterIsInstance<BillingEvent.EntitlementActivated>().map { it.productId },
        )
        assertEquals(1, port.acknowledged.size)
    }

    @Test
    fun `a consumable is consumed only after the app confirms it`() = runTest {
        val processor = processor()
        val coins = purchase("coins_100")
        assertEquals(PurchaseResult.Purchased(null, attribution), processor.process(coins, attribution))
        val grant = consumables.value.single()
        assertEquals("coins_100", grant.productId)
        assertEquals(3, grant.quantity)
        assertEquals(sha256Hex("GPA.1"), grant.orderIdHash)
        assertEquals(emptyList<String>(), port.consumed)
        assertEquals(emptyList<String>(), port.acknowledged)

        processor.process(coins, attribution)
        assertEquals(1, consumables.value.size)

        grant.confirm()
        assertEquals(listOf("secret-token"), port.consumed)
        assertEquals(TokenState.CONSUMED, ledger())
        assertTrue(consumables.value.isEmpty())

        grant.confirm()
        processor.process(coins, attribution)
        assertEquals(1, port.consumed.size)
        assertTrue(consumables.value.isEmpty())
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
        assertEquals(0, count<BillingEvent.EntitlementActivated>())
    }

    @Test
    fun `an unconfirmed consumable is delivered again after a restart`() = runTest {
        processor().process(purchase("coins_100"), attribution)
        val afterRestart = MutableStateFlow<List<ConsumableGrant>>(emptyList())
        processor(ledger = TokenLedger(application), grants = afterRestart).process(purchase("coins_100"), attribution)
        assertEquals(consumables.value, afterRestart.value)
        assertEquals(1, afterRestart.value.size)
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
        assertEquals(emptyList<String>(), port.consumed)
    }

    @Test
    fun `a failed consume is reported and retried on the next sync without delivering again`() = runTest {
        repeat(3) { port.consumeResults += PlayResult.Failed(BillingError.NetworkError()) }
        val processor = processor()
        processor.process(purchase("coins_100"), attribution)
        consumables.value.single().confirm()
        assertEquals(TokenState.CONFIRMED, ledger())
        assertEquals(1, count<BillingEvent.ConsumeFailed>())

        processor.process(purchase("coins_100"), attribution)
        assertTrue(consumables.value.isEmpty())
        assertEquals(4, port.consumed.size)
        assertEquals(TokenState.CONSUMED, ledger())
    }

    @Test
    fun `a consume answered with item not owned counts as consumed`() = runTest {
        port.consumeResults += PlayResult.Failed(BillingError.ItemNotOwned(8))
        processor().process(purchase("coins_100"), attribution)
        consumables.value.single().confirm()
        assertEquals(1, port.consumed.size)
        assertEquals(TokenState.CONSUMED, ledger())
        assertEquals(0, count<BillingEvent.ConsumeFailed>())
    }

    @Test
    fun `the ledger keeps the most recently used tokens`() {
        val ledger = TokenLedger(application, capacity = 3)
        ledger["t1"] = TokenState.GRANTED
        ledger["t2"] = TokenState.GRANTED
        ledger["t3"] = TokenState.GRANTED
        assertEquals(TokenState.GRANTED, ledger["t1"])
        ledger["t4"] = TokenState.CONSUMED

        val reloaded = TokenLedger(application, capacity = 3)
        assertNull(reloaded["t2"])
        assertEquals(TokenState.GRANTED, reloaded["t1"])
        assertEquals(TokenState.CONSUMED, reloaded["t4"])
    }

    @Test
    fun `no purchase token reaches the logs, the events or the ledger`() = runTest {
        port.acknowledgeResults += PlayResult.Failed(BillingError.NetworkError())
        val processor = processor(verifier(VerificationResult.Verified()))
        processor.process(purchase(state = PlayPurchaseState.PENDING), attribution)
        processor.process(purchase(), attribution)
        processor.process(purchase(token = "other-token", state = PlayPurchaseState.UNSPECIFIED), attribution)
        processor.process(purchase("coins_100", token = "coin-token"), attribution)
        consumables.value.single().confirm()
        processor(verifier(VerificationResult.Rejected("no"))).process(purchase(token = "bad-token"), attribution)

        assertTrue(logged.isNotEmpty())
        val stored = application.getSharedPreferences(TokenLedger.NAME, 0).all.values.joinToString()
        val seen = logged + events.map { it.toString() } + consumables.value.map { it.toString() } + verified.map { it.toString() } + stored
        for (token in listOf("secret-token", "other-token", "coin-token", "bad-token")) {
            assertFalse(token, seen.any { it.contains(token) })
        }
    }
}
