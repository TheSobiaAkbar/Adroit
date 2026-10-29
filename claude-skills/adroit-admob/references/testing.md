# Testing ads

## Unit tests: `adroit-testing`

`testImplementation(libs.adroit.testing)` gives you `FakeAdsClient`, a complete `AdsClient` that never touches Google.

**Code that takes an `AdsClient`.** This is the preferred approach: inject `Adroit.client` in production.

```kotlin
@RunWith(RobolectricTestRunner::class)   // only if the subject needs Android (viewModelScope main dispatcher, Views)
class UnlockViewModelTest {
    private val ads = FakeAdsClient()

    @Test fun `an earned reward is granted once`() {
        val vm = UnlockViewModel(ads)
        ads.enqueueRewardResult(AppAds.UnlockRewarded.rewardedPlacement(),
            RewardResult.Earned(Reward("unlock", 1), "reward-1"))
        vm.watchAd(Activity())
        assertTrue(vm.state.value.unlocked)
    }

    @Test fun `a reward earned after rotation still arrives`() {
        val vm = UnlockViewModel(ads)
        ads.emitUnclaimedReward(RewardResult.Earned(Reward("unlock", 1), "reward-2"))
        assertTrue(vm.state.value.unlocked)
    }
}
```

**Code that calls the `Adroit` object directly.** `AdroitTestRule` swaps the object for a `FakeAdsClient` in each
test:

```kotlin
@get:Rule val admob = AdroitTestRule()

@Test fun `the flow continues when no ad shows`() {
    admob.client.enqueueInterstitialResult(AppAds.ResultInterstitial.interstitialPlacement(),
        FullScreenResult.NotShown(AdError.NotReady))
    var opened = false
    onDone(Activity()) { opened = true }
    assertTrue(opened)
}
```

**What the fake gives you:**
- To script results: `enqueueInterstitialResult`, `enqueueRewardResult`, `enqueueRewardedInterstitialResult`,
  `enqueueAppOpenResult` and `enqueueNativeResult`. With nothing queued, the result is `NotShown(NotReady)` or
  `Failed(NotReady)`.
- To set state: `setState`, `setConsentState`, `setAvailability`, `setNativeStyle`, `isAdFree`,
  `initialBannerState` and `nativeAssets`. The last two are properties.
- To emit: `emit(event)`, `emitUnclaimedReward` and `emitUnclaimedRewardedInterstitialReward`.
- To inspect: `recordedCalls: List<FakeCall>` (`ShowInterstitial`, `ShowRewarded`, `SkipNextAppOpen`, `SetAdFree`,
  `Preload`, …), `emittedEvents`, `createdBanners` and `loadedNativeAds`. `clearRecordedCalls()` resets the list.
- Callbacks are synchronous unless you pass an `Executor`.
- **It enforces nothing:** no frequency, counter or consent rules. It answers exactly what you queued, so a test of
  counter logic belongs in the library, not the app.
- `AdSlots` falls back to `Adroit.interstitial` while a test client is installed.
- `banner.create` and `native.render` need Robolectric.

**What an app's ad tests should check:**
- The flow continues for every result: `Dismissed`, `NotShown(NotReady)`, `NotShown(Disabled)` and
  `NotShown(FrequencyCapped)`.
- A reward is granted only for `Earned`, only once per `rewardId`, and also when it comes from `unclaimedResults`.
- The "watch ad" option hides when availability is `Disabled`.
- A premium or ad-free user never reaches a show call (assert on `recordedCalls`), where the app gates this itself.
- Tolerant `RemoteValue` parsers: bad JSON falls back to the defaults.
- The phase files are current (`remote-config.md` §6).

## Seams in multi-module apps

- Features get ad behaviour through their own `fun interface` (`architecture.md`). Unit tests pass a lambda; no
  Adroit is needed.
- Instrumented tests replace the Hilt module:
```kotlin
@Module @TestInstallIn(components = [SingletonComponent::class], replaces = [AdsModule::class])
object FakeUnlockAdModule { @Provides fun unlockAd(): UnlockAd = UnlockAd { _, onResult -> onResult(AdResult.EARNED) } }
```
- Inline `AdSlot`s in instrumented tests: Adroit runs for real on test ads. That is slow and needs network, so for
  end-to-end UI tests, prefer a build where `Adroit.setAdFree(true)` (an `AD_FREE` build field) or pass empty slots.

## Manual QA checklist (debug build, emulator or phone)

- [ ] Logcat `Adroit`: `Firebase parameter <Name>: {…}` once per ad, then `Remote Config fetched and activated`.
- [ ] First launch: the consent form (debug acts as the EEA), the progress bar, a test splash ad, then the app.
- [ ] Every ad says "Test Ad". Full-screen ads show "Loading ad…" first; inline ads show a shimmer.
- [ ] Airplane mode: a 3 s splash, no ads, inline slots collapse, every flow continues.
- [ ] `{ "enabled": false }` on each full-screen ad: its flow continues at once.
- [ ] The rewarded unlock grants once; closing early grants nothing; offline shows the "no ad" state.
- [ ] Home → back to the app shows the app open ad. Share sheet → back, and settings → back, show none.
- [ ] Rotation mid-splash doesn't restart it. Home mid-splash, then back, resumes it and never navigates in the
      background.
- [ ] Premium (with billing): no ad anywhere, no banner shimmer, and the next splash is short with no consent.
- [ ] Privacy options row appears (debug EEA) and opens the form.
- [ ] Banner close button (if any) opens the remove-ads offer.

**Instrumented tests** must run on an emulator, never on a phone with real data. With a phone also attached, run each
test APK by serial (`adb -s emulator-5554 shell am instrument -w …`). The Gradle `connected…` task runs on every
device and uninstalls the app afterwards.
