# Splash screen

`SplashFlow` runs a splash screen's whole ads sequence: consent, the library's start, a splash timer with progress,
and the splash ad. It never gets stuck, whatever the network, the consent form or the ad does. Your screen only draws
it: a logo, the app name, and a progress bar.

## The sequence

1. **Consent** (state `Preparing`, no progress bar). Google's consent form gets at most `consentTimeoutSeconds` (8)
   to appear. If it doesn't, the splash continues without it and the form isn't shown this launch, not even later:
   it's tried again on the next launch. A form that appeared in time stays until the user answers, and nothing else
   starts meanwhile: no timer, no progress bar, no splash ad request. Until the user answers, every splash shows the
   form again, including a new splash in the same process after the first one was closed or recreated. Then the
   library starts, whatever the answer: granted, denied, already given, or no form.
2. **The splash timer** (state `Loading`, progress 0 to 100). It lasts:
   - `splashSeconds` (11) for a user who may see the splash ad;
   - `premiumSplashSeconds` (3) for a premium user, who also gets no consent form and no ad;
   - `offlineSplashSeconds` (3) without internet (no ad is requested; the library still starts).

   Meanwhile the splash ad loads. When it's ready and the screen is in front, the progress jumps to 100 and the timer
   stops.
3. **The splash ad** (state `ShowingAd`): the splash waits as long as the user keeps the ad open.
4. **Next screen** (state `Finished`): when the ad is closed, when it fails to show, or when the timer ends first. An
   ad that loads after the timer isn't shown on the splash.

Firebase Remote Config is never waited for. The timer uses the values active when it starts: this session's fetch
if it finished during consent, otherwise the last session's (see [Remote configuration](REMOTE_CONFIG.md)). If this
session's fetch arrives during the timer and swaps the splash ad's format (interstitial ↔ app open), the splash asks
for the ad in its new format and still shows it if it's ready before the timer ends.

Internet is judged by `NetworkMonitor`: Wi-Fi without working internet (a sign-in page) counts as offline.

## Setup

The splash ad is an ad definition with `splash = true` (see [Ad profiles](AD_PROFILES.md)):
`SplashAd(AdProfile.interstitial(splash = true))`. A Firebase value can swap it to an app open ad.

Keep the flow in a ViewModel:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Splash.kt#splash-viewmodel -->
```kotlin
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // Survives rotation: consent, the timer and the splash ad run once per launch. A premium user (Adroit.isAdFree,
    // from your Adroit.setAdFree call in Application.onCreate) skips consent and the ad.
    val flow = SplashFlow.create(application, AppAds.SplashAd)

    override fun onCleared() = flow.cancel()
}
```

Draw its state:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Splash.kt#splash-activity -->
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.snippet_banner) // your splash layout: logo, app name, progress bar
    viewModel.flow.start(this) // after a rotation, hands the new Activity to the running flow
    lifecycleScope.launch {
        // STARTED: a splash sent to the background moves on when the user comes back.
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.flow.state.collect { state ->
                when (state) {
                    SplashState.Preparing -> hideProgress() // consent: no progress bar yet
                    is SplashState.Loading -> showProgress(state.progress) // 0..100
                    SplashState.ShowingAd -> Unit // the splash ad is on screen
                    is SplashState.Finished -> goToMain()
                }
            }
        }
    }
}
```

- Call `Adroit.setAdFree(true)` for premium users in `Application.onCreate` (with Adroit Billing, the one-line
  bridge does it). No other screen shows ads, and `SplashFlow` reads it as `Adroit.isAdFree`, so you pass no premium
  flag to the splash.
- Exclude the splash Activity from automatic app open ads (`AdProfile.appOpen(excludedActivities = ...)`).
- `start` is safe to call from every `onCreate`: after a rotation, it hands the new Activity to the running flow.

## Changing the timings from Firebase

The timings are fields of the `AdSettings` parameter (JSON), next to the other settings:

```json
{ "splashSeconds": 13, "premiumSplashSeconds": 3, "offlineSplashSeconds": 3, "consentTimeoutSeconds": 8 }
```

Set their defaults in code with `AdSettings(splashSeconds = 11, ...)` (see [Ad profiles](AD_PROFILES.md#settings)).
Each is a whole number of seconds, at least 1; an invalid value is ignored with a warning.

## What is guaranteed

- The consent step ends within `consentTimeoutSeconds` plus 2 seconds, even if Google's consent service never answers.
  The time the consent form is on screen doesn't count: the splash waits for the user's answer.
- The timer always ends: the splash moves on at the latest when it does, unless the splash ad is on screen.
- A splash ad is never shown after the timer, nor while the screen is in the background.
- The flow doesn't restart on rotation, and `cancel()` stops it without closing an ad on screen.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-28
