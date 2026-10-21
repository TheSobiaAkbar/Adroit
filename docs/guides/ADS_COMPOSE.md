# AdMob ads in a Jetpack Compose + Kotlin app

This guide takes a new Android Studio project (Jetpack Compose + Kotlin) to a release-ready app with **AdMob only**.
Follow it from top to bottom: every file is complete, and every step ends with a check. You don't need any other guide.

Another kind of app? [AdMob only, XML + Kotlin](ADS_XML.md) · [in-app purchases only, Jetpack Compose +
Kotlin](IAP_COMPOSE.md) · [in-app purchases only, XML + Kotlin](IAP_XML.md) · [AdMob + in-app purchases, Jetpack Compose
+ Kotlin](ADS_IAP_COMPOSE.md) · [AdMob + in-app purchases, XML + Kotlin](ADS_IAP_XML.md). Not sure: [Getting
started](../GETTING_STARTED.md).

**Contents**

1. [What you'll have at the end](#1-what-youll-have-at-the-end)
2. [Dependencies](#2-dependencies)
3. [Firebase project setup](#3-firebase-project-setup)
4. [The enums](#4-the-enums)
5. [The Application class](#5-the-application-class)
6. [The splash screen](#6-the-splash-screen)
7. [The screens](#7-the-screens)
8. [Firebase Remote Config: what to paste](#8-firebase-remote-config-what-to-paste)
9. [Test it](#9-test-it)
10. [Release checklist](#10-release-checklist)

## 1. What you'll have at the end

- A splash screen that checks Google Play for an update, asks for consent where the law requires it, and shows a splash
  ad. It never gets stuck, even offline.
- Interstitial, rewarded, app open, banner and native ads, each one line of code, each tunable from Firebase (ad unit
  IDs, counters, timers, styles, on/off) without a new release.
- A "Restart" message when an app update has downloaded in the background.

**Before you start**

| What | Where | Example |
|---|---|---|
| Your app's package name | `applicationId` in `app/build.gradle.kts` | `com.example.myapp` |
| An AdMob app ID | AdMob → Apps → your app → App settings | `ca-app-pub-1234567890123456~1234567890` |
| One ad unit ID per ad | AdMob → Apps → your app → Ad units, each of the right format | `ca-app-pub-1234567890123456/1234567890` |
| A Firebase project | [console.firebase.google.com](https://console.firebase.google.com) | |

You can start without the IDs and products: an ad without ad unit IDs shows Google's test ads, and debug builds always
do.

## 2. Dependencies

Adroit is on Maven Central: no account and no token. Check that `settings.gradle.kts` lists `mavenCentral()`, as a new
Android Studio project already does.

This setup needs these Adroit artifacts, and only these:

| Artifact | What for |
|---|---|
| `adroit-compose` | the ad composables (`AdSlot`, …); brings `adroit-core`, `adroit-views` and Google's ads and consent SDKs |
| `adroit-firebase` | every ad controlled from Firebase Remote Config, and ad events in Analytics |
| `adroit-update` | Google Play's in-app update, controlled by the Firebase `AppUpdate` parameter |

Don't add `play-services-ads`, `ads-mobile-sdk` or `com.android.billingclient:billing` yourself: the Adroit artifacts
bring the versions they are tested with.

In `gradle/libs.versions.toml`, add to the existing sections:

```toml
[versions]
adroit = "1.0.0-rc6"
googleServices = "4.5.0"

[libraries]
adroit-compose = { module = "io.github.thesobiaakbar:adroit-compose", version.ref = "adroit" }
adroit-firebase = { module = "io.github.thesobiaakbar:adroit-firebase", version.ref = "adroit" }
adroit-update = { module = "io.github.thesobiaakbar:adroit-update", version.ref = "adroit" }

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

In the root `build.gradle.kts`:

```kotlin
plugins {
    // ... the plugins Android Studio put here
    alias(libs.plugins.google.services) apply false
}
```

In `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) // reads app/google-services.json
}

android {
    // ... namespace, defaultConfig and the rest, as Android Studio wrote them
    compileSdk = 37 // Compose 1.12 (the Compose BOM below) needs it
    defaultConfig {
        minSdk = 24 // Adroit needs 24 or higher
    }
    buildFeatures {
        compose = true
        buildConfig = true // for BuildConfig.DEBUG
    }
}

dependencies {
    implementation(libs.adroit.compose)
    implementation(libs.adroit.firebase)
    implementation(libs.adroit.update)

    // Your usual Compose libraries (the "Empty Activity" template adds most of them):
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
}
```

(Your plugin aliases may have other names: keep the ones Android Studio wrote, and add `google-services`.)

In `app/src/main/AndroidManifest.xml`, register the Application class (step 5) and make the splash (step 6) the
launcher:

```xml
<application
    android:name=".AdsApp"
    ...>

    <activity
        android:name=".SplashActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <activity android:name=".MainActivity" />

    <!-- Your AdMob app ID (AdMob → Apps → App settings). This one is Google's sample ID, for testing only. -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3940256099942544~3347511713" />
</application>
```

Remove the `intent-filter` Android Studio put on `MainActivity`. The libraries add the permissions they need (internet,
billing) themselves.

**Check:** *Sync Project with Gradle Files* succeeds. If an Adroit artifact isn't found, check `mavenCentral()` in
`settings.gradle.kts` and the version number.

## 3. Firebase project setup

1. In the [Firebase console](https://console.firebase.google.com), create a project (or open yours). Google Analytics:
   yes.
2. **Add app** → Android, with your **exact** package name, then **Register app**.
3. Download `google-services.json` into your project's `app/` folder.
4. Skip the console's "Add Firebase SDK" step: the Adroit artifacts bring the Firebase libraries.

**Debug builds with another package name.** If your debug build type has an `applicationIdSuffix` (for example
`.debug`), register that package as a second Android app in the same Firebase project, and download
`google-services.json` again: one file holds both apps. Both read the same Remote Config parameters.

Remote Config needs nothing else in code: the first Adroit Firebase module the app uses fetches it once per launch, in
the background, for the ads and the app update. Nothing waits for it. Step 8 lists the parameters to create.

**Check:** the app builds and starts. Logcat (tag `AdroitRemoteConfig`) shows `Remote Config fetched and activated` once
per launch.

## 4. The enums

Everything the app shows or sells is listed once, in enums. **Each entry's name is also its Firebase Remote Config
parameter**: a JSON value under that name in Firebase changes that one entry, without a new release. So pick names that
say what the entry is, and never rename one after release.

**The ads**, in `ads/AppAds.kt` (one line per ad):

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/AppAds.kt#guide-ads-enum -->
```kotlin
/** Every ad in the app. Each entry's name is also its Firebase Remote Config parameter. */
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    // Full-screen ads
    SplashAd(AdProfile.interstitial(splash = true)),
    LevelCompleteAd(AdProfile.interstitial(counter = 2, loadAgain = true)),
    ExtraLifeRewarded(AdProfile.rewarded(loadAgain = true)),
    AppOpenAd(AdProfile.appOpen(excludedActivities = listOf("com.example.myapp.SplashActivity"), loadAgain = true)),

    // Banners and native ads
    HomeBanner(AdProfile.banner(style = BannerSize.AnchoredAdaptive)),
    DetailsBanner(AdProfile.banner(style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),
    HomeNative(AdProfile.native(style = NativeStyle.LARGE_BUTTON_BOTTOM)),
    FeedNative(AdProfile.native(style = NativeStyle.SMALL_BUTTON_END, loadAgain = true)),
    ;

    companion object {
        /** What every ad shares: the Firebase parameter "AdSettings". */
        val settings = AdSettings(splashSeconds = 11, minSecondsBetweenFullScreenAds = 30)
    }
}
```

- **The format** is the function: `AdProfile.interstitial`, `rewarded`, `rewardedInterstitial`, `appOpen`, `banner` or
  `native`. Every argument is optional; each is a field of the ad's Firebase value ([Ad
  profiles](../AD_PROFILES.md#the-json)).
- **Ad unit IDs:** add `ids = listOf("ca-app-pub-…/…")` when you have them, or set them in Firebase only (step 8). No
  IDs: Google's test ads. Two or more are used in turn.
- **`counter = 2`:** an interstitial on every 2nd call; `loadAgain = true`: the next ad loads right after each show.
  Counters, timers and their loading rules: [Counter and timer](../AD_PROFILES.md#counter-and-timer).
- **One splash ad** (`splash = true`). The app open ad's `excludedActivities` lists the screens it must never cover:
  always the splash. Replace `com.example.myapp` with your package.
- **Styles:** banners are `AnchoredAdaptive` (full width), `Collapsible`, `MediumRectangle` or `Standard`; native ads
  have five built-in styles or your own layout ([Native](../NATIVE.md#templates-and-custom-layouts)).
- **`AppAds.settings`** is what every ad shares: the Firebase parameter `AdSettings`. `splashSeconds` is the splash
  timer and `minSecondsBetweenFullScreenAds` the gap between two full-screen ads.


## 5. The Application class

`AdsApp.kt`, next to your activities, registered in the manifest in step 2:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/Apps.kt#guide-ads-app -->
```kotlin
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase Remote Config: debug builds see a console change on the next launch. Adroit fetches it once per
        // launch, in the background: nothing waits for it.
        FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = 10
            },
        )
        // The ads: cheap and synchronous, no network. Consent and the SDK start on the splash.
        Adroit.initialize(
            this,
            adMobConfig {
                // Debug builds show only Google's test ads, and ask for consent as in Europe.
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AppAds.settings) // every ad and its defaults
                remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the defaults, ad by ad
                logging { level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN }
            },
        )
    }
}
```

- **Debug builds** (`DEVELOPMENT`) show only Google's test ads, and ask for consent as if you were in Europe. **Release
  builds** (`PRODUCTION`) show your real ads.
- Inside `adMobConfig { }`, `remoteConfig` is the library's setting: don't name a variable of your own `remoteConfig`
  there.
- There's no Remote Config fetch call: the Adroit Firebase modules start one, once per launch.

**Check:** filter logcat by `Adroit`: one line `Firebase parameter <Name>: {…}` per ad.

## 6. The splash screen

The splash runs everything at app start, and always ends somewhere:

1. **The update check:** Google Play's update, as the Firebase `AppUpdate` parameter says (flexible by default). A user
   who refuses a forced update leaves the app.
2. **Consent:** Google's consent form, where the user's region needs it. The splash waits for the answer.
3. **The timer and the splash ad:** a progress bar for `splashSeconds`, and the splash ad if it loads in time. Premium
   users, offline users and a splash ad turned off get a 3-second splash without an ad.
4. **The routing:** the main screen.

It never gets stuck, even offline, and a rotation never restarts it: everything is kept in its ViewModel.

The splash's look, `SplashScreen.kt` (change it as you like):

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/SplashScreen.kt#guide-splash-screen-compose -->
```kotlin
/** The splash's look: the app's name, and the timer's progress (0..100) once there is one. */
@Composable
fun SplashScreen(percent: Int?) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Text("MyApp", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineMedium)
            if (percent != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp, bottom = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("$percent%")
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The progress bar for a [SplashState]: none while the update check and consent run, then 0..100. */
fun SplashState.percent(): Int? = when (this) {
    SplashState.Preparing -> null
    is SplashState.Loading -> progress
    SplashState.ShowingAd, is SplashState.Finished -> 100
}
```

`SplashActivity.kt`, with its ViewModel:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/ads/compose/AdsSplashCompose.kt#guide-ads-splash-compose -->
```kotlin
/** Keeps the splash across rotations: the update check, consent, the timer and the splash ad run once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow = SplashFlow.create(application, AppAds.SplashAd)

    private var update: Job? = null

    /** Google Play's update check, once per launch: a rotation waits for the same check. */
    suspend fun checkForUpdate(activity: Activity) {
        val job = update ?: viewModelScope.launch { AdroitUpdate.check(activity) }.also { update = it }
        job.join()
    }

    override fun onCleared() = flow.cancel()
}

/** Its own Activity, not a navigation destination: the app open ad excludes it by class (`excludedActivities`). */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // The update first: a forced update must not let consent or an ad start.
            viewModel.checkForUpdate(this@SplashActivity)
            viewModel.flow.start(this@SplashActivity) // after a rotation, hands the new Activity to the running flow
        }
        setContent {
            MaterialTheme {
                val state by viewModel.flow.state.collectAsStateWithLifecycle()
                SplashScreen(state.percent())
            }
        }
        lifecycleScope.launch {
            // STARTED: a splash sent to the background moves on when the user comes back.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flow.state.collect { if (it is SplashState.Finished) goToMain() }
            }
        }
    }

    private fun goToMain() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
```

- **Its own Activity, not a navigation destination:** the app open ad excludes screens by Activity, and the consent form
  and the splash ad need an Activity with nothing drawn over it.
- **The update check runs in the ViewModel,** once per launch: after a rotation, the new Activity waits for the same
  check instead of starting a second one.
- **`flow.start(this)` in `onCreate`:** after a rotation it hands the new Activity to the running flow. Consent, the
  timer and the splash ad never restart.
- **Navigation happens only while the screen is in front** (`STARTED`): a splash sent to the background moves on when
  the user comes back.

**A splash without an ad.** To keep the splash (the update check, consent, the timer) but drop its ad, turn the ad
off in Firebase with `{ "enabled": false }` in `SplashAd`, or pass `null` instead of `AppAds.SplashAd` to
`SplashFlow.create`. The splash then lasts `premiumSplashSeconds` (3 s) ([Splash
screen](../SPLASH.md#a-splash-without-an-ad)).

**Consent in debug builds.** A debug build behaves as if you were in Europe, so the consent form shows wherever you are.
Until it's answered it shows on every launch; once answered, clear the app's storage to see it again. The form itself
comes from AdMob: publish a GDPR message in **AdMob → Privacy & messaging**, or no form shows
([Consent](../CONSENT.md)).

**Check:** launch a debug build: the consent form, the progress bar, a test ad, then your main screen. Logcat (tag
`PlayCore`) shows the update check.

## 7. The screens

Two rules cover every ad:

1. **Ads load when you ask.** Ask when the screen that shows them comes to the front (`AdSlots.load`). Banner and native
   slots ask by themselves.
2. **Every show calls you back, whether an ad showed or not.** Continue the app's flow in the callback.

### The main screen and its ads

`MainActivity.kt`, with the home screen:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/ads/compose/AdsMainCompose.kt#guide-ads-main-compose -->
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val snackbars = remember { SnackbarHostState() }
                UpdateSnackbar(snackbars) // a flexible update downloaded in the background: offer "Restart"
                Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
                    HomeScreen(Modifier.padding(padding))
                }
            }
        }
        // The app open ad shows by itself when the user comes back to the app. Load it once.
        AdSlots.load(AppAds.AppOpenAd)
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val launcher = rememberFullScreenAdLauncher()
    val privacyRequired by Adroit.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()

    // Ads load only when asked: each time this screen comes to the front, its full-screen ads (ready when a button is
    // tapped) and the next screen's banner (shown the moment it opens). Nothing happens if one is already loaded.
    LifecycleResumeEffect(Unit) {
        AdSlots.load(AppAds.LevelCompleteAd)
        AdSlots.load(AppAds.ExtraLifeRewarded)
        AdSlots.load(AppAds.DetailsBanner)
        onPauseOrDispose {}
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Interstitial: the callback runs whether an ad showed or not. Always continue the app's flow in it.
            Button(
                onClick = { if (activity != null) AdSlots.showFullScreen(activity, AppAds.LevelCompleteAd) { openNextLevel() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Level complete") }

            // Rewarded: give the reward only for Earned (in a ViewModel in a real app, so a rotation can't lose it).
            Button(
                onClick = {
                    launcher.showRewarded(AppAds.ExtraLifeRewarded.rewardedPlacement()) { result ->
                        when (result) {
                            is RewardResult.Earned -> giveLives(result.reward.amount)
                            RewardResult.NotEarned -> Toast.makeText(context, "Watch the whole ad", Toast.LENGTH_LONG).show()
                            is RewardResult.Failed -> Toast.makeText(context, "No ad right now", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Extra life (watch an ad)") }

            // Privacy options: Google requires a way to change consent, where consent applies.
            if (privacyRequired && activity != null) {
                Button(onClick = { Adroit.privacy.showPrivacyOptions(activity) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Privacy options")
                }
            }

            // A native ad inside the page: its template comes from HomeNative's style.
            AdSlot(AppAds.HomeNative, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
        // The banner, pinned to the bottom. A banner or a native ad, whichever Firebase says.
        AdSlot(AppAds.HomeBanner)
    }
}

private fun openNextLevel() = Unit // your app's next screen

private fun giveLives(amount: Int) = Unit // your app's reward
```

- **`AdSlot`** shows a banner or a native ad, whichever the ad is (Firebase can even swap them), with a shimmer while it
  loads, and collapses if no ad comes. Its ad belongs to the screen: a rotation, a tab switch or scrolling it away shows
  the same ad again. New ad on demand: change its `refreshKey`. In a `LazyColumn`, give each ad item its own `slotKey`
  ([Jetpack Compose](../COMPOSE.md)).
- **Rewards in a ViewModel:** a rotation recreates the composable, not the ViewModel, and
  `Adroit.rewarded.unclaimedResults` delivers a reward whose screen was gone ([Rewarded](../REWARDED.md)).
- **What the user sees on `showFullScreen`:** "Loading ad…" for about a second, then the ad. Not loaded yet: "Loading
  ad…" for at most `loadTimeoutSeconds` (8), then the ad or your callback. Not due (`counter`), capped, offline or
  premium: no ad, your callback runs at once.
- **The app open ad** shows by itself when the user returns to the app, never over the splash ([App
  open](../APP_OPEN.md)). **Rewarded interstitials** need an intro screen first ([Rewarded
  interstitial](../REWARDED_INTERSTITIAL.md)).
- **Your ads' look:** colors in `res/values/ads_style.xml` or from Firebase, and your own native layout registered by
  name ([Native: Styling](../NATIVE.md#styling)).

### The update message

When a flexible update has downloaded in the background, offer the restart that installs it. `UpdateSnackbar.kt`, placed
in the main screen with its `Scaffold`'s snackbar host (the `MainActivity` above already does):

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/UpdateSnackbar.kt#guide-update-compose -->
```kotlin
/** In the main screen, with the Scaffold's snackbar host: once a flexible update has downloaded, offers "Restart". */
@Composable
fun UpdateSnackbar(snackbars: SnackbarHostState) {
    LaunchedEffect(snackbars) {
        AdroitUpdate.readyToInstall.first { it }
        val result = snackbars.showSnackbar("An update is ready", actionLabel = "Restart", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) AdroitUpdate.install()
    }
}
```

"Restart" installs the update; Google Play restarts the app. More: [In-app update](../APP_UPDATE.md).

## 8. Firebase Remote Config: what to paste

In the Firebase console → **Remote Config**, create each parameter below: **Add parameter**, the name, data type
**JSON**, the JSON as the default value → **Save**. When all are added, **Publish changes**. Users get them on their
next launch.

A value needs only the fields you change: the rest keep the values from your enums. A value with a mistake is ignored
(with a warning in logcat), and the entry keeps its previous values.

### Ads

Replace each `ca-app-pub-XXXXXXXXXXXXXXXX/…` with your ad unit ID **of that format**, or remove `adUnitIds` to keep the
IDs from `AppAds`.

**`AdSettings`** (what every ad shares)

```json
{
  "adsEnabled": true,
  "maxInterstitialsPerSession": 0,
  "minSecondsBetweenFullScreenAds": 30,
  "splashSeconds": 11,
  "premiumSplashSeconds": 3,
  "offlineSplashSeconds": 3,
  "consentTimeoutSeconds": 8,
  "loadTimeoutSeconds": 8,
  "showLoading": true,
  "loadingSeconds": 1
}
```

**`SplashAd`**

```json
{ "format": "interstitial", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/1111111111"], "splashTimeoutSeconds": 8 }
```

**`LevelCompleteAd`**

```json
{
  "format": "interstitial",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/2222222222"],
  "counter": 2,
  "loadAhead": 1,
  "timerSeconds": 0,
  "maxPerSession": 0,
  "loadAgain": true
}
```

**`ExtraLifeRewarded`**

```json
{ "format": "rewarded", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/3333333333"], "loadAgain": true }
```

**`AppOpenAd`**

```json
{ "format": "app_open", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/4444444444"], "timerSeconds": 240, "loadAgain": true }
```

**`HomeBanner`**

```json
{ "format": "banner", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/5555555555"], "style": "adaptive" }
```

**`DetailsBanner`**

```json
{ "format": "banner", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/6666666666"], "style": "collapsible_bottom" }
```

**`HomeNative`**

```json
{ "format": "native", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/7777777777"], "style": "large_button_bottom" }
```

**`FeedNative`**

```json
{ "format": "native", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/8888888888"], "style": "small_button_end", "loadAgain": true }
```

Every field of every ad: [Ad profiles](../AD_PROFILES.md#the-json). Instead of typing them, you can generate all of them at once from `AppAds` with
`AdDefinitions.firebaseTemplate(AppAds.entries, AppAds.settings)` and import the file ([Ad
profiles](../AD_PROFILES.md#getting-the-json-for-firebase)).

Quick changes:

| To… | Parameter | Value |
|---|---|---|
| Turn one ad off | the ad's | `{ "enabled": false }` |
| Turn every ad off | `AdSettings` | `{ "adsEnabled": false }` |
| An interstitial on every 3rd tap | the ad's | `{ "counter": 3 }` |
| 60 s between two shows instead | the ad's | `{ "counter": 0, "timerSeconds": 60 }` |
| Two ad units in turn | the ad's | `{ "adUnitIds": ["ca-app-pub-…/111", "ca-app-pub-…/222"] }` |
| A native ad instead of a banner | the banner's | `{ "format": "native", "style": "large_button_bottom", "adUnitIds": ["<a native ad unit>"] }` |
| The splash without its ad | `SplashAd` | `{ "enabled": false }` |

### App update

**`AppUpdate`**

```json
{ "type": "flexible", "forceBelowVersionCode": 0 }
```

| `type` | What a user with an older version sees |
|---|---|
| `flexible` (default) | Google Play's update prompt. They may decline and continue; an accepted update downloads while they use the app, then the "Restart" message of step 7. |
| `force` | Google Play's full-screen update. They can't continue without updating; backing out leaves the app. |
| `off` | Nothing. |

`forceBelowVersionCode`: above 0, every installed version below it is forced to update, even when `type` is `flexible`.
Use it to retire old versions. More: [In-app update](../APP_UPDATE.md).

### Your own values

Anything else you want to change from Firebase (a flag, a number, a text): [Your app's own
values](../REMOTE_CONFIG.md#your-apps-own-values).

## 9. Test it

A debug build on a phone or an emulator:

- [ ] Logcat (`Adroit`): `Firebase parameter <Name>: {…}` per ad, then `Remote Config fetched`.
- [ ] The consent form (first launch), the progress bar, a test ad, then the next screen.
- [ ] Every ad is labelled "Test Ad". Full-screen ads show "Loading ad…" first; banners and native ads, a shimmer.
- [ ] With `{ "enabled": false }` published in `SplashAd`, the splash lasts 3 seconds and shows no ad.
- [ ] In airplane mode, the splash lasts 3 seconds and the app works without ads.
- [ ] **The update check** returns at once in a debug build (it isn't installed from Play). To see the real flows,
      upload two builds to Internal App Sharing or the internal testing track, the second with a higher `versionCode`,
      install the first from Play, then open it ([In-app update: Testing](../APP_UPDATE.md#testing)).

Ads in unit tests: `adroit-testing`'s `FakeAdsClient` ([Testing](../TESTING.md)).

## 10. Release checklist

- [ ] Every ad in `AppAds` has its real ad unit IDs, in the enum or in Firebase. A release build warns in logcat about
      each ad still on test ads.
- [ ] The manifest has **your** AdMob app ID, not Google's sample one.
- [ ] A GDPR message is published in AdMob → Privacy & messaging, and `app-ads.txt` is set up.
- [ ] `AdSettings` in Firebase has `"adsEnabled": true` (or isn't set). Don't click your own real ads.
- [ ] The Firebase parameters are published, and your `google-services.json` is the release app's.
- [ ] A release build (R8 on, `isMinifyEnabled = true`) starts and works: Adroit ships its own R8 rules, you add none.
- [ ] **Every release raises `versionCode`.** `AppUpdate` compares versions by it: with `forceBelowVersionCode`, set it
      to the first version every user must have.

Problems? [Troubleshooting](../TROUBLESHOOTING.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, Play Billing Library 9.1.0, Play In-App Updates 2.1.0, 2026-10-21
