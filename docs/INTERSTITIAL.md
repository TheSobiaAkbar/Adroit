# Interstitial ads

## When to use them

A full-screen ad at a natural break: between levels, after finishing a task. Never show one as the user enters or
leaves the app, or right after another full-screen ad
([Google's interstitial guidance](https://developers.google.com/admob/android/next-gen/interstitial)).

## Integration

The library preloads each interstitial placement as soon as ads are ready, and loads the next one after each show. At
the break, call `show` and continue your flow in the callback, which is always called exactly once:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/FullScreen.kt#interstitial-result -->
```kotlin
fun showBetweenLevels(activity: Activity, next: () -> Unit) {
    CompanyAdMob.interstitial.show(activity, Placements.LevelComplete) { result ->
        when (result) {
            is FullScreenResult.Dismissed -> next()
            is FullScreenResult.NotShown -> {
                val capped = result.error as? AdError.FrequencyCapped
                if (capped != null) println("Next interstitial possible in ${capped.retryAfter}")
                next() // not ready, capped, offline, ad-free...: never block the user
            }
        }
    }
}
```

The suspend form returns when the ad is dismissed, or right away when it can't be shown:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/FullScreen.kt#interstitial-suspend -->
```kotlin
class LevelEndActivity : AppCompatActivity() {
    fun onNextLevelClicked() {
        lifecycleScope.launch {
            CompanyAdMob.interstitial.showAndAwait(this@LevelEndActivity, Placements.LevelComplete)
            startActivity(Intent(this@LevelEndActivity, LauncherActivity::class.java))
        }
    }
}
```

For placements with `preload = PreloadStrategy.ON_DEMAND`, preload before the break:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/FullScreen.kt#interstitial-preload -->
```kotlin
fun preloadForUpcomingBreak() {
    // Loads now if the placement's preload strategy didn't already: for ON_DEMAND placements.
    CompanyAdMob.interstitial.preload(Placements.LevelComplete)
    val readyNow = CompanyAdMob.interstitial.isReady(Placements.LevelComplete)
}
```

In Compose, use `rememberFullScreenAdLauncher().showInterstitial(placement) { … }` ([Compose](COMPOSE.md)). From a
Fragment, pass `requireActivity()`.

## Lifecycle

- An ad is loaded without an Activity; `show` needs the Activity to show it over.
- Only one full-screen ad shows at a time, across every format. A second call gets `NotShown(AlreadyShowing)`.
- If the calling coroutine of `showAndAwait` is cancelled (for example by a rotation), the ad keeps showing.
- `clear(placement)` drops the cached ad.

## State and results

`interstitial.availability(placement)` (or `rememberAdAvailability`): `Unavailable`, `Loading`, `Ready`,
`Failed(error)` or `Disabled`.

`FullScreenResult` is `Dismissed(clicked)` or `NotShown(error)`, where `error` is one of:

| Error | Meaning |
|---|---|
| `NotReady` | No ad loaded yet (a load starts for next time). |
| `FrequencyCapped(retryAfter)` | A frequency limit applies; `retryAfter` says how long. |
| `Disabled(reason)` | Ad-free user, remote kill switch, placement disabled, or no consent. |
| `NotInitialized`, `ConsentRequired` | Ads aren't ready yet. |
| `Expired` | The cached ad was too old; a new one loads. |
| `AlreadyShowing` | Another full-screen ad is on screen. |
| `ActivityUnavailable` | The Activity was finishing or destroyed. |
| `ShowFailed`, `Timeout` | The SDK failed to show it, or didn't report within `advanced.showTimeout`. |

## Events

`LoadRequested`, `Loaded`, `LoadFailed`, `ShowRequested`, `Shown`, `Impression`, `Clicked`, `Paid`, `Dismissed`,
`NotShown` and `Expired`. See [Analytics](ANALYTICS.md).

## Retry, expiry and frequency

- Failed loads retry after 5 s, doubling up to 120 s, 5 attempts; a no-fill waits at least 30 s.
- A cached ad expires after 55 minutes, a few minutes before Google's one-hour limit.
- `frequency { minInterval; maxPerSession }` per placement, plus the global
  `minIntervalBetweenFullScreenAds` (30 s by default).
- `frequency { counter = 3 }`: an ad shows at most on every 3rd `show` call (the 3rd, 6th, 9th…); the others return
  `NotShown(CounterNotReached)` at once. A due counter stays due until an ad shows, and `minInterval` is ignored
  while a counter is set.
- `frequency { maxInterstitialsPerSession = 4 }` in the app-wide options: at most 4 interstitials per session across
  all placements, splash ones not counted.
- Several ad unit IDs per placement rotate: each load uses the next one.

## Splash screen

Mark the splash placement with `splash = true` and show it with
`CompanyAdMob.interstitial.showOnSplash(activity, placement) { … }`. It waits up to `splashTimeout` (8 s by default) for
consent, initialization and an ad, then shows it or gives up with `NotShown(Timeout)`; continue to your main screen
in both cases. See [Ad profiles](AD_PROFILES.md#showing-the-ads).

## Policy notes

Follow [Google's interstitial best practices](https://developers.google.com/admob/android/next-gen/interstitial): show
interstitials at natural transitions, not unexpectedly or repeatedly, and never in a way that makes users click by
accident. The frequency limits help, but choosing the moments is your app's responsibility.

## FAQ

**Why does nothing show on the first try?** The ad may still be loading: the result is `NotShown(NotReady)`. Keep
the flow going; the next break will have one.

**Can I wait for an ad before navigating?** Don't block the user on an ad. Show it only if it's `Ready`.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
