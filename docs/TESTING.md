# Testing

## Unit tests with `adroit-testing`

`io.github.thesobiaakbar:adroit-testing` replaces the library with fakes that never touch Google's SDKs, so view models and
screens that show ads can be tested on the JVM or with Robolectric.

```kotlin
testImplementation("io.github.thesobiaakbar:adroit-testing:1.0.0-rc6")
```

### `FakeAdsClient`

A complete `AdsClient`. Pass it wherever your code takes an `AdsClient`
([dependency injection](GETTING_STARTED.md#9-dependency-injection)), script the results, and assert:

<!-- snippet: docs-snippets/src/test/kotlin/io/github/thesobiaakbar/adroit/docs/TestingSnippets.kt#fake-client -->
```kotlin
@RunWith(RobolectricTestRunner::class)
class CoinsViewModelTest {
    private val ads = FakeAdsClient()
    private val activity = Activity() // FakeAdsClient never touches it

    @Test
    fun `an earned reward is granted once`() {
        val viewModel = CoinsViewModel(ads)
        ads.enqueueRewardResult(Placements.ExtraLife, RewardResult.Earned(Reward("coins", 50), "reward-1"))

        viewModel.watchAdForCoins(activity)

        assertEquals(50, viewModel.coins.value)
    }
}
```

What you can script and inspect:
- `enqueueInterstitialResult`, `enqueueRewardResult`, `enqueueRewardedInterstitialResult`, `enqueueAppOpenResult`,
  `enqueueNativeResult`: the next result for a placement (without one: `NotShown(NotReady)` or the equivalent).
- `setState`, `setConsentState`, `setAvailability`, `setInitialBannerState`, `setNativeAssets`.
- `emit(event)`, `emitUnclaimedReward`, `emitUnclaimedRewardedInterstitialReward`.
- `recordedCalls`, `createdBanners`, `loadedNativeAds`, `emittedEvents`, `isAdFree`.

Callbacks are synchronous by default; pass an `Executor` to the constructor to make them asynchronous.

### `AdroitTestRule`

For code that calls the `Adroit` object directly, the rule turns it into a `FakeAdsClient` for each test and
restores it afterwards:

<!-- snippet: docs-snippets/src/test/kotlin/io/github/thesobiaakbar/adroit/docs/TestingSnippets.kt#test-rule -->
```kotlin
/** For code that calls the Adroit object directly: the rule makes it a FakeAdsClient for each test. */
@RunWith(RobolectricTestRunner::class)
class LevelFlowTest {
    @get:Rule
    val admob = AdroitTestRule()

    @Test
    fun `the next level opens whether or not the interstitial shows`() {
        admob.client.enqueueInterstitialResult(
            Placements.LevelComplete,
            FullScreenResult.NotShown(AdError.NotReady),
        )
        var opened = false

        onLevelComplete(Activity()) { opened = true }

        assertEquals(true, opened)
    }
}
```

### Robolectric

- `FakeAdsClient` needs no Android framework. Use Robolectric when the code under test does: `viewModelScope`
  (a main dispatcher), Views, `Activity` lifecycles, or Compose UI tests.
- `FakeBannerAdHandle` and `FakeNativeAdHandle` (from `createdBanners` and `loadedNativeAds`) provide real Views for
  layout tests.

## Billing with `adroit-billing-testing`

`io.github.thesobiaakbar:adroit-billing-testing` has `FakeBilling`, a `BillingClientApi` with no Google Play, no
device and no Robolectric. Code that takes `BillingClientApi` (with `AdroitBilling` as the default, or bound by
dependency injection) gets the fake in tests:

```kotlin
testImplementation("io.github.thesobiaakbar:adroit-billing-testing:1.0.0-rc6")
```

<!-- snippet: docs-snippets/src/test/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingTestingSnippets.kt#billing-fake-test -->
```kotlin
/** Plain JVM: no Play, no device, no Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {
    private val billing = FakeBilling()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `premium follows what the user owns`() {
        val vm = PremiumViewModel(billing)
        assertFalse(vm.entitlements.value.has("premium"))

        billing.own(AppProducts.PremiumLifetime)

        assertTrue(vm.entitlements.value.has("premium"))
    }

    @Test
    fun `restore reports its result once`() = runTest {
        billing.restoreResult = RestoreResult.NothingToRestore
        val vm = PremiumViewModel(billing)

        vm.restore()

        assertEquals(RestoreResult.NothingToRestore, vm.restoreResults.first())
    }
}
```

What the fake does:

- `own(…)` sets what the user owns, as Play-confirmed entitlements; `entitlements`, `catalog` and `connection` can
  also be set directly.
- A purchase succeeds by default and adds the product's entitlements; `nextPurchaseResult` makes the next one
  `Cancelled`, `Failed`, `Pending` (which grants nothing, as in the library) or `AlreadyOwned`.
  `holdNextPurchase()` keeps it waiting, to assert a "purchasing" state. `purchaseRequests` lists every request,
  with its placement.
- `restoreResult` and `manageSubscriptionsResult` are what `restore()` and `openManageSubscriptions` return.
- `emitConsumable(…)` delivers a bought consumable; `confirmedConsumables` lists the ones the app confirmed.
- `TestProducts` builds products with plans, offers and prices (`TestProducts.subscription(…) { basePlan(…) { … } }`),
  and `TestPaywalls.model(…)` a paywall's content, for UI tests of a `PaywallController`
  (`PaywallController.forModel`).

It differs from the library in a few places: nothing runs on the main thread or talks to Play, a purchase completes
inside the call unless held, and a consumable is delivered once whether or not it's confirmed.

For a device, use demo mode (`DemoBilling(true)` in a debug build): made-up products and a dialog to pick "Buy",
"Pending payment" or "Cancel" ([Billing](BILLING.md)).

## Manual QA

- Use DEVELOPMENT, or STAGING with its default demo ad units, for every build testers get. Google's demo ads are
  labelled "Test Ad" and are safe to click
  ([Google](https://developers.google.com/admob/android/next-gen/test-ads)).
- To test real ad units, register the testers' devices: run the app once, find the device ID the Google SDK logs, and
  add it to `testing { testDeviceIds += "…" }` in a STAGING build. Emulators are always test devices.
- Test consent with `debugGeography` and `resetConsentForTesting()` ([Consent](CONSENT.md#testing-consent)).
- `Adroit.diagnostics.snapshot()` (for example on a debug screen) shows the state of every placement, and
  `openAdInspector` shows what the Google SDK received.
- `sample-xml` and `sample-compose` (the same app, in Views and in Compose) exercise every
  format, the splash, rotation, offline mode and Firebase overrides.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, Play Billing Library 9.1.0, 2026-10-20
