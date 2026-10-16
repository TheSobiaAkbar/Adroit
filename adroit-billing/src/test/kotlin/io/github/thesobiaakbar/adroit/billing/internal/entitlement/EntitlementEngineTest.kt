package io.github.thesobiaakbar.adroit.billing.internal.entitlement

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingConfigSource
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.EntitlementGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementLossReason
import io.github.thesobiaakbar.adroit.billing.EntitlementProvider
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.GrantSource
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.ServerLifecycleKind
import io.github.thesobiaakbar.adroit.billing.SnapshotStatus
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.attribution.Attempt
import io.github.thesobiaakbar.adroit.billing.internal.attribution.AttemptStore
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayProductType
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.purchase.PurchaseProcessor
import io.github.thesobiaakbar.adroit.billing.internal.purchase.TokenLedger
import io.github.thesobiaakbar.adroit.billing.internal.sha256Hex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import org.robolectric.Shadows.shadowOf
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EntitlementEngineTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val port = FakePlayBillingPort()
    private val log = BillingLog(BillingLogLevel.NONE)
    private val events = mutableListOf<BillingEvent>()
    private val state = MutableStateFlow(EntitlementSnapshot.EMPTY_UNKNOWN)
    private val attempts = AttemptStore(application)
    private val cache = EntitlementCache(application)

    private fun TestScope.engine(
        remote: String? = null,
        provider: EntitlementProvider? = null,
        adFree: Set<String>? = null,
        config: BillingConfigSource = BillingConfigSource { if (it == "AdroitBilling") remote else null },
    ): EntitlementEngine {
        val settings = BillingSettings(
            TestProducts.entries,
            config = config,
            entitlementProvider = provider,
            events = listOf(BillingEventListener { events += it }),
            adFreeEntitlements = adFree,
        )
        val connection = ConnectionManager(port, backgroundScope, MutableStateFlow(BillingConnectionState.Connected), log)
        val bus = EventBus(settings.events, log)
        val ledger = TokenLedger(application)
        val processor = PurchaseProcessor(
            connection, settings, ledger, MutableSharedFlow(), MutableStateFlow(emptyList()), bus, log, "com.example",
            { null }, now = { currentTime },
        )
        return EntitlementEngine(
            connection, settings, processor, ledger, attempts, cache, state, bus, backgroundScope, log, { "user-1" },
            now = { currentTime },
        )
    }

    private fun purchase(
        product: String = "premium_sub",
        token: String = "sub-token",
        state: PlayPurchaseState = PlayPurchaseState.PURCHASED,
        suspended: Boolean = false,
    ) = PlayPurchase(
        listOf(product), token, "GPA.$token", 5, state, 1, isAcknowledged = true, isAutoRenewing = true,
        isSuspended = suspended, obfuscatedAccountId = null, obfuscatedProfileId = null, originalJson = "{}", signature = "",
    )

    private fun owns(vararg subs: PlayPurchase, inapp: List<PlayPurchase> = emptyList()) {
        port.owned[PlayProductType.SUBS] = subs.toList()
        port.owned[PlayProductType.INAPP] = inapp
    }

    private fun grant(product: String, type: ProductType, state: GrantState = GrantState.ACTIVE, source: GrantSource = GrantSource.CLIENT) =
        EntitlementGrant(product, type, setOf("premium", "ad_free"), state, source, 5)

    private val sub = grant("premium_sub", ProductType.SUBSCRIPTION)
    private val lifetime = grant("premium_lifetime", ProductType.NON_CONSUMABLE)
    private val failed = PlayResult.Failed(BillingError.NetworkError())

    @Test
    fun `the cache is published synchronously at start, before Play answers`() = runTest {
        cache.save(SavedGrants(listOf(sub), atMillis = -3_600_000))
        engine()
        val snapshot = state.value
        assertEquals(SnapshotStatus.Cached(-3_600_000), snapshot.status)
        assertTrue(snapshot.has("premium"))
        assertTrue(snapshot.adFree)
        assertEquals(0, port.queryPurchasesCalls)
    }

    @Test
    fun `without a cache the start is unknown and grants nothing`() = runTest {
        engine()
        assertEquals(SnapshotStatus.Unknown, state.value.status)
        assertFalse(state.value.adFree)
    }

    @Test
    fun `a sync grants owned products and saves them`() = runTest {
        owns(purchase(), inapp = listOf(purchase("premium_lifetime", "life-token"), purchase("coins_100", "coin-token")))
        val engine = engine()
        assertNull(engine.sync())
        val snapshot = state.value
        assertEquals(SnapshotStatus.Fresh(0), snapshot.status)
        assertEquals(listOf(lifetime, sub), snapshot.grants)
        assertEquals(snapshot.grants, cache.load()!!.grants)
    }

    @Test
    fun `pending and suspended purchases are listed but grant nothing`() = runTest {
        owns(purchase(suspended = true), inapp = listOf(purchase("premium_lifetime", "life-token", PlayPurchaseState.PENDING)))
        engine().sync()
        assertEquals(listOf(GrantState.PENDING, GrantState.SUSPENDED), state.value.grants.map { it.state })
        assertEquals(emptySet<String>(), state.value.active)
        assertFalse(state.value.adFree)
    }

    @Test
    fun `suspended subscriptions are queried when Play supports it`() = runTest {
        port.featuresSupported = true
        engine().sync()
        assertEquals(listOf(false, true), port.includeSuspendedArgs)
    }

    @Test
    fun `a failed sync keeps the grants and adds none`() = runTest {
        owns(purchase())
        val engine = engine()
        engine.sync()
        val before = state.value
        owns(purchase(), inapp = listOf(purchase("premium_lifetime", "life-token")))
        port.purchasesResults += failed
        assertEquals(BillingError.NetworkError(), engine.sync())
        assertEquals(before, state.value)
    }

    @Test
    fun `a failed sync after a cold start keeps the cached grants`() = runTest {
        cache.save(SavedGrants(listOf(sub), atMillis = 0))
        val engine = engine()
        port.purchasesResults += failed
        engine.sync()
        assertEquals(SnapshotStatus.Cached(0), state.value.status)
        assertTrue(state.value.has("premium"))
    }

    @Test
    fun `a subscription without a fresh sync for longer than the grace period is dropped`() = runTest {
        owns(purchase())
        val engine = engine()
        engine.sync()
        repeat(4) { port.purchasesResults += failed }
        advanceTimeBy(72 * 3_600_000L)
        runCurrent()
        assertTrue(state.value.has("premium"))
        advanceTimeBy(2)
        assertEquals(EntitlementSnapshot(emptyList(), SnapshotStatus.Unknown, setOf("premium", "ad_free")), state.value)
        engine.sync()
        assertFalse(state.value.has("premium"))
    }

    @Test
    fun `the remote grace period applies and non-consumables outlive it`() = runTest {
        cache.save(SavedGrants(listOf(sub, lifetime), atMillis = 0))
        engine(remote = """{"offlineGraceHours": 1}""")
        assertTrue(state.value.has("premium"))
        advanceTimeBy(3_600_002)
        assertEquals(listOf(lifetime), state.value.grants)
        assertEquals(SnapshotStatus.Cached(0), state.value.status)
    }

    @Test
    fun `the remote mapping remaps declared products and can't add any`() = runTest {
        val remote = """{"products": {"PremiumSub": {"entitlements": ["gold"]}, "Hacker": {"entitlements": ["premium"]}},
            "adFreeEntitlements": ["gold"]}"""
        owns(purchase(), inapp = listOf(purchase("hacker", "hack-token")))
        engine(remote).sync()
        assertEquals(listOf("premium_sub"), state.value.grants.map { it.productId })
        assertEquals(setOf("gold"), state.value.active)
        assertTrue(state.value.adFree)
    }

    @Test
    fun `the remote mapping grants nothing without a purchase`() = runTest {
        engine("""{"products": {"Lifetime": {"entitlements": ["premium"]}}, "adFreeEntitlements": ["premium"]}""").sync()
        assertEquals(emptySet<String>(), state.value.active)
        assertFalse(state.value.adFree)
    }

    @Test
    fun `an invalid remote value is ignored`() = runTest {
        owns(purchase())
        engine("not json").sync()
        assertEquals(setOf("premium", "ad_free"), state.value.active)
    }

    @Test
    fun `adFree follows the settings, else every entitlement`() = runTest {
        owns(purchase())
        engine(adFree = setOf("vip")).sync()
        assertFalse(state.value.adFree)
        engine().sync()
        assertTrue(state.value.adFree)
    }

    @Test
    fun `backend grants are merged with Play's and win for the same product`() = runTest {
        val grace = grant("premium_sub", ProductType.SUBSCRIPTION, GrantState.GRACE_FROM_BACKEND, GrantSource.BACKEND)
        var accountHash: String? = null
        owns(purchase(suspended = true), inapp = listOf(purchase("premium_lifetime", "life-token")))
        engine(provider = { accountHash = it; listOf(grace) }).sync()
        assertEquals(listOf(lifetime, grace), state.value.grants)
        assertEquals(sha256Hex("user-1"), accountHash)
    }

    @Test
    fun `an unavailable or failing provider keeps Play's grants`() = runTest {
        owns(purchase())
        engine(provider = { null }).sync()
        assertEquals(listOf(sub), state.value.grants)
        engine(provider = { error("down") }).sync()
        assertEquals(listOf(sub), state.value.grants)
    }

    private fun losses() = events.filterIsInstance<BillingEvent.EntitlementLost>().map { it.entitlement to it.reason }

    private fun lifecycle() = events.filterIsInstance<BillingEvent.ServerLifecycle>().map { it.kind }

    @Test
    fun `each lost entitlement is reported once with its client-side reason`() = runTest {
        cache.save(SavedGrants(listOf(sub), atMillis = 0))
        val engine = engine()
        owns(purchase())
        engine.sync()
        assertEquals(emptyList<Any>(), losses())

        owns(purchase(suspended = true))
        engine.sync()
        engine.sync()
        val issue = EntitlementLossReason.PAYMENT_ISSUE
        assertEquals(listOf("premium" to issue, "ad_free" to issue), losses())

        events.clear()
        owns(purchase())
        engine.sync()
        owns()
        engine.sync()
        val gone = EntitlementLossReason.NOT_OWNED
        assertEquals(listOf("premium" to gone, "ad_free" to gone), losses())
    }

    @Test
    fun `a subscription dropped by the grace period is lost for that reason`() = runTest {
        owns(purchase())
        val engine = engine()
        engine.sync()
        repeat(4) { port.purchasesResults += failed }
        advanceTimeBy(72 * 3_600_000L + 2)
        assertEquals(EntitlementLossReason.OFFLINE_GRACE_ENDED, losses().map { it.second }.distinct().single())
    }

    @Test
    fun `an account switch and a remote remap are reported as such`() = runTest {
        var remote: String? = null
        val config = BillingConfigSource { if (it == "AdroitBilling") remote else null }
        owns(purchase())
        val engine = engine(config = config)
        engine.sync()
        remote = """{"products": {"PremiumSub": {"entitlements": ["premium"]}}}"""
        engine.publish()
        assertEquals(listOf("ad_free" to EntitlementLossReason.CONFIG_CHANGED), losses())

        events.clear()
        engine.setAccount("alice")
        runCurrent()
        engine.setAccount("bob")
        runCurrent()
        assertEquals("premium" to EntitlementLossReason.ACCOUNT_SWITCHED, losses().first())
    }

    @Test
    fun `server lifecycle events come only from the provider's changes between syncs`() = runTest {
        var backend: List<EntitlementGrant>? = listOf(grant("premium_sub", ProductType.SUBSCRIPTION, source = GrantSource.BACKEND))
        owns(purchase())
        val engine = engine(provider = { backend })
        engine.sync()
        engine.sync()
        assertEquals(emptyList<ServerLifecycleKind>(), lifecycle())

        for (next in listOf(GrantState.GRACE_FROM_BACKEND, GrantState.ACTIVE, GrantState.SUSPENDED, GrantState.ACTIVE)) {
            backend = listOf(grant("premium_sub", ProductType.SUBSCRIPTION, next, GrantSource.BACKEND))
            engine.sync()
        }
        backend = null
        engine.sync()
        backend = emptyList()
        engine.sync()
        assertEquals(
            listOf(ServerLifecycleKind.GRACE_PERIOD, ServerLifecycleKind.RECOVERED, ServerLifecycleKind.ON_HOLD, ServerLifecycleKind.RECOVERED),
            lifecycle(),
        )
        assertTrue(events.filterIsInstance<BillingEvent.ServerLifecycle>().all { it.source == GrantSource.BACKEND })
    }

    @Test
    fun `the provider ending a grant it reported is ENDED, and Play alone never produces lifecycle events`() = runTest {
        var backend: List<EntitlementGrant>? = listOf(grant("premium_sub", ProductType.SUBSCRIPTION, source = GrantSource.BACKEND))
        val engine = engine(provider = { backend })
        engine.sync()
        backend = emptyList()
        engine.sync()
        assertEquals(listOf(ServerLifecycleKind.ENDED), lifecycle())

        events.clear()
        val play = engine()
        owns(purchase(suspended = true))
        play.sync()
        owns()
        play.sync()
        assertEquals(emptyList<ServerLifecycleKind>(), lifecycle())
    }

    @Test
    fun `restore reports what became active`() = runTest {
        val engine = engine()
        owns(purchase())
        val result = engine.restore() as RestoreResult.Restored
        assertEquals(setOf("premium", "ad_free"), result.newlyActive)
        assertEquals(state.value, result.snapshot)
        assertEquals(setOf("premium", "ad_free"), (events.last() as BillingEvent.RestoreCompleted).restored)
        assertEquals(emptySet<String>(), (engine.restore() as RestoreResult.Restored).newlyActive)
        assertTrue(events.first() is BillingEvent.RestoreInitiated)
    }

    @Test
    fun `restore without purchases has nothing to restore`() = runTest {
        assertEquals(RestoreResult.NothingToRestore, engine().restore())
    }

    @Test
    fun `a failed restore changes nothing`() = runTest {
        cache.save(SavedGrants(listOf(lifetime), atMillis = 0))
        val engine = engine()
        val before = state.value
        port.purchasesResults += failed
        assertEquals(RestoreResult.Failed(BillingError.NetworkError()), engine.restore())
        assertEquals(before, state.value)
        assertFalse(events.any { it is BillingEvent.RestoreCompleted })
    }

    @Test
    fun `switching accounts clears the saved state and syncs again`() = runTest {
        cache.save(SavedGrants(listOf(lifetime), atMillis = 0))
        attempts.save(Attempt(Attribution("a-1", "home", null, null, "premium_sub", null, null, false), 0))
        val engine = engine()
        engine.setAccount("alice")
        engine.setAccount(null)
        runCurrent()
        assertEquals(listOf(lifetime), state.value.grants)
        assertEquals(0, port.queryPurchasesCalls)

        owns(purchase())
        engine.setAccount("bob")
        runCurrent()
        assertEquals(emptyList<Attempt>(), attempts.all())
        assertEquals(2, port.queryPurchasesCalls)
        assertEquals(listOf(sub), state.value.grants)
        assertEquals(sha256Hex("bob"), cache.accountHash)
    }

    @Test
    fun `the foreground syncs at most once every 30 s`() = runTest {
        val engine = engine()
        engine.onForeground()
        runCurrent()
        engine.onForeground()
        advanceTimeBy(29_999)
        engine.onForeground()
        runCurrent()
        assertEquals(2, port.queryPurchasesCalls)
        advanceTimeBy(1)
        engine.onForeground()
        runCurrent()
        assertEquals(4, port.queryPurchasesCalls)
    }

    @Test
    fun `a purchase is in the snapshot when purchase() returns, even if the sync fails`() = runTest {
        val engine = engine()
        port.purchasesResults += failed
        engine.afterPurchase(PurchaseResult.Purchased(sub, Attribution.unknown("premium_sub")))
        assertEquals(listOf(sub), state.value.grants)
        owns(purchase())
        engine.afterPurchase(PurchaseResult.AlreadyOwned(Attribution.unknown("premium_sub")))
        assertEquals(SnapshotStatus.Fresh(0), state.value.status)
    }

    @Test
    fun `the cache holds no purchase token`() = runTest {
        owns(purchase(token = "secret-sub-token"), inapp = listOf(purchase("premium_lifetime", "secret-life-token")))
        engine().sync()
        shadowOf(Looper.getMainLooper()).idle()
        val xml = File(application.applicationInfo.dataDir, "shared_prefs/${EntitlementCache.NAME}.xml").readText()
        assertTrue(xml.contains("premium_lifetime"))
        val prefs = application.getSharedPreferences(EntitlementCache.NAME, Context.MODE_PRIVATE).all.toString()
        for (text in listOf(xml, prefs)) {
            for (token in listOf("secret-sub-token", "secret-life-token")) {
                assertFalse(text.contains(token))
                assertFalse(text.contains(sha256Hex(token).take(12)))
            }
        }
    }
}
