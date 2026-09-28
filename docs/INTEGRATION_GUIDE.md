# Integration guide: ads in a new app, step by step

This page is a checklist for adding the ads library to a new app, from nothing to ads you can tune from Firebase
without a release. Every step says what to do and how to check it worked. You write one Kotlin file listing your ads,
and add a few lines to three others. The other pages explain each part in depth; you don't need them to finish.

## What you need before you start

| What | Where to get it | Example |
|---|---|---|
| The app's package name | `applicationId` in `app/build.gradle.kts` | `com.mycompany.puzzle` |
| An AdMob app ID | AdMob, Apps, your app, App settings | `ca-app-pub-1234567890123456~1234567890` |
| An ad unit ID per ad | AdMob, Apps, your app, Ad units. One per ad, of the right format. | `ca-app-pub-1234567890123456/1234567890` |
| A Firebase project with the app added | [Firebase console](https://console.firebase.google.com), Add app, Android, the **exact** package name | |
| `google-services.json` | Firebase, Project settings, your Android app, Download | |
| Access to the library | A GitHub token with `read:packages`, or the company repository URL | |

You can start without the ad unit IDs: an ad without IDs shows Google's test ads until you add them.

## 1. Add the library

Follow steps 1 and 2 of [Getting started](GETTING_STARTED.md) (the repository and the dependencies), then add the
Firebase module:

```kotlin
// app/build.gradle.kts
plugins {
    id("com.google.gms.google-services") // reads google-services.json
}
dependencies {
    implementation("com.company:admob-views:0.1.0") // or admob-compose for Compose apps
    implementation("com.company:admob-firebase:0.1.0") // Remote Config and Analytics; brings Firebase itself
}
```

Declare the google-services plugin in the root `build.gradle.kts` (`id("com.google.gms.google-services") version
"4.5.0" apply false`), and put `google-services.json` in the `app/` folder.

**Check:** the project syncs and builds.

## 2. Add the AdMob app ID

In `AndroidManifest.xml`, inside `<application>` (step 3 of [Getting started](GETTING_STARTED.md)):

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-1234567890123456~1234567890" />
```

**Check:** without it, `initialize` reports `APP_ID_MISSING` in logcat and no ad loads.

## 3. List your ads: the one file

Create `AppAds.kt` with one entry per ad in the app. The entry name is the ad's name everywhere: in code, in XML and
in Firebase. Pick names that say where the ad is (`HomeBanner`, `LevelCompleteAd`), and never rename one after
release, because Firebase finds the ad by its name.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-enum -->
```kotlin
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    SplashAd(AdProfile.interstitial(ids = listOf("ca-app-pub-0000000000000000/0000000001"), splash = true)),
    HistoryAd(
        AdProfile.interstitial(
            ids = listOf("ca-app-pub-0000000000000000/0000000002", "ca-app-pub-0000000000000000/0000000003"),
            counter = 3,
            maxPerSession = 5,
            loadAgain = true, // the next ad unit loads right after each show
        ),
    ),
    HomeBanner(
        AdProfile.banner(
            ids = listOf("ca-app-pub-0000000000000000/0000000004"),
            style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
        ),
    ),
    FeedNative(AdProfile.native(ids = listOf("ca-app-pub-0000000000000000/0000000005"), style = NativeStyle.LARGE)),
}
```

- One entry per place that shows an ad. Two places that show the same kind of ad can share ad unit IDs.
- The format is chosen by `AdProfile.interstitial`, `rewarded`, `rewardedInterstitial`, `appOpen`, `banner` or
  `native`. The other arguments are optional; each field is described in [Ad profiles](AD_PROFILES.md#the-json).
- Leave `ids` out while you wait for the real ad units: that ad shows Google's test ads.
- The splash screen's ad has `splash = true`. There is at most one.
- An ad loads only when your code asks (step 6). `loadAgain = true` also loads the next one after each show.

## 4. Start everything in the Application class

Create (or open) your `Application` class, and register it in the manifest (`android:name=".MyApp"` on
`<application>`):

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-setup -->
```kotlin
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteConfigSession.start(this) // fetches Firebase once this session, without making anything wait
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                // Debug builds show only Google's test ads, whatever IDs the definitions list.
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AdSettings()) // every ad and its default values
                remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the defaults, ad by ad
            },
        )
    }
}
```

- `RemoteConfigSession.start` fetches your Firebase values once per launch, in the background. The app never waits
  for it.
- Debug builds (`DEVELOPMENT`) always show test ads, so you can't click your own real ads by mistake while testing.
- If the app sells an ad-free upgrade, add `if (userIsPremium) CompanyAdMob.setAdFree(true)` right after
  `initialize`.

**Check:** run a debug build and filter logcat by `CompanyAdMob`. You see one line `Firebase parameter <Name>: {…}`
per ad.

## 5. The splash screen

The splash screen asks for consent (Europe and other regions that require it), starts the ads, shows a progress bar
and the splash ad, and then opens your main screen. It never gets stuck, even offline. Keep the flow in a ViewModel:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Splash.kt#splash-viewmodel -->
```kotlin
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // Survives rotation: consent, the timer and the splash ad run once per launch.
    val flow = SplashFlow.create(application, AppAds.SplashAd, isPremium = Premium.isPremium(application))

    override fun onCleared() = flow.cancel()
}
```

and draw it in the splash Activity, your launcher Activity:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Splash.kt#splash-activity -->
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

- The layout is yours: usually a logo, the app name and a horizontal progress bar with a percentage, hidden while the
  state is `Preparing`. `sample-xml`'s `SplashActivity` and `activity_splash.xml` are a complete example.
- If the app has an app open ad, add the splash Activity to its `excludedActivities`, so it never shows over the
  splash.

**Check:** launch the app. The bar appears within a second or two (longer only when the consent form shows), and a
test ad shows before the main screen. [Splash screen](SPLASH.md) lists every case.

## 6. Show the ads

Ads load only when you ask, so ask when the screen that shows them opens. For an ad that's shown again and again (a
level-complete ad, the app open ad), set `loadAgain = true` in its entry, so the next ad loads right after each show.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-load -->
```kotlin
override fun onResume() {
    super.onResume()
    // Ads load only when asked: load HistoryAd now, so it's ready when the user opens the history. After a failed
    // load, the next resume asks again. Nothing happens if it's loaded or loading already.
    AdSlots.load(AppAds.HistoryAd)
}
```

Then call `showFullScreen` where the ad belongs, and continue in its callback, whatever the result.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AdProfiles.kt#profiles-counter -->
```kotlin
// With "counter": 3 in HistoryAd, this shows an ad on the 3rd, 6th, 9th… call. Other calls return at once
// with NotShown(CounterNotReached), and the app just continues.
AdSlots.showFullScreen(this, AppAds.HistoryAd) { result: FullScreenResult -> openHistory() }
```

Banners and native ads: put an `AdSlotView` in the layout, with the ad's entry name.

```xml
<com.company.admob.views.AdSlotView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:company_admob_placement="HomeBanner" />
```

In Compose, use `AdSlot(AppAds.HomeBanner)`. The app open ad needs no code: it shows by itself when the user returns
to the app. Rewarded ads use `CompanyAdMob.rewarded` ([Rewarded](REWARDED.md)).

**Check:** each ad shows a test ad labelled "Test Ad" in a debug build.

## 7. Create the Firebase parameters

Generate the template once, from a unit test or a debug screen:

```kotlin
File("remote_config_template.json").writeText(AdDefinitions.firebaseTemplate(AppAds.entries, AdSettings()))
```

Then, in the Firebase console:

1. Open **Remote Config**. If the project has no parameters yet, choose **Create configuration** first.
2. Open the ⋮ menu at the top right, choose **Publish from a file**, pick the template, and publish.
   **This replaces every parameter of the project**, so on a project that already uses Remote Config, add the
   parameters one by one instead: for each ad, **Add parameter**, the ad's entry name as the key, data type **JSON**,
   and the ad's line from the log (step 4) as the default value.
3. Each ad now has its parameter, plus `AdSettings` for the settings shared by every ad.

To change an ad later, edit its parameter's value and **Publish changes**. A value only needs the fields you change,
for example:

| To… | Set this parameter | To this value |
|---|---|---|
| Turn one ad off | the ad's | `{ "enabled": false }` |
| Show an interstitial every 3rd time | the ad's | `{ "counter": 3 }` |
| Wait 60 s between two shows of an ad | the ad's | `{ "timerSeconds": 60 }` |
| Use two ad units in turn | the ad's | `{ "adUnitIds": ["ca-app-pub-…/111", "ca-app-pub-…/222"] }` |
| Make a banner collapsible | the banner's | `{ "style": "collapsible_bottom" }` |
| Show a native ad instead of a banner | the banner's | `{ "format": "native", "style": "large", "adUnitIds": ["<a native ad unit>"] }` |
| Show an app open ad instead of an interstitial | the interstitial's | `{ "format": "app_open", "adUnitIds": ["<an app open ad unit>"] }` |
| Lengthen the splash to 13 s | `AdSettings` | `{ "splashSeconds": 13 }` |
| Load the next ad right after each show | the ad's | `{ "loadAgain": true }` |
| Wait at most 5 s for any full-screen ad that isn't loaded | `AdSettings` | `{ "loadTimeoutSeconds": 5 }` |
| No "Loading ad…" screen before one ad | the ad's | `{ "showLoading": false }` |
| Turn every ad off | `AdSettings` | `{ "adsEnabled": false }` |

Every field is listed in [Ad profiles](AD_PROFILES.md#the-json), and format changes in
[Swapping formats](AD_PROFILES.md#swapping-formats). A value with a mistake is ignored, and the ad keeps working with
its previous settings.

**Check:** users get a change on their next launch. Debug builds that set `minimumFetchIntervalInSeconds = 0`
([Remote configuration](REMOTE_CONFIG.md#fetching-once-per-session)) get it on the next launch too; release builds
may keep Firebase's cached values for up to 12 hours.

## 8. Before release

- [ ] Every entry in `AppAds` has its real ad unit IDs, in the enum or in Firebase. In a release build, the log warns
  about each ad still on test ads.
- [ ] The manifest has the real AdMob app ID, not Google's sample one.
- [ ] Consent messages (GDPR, US states) are published in AdMob, Privacy & messaging ([Consent](CONSENT.md)).
- [ ] `app-ads.txt` is set up for the app in AdMob.
- [ ] A release build, installed on a phone, shows ads (real ads can take a few hours for a new ad unit).
- [ ] If you ship an ad-free upgrade, a premium user sees no ad and a 3-second splash.

Problems? See [Troubleshooting](TROUBLESHOOTING.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-28
