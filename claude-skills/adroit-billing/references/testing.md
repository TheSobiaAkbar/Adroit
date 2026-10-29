# Testing billing

`testImplementation(libs.adroit.billing.testing)` gives you `FakeBilling`, `TestProducts` and `TestPaywalls`.
`FakeBilling` runs on the plain JVM, with no Play and no device, and never touches the `Activity` it is given. On the
plain JVM, the stub `android.jar` throws for `Activity()`, so tests that pass an Activity use Robolectric:
`Robolectric.buildActivity(Activity::class.java).get()`. Alternatively, set
`testOptions { unitTests.isReturnDefaultValues = true }`.

**Double-credit test:** `emitConsumable` gives every grant a new `orderIdHash`. To simulate a redelivered grant,
build one with the same hash. The constructor is public, for tests:
```kotlin
val grant = ConsumableGrant("gems_50", 1, Attribution.unknown("gems_50"), orderIdHash = "order-1") {}
wallet.onGrant(grant); wallet.onGrant(grant)          // the same order twice
assertEquals(50, wallet.balance.value)
```

## `FakeBilling`

```kotlin
class FakeBilling(products: List<ProductInfo> = emptyList()) : BillingClientApi {
    val entitlements: MutableStateFlow<EntitlementSnapshot>    // starts Fresh, empty
    val connection: MutableStateFlow<BillingConnectionState>   // Connected
    val catalog: MutableStateFlow<CatalogState>                // Ready(products)
    var nextPurchaseResult: PurchaseResult?                    // script the next purchase
    var restoreResult: RestoreResult = RestoreResult.NothingToRestore
    var manageSubscriptionsResult: Boolean = true
    val purchaseRequests: List<PurchaseRequest>                // assert basePlanId, placement, offer
    val confirmedConsumables: List<ConsumableGrant>
    fun own(vararg products: ProductDefinition)                // the user now owns these (entitlements update)
    fun holdNextPurchase(): CompletableDeferred<PurchaseResult> // test "purchasing…" states
    fun emitPurchaseUpdate(update: PurchaseUpdate)
    fun emitConsumable(product: ProductDefinition, quantity: Int = 1, placement: String = "test")
}
```

## Products with prices: `TestProducts`

```kotlin
private enum class P(override val defaults: ProductSpec) : ProductDefinition {
    Sub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
    Lifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
}
private val sub = TestProducts.subscription(P.Sub) {
    basePlan("weekly") { recurring("P1W", "€1.99", 1_990_000, "EUR") }
    basePlan("monthly") {
        recurring("P1M", "€4.99", 4_990_000, "EUR")
        offer("trial-3d", tags = listOf("trial")) { trial("P3D") }
    }
}
private val lifetime = TestProducts.oneTime(P.Lifetime, "€29.99", 29_990_000, "EUR")
val billing = FakeBilling(listOf(sub, lifetime))
```
The builder also has `intro(period, formatted, micros, currency, cycles = 1)`.

## Paywalls: `TestPaywalls` + `PaywallController.forModel`

Test a ViewModel that runs a paywall through a real controller, built from a fixed model. This needs no
`AdroitBilling.initialize`:

```kotlin
private enum class W(override val defaults: PaywallSpec) : PaywallDefinition {
    RemoveAds(PaywallSpec(plans = listOf(PlanSpec(P.Sub, basePlanId = "weekly")),
        close = CloseBehavior(delaySeconds = 2, back = BackBehavior.AFTER_DELAY))),
}

@RunWith(RobolectricTestRunner::class)
class RemoveAdsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val billing = FakeBilling(listOf(sub))

    private fun viewModel() = RemoveAdsViewModel { scope ->           // the feature's fun interface
        PaywallController.forModel(W.RemoveAds, TestPaywalls.model(W.RemoveAds, listOf(sub)), "ad_close",
            scope, billing, clock = { 0L })                           // clock: virtual time drives the close delay
    }

    @Test fun `the cross appears after 2 seconds, then closes`() = runTest {
        val vm = viewModel()
        assertEquals("weekly", vm.state.value.plan?.basePlanId)
        vm.onIntent(SaleIntent.Shown)
        advanceTimeBy(1_999); assertFalse(vm.state.value.closeVisible)
        advanceTimeBy(2);     assertTrue(vm.state.value.closeVisible)
    }

    @Test fun `buying makes the user premium`() = runTest {
        val vm = viewModel()
        billing.nextPurchaseResult = null                             // default: purchase succeeds and owns the product
        vm.onIntent(SaleIntent.Buy(Robolectric.buildActivity(Activity::class.java).get()))
        assertTrue(billing.entitlements.value.has("premium"))
        assertEquals("ad_close", billing.purchaseRequests.single().placement)
        vm.viewModelScope.cancel()                                    // close running timers before runTest returns
    }
}
```
- `forModel` requires a non-empty `model.plans`. It records no events and no show counts.
- `TestPaywalls.model(definition, products, selected = 0, spec = definition.defaults)` picks offers in a simplified
  way, which is enough for UI and ViewModel tests.
- A ViewModel whose controller runs a countdown must be closed (`viewModelScope.cancel()`) before `runTest` returns.
  Otherwise the scheduler keeps draining.

## Plain ViewModel (no paywall)

```kotlin
class PremiumViewModelTest {
    private val billing = FakeBilling()
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `premium follows what the user owns`() {
        val vm = PremiumViewModel(billing)
        assertFalse(vm.entitlements.value.has("premium"))
        billing.own(P.Lifetime)
        assertTrue(vm.entitlements.value.has("premium"))
    }
    @Test fun `restore reports once`() = runTest {
        billing.restoreResult = RestoreResult.NothingToRestore
        val vm = PremiumViewModel(billing); vm.restore()
        assertEquals(RestoreResult.NothingToRestore, vm.restoreResults.first())
    }
}
```

## What to cover

- Every `PaywallUiState` branch:
  - `Purchased` ends the paywall as premium;
  - `Closed` continues the flow;
  - `Unavailable(null)` (offline) shows no price and can be closed;
  - `Unavailable(ENTITLED)` ends as premium.
- `Pending` shows the pending message and grants nothing. Use `nextPurchaseResult = PurchaseResult.Pending(...)`, or
  `holdNextPurchase()`.
- The close delay and back behaviour, in virtual time.
- The purchase request carries the right `basePlanId`, offer and `placement`.
- Consumables: crediting is idempotent by `orderIdHash`, and every grant is confirmed (`confirmedConsumables`).
- Gates: premium skips the paywall; closing a gate doesn't open the content.
- Kill switch: with `PremiumEnabled` off, no sale surface appears.

## Manual QA (debug, demo billing)

- [ ] Each paywall shows "(DEMO)" prices. **Buy** closes it and unlocks; the next launch knows the user is premium.
- [ ] **Pending payment**: the "pending" message, then premium about 15 s later. **Cancel**: nothing changes.
- [ ] After buying, with ads: no ad anywhere, no banner shimmer, and the next splash is short with no consent.
- [ ] Get Pro / Join Pro disappear after buying; Restore says "up to date".
- [ ] Airplane mode: paywalls show no price and can be closed; a premium user stays premium (Cached).
- [ ] Rotation mid-paywall keeps the selection and the countdown.

**Real Play:**
1. Set `DemoBilling(false)`, or use a release-signed build.
2. Upload it to internal testing.
3. Add a license tester.
4. Install from Play.

See `play-console-release.md`.
