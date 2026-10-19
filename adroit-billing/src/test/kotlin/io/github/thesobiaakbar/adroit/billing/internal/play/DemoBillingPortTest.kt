package io.github.thesobiaakbar.adroit.billing.internal.play

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.BillingPeriod
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class DemoBillingPortTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        application.getSharedPreferences("adroit_billing_demo", 0).edit().clear().commit()
    }

    private fun port() = DemoBillingPort(application, TestProducts.entries, TestPaywalls.entries, pendingMillis = 1_000)

    @Test
    fun `products have Play's shape with DEMO prices`() = runTest {
        val subs = (port().queryProducts(PlayProductType.SUBS, TestProducts.entries) as PlayResult.Ok).value
        val sub = subs.products.single().info

        assertEquals(listOf("weekly", "monthly", "yearly"), sub.plans.map { it.basePlanId }.take(3))
        val monthly = sub.plans.first { it.basePlanId == "monthly" }
        assertEquals(listOf("demo-trial", null), monthly.offers.map { it.offerId })
        assertEquals(BillingPeriod(days = 3), monthly.offers[0].freeTrial)
        assertEquals("$4.99 (DEMO)", monthly.basePhase?.price?.formatted)
        assertEquals(listOf("intro"), sub.plans.first { it.basePlanId == "yearly" }.offers[0].offerTags)
        assertTrue(subs.products.single().offerTokens.containsKey(OfferKey("monthly", "demo-trial")))

        val inapp = (port().queryProducts(PlayProductType.INAPP, TestProducts.entries) as PlayResult.Ok).value
        assertEquals(setOf("premium_lifetime", "coins_100"), inapp.products.map { it.info.productId }.toSet())
    }

    @Test
    fun `a bought purchase is kept until consumed, across instances`() = runTest {
        val port = port()
        port.choose(LaunchParams("coins_100", "demo:coins_100"), 0)
        val purchase = (port.purchasesUpdates.first() as PlayPurchasesUpdate.Purchased).purchases.single()
        assertEquals(PlayPurchaseState.PURCHASED, purchase.state)

        assertEquals(listOf(purchase.purchaseToken), (port().queryPurchases(PlayProductType.INAPP, false) as PlayResult.Ok).value.map { it.purchaseToken })
        port.consume(purchase.purchaseToken)
        assertEquals(emptyList<PlayPurchase>(), (port().queryPurchases(PlayProductType.INAPP, false) as PlayResult.Ok).value)
    }

    @Test
    fun `cancel reports a cancelled flow and stores nothing`() = runTest {
        val port = port()
        port.choose(LaunchParams("premium_sub", "demo:premium_sub:monthly:null"), 2)
        assertEquals(PlayPurchasesUpdate.Cancelled, port.purchasesUpdates.first())
        assertEquals(emptyList<PlayPurchase>(), (port.queryPurchases(PlayProductType.SUBS, false) as PlayResult.Ok).value)
    }

    @Test
    fun `a pending purchase completes later`() = runTest {
        val port = port()
        port.choose(LaunchParams("premium_sub", "demo:premium_sub:monthly:null"), 1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        val states = port.purchasesUpdates.take(2).toList().map { (it as PlayPurchasesUpdate.Purchased).purchases.single().state }
        assertEquals(listOf(PlayPurchaseState.PENDING, PlayPurchaseState.PURCHASED), states)
        val stored = (port.queryPurchases(PlayProductType.SUBS, false) as PlayResult.Ok).value.single()
        assertEquals(PlayPurchaseState.PURCHASED, stored.state)
        assertTrue(stored.isAutoRenewing)
    }
}
