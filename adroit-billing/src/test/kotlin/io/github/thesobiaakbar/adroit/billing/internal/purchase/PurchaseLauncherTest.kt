package io.github.thesobiaakbar.adroit.billing.internal.purchase

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.AttributionToken
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.OfferSelection
import io.github.thesobiaakbar.adroit.billing.PurchaseRequest
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.PurchaseUpdate
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.attribution.Attempt
import io.github.thesobiaakbar.adroit.billing.internal.attribution.AttemptStore
import io.github.thesobiaakbar.adroit.billing.internal.catalog.CatalogRepository
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.CatalogFixtures
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.FakePlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchase
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchaseState
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayPurchasesUpdate
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.play.toPlayProduct
import io.github.thesobiaakbar.adroit.billing.internal.sha256Hex
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PurchaseLauncherTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val port = FakePlayBillingPort()
    private val catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)
    private val log = BillingLog(BillingLogLevel.NONE)
    private val events = mutableListOf<BillingEvent>()
    private val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 16)
    private val received = mutableListOf<PurchaseUpdate>()
    private val store = AttemptStore(application)
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Before
    fun setUp() {
        port.products["premium_sub"] = CatalogFixtures.productDetails(CatalogFixtures.subscription).toPlayProduct(TestProducts.PremiumSub)
        port.products["premium_lifetime"] = CatalogFixtures.productDetails(CatalogFixtures.lifetime).toPlayProduct(TestProducts.Lifetime)
    }

    private suspend fun TestScope.launcher(
        token: AttributionToken = AttributionToken.LOCAL_ONLY,
        account: String? = null,
    ): PurchaseLauncher {
        val settings = BillingSettings(
            TestProducts.entries, TestPaywalls.entries, events = listOf(BillingEventListener { events += it }),
            attributionToken = token,
        )
        val connection = ConnectionManager(port, backgroundScope, MutableStateFlow(BillingConnectionState.Connected), log)
        val catalog = CatalogRepository(connection, settings.products, catalogState, backgroundScope, log, maxAgeMillis = 1)
        catalog.load()
        val launcher = PurchaseLauncher(
            connection, catalog, catalogState, settings, store, updates, EventBus(settings.events, log), log,
            account = { account }, debuggable = true,
            PurchaseProcessor(
                connection, settings, TokenLedger(application), updates, MutableStateFlow(emptyList()),
                EventBus(settings.events, log), log, application.packageName, { account },
            ),
        )
        backgroundScope.launch { launcher.handle(port.purchasesUpdates) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { updates.collect { received += it } }
        runCurrent()
        return launcher
    }

    private fun TestScope.start(launcher: PurchaseLauncher, request: PurchaseRequest): Deferred<PurchaseResult> =
        backgroundScope.async { launcher.purchase(activity, request) }.also { runCurrent() }

    private fun TestScope.play(update: PlayPurchasesUpdate) {
        port.emit(update)
        runCurrent()
    }

    private fun purchase(product: String = "premium_sub", state: PlayPurchaseState = PlayPurchaseState.PURCHASED, profileId: String? = null) =
        PlayPurchase(
            listOf(product), "token-1", "GPA.1", 0, state, 1, isAcknowledged = false, isAutoRenewing = true,
            isSuspended = false, obfuscatedAccountId = null, obfuscatedProfileId = profileId, originalJson = "{}", signature = "",
        )

    private val sub = PurchaseRequest(TestProducts.PremiumSub, placement = "home", basePlanId = "monthly", offer = OfferSelection.Id("trial"))

    @Test
    fun `a purchase launches the requested offer and returns Play's result with its attribution`() = runTest {
        val launcher = launcher()
        val result = start(launcher, sub.copy(campaignId = "spring"))
        val params = port.launches.single()
        assertEquals("token-monthly-trial", params.offerToken)
        assertNull(params.obfuscatedAccountId)
        assertNull(params.obfuscatedProfileId)

        play(PlayPurchasesUpdate.Purchased(listOf(purchase())))
        val attribution = (result.await() as PurchaseResult.Purchased).attribution
        assertEquals(
            Attribution(attribution.attemptId, "home", null, "spring", "premium_sub", "monthly", "trial", recovered = false),
            attribution,
        )
        assertEquals(
            listOf(
                BillingEvent.PurchaseInitiated(attribution, 0),
                BillingEvent.PurchaseCompleted(listOf("premium_sub"), attribution, 0),
                BillingEvent.EntitlementActivated("premium_sub", setOf("premium", "ad_free"), attribution, 0),
            ),
            events.map { it.withTime(0) },
        )
        assertEquals(listOf("token-1"), port.acknowledged)
        assertEquals(emptyList<PurchaseUpdate>(), received)
    }

    @Test
    fun `without a base plan the paywall's plan is used, else Play's first`() = runTest {
        val launcher = launcher()
        start(launcher, PurchaseRequest(TestProducts.PremiumSub, "onboarding", paywallId = "Onboarding"))
        assertEquals("token-yearly-base", port.launches.last().offerToken)
        play(PlayPurchasesUpdate.Cancelled)
        start(launcher, PurchaseRequest(TestProducts.PremiumSub, "home"))
        assertEquals("token-weekly-base", port.launches.last().offerToken)
    }

    @Test
    fun `one-time products use the option's regular price unless an offer is requested`() = runTest {
        val launcher = launcher()
        start(launcher, PurchaseRequest(TestProducts.Lifetime, "home"))
        assertEquals("token-buy", port.launches.last().offerToken)
        play(PlayPurchasesUpdate.Cancelled)
        start(launcher, PurchaseRequest(TestProducts.Lifetime, "home", offer = OfferSelection.Tags(listOf("launch"))))
        assertEquals("token-buy-launch", port.launches.last().offerToken)
    }

    @Test
    fun `a missing offer fails without launching and is never substituted`() = runTest {
        val launcher = launcher()
        assertEquals(
            PurchaseResult.Failed(BillingError.OfferNotFound("premium_sub", "weekly", "trial"), null),
            launcher.purchase(activity, sub.copy(basePlanId = "weekly")),
        )
        assertEquals(
            PurchaseResult.Failed(BillingError.OfferNotFound("premium_sub", "monthly", "with tags [discount]"), null),
            launcher.purchase(activity, sub.copy(offer = OfferSelection.Tags(listOf("discount")))),
        )
        assertEquals(
            PurchaseResult.Failed(BillingError.OfferNotFound("premium_sub", "quarterly", null), null),
            launcher.purchase(activity, sub.copy(basePlanId = "quarterly")),
        )
        assertEquals(
            PurchaseResult.Failed(BillingError.ProductNotFound("coins_100"), null),
            launcher.purchase(activity, PurchaseRequest(TestProducts.Coins, "shop")),
        )
        assertEquals(emptyList<Any>(), port.launches)
        assertEquals(emptyList<Attempt>(), store.all())
        assertTrue(events.all { it is BillingEvent.PurchaseFailed })
    }

    @Test
    fun `the attempt is saved before Play's sheet opens`() = runTest {
        val launcher = launcher()
        var saved: List<Attempt>? = null
        port.onLaunch = { saved = AttemptStore(application).all() }
        start(launcher, sub)
        assertEquals("home", saved!!.single().attribution.placement)
        assertTrue(events.single() is BillingEvent.PurchaseInitiated)
    }

    @Test
    fun `a launch error fails the attempt and frees the next purchase`() = runTest {
        val launcher = launcher()
        port.launchResults += PlayResult.Failed(BillingError.DeveloperError(5, "bad offer token"))
        val result = launcher.purchase(activity, sub) as PurchaseResult.Failed
        assertEquals(BillingError.DeveloperError(5, "bad offer token"), result.error)
        assertEquals("home", result.attribution!!.placement)
        assertEquals(emptyList<Attempt>(), store.all())
        assertTrue(events.last() is BillingEvent.PurchaseFailed)

        start(launcher, sub)
        assertEquals(2, port.launches.size)
    }

    @Test
    fun `closing Play's sheet is Cancelled, not an error`() = runTest {
        val launcher = launcher()
        val result = start(launcher, sub)
        play(PlayPurchasesUpdate.Cancelled)
        assertTrue(result.await() is PurchaseResult.Cancelled)
        assertTrue(events.last() is BillingEvent.PurchaseCancelled)
        assertEquals(emptyList<Attempt>(), store.all())
    }

    @Test
    fun `an item already owned is AlreadyOwned`() = runTest {
        val launcher = launcher()
        val result = start(launcher, sub)
        play(PlayPurchasesUpdate.Failed(BillingError.ItemAlreadyOwned(7)))
        assertEquals("home", (result.await() as PurchaseResult.AlreadyOwned).attribution.placement)
    }

    @Test
    fun `a second purchase while one runs fails at once`() = runTest {
        val launcher = launcher()
        val first = start(launcher, sub)
        assertEquals(
            PurchaseResult.Failed(BillingError.PurchaseInProgress, null),
            launcher.purchase(activity, PurchaseRequest(TestProducts.Lifetime, "settings")),
        )
        assertEquals(1, port.launches.size)
        play(PlayPurchasesUpdate.Purchased(listOf(purchase())))
        assertTrue(first.await() is PurchaseResult.Purchased)
    }

    @Test
    fun `a finishing Activity is NoActivity`() = runTest {
        val launcher = launcher()
        activity.finish()
        assertEquals(PurchaseResult.Failed(BillingError.NoActivity, null), launcher.purchase(activity, sub))
    }

    @Test
    fun `a pending purchase frees the next purchase and keeps its attempt for later`() = runTest {
        val launcher = launcher()
        val result = start(launcher, sub)
        play(PlayPurchasesUpdate.Purchased(listOf(purchase(state = PlayPurchaseState.PENDING))))
        val attribution = (result.await() as PurchaseResult.Pending).attribution

        play(PlayPurchasesUpdate.Purchased(listOf(purchase())))
        assertEquals(
            listOf(
                PurchaseUpdate.Pending(listOf("premium_sub"), attribution),
                PurchaseUpdate.Completed(listOf("premium_sub"), attribution.copy(recovered = true)),
            ),
            received,
        )
    }

    @Test
    fun `when the caller is cancelled the outcome arrives on purchaseUpdates`() = runTest {
        val launcher = launcher()
        val result = start(launcher, sub)
        result.cancel()
        runCurrent()
        play(PlayPurchasesUpdate.Purchased(listOf(purchase())))
        val update = received.single() as PurchaseUpdate.Completed
        assertEquals("home", update.attribution.placement)
        assertFalse(update.attribution.recovered)
    }

    @Test
    fun `a purchase of an earlier process is matched to its saved attempt`() = runTest {
        val saved = Attribution("a-1", "onboarding", "Onboarding", null, "premium_sub", "yearly", null, recovered = false)
        store.save(Attempt(saved, System.currentTimeMillis()))
        launcher()
        play(PlayPurchasesUpdate.Purchased(listOf(purchase())))
        assertEquals(listOf(PurchaseUpdate.Completed(listOf("premium_sub"), saved.copy(recovered = true))), received)
    }

    @Test
    fun `an account is sent only as a hash, and the profile ID only when asked`() = runTest {
        val launcher = launcher(account = "user-1")
        start(launcher, sub)
        assertEquals(sha256Hex("user-1"), port.launches.last().obfuscatedAccountId)
        assertNull(port.launches.last().obfuscatedProfileId)
    }

    @Test
    fun `the profile ID carries the attempt ID, with an install ID as account`() = runTest {
        val launcher = launcher(token = AttributionToken.PROFILE_ID)
        val result = start(launcher, sub)
        val params = port.launches.last()
        val accountId = params.obfuscatedAccountId!!
        assertEquals(32, accountId.length)
        assertNotEquals(params.obfuscatedProfileId, accountId)

        play(PlayPurchasesUpdate.Purchased(listOf(purchase(profileId = params.obfuscatedProfileId))))
        val attribution = (result.await() as PurchaseResult.Purchased).attribution
        assertEquals(attribution.attemptId!!.replace("-", ""), params.obfuscatedProfileId)
    }

    private fun BillingEvent.withTime(time: Long): BillingEvent = when (this) {
        is BillingEvent.PurchaseInitiated -> copy(timestampMillis = time)
        is BillingEvent.PurchaseCompleted -> copy(timestampMillis = time)
        is BillingEvent.EntitlementActivated -> copy(timestampMillis = time)
        else -> this
    }
}
