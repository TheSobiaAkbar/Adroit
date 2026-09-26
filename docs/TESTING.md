# Testing

## Unit tests with `admob-testing`

`com.company:admob-testing` replaces the library with fakes that never touch Google's SDKs, so view models and
screens that show ads can be tested on the JVM or with Robolectric.

```kotlin
testImplementation("com.company:admob-testing:0.1.0")
```

### `FakeAdsClient`

A complete `AdsClient`. Pass it wherever your code takes an `AdsClient`
([dependency injection](GETTING_STARTED.md#9-dependency-injection)), script the results, and assert:

<!-- snippet: docs-snippets/src/test/kotlin/com/company/admob/docs/TestingSnippets.kt#fake-client -->
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

### `CompanyAdMobTestRule`

For code that calls the `CompanyAdMob` object directly, the rule turns it into a `FakeAdsClient` for each test and
restores it afterwards:

<!-- snippet: docs-snippets/src/test/kotlin/com/company/admob/docs/TestingSnippets.kt#test-rule -->
```kotlin
/** For code that calls the CompanyAdMob object directly: the rule makes it a FakeAdsClient for each test. */
@RunWith(RobolectricTestRunner::class)
class LevelFlowTest {
    @get:Rule
    val admob = CompanyAdMobTestRule()

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

## Manual QA

- Use DEVELOPMENT, or STAGING with its default demo ad units, for every build testers get. Google's demo ads are
  labelled "Test Ad" and are safe to click
  ([Google](https://developers.google.com/admob/android/next-gen/test-ads)).
- To test real ad units, register the testers' devices: run the app once, find the device ID the Google SDK logs, and
  add it to `testing { testDeviceIds += "…" }` in a STAGING build. Emulators are always test devices.
- Test consent with `debugGeography` and `resetConsentForTesting()` ([Consent](CONSENT.md#testing-consent)).
- `CompanyAdMob.diagnostics.snapshot()` (for example on a debug screen) shows the state of every placement, and
  `openAdInspector` shows what the Google SDK received.
- The AdMob Playground (`sample-compose`) lets you exercise every format, rotation, offline mode and remote overrides.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
