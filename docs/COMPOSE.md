# Jetpack Compose

`io.github.thesobiaakbar:adroit-compose` adds six APIs on top of `adroit-core`. It depends on no design system (no Material),
navigation or DI library.

Adding ads to a new Compose app, step by step, with the splash screen and every format:
[Adding ads to a new Jetpack Compose app](NEW_APP_COMPOSE.md).

## `AdSlot`

```kotlin
@Composable
fun AdSlot(
    definition: AdDefinition,                          // or key: String, the ad's name
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
    slotKey: Any = Unit,                               // distinct and stable per slot on a screen (list item key)
    refreshKey: Any? = null,                           // change it to ask for a new ad
)
```

A banner or a native ad, whichever format the ad has now: a Firebase value can swap an ad between banner and native,
and the slot follows. Size and template come from the ad's style. It's [`AdBanner`](#adbanner) or
[`AdNative`](#adnative-and-remembernativead) underneath.

## `AdBanner`

```kotlin
@Composable
fun AdBanner(
    placement: BannerPlacement,
    modifier: Modifier = Modifier,
    options: BannerOptions = BannerOptions(),          // size, and whether to reserve the expected height
    placeholder: (@Composable () -> Unit)? = null,     // shown over the reserved space while loading
    slotKey: Any = Unit,                               // distinct and stable per banner on a screen (list item key)
    refreshKey: Any?,                                  // optional overload: change it to ask for a new banner
    onStateChange: (BannerState) -> Unit = {},         // main thread, each new state
)
```

- The banner is kept like a native ad: in a ViewModel of the current `LocalViewModelStoreOwner` (a navigation
  destination, a Fragment, or the Activity), one per (`placement`, `options`, `slotKey`).
- Recomposition, leaving the composition and coming back (another tab, scrolling out of a lazy list) and
  configuration changes show the same ad again, with no new request. Off screen it keeps no View or Activity: the
  loaded ad is taken out of its view, and put into a new one for the Activity it comes back in.
- It's destroyed when that owner is cleared, for example when a navigation destination is popped.
- In a `LazyColumn`, give each banner item a distinct, stable `slotKey`, as for native ads.
- A changed `refreshKey` loads a new banner in the background; the shown one stays until it's loaded, and stays if
  it fails.
- Needs an Activity in `LocalContext`; without one it logs an error and shows nothing.

## `AdNative` and `rememberNativeAd`

```kotlin
@Composable
fun AdNative(
    placement: NativePlacement,
    modifier: Modifier = Modifier,
    layout: NativeAdLayout? = null,                    // null: the ad's style (NativeTemplates.forPlacement)
    slotKey: Any = Unit,                               // distinct and stable per ad on a screen (list item key)
    renderer: NativeAdRenderer? = null,                // styling after the standard binding
    placeholder: (@Composable () -> Unit)? = null,
    refreshKey: Any?,                                  // optional overload: change it to ask for a new ad
)

@Composable
fun rememberNativeAd(placement: NativePlacement, slotKey: Any = Unit): State<NativeAdResult?>
```

- The ad is kept in a ViewModel of the current `LocalViewModelStoreOwner` (the Activity, a Fragment, or a navigation
  destination). Recomposition, configuration changes and scrolling out of a lazy list don't reload it.
- It's destroyed when that owner is cleared, for example when a navigation destination is popped. Until then every
  slot keeps its ad, however many the screen has.
- A failed load isn't kept: the slot tries again the next time it enters the composition. `AdNative` shows nothing
  when the load fails or ads are blocked.

See [Native](NATIVE.md) for templates, custom layouts and a lazy-list example.

## `rememberAdAvailability`

```kotlin
@Composable
fun rememberAdAvailability(placement: AdPlacement): State<AdAvailability>
```

Whether a full-screen placement has an ad ready, collected while the lifecycle is at least STARTED; for example to
enable a "Watch ad" button. It only observes (call `preload` to load). Banner and native placements always report
`Unavailable`. Safe to use before initialization.

## `rememberFullScreenAdLauncher`

```kotlin
@Composable
fun rememberFullScreenAdLauncher(): FullScreenAdLauncher

interface FullScreenAdLauncher {
    fun showInterstitial(placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit = {})
    fun showRewarded(placement: RewardedPlacement, onResult: (RewardResult) -> Unit)
    fun showRewardedInterstitial(placement: RewardedInterstitialPlacement, onResult: (RewardResult) -> Unit)
}
```

Shows full-screen ads from event handlers over the composition's Activity. It holds the Activity only weakly, and is
stable across recompositions. Every `onResult` is called exactly once, on the main thread. Without an Activity the
result is `NotShown(ActivityUnavailable)` or `Failed(ActivityUnavailable)`.

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/FullScreen.kt#rewarded-compose -->
```kotlin
@Composable
fun WatchAdButton(onReward: (RewardResult.Earned) -> Unit) {
    val availability by rememberAdAvailability(Placements.ExtraLife)
    val launcher = rememberFullScreenAdLauncher()
    Button(
        enabled = availability == AdAvailability.Ready,
        onClick = { launcher.showRewarded(Placements.ExtraLife) { if (it is RewardResult.Earned) onReward(it) } },
    ) { Text("Watch an ad for 50 coins") }
}
```

## State and configuration changes

- Observe library state with `collectAsStateWithLifecycle()`: `Adroit.state`, `privacy.consentState`,
  `privacy.isPrivacyOptionsRequired`, and `handle.state` for banners and native ads.
- Grant rewards and keep results in ViewModels, not in composables: a rotation recreates the composition, and
  `rememberSaveable` state is the only composition state that survives it.
- `sample-compose` uses every API: a splash Activity, then Navigation Compose destinations with banners, native ads
  and banners in a `LazyColumn`, full-screen ads and a native style gallery.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-30
