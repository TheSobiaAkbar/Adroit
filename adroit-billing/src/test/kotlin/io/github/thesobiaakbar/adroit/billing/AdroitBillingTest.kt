package io.github.thesobiaakbar.adroit.billing

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.internal.entitlement.EntitlementCache
import io.github.thesobiaakbar.adroit.billing.internal.entitlement.SavedGrants
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayProductType
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import io.github.thesobiaakbar.adroit.billing.internal.play.OfferKey
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayProduct
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchasesUpdate
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class AdroitBillingTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val valid = BillingSettings(TestProducts.entries, TestPaywalls.entries)
    private val invalid = BillingSettings(emptyList())

    private val port = FakePlayBillingPort()

    @Before
    fun setUp() {
        AdroitBilling.resetForTests()
        AdroitBilling.portFactory = { port }
    }

    @After
    fun tearDown() {
        setDebuggable(false)
        AdroitBilling.resetForTests()
    }

    private fun setDebuggable(debuggable: Boolean) {
        val info = application.applicationInfo
        info.flags = if (debuggable) info.flags or ApplicationInfo.FLAG_DEBUGGABLE
        else info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    @Test
    fun `flows exist before initialize and start unknown`() {
        assertEquals(EntitlementSnapshot.EMPTY_UNKNOWN, AdroitBilling.entitlements.value)
        assertEquals(BillingConnectionState.Disconnected, AdroitBilling.connection.value)
        assertEquals(CatalogState.Loading, AdroitBilling.catalog.value)
    }

    @Test
    fun `calls before initialize report NotInitialized instead of throwing`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val result = AdroitBilling.purchase(activity, PurchaseRequest(TestProducts.PremiumSub, placement = "home"))
        assertEquals(PurchaseResult.Failed(BillingError.NotInitialized, null), result)
        assertEquals(RestoreResult.Failed(BillingError.NotInitialized), AdroitBilling.restore())
        AdroitBilling.showInAppMessages(activity)
        assertEquals(0, port.inAppMessageCalls)
    }

    @Test
    fun `the manage subscriptions link names the product when given`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=premium_sub&package=com.example",
            manageSubscriptionsUri("com.example", "premium_sub").toString(),
        )
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=com.example",
            manageSubscriptionsUri("com.example", null).toString(),
        )
    }

    @Test
    fun `openManageSubscriptions opens Play's page for the app`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertTrue(AdroitBilling.openManageSubscriptions(activity, TestProducts.PremiumSub))
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(manageSubscriptionsUri(activity.packageName, "premium_sub"), intent.data)
    }

    @Test
    fun `an in-app message that changed a subscription syncs purchases`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        AdroitBilling.initialize(application, valid)
        shadowOf(Looper.getMainLooper()).idle()
        val queries = port.queryPurchasesCalls

        AdroitBilling.showInAppMessages(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, port.inAppMessageCalls)
        assertEquals(queries, port.queryPurchasesCalls)

        port.inAppMessageResult = PlayResult.Ok("fixed-token")
        AdroitBilling.showInAppMessages(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(queries + 2, port.queryPurchasesCalls)
    }

    @Test
    fun `needsPaymentFix marks suspended grants`() {
        val suspended = EntitlementGrant("premium_sub", ProductType.SUBSCRIPTION, setOf("premium"), GrantState.SUSPENDED, GrantSource.CLIENT, 1)
        val snapshot = EntitlementSnapshot(listOf(suspended), SnapshotStatus.Fresh(1), emptySet())
        assertTrue(suspended.needsPaymentFix)
        assertTrue(snapshot.needsPaymentFix)
        assertFalse(snapshot.has("premium"))
        assertFalse(suspended.copy(state = GrantState.ACTIVE).needsPaymentFix)
    }

    @Test
    fun `valid settings are kept`() {
        AdroitBilling.initialize(application, valid)
        assertSame(valid, AdroitBilling.settings)
    }

    @Test
    fun `invalid settings throw in a debuggable app, listing the problem`() {
        setDebuggable(true)
        val error = assertThrows(IllegalStateException::class.java) { AdroitBilling.initialize(application, invalid) }
        assertTrue(error.message!!.contains("products is empty"))
        assertNull(AdroitBilling.settings)
    }

    @Test
    fun `invalid settings leave billing off in a release app, without throwing`() {
        setDebuggable(false)
        AdroitBilling.initialize(application, invalid)
        assertNull(AdroitBilling.settings)
    }

    @Test
    fun `a second initialize with equal settings does nothing`() {
        setDebuggable(true)
        AdroitBilling.initialize(application, valid)
        val runtime = AdroitBilling.runtime
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries, TestPaywalls.entries))
        assertSame(runtime, AdroitBilling.runtime)
    }

    @Test
    fun `a second initialize with different settings throws in a debuggable app`() {
        setDebuggable(true)
        AdroitBilling.initialize(application, valid)
        assertThrows(IllegalStateException::class.java) {
            AdroitBilling.initialize(application, BillingSettings(listOf(TestProducts.Coins)))
        }
        assertSame(valid, AdroitBilling.settings)
    }

    @Test
    fun `a second initialize keeps the first settings in a release app`() {
        setDebuggable(false)
        AdroitBilling.initialize(application, valid)
        AdroitBilling.initialize(application, BillingSettings(listOf(TestProducts.Coins)))
        assertSame(valid, AdroitBilling.settings)
    }

    @Test
    fun `saved entitlements are available as soon as initialize returns`() {
        val lifetime = EntitlementGrant("premium_lifetime", ProductType.NON_CONSUMABLE, setOf("premium"), GrantState.ACTIVE, GrantSource.CLIENT, 1)
        EntitlementCache(application).save(SavedGrants(listOf(lifetime), atMillis = 1))
        port.connectGate = CompletableDeferred()
        AdroitBilling.initialize(application, valid)
        assertEquals(SnapshotStatus.Cached(1), AdroitBilling.entitlements.value.status)
        assertTrue(AdroitBilling.entitlements.value.has("premium"))
    }

    @Test
    fun `initialize syncs purchases once connected, and restore reads them again`() = runTest {
        port.owned[PlayProductType.INAPP] = listOf(
            PlayPurchase(listOf("premium_lifetime"), "life-token", null, 1, PlayPurchaseState.PURCHASED, 1, true, false, false, null, null, "{}", ""),
        )
        AdroitBilling.initialize(application, valid)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AdroitBilling.entitlements.value.status is SnapshotStatus.Fresh)
        assertTrue(AdroitBilling.entitlements.value.adFree)
        val restored = AdroitBilling.restore() as RestoreResult.Restored
        assertEquals(emptySet<String>(), restored.newlyActive)
    }

    @Test
    fun `setAccount before initialize is remembered`() {
        AdroitBilling.setAccount("user-1")
        assertEquals("user-1", AdroitBilling.requestedAccount)
    }

    @Test
    fun `listeners from settings and addEventListener get events until removed`() = runTest {
        val fromSettings = mutableListOf<BillingEvent>()
        val added = mutableListOf<BillingEvent>()
        val listener = BillingEventListener { added += it }
        AdroitBilling.addEventListener(listener)
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries, events = listOf(BillingEventListener { fromSettings += it })))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(BillingEvent.ConfigLoaded(null, added[0].timestampMillis), added[0])
        assertTrue(added[1] is BillingEvent.ConnectionStarted)
        assertEquals(fromSettings, added)

        AdroitBilling.removeEventListener(listener)
        val before = added.size
        AdroitBilling.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fromSettings.last() is BillingEvent.CatalogLoaded)
        assertEquals(before + 1, fromSettings.size)
        assertEquals(before, added.size)
    }

    @Test
    fun `initialize connects to Play`() {
        AdroitBilling.initialize(application, valid)
        assertEquals(BillingConnectionState.Connected, AdroitBilling.connection.value)
        assertEquals(1, port.connectCalls)
    }

    @Test
    fun `refresh reconnects after billing was unavailable`() = runTest {
        port.connectResults += PlayResult.Failed(BillingError.Unavailable(3))
        AdroitBilling.initialize(application, valid)
        assertTrue(AdroitBilling.connection.value is BillingConnectionState.Unavailable)
        AdroitBilling.refresh()
        assertEquals(BillingConnectionState.Connected, AdroitBilling.connection.value)
    }

    @Test
    fun `the catalog loads once connected and products can be looked up`() = runTest {
        assertNull(AdroitBilling.product(TestProducts.Coins))
        val coins = ProductInfo(TestProducts.Coins, "coins_100", ProductType.CONSUMABLE, "t", "n", "d",
            listOf(OneTimeOption(null, null, emptyList(), Price("$0.99", 990_000, "USD"))), emptyList())
        port.products["coins_100"] = PlayProduct(coins, emptyMap())
        AdroitBilling.initialize(application, valid)
        assertEquals(setOf(TestProducts.PremiumSub, TestProducts.Lifetime), (AdroitBilling.catalog.value as CatalogState.Ready).missing)
        assertEquals(coins, AdroitBilling.product(TestProducts.Coins))
        assertEquals(coins, AdroitBilling.awaitProduct(TestProducts.Coins))
        assertNull(AdroitBilling.awaitProduct(TestProducts.Lifetime))
    }

    @Test
    fun `refresh reloads the catalog`() = runTest {
        AdroitBilling.initialize(application, valid)
        val calls = port.queryProductsCalls
        AdroitBilling.refresh()
        assertEquals(calls + 2, port.queryProductsCalls)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `awaitProduct gives up after its timeout`() = runTest {
        assertNull(AdroitBilling.awaitProduct(TestProducts.Coins, timeout = 1.seconds))
        assertEquals(1_000L, currentTime)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `purchase runs through the initialized runtime`() = runTest {
        val coins = ProductInfo(TestProducts.Coins, "coins_100", ProductType.CONSUMABLE, "t", "n", "d",
            listOf(OneTimeOption(null, null, emptyList(), Price("$0.99", 990_000, "USD"))), emptyList())
        port.products["coins_100"] = PlayProduct(coins, mapOf(OfferKey(null, null) to "token-coins"))
        AdroitBilling.initialize(application, valid)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val result = backgroundScope.async { AdroitBilling.purchase(activity, PurchaseRequest(TestProducts.Coins, placement = "shop")) }
        runCurrent()
        assertEquals("token-coins", port.launches.single().offerToken)
        port.emit(PlayPurchasesUpdate.Cancelled)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("shop", (result.await() as PurchaseResult.Cancelled).attribution.placement)
    }

    @Test
    fun `consumables hands each collector the grants still waiting`() = runTest {
        val grant = ConsumableGrant("coins_100", 1, Attribution.unknown("coins_100"), "order-hash") {}
        AdroitBilling.sharedConsumables.value = listOf(grant)
        assertEquals(grant, AdroitBilling.consumables.first())
        assertEquals(grant, AdroitBilling.consumables.first())
    }

    @Test
    fun `only shutdownForTests ends the connection`() {
        AdroitBilling.initialize(application, valid)
        Robolectric.buildActivity(Activity::class.java).setup().destroy()
        assertEquals(0, port.endConnectionCalls)
        AdroitBilling.shutdownForTests()
        assertEquals(1, port.endConnectionCalls)
        assertEquals(BillingConnectionState.Disconnected, AdroitBilling.connection.value)
    }

    @Test
    fun `a config update re-resolves paywalls for the next opening`() {
        var value: String? = null
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val source = object : BillingConfigSource {
            override fun raw(key: String) = if (key == "Onboarding") value else null
            override val updates = changes
        }
        AdroitBilling.initialize(application, BillingSettings(TestProducts.entries, TestPaywalls.entries, config = source))
        shadowOf(Looper.getMainLooper()).idle()
        val config = AdroitBilling.runtime!!.config
        val opened = config.paywall(TestPaywalls.Onboarding)
        value = """{"close": {"delaySeconds": 9}}"""
        changes.tryEmit(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(9, config.paywall(TestPaywalls.Onboarding).spec.close.delaySeconds)
        assertEquals(3, opened.spec.close.delaySeconds)
    }
}
