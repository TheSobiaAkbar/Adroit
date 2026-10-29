# Multi-module, Hilt, MVI apps

Read this when the app has feature modules (a Hilt single-module app can skip it: put everything in the one module). These patterns come
from PhotoRecoveryNewApp, a production app with `:app`, `:core:*`, `:feature:*` and `:recovery:*` modules. For a
single-module app, skip this file.

## The rule: Adroit's ads live only in `:app`

- **Features never depend on `adroit-compose`, `adroit-views` or `adroit-core`.** A feature that shows an ad takes it
  as a slot, and a feature that needs a full-screen or rewarded ad takes it through a small interface the feature owns.
- Only `:app` knows `AppAds`, calls `Adroit.initialize` and wires the splash, the app open ad and the close-button
  collector. It is the module that sees everything.
- **Why:** features stay previewable and testable without the ads SDK, and a screen's ads change in one place
  (navigation), without touching the feature.
- The exception is `adroit-billing`: a feature that runs a paywall may depend on it (billing only, never the ads
  modules). See the adroit-billing skill, `architecture.md`.

## Inline ads as composable slots

```kotlin
// :feature:home: the screen takes an ad slot; it draws it where the design puts it, and knows nothing about Adroit.
@Composable
fun HomeScreen(state: HomeState, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier,
               ad: @Composable () -> Unit = {}) {
    Column(modifier) { Content(state, onIntent, Modifier.weight(1f)); ad() }
}

// :feature:home: HomeNavigation.kt passes the slot through
fun NavGraphBuilder.homeScreen(onOpenScan: (MediaType) -> Unit, ad: @Composable () -> Unit = {}) {
    composable<HomeRoute> { HomeScreen(viewModel = hiltViewModel(), onOpenScan = onOpenScan, ad = ad) }
}

// :app: AppNavHost decides which ad goes where
homeScreen(onOpenScan = ::startScan, ad = { AdSlot(AppAds.HomeBanner, Modifier.fillMaxWidth()) })
```
- Previews and tests pass nothing, so the default is `{}`.
- If a list needs several ads, pass `ad: @Composable (index: Int) -> Unit` and use `slotKey = "feed-$index"` inside.
- The ad's `ViewModelStoreOwner` is the nav back-stack entry, so the ad lives and dies with the destination.

## Full-screen ads: at the navigation layer, in callbacks

Interstitials belong to transitions, and transitions are decided in `:app`, so `AppNavHost` shows them:

```kotlin
// :app: AppNavHost
private fun showScanAd(activity: Activity?, next: () -> Unit) {
    if (activity == null) next() else AdSlots.showFullScreen(activity, AppAds.ScanCompleteInterstitial) { next() }
}
scanScreen(
    onScanStarted = { AdSlots.load(AppAds.ScanCompleteInterstitial) },              // load when the work starts
    onScanFinish = { type -> showScanAd(activity) { navController.navigate(ResultsRoute(type)) } },
    onLeaveNoResults = { showScanAd(activity) { navController.popBackStack() } },
)
```
The feature only emits its navigation lambdas (`onScanFinish`). It never knows an ad showed in between.

## Rewarded ads: a feature-owned `fun interface`, bound by Hilt

When a feature's ViewModel needs the reward result (to unlock something), the feature declares what it needs, and
`:app` binds it to Adroit:

```kotlin
// :feature:launch: the feature owns the contract (no Adroit import)
fun interface UnlockAd { fun show(activity: Activity, onResult: (AdResult) -> Unit) }
enum class AdResult { EARNED, NOT_EARNED, FAILED }

@HiltViewModel
class ScanUnlockViewModel @Inject constructor(private val ad: UnlockAd, /* … */) : MviViewModel<…>() {
    fun watch(activity: Activity) = ad.show(activity) { result ->
        when (result) { AdResult.EARNED -> unlock(); AdResult.NOT_EARNED -> showWatchWhole(); AdResult.FAILED -> showNoAd() }
    }
}

// :app: di/AdsModule.kt, a module of its own so instrumented tests can replace it
@Module @InstallIn(SingletonComponent::class)
object AdsModule {
    @Provides fun unlockAd(): UnlockAd = UnlockAd { activity, onResult ->
        AdSlots.load(AppAds.ScanUnlockRewarded)            // if the preload failed or was used
        Adroit.rewarded.show(activity, AppAds.ScanUnlockRewarded.rewardedPlacement()) { result ->
            onResult(when (result) {
                is RewardResult.Earned -> AdResult.EARNED
                RewardResult.NotEarned -> AdResult.NOT_EARNED
                is RewardResult.Failed -> AdResult.FAILED
            })
        }
    }
}
```
- Unit tests pass `UnlockAd { _, onResult -> onResult(AdResult.EARNED) }`.
- Instrumented tests replace the module:
  `@TestInstallIn(components = [SingletonComponent::class], replaces = [AdsModule::class])`.
- When the reward must survive a rotation mid-ad, also collect `Adroit.rewarded.unclaimedResults` in a singleton
  (in `:app`), and feed it to the same unlock state.
- Whether the "watch ad" option shows at all (the ad is turned off) is an `:app` decision too. Pass the feature a
  boolean, for example `rewardedAdOn()`, which checks that `Adroit.rewarded.availability(...)` is not `Disabled`.

## The close button: one collector in `:app`

Collect `Adroit.events.filterIsInstance<AdEvent.CloseClicked>()` once, in `AppNavHost` or `MainActivity`, and navigate
to the remove-ads paywall route there (formats.md §5). Features never see it.

## App-level switches and premium gating

Anything that depends on premium or on a Firebase switch (Get Pro buttons, "sell" flags, the unlock sheet) is decided
in `:app`, then passed into features as plain values or slots:

```kotlin
val entitlements by AdroitBilling.entitlements.collectAsStateWithLifecycle()
val sell = PremiumEnabled.value && !entitlements.has(PREMIUM)
homeScreen(proButton = { if (sell) GetProButton(onClick = { navController.navigate(PremiumRoute(PRO_BUTTON)) }) })
```

## DI bindings for pure-JVM modules

If `:core:common` or `:domain` are pure Kotlin/JVM, their Hilt bindings live in `:app` (`di/`), next to `AdsModule`
and `BillingModule`.

## Build types without ads

```kotlin
// app/build.gradle.kts
defaultConfig { buildConfigField("boolean", "AD_FREE", "false") }
buildTypes {
    create("benchmarkRelease") { buildConfigField("boolean", "AD_FREE", "true") }    // and nonMinifiedRelease
}
// Application.onCreate
if (BuildConfig.AD_FREE) Adroit.setAdFree(true)
// and in SplashFlow.create(..., isPremium = BuildConfig.AD_FREE || Adroit.isAdFree)
```
Baseline profiles and macrobenchmarks then measure the app, not ad loads.

## Strings and design system

- Ad-adjacent UI (privacy options row, "watch ad" button, unlock sheet) uses the app's string resources and design
  tokens, like any other UI.
- Restyle the ad templates themselves through Adroit's resources (`ads_style.xml`, `adroit_ad_margin`), not with
  custom layouts, unless the design demands one.
