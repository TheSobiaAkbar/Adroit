package io.github.thesobiaakbar.adroit.billing

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.OfferKey
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayProduct
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayProductType
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchasesUpdate
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.purchase.TokenLedger
import io.github.thesobiaakbar.adroit.billing.internal.purchase.TokenState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/*
 * The whole library through AdroitBilling on a fake Play (B22). A "process death" is resetForTests(): every in-memory
 * value is gone, the preferences (ledger, attempts, entitlement cache) stay, as after `am kill`.
 */

private fun idle() = shadowOf(Looper.getMainLooper()).idle()

private fun oneTime(definition: TestProducts, token: String) = PlayProduct(
    ProductInfo(
        definition, definition.defaults.productId, definition.defaults.type, "t", "n", "d",
        listOf(OneTimeOption(null, null, emptyList(), Price("$0.99", 990_000, "USD"))), emptyList(),
    ),
    mapOf(OfferKey(null, null) to token),
)

private fun purchase(product: String, token: String, acknowledged: Boolean = false) = PlayPurchase(
    listOf(product), token, "GPA.$token", 1, PlayPurchaseState.PURCHASED, 1, acknowledged, false, false, null, null, "{}", "",
)

/** A fresh process: a new fake Play with [owned] purchases, and the library initialized on it. */
private fun process(events: MutableList<BillingEvent>, vararg owned: PlayPurchase): FakePlayBillingPort {
    AdroitBilling.resetForTests()
    val port = FakePlayBillingPort()
    port.products["premium_lifetime"] = oneTime(TestProducts.Lifetime, "offer-lifetime")
    port.products["coins_100"] = oneTime(TestProducts.Coins, "offer-coins")
    port.owned[PlayProductType.INAPP] = owned.toList()
    AdroitBilling.portFactory = { port }
    AdroitBilling.initialize(
        ApplicationProvider.getApplicationContext(),
        BillingSettings(TestProducts.entries, TestPaywalls.entries, events = listOf(BillingEventListener { events += it })),
    )
    idle()
    return port
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProcessDeathTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val events = mutableListOf<BillingEvent>()

    @Before
    fun setUp() = AdroitBilling.resetForTests()

    @After
    fun tearDown() = AdroitBilling.resetForTests()

    private inline fun <reified T : BillingEvent> count() = events.count { it is T }

    @Test
    fun `killed while Play's sheet is open - the next process grants once, acknowledges, and keeps the placement`() = runTest {
        val first = process(events)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        backgroundScope.async { AdroitBilling.purchase(activity, PurchaseRequest(TestProducts.Lifetime, placement = "settings")) }
        runCurrent()
        assertEquals(1, first.launches.size)

        // Killed with the sheet open; the user paid. The next process finds the purchase in Play's list.
        val second = process(events, purchase("premium_lifetime", "life-token"))

        assertTrue(AdroitBilling.entitlements.value.has("premium"))
        assertEquals(listOf("life-token"), second.acknowledged)
        val completed = events.filterIsInstance<BillingEvent.PurchaseCompleted>().single()
        assertEquals("settings", completed.attribution.placement)
        assertTrue(completed.attribution.recovered)
        assertEquals(TokenState.ACKNOWLEDGED, TokenLedger(application)["life-token"])
    }

    @Test
    fun `killed between grant and acknowledge - the next process acknowledges without a second grant`() {
        val owned = purchase("premium_lifetime", "life-token")
        val first = FakePlayBillingPort().apply { acknowledgeGate = CompletableDeferred() }
        AdroitBilling.portFactory = { first }
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries, events = listOf(BillingEventListener { events += it })))
        first.owned[PlayProductType.INAPP] = listOf(owned)
        AdroitBilling.runtime!!.entitlements.requestSync()
        idle()
        assertEquals(TokenState.GRANTED, TokenLedger(application)["life-token"])
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())

        val second = process(events, owned)

        assertEquals(listOf("life-token"), second.acknowledged)
        assertEquals(TokenState.ACKNOWLEDGED, TokenLedger(application)["life-token"])
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
        assertEquals(1, count<BillingEvent.EntitlementActivated>())
        assertTrue(AdroitBilling.entitlements.value.has("premium"))
    }

    @Test
    fun `killed with an unconfirmed consumable - delivered again, consumed once after confirm`() = runTest {
        val coins = purchase("coins_100", "coins-token")
        val first = process(events, coins)
        val delivered = AdroitBilling.consumables.first()
        assertTrue(first.consumed.isEmpty())

        val second = process(events, coins)
        val again = AdroitBilling.consumables.first()
        assertEquals(delivered.orderIdHash, again.orderIdHash)
        again.confirm()
        idle()

        assertEquals(listOf("coins-token"), second.consumed)
        assertEquals(TokenState.CONSUMED, TokenLedger(application)["coins-token"])
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())

        // Play still lists it until the consume reaches it: nothing is delivered nor consumed again.
        val third = process(events, coins)
        assertTrue(AdroitBilling.sharedConsumables.value.isEmpty())
        assertTrue(third.consumed.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConnectionChaosTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val events = mutableListOf<BillingEvent>()
    private val disconnected = PlayResult.Failed(BillingError.ServiceDisconnected())

    @Before
    fun setUp() = AdroitBilling.resetForTests()

    @After
    fun tearDown() = AdroitBilling.resetForTests()

    private inline fun <reified T : BillingEvent> count() = events.count { it is T }

    private fun TestScope.buy(product: TestProducts) = backgroundScope.async {
        AdroitBilling.purchase(Robolectric.buildActivity(Activity::class.java).setup().get(), PurchaseRequest(product, placement = "shop"))
    }

    @Test
    fun `a disconnect mid-query reconnects and gives one catalog and one grant`() {
        val port = FakePlayBillingPort()
        port.products["premium_lifetime"] = oneTime(TestProducts.Lifetime, "offer-lifetime")
        port.productsResults += disconnected
        port.purchasesResults += disconnected
        port.owned[PlayProductType.INAPP] = listOf(purchase("premium_lifetime", "life-token"))
        AdroitBilling.portFactory = { port }
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries, events = listOf(BillingEventListener { events += it })))
        idle()

        assertTrue(port.connectCalls >= 2)
        assertEquals(BillingConnectionState.Connected, AdroitBilling.connection.value)
        assertTrue(AdroitBilling.catalog.value is CatalogState.Ready)
        assertTrue(AdroitBilling.entitlements.value.status is SnapshotStatus.Fresh)
        assertTrue(AdroitBilling.entitlements.value.has("premium"))
        assertEquals(listOf("life-token"), port.acknowledged)
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
    }

    @Test
    fun `a disconnect at launch reconnects and opens the sheet once more, then one purchase`() = runTest {
        val port = process(events)
        port.launchResults += disconnected
        val result = buy(TestProducts.Lifetime)
        runCurrent()
        idle()
        assertEquals(2, port.launches.size)

        port.emit(PlayPurchasesUpdate.Purchased(listOf(purchase("premium_lifetime", "life-token"))))
        idle()

        assertTrue(result.await() is PurchaseResult.Purchased)
        assertEquals(listOf("life-token"), port.acknowledged)
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
    }

    @Test
    fun `a disconnect while the sheet is open doesn't block the purchase or the next one`() = runTest {
        val port = process(events)
        port.onLaunch = { port.disconnect() }
        val result = buy(TestProducts.Lifetime)
        runCurrent()
        idle()
        assertEquals(BillingConnectionState.Connected, AdroitBilling.connection.value)

        port.emit(PlayPurchasesUpdate.Cancelled)
        idle()
        assertTrue(result.await() is PurchaseResult.Cancelled)

        port.onLaunch = {}
        val next = buy(TestProducts.Coins)
        runCurrent()
        assertEquals(2, port.launches.size)
        port.emit(PlayPurchasesUpdate.Cancelled)
        idle()
        assertTrue(next.await() is PurchaseResult.Cancelled)
    }

    @Test
    fun `a disconnect during acknowledge reconnects and acknowledges once more, without a second grant`() {
        val owned = purchase("premium_lifetime", "life-token")
        val port = FakePlayBillingPort().apply {
            acknowledgeResults += disconnected
            this.owned[PlayProductType.INAPP] = listOf(owned)
        }
        AdroitBilling.portFactory = { port }
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries, events = listOf(BillingEventListener { events += it })))
        idle()

        assertEquals(listOf("life-token", "life-token"), port.acknowledged)
        assertEquals(TokenState.ACKNOWLEDGED, TokenLedger(application)["life-token"])
        assertEquals(1, count<BillingEvent.PurchaseCompleted>())
        assertEquals(0, count<BillingEvent.AcknowledgeFailed>())
    }

    @Test
    fun `a Play service that never answers fails calls after 10 s instead of hanging`() = runTest {
        val port = FakePlayBillingPort().apply { connectGate = CompletableDeferred() }
        AdroitBilling.portFactory = { port }
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries))

        val restored = AdroitBilling.restore()

        assertTrue((restored as RestoreResult.Failed).error is BillingError.ServiceDisconnected)
        assertEquals(10_000L, currentTime)
        assertFalse(AdroitBilling.entitlements.value.has("premium"))
    }

    @Test
    fun `a purchase while Play never answers ends instead of hanging`() = runTest {
        val port = FakePlayBillingPort().apply { connectGate = CompletableDeferred() }
        AdroitBilling.portFactory = { port }
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries))

        val result = buy(TestProducts.Lifetime).await()

        assertTrue(result is PurchaseResult.Failed)
        assertTrue(currentTime <= TimeUnit.SECONDS.toMillis(15))
        assertTrue(port.launches.isEmpty())
    }
}
