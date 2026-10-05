# Adding ads to a new Jetpack Compose app

This page takes a new Android Studio project (Kotlin, Jetpack Compose) to a complete ad setup:

- consent on the splash screen;
- a splash ad;
- interstitial, rewarded, rewarded interstitial and app open ads;
- banners and native ads as composables, which can load before their screen opens;
- banners and native ads kept by their screen: across tabs, scrolling, lazy lists and rotation;
- native ads in your own layouts and colors;
- a "remove ads" purchase;
- every ad, and any other value you choose, controlled from Firebase without a new release.

Your app needs two of the plugin's modules: `adroit-compose` (the composables) and `adroit-firebase` (Firebase Remote
Config). They bring everything else: `adroit-core`, `adroit-views` (the native ad templates), Google's ads and consent
SDKs, and the [sdp/ssp](https://github.com/intuit/sdp) sizes. Don't add `adroit-testing`, the sample apps,
`play-services-ads` or `ads-mobile-sdk`.

The finished result is the `sample-compose` app in the Adroit project: every step below is taken from it. For a
Kotlin + XML app, see [Adding ads to a new Kotlin + XML app](NEW_APP_XML.md) instead.

## What changes from one project to the next

Most of the setup is the same in every app: copy it once, then change only this.

| File | What you change in each project | Step |
|---|---|---|
| `settings.gradle.kts` | Add the Adroit repository | [2](#2-add-the-adroit-repository) |
| `build.gradle.kts` (root) and `app/build.gradle.kts` | Add the Google services plugin and the dependencies | [3](#3-connect-firebase), [4](#4-add-the-dependencies) |
| `app/google-services.json` | This app's file, from its Firebase project | [3](#3-connect-firebase) |
| `AndroidManifest.xml` | This app's AdMob app ID | [7](#7-the-manifest) |
| `ads/AppAds.kt` | This app's ads and their ad unit IDs | [5](#5-list-your-ads-appadskt) |
| `MyApp.kt`, `SplashActivity.kt` | Copy them. Change only the `package` line (and the splash's look, if you like). | [6](#6-start-everything-the-application-class), [8](#8-the-splash-screen) |
| `res/values/ads_style.xml` | Optional: this app's ad colors | [10](#10-colors) |
| Your screens | One composable or one line per ad | [9](#9-show-the-ads) |
| Firebase console | This app's parameters, one per ad | [13](#13-firebase-the-parameters-and-sample-values) |

**Contents**

1. [What you need](#1-what-you-need)
2. [Add the Adroit repository](#2-add-the-adroit-repository)
3. [Connect Firebase](#3-connect-firebase)
4. [Add the dependencies](#4-add-the-dependencies)
5. [List your ads: AppAds.kt](#5-list-your-ads-appadskt)
6. [Start everything: the Application class](#6-start-everything-the-application-class)
7. [The manifest](#7-the-manifest)
8. [The splash screen](#8-the-splash-screen)
9. [Show the ads](#9-show-the-ads)
10. [Colors](#10-colors)
11. [Your own native ad layout](#11-your-own-native-ad-layout)
12. [Your own Firebase values](#12-your-own-firebase-values)
13. [Firebase: the parameters and sample values](#13-firebase-the-parameters-and-sample-values)
14. [Test it](#14-test-it)
15. [Before release](#15-before-release)

## 1. What you need

| What | Where | Example |
|---|---|---|
| Your app's package name | `applicationId` in `app/build.gradle.kts` | `com.example.myapp` |
| An AdMob app ID | AdMob → Apps → your app → App settings | `ca-app-pub-1234567890123456~1234567890` |
| One ad unit ID per ad | AdMob → Apps → your app → Ad units, each of the right format | `ca-app-pub-1234567890123456/1234567890` |
| A Firebase project | [console.firebase.google.com](https://console.firebase.google.com) | |
| Access to Adroit | An invite and a GitHub token: [Getting access](ACCESS.md) | |

You can start without ad unit IDs: an ad without IDs shows Google's test ads, and debug builds always do.

## 2. Add the Adroit repository

Adroit is private: you need an invite and a GitHub token first ([Getting access](ACCESS.md)). Put the token in
`~/.gradle/gradle.properties` on your computer (`C:\Users\<you>\.gradle\gradle.properties` on Windows), never in the
project:

```properties
gpr.user=<your GitHub user name>
gpr.key=<your token with read:packages>
```

**In your app**, add the repository to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/TheSobiaAkbar/Adroit") {
            credentials {
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
        }
    }
}
```

Working on Adroit itself? Run `./gradlew publishToMavenLocal` in the Adroit folder and use `mavenLocal()` instead.

## 3. Connect Firebase

1. In the [Firebase console](https://console.firebase.google.com), create a project, or open yours.
2. **Add app** → Android, with your **exact** package name, then **Register app**.
3. Download `google-services.json` into your project's `app/` folder.
4. Skip the console's SDK steps: the plugin brings the Firebase libraries.

In the root `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "9.1.1" apply false // already there, with your version
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false // already there in a Compose project
    id("com.google.gms.google-services") version "4.5.0" apply false
}
```

With a version catalog (`gradle/libs.versions.toml`), add the plugin there, as Android Studio does for the others.

## 4. Add the dependencies

In `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services") // reads app/google-services.json
}

android {
    // ...
    defaultConfig {
        minSdk = 24 // the plugin needs 24 or higher
    }
    buildFeatures {
        compose = true
        buildConfig = true // for BuildConfig.DEBUG
    }
}

dependencies {
    implementation("io.github.thesobiaakbar:adroit-compose:1.0.0-rc4")
    implementation("io.github.thesobiaakbar:adroit-firebase:1.0.0-rc4")

    // Your usual Compose libraries (Android Studio's "Empty Activity" template adds most of them):
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
}
```

`adroit-compose` needs a `compileSdk` of 37 (its Compose version asks for it). With an older one, the build says so:
raise `compileSdk` in `android { }`.

**Check:** *Sync Project with Gradle Files* succeeds. If `io.github.thesobiaakbar:adroit-compose` isn't found, check step 2: the repository
in `settings.gradle.kts`, and `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`.

## 5. List your ads: AppAds.kt

Every ad of the app is one line of **one file**, `app/src/main/java/com/example/myapp/ads/AppAds.kt`:

```kotlin
package com.example.myapp.ads

import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings

/** Every ad in the app. Each entry's name is also its Firebase Remote Config parameter. */
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    // Full-screen ads
    SplashAd(AdProfile.interstitial(splash = true)),
    LevelCompleteAd(AdProfile.interstitial(counter = 2, loadAgain = true)),
    ExtraLifeRewarded(AdProfile.rewarded(loadAgain = true)),
    BonusRewardedInterstitial(AdProfile.rewardedInterstitial()),
    AppOpenAd(
        AdProfile.appOpen(
            excludedActivities = listOf("com.example.myapp.SplashActivity"),
            loadAgain = true,
        ),
    ),

    // Banners
    HomeBanner(AdProfile.banner(style = BannerSize.AnchoredAdaptive)),
    DetailsBanner(AdProfile.banner(style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),

    // Native ads
    HomeNative(AdProfile.native(style = NativeStyle.LARGE_BUTTON_BOTTOM)),
    FeedNative(AdProfile.native(style = NativeStyle.SMALL_BUTTON_END, loadAgain = true)),
    PromoNative(AdProfile.native(layout = "promo")), // your own layout, registered in MyApp
    ;

    companion object {
        /** Settings shared by every ad (the Firebase parameter "AdSettings"). */
        val settings = AdSettings(splashSeconds = 13, minSecondsBetweenFullScreenAds = 30)
    }
}
```

- **The entry name is the ad's name everywhere:** in code (`AppAds.HomeBanner`), in composables
  (`AdSlot(AppAds.HomeBanner)`) and in Firebase (a parameter called `HomeBanner`). Never rename one after release.
- **The format** is the function: `AdProfile.interstitial`, `rewarded`, `rewardedInterstitial`, `appOpen`, `banner`
  or `native`.
- **Ad unit IDs:** add `ids = listOf("ca-app-pub-…/…")` when you have them, or set them in Firebase only
  ([step 13](#13-firebase-the-parameters-and-sample-values)). No IDs: Google's test ads. Two IDs or more are used in
  turn.
- **One splash ad at most** (`splash = true`). **`excludedActivities`** lists the Activities the app open ad must
  never cover: always your splash. That's why the splash is its own Activity ([step 8](#8-the-splash-screen)): a
  navigation destination can't be excluded.
- **Native styles:** `NativeStyle.SMALL_BUTTON_END`, `SMALL_BUTTON_BOTTOM`, `LARGE_BUTTON_BOTTOM` (the default),
  `LARGE_COMPACT` or `MEDIA_START` ([what each looks like](#native-styles)), or your own layout:
  `AdProfile.native(layout = "promo")` ([step 11](#11-your-own-native-ad-layout)).
- **Banner styles:** `BannerSize.AnchoredAdaptive` (default, full width), `BannerSize.Collapsible(…BOTTOM)`,
  `BannerSize.MediumRectangle` (300×250) or `BannerSize.Standard` (320×50).
- Every argument is optional. The main ones:

| Argument | Formats | Meaning |
|---|---|---|
| `counter = 3` | interstitial, rewarded interstitial | The counter: shows an ad only on every 3rd `show` call. |
| `counters = listOf(3, 2)` | interstitial, rewarded interstitial | Several max counters in a loop: an ad after 3 calls, then after 2, then 3 again. |
| `counterStart = 1`, `counterStep = 0.5` | interstitial, rewarded interstitial | Where the counter starts, and what each call adds (defaults 0 and 1). |
| `counterResetOnFail = true` | interstitial, rewarded interstitial | A due ad that couldn't be shown is counted again from 0, instead of tried on the next call. |
| `loadAhead = 1` | interstitial, rewarded interstitial | How many calls before it's due a counter ad loads (0 = only when due). |
| `timerSeconds = 60` | interstitial, rewarded interstitial, app open | The timer, used when there's no counter: at least 60 s between two shows, and the next ad is requested when the 60 s are over. Leave it out for no timer (0). |
| `maxPerSession = 5` | full-screen ads | At most 5 shows per app launch. |
| `loadAgain = true` | all but banners | Loads the next ad right after each show. For ads shown again and again. |
| `loadTimeoutSeconds = 8` | full-screen ads | How long a show waits for an ad that isn't loaded yet. |
| `showLoading = false` | all | No "Loading ad…" screen before this full-screen ad, and no shimmer on this banner or native ad. |
| `enabled = false` | all | Turns this ad off. |

Counter and timer are explained, with the values to paste, in
[step 13](#13-firebase-the-parameters-and-sample-values): everything in this table can also be changed from Firebase.

`AdSettings` holds what every ad shares: `splashSeconds` (the splash timer, default 11),
`minSecondsBetweenFullScreenAds` (default 0: no gap), `maxInterstitialsPerSession`, and the defaults of `loadTimeoutSeconds`,
`showLoading` and `loadingSeconds`.

## 6. Start everything: the Application class

`MyApp.kt`, next to your activities. Copy it; change only the `package` line and, if you have your own native
layouts, step 3:

```kotlin
package com.example.myapp

import android.app.Application
import android.content.Context
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.firebase.FirebaseRemoteConfigProvider
import io.github.thesobiaakbar.adroit.firebase.RemoteConfigSession
import io.github.thesobiaakbar.adroit.logging.LogLevel
import io.github.thesobiaakbar.adroit.views.NativeTemplates
import com.example.myapp.ads.AppAds
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Firebase Remote Config: fetched once per launch, in the background.
        val firebaseConfig = FirebaseRemoteConfig.getInstance()
        firebaseConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0 // debug: console changes on next launch
                fetchTimeoutInSeconds = 10
            },
        )
        RemoteConfigSession.start(this, firebaseConfig)

        // 2. The ads library.
        Adroit.initialize(
            this,
            adMobConfig {
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AppAds.settings)
                remoteConfig = FirebaseRemoteConfigProvider()
                logging { level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN }
            },
        )

        // 3. Your own native layouts, by name (only if you have any).
        NativeTemplates.register("promo", R.layout.ad_promo)

        // 4. A user who bought "remove ads": no ads at all.
        if (Premium.isPremium(this)) Adroit.setAdFree(true)
    }
}

/** Your own purchase check. Here: a saved flag. */
object Premium {
    fun isPremium(context: Context): Boolean =
        context.getSharedPreferences("premium", Context.MODE_PRIVATE).getBoolean("premium", false)

    fun setPremium(context: Context, premium: Boolean) {
        context.getSharedPreferences("premium", Context.MODE_PRIVATE).edit().putBoolean("premium", premium).apply()
        Adroit.setAdFree(premium)
    }
}
```

- **Debug builds** (`DEVELOPMENT`) show only Google's test ads, and ask for consent as in Europe (step 8).
  **Release builds** (`PRODUCTION`) show your real ads.
- Name the Firebase variable anything **except** `remoteConfig`: inside `adMobConfig { }` that name is the plugin's
  setting, and a variable called that hides it.
- Replace `Premium` with your real purchase check. What matters is `Adroit.setAdFree(true)` for a premium user,
  at startup and right after a purchase.
- It's the same file as in an XML app: nothing in it depends on Compose.

## 7. The manifest

In `app/src/main/AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    ...>

    <!-- The splash screen is the launcher: its own Activity, so the app open ad can exclude it. -->
    <activity
        android:name=".SplashActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <!-- Every other screen: a navigation destination in MainActivity. -->
    <activity android:name=".MainActivity" />

    <!-- Your AdMob app ID (AdMob → Apps → App settings). This one is Google's sample ID for testing. -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3940256099942544~3347511713" />
</application>
```

Move the `intent-filter` Android Studio put on `MainActivity` to `SplashActivity`. The plugin adds the `INTERNET`
permission itself.

**Check:** run a debug build and filter logcat by `Adroit`: one line `Firebase parameter <Name>: {…}` per ad.

## 8. The splash screen

The splash does everything at app start, and always ends on your main screen:

1. **Consent:** Google's consent form, when the user's region needs it. The splash waits for the answer.
2. **The timer:** a progress bar for `splashSeconds` (13 in `AppAds.settings`); 3 seconds offline or for premium users.
3. **The splash ad,** if it loads before the timer ends.

It never gets stuck, even offline. It's **one file**, `SplashActivity.kt`, with no XML layout. Copy it as it is;
change the `SplashScreen` composable's look if you like:

```kotlin
package com.example.myapp

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.splash.SplashState
import com.example.myapp.ads.AppAds
import kotlinx.coroutines.launch

/** Keeps the splash running across rotation: consent, timer and splash ad happen once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow = SplashFlow.create(application, AppAds.SplashAd, Premium.isPremium(application))

    override fun onCleared() = flow.cancel()
}

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.flow.start(this) // after a rotation, hands the new Activity to the running flow
        setContent {
            MaterialTheme {
                val state by viewModel.flow.state.collectAsStateWithLifecycle()
                SplashScreen(state)
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

@Composable
private fun SplashScreen(state: SplashState) {
    val percent = when (state) {
        SplashState.Preparing -> null // consent form may be on screen: no progress bar yet
        is SplashState.Loading -> state.progress // 0..100
        SplashState.ShowingAd, is SplashState.Finished -> 100 // the splash ad is on screen
    }
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
```

- **Why an Activity and not a navigation destination:** the app open ad excludes screens by Activity
  (`excludedActivities`), and the consent form and the splash ad need an Activity with nothing else drawn over it.
- **Navigation happens in the Activity, not in the composable,** only while it's in front (`STARTED`): a splash sent
  to the background moves on when the user comes back.
- **`flow.start(this)` in `onCreate`,** not in a composable: after a rotation it hands the new Activity to the running
  flow. Consent, the timer and the splash ad never restart.

**Consent in debug builds.** A debug build behaves as if you were in Europe, so the consent form shows on your phone
or emulator wherever you are. Until it's answered it shows on every launch; once answered, it doesn't show again: to
see it again, clear the app's storage (Settings → Apps → MyApp → Storage → Clear storage). Release builds use the
user's real location. The form itself comes from AdMob: publish a GDPR message in **AdMob → Privacy & messaging**, or
no form shows.

**Check:** launch a debug build: the consent form, then the progress bar, then a test ad, then your main screen.

## 9. Show the ads

Two rules cover every ad:

1. **Ads load when you ask.** Ask when the screen that shows them comes to the front: `AdSlots.load` in a
   `LifecycleResumeEffect`. Banner and native composables ask by themselves.
2. **Every show calls you back, whether an ad showed or not.** Continue your app's flow inside the callback.

### The main screen

`MainActivity.kt` hosts every screen after the splash, as navigation destinations. It also loads the app open ad:

```kotlin
package com.example.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.thesobiaakbar.adroit.AdSlots
import com.example.myapp.ads.AppAds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                Scaffold { padding ->
                    NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
                        composable("home") { HomeScreen(onOpenDetails = { navController.navigate("details") }) }
                        composable("details") { DetailsScreen() }
                        composable("feed") { FeedScreen() }
                    }
                }
            }
        }
        // The app open ad, for the user's first return to the app. loadAgain loads the next one after each show.
        AdSlots.load(AppAds.AppOpenAd)
    }
}
```

Each destination has its own lifecycle and ViewModel store: its banners are destroyed when it leaves the screen, and
its native ads are kept across rotation and destroyed when it's popped. Nothing to clean up yourself.

### Interstitial

```kotlin
package com.example.myapp

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.compose.AdSlot
import io.github.thesobiaakbar.adroit.compose.rememberFullScreenAdLauncher
import io.github.thesobiaakbar.adroit.config.rewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.config.rewardedPlacement
import com.example.myapp.ads.AppAds

@Composable
fun HomeScreen(onOpenDetails: () -> Unit, coins: CoinsViewModel = viewModel()) {
    val activity = LocalActivity.current
    val launcher = rememberFullScreenAdLauncher()
    var showBonusIntro by rememberSaveable { mutableStateOf(false) }
    var bannerRefresh by rememberSaveable { mutableIntStateOf(0) }

    // Each time this screen comes to the front: load its full-screen ads, so they are ready when a button is
    // tapped. Does nothing for an ad that is already loaded or loading.
    LifecycleResumeEffect(Unit) {
        AdSlots.load(AppAds.LevelCompleteAd)
        AdSlots.load(AppAds.ExtraLifeRewarded)
        AdSlots.load(AppAds.BonusRewardedInterstitial)
        // The Details screen's banner, loaded ahead: it shows the moment Details opens.
        AdSlots.load(AppAds.DetailsBanner)
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Button(
                onClick = {
                    // Runs whether the ad showed or not: always continue here.
                    if (activity != null) AdSlots.showFullScreen(activity, AppAds.LevelCompleteAd) { onOpenDetails() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Level complete") }
            Button(
                onClick = { launcher.showRewarded(AppAds.ExtraLifeRewarded.rewardedPlacement(), coins::onRewardResult) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Extra life") }
            Button(onClick = { showBonusIntro = true }, modifier = Modifier.fillMaxWidth()) { Text("Bonus round") }
            Button(onClick = { bannerRefresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Refresh the banner") }

            // A native ad inside the page: its template comes from HomeNative's style.
            AdSlot(AppAds.HomeNative, Modifier.fillMaxWidth().padding(top = 16.dp))
            // A native ad in your own layout (step 11), registered as "promo".
            AdSlot(AppAds.PromoNative, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
        // The banner, pinned to the bottom. Its size comes from HomeBanner's style. A new refreshKey loads a new one.
        AdSlot(AppAds.HomeBanner, refreshKey = bannerRefresh)
    }

    if (showBonusIntro) {
        BonusIntroDialog(
            onAccept = {
                launcher.showRewardedInterstitial(
                    AppAds.BonusRewardedInterstitial.rewardedInterstitialPlacement(),
                    coins::onRewardResult,
                )
            },
            onDismiss = { showBonusIntro = false },
        )
    }
}
```

`AdSlots.showFullScreen` needs the Activity: `LocalActivity.current` gives it. It shows the ad in whatever format
it has now (an interstitial, or an app open ad if Firebase swapped it).

| Situation | What the user sees |
|---|---|
| The ad is loaded | "Loading ad…" for about a second, then the ad. Your callback runs when it closes. |
| It's still loading | "Loading ad…" for at most `loadTimeoutSeconds` (8), then the ad, or your callback if it didn't come. |
| The load failed, or the user is offline | Your callback runs at once. |
| `counter = 2` and this is the 1st call | No ad; your callback runs, and the ad loads for the next call (`loadAhead`). The 2nd, 4th, 6th… calls show an ad. |
| A limit (`timerSeconds`, `maxPerSession`, the gap between ads) or a premium user | No ad; your callback runs. |

(`sample-compose` sends "open Details" through its ViewModel instead of calling `navController` from the callback, so
it still happens if the phone is rotated while the ad is on screen.)

### Rewarded

`rememberFullScreenAdLauncher()` shows full-screen ads from a composable, over its Activity. Give the reward only for
`RewardResult.Earned`, and in a ViewModel, not in the composable: a rotation recreates the composable, not the
ViewModel.

```kotlin
package com.example.myapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.RewardResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class CoinsViewModel : ViewModel() {
    private val granted = mutableSetOf<String>()
    private val mutableCoins = MutableStateFlow(0)
    val coins: StateFlow<Int> = mutableCoins

    init {
        // A reward that arrived after its screen was gone (the user rotated or left during the ad).
        viewModelScope.launch {
            merge(Adroit.rewarded.unclaimedResults, Adroit.rewardedInterstitial.unclaimedResults)
                .collect(::onRewardResult)
        }
    }

    fun onRewardResult(result: RewardResult) {
        // Each reward once, even if it's delivered twice.
        if (result is RewardResult.Earned && granted.add(result.rewardId)) mutableCoins.value += result.reward.amount
    }
}
```

To tell the user why no reward came, look at the result:

```kotlin
when (result) {
    is RewardResult.Earned -> giveCoins(result.reward.amount)
    RewardResult.NotEarned -> toast("Watch the whole ad to get the reward")
    is RewardResult.Failed -> toast("No ad right now, try again later")
}
```

To enable a "Watch ad" button only when an ad is ready:
`val ready by rememberAdAvailability(AppAds.ExtraLifeRewarded.rewardedPlacement())`, then
`Button(enabled = ready == AdAvailability.Ready, …)`.

### Rewarded interstitial

Google requires an intro screen first, which offers the reward and lets the user say no. `HomeScreen` above shows
this dialog, and the ad when the countdown ends:

```kotlin
package com.example.myapp

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun BonusIntroDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(5) }
    val accept by rememberUpdatedState(onAccept)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        accept()
        dismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watch a short ad to earn bonus coins?") },
        text = { Text("The ad starts in ${remaining.coerceAtLeast(1)} s") },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("No thanks") } },
    )
}
```

### App open

It shows by itself when the user comes back to the app, never over `excludedActivities`. `MainActivity` loads it
once: `AdSlots.load(AppAds.AppOpenAd)`. With `loadAgain = true`, the next one loads after each show.

### Banners and native ads: one composable

Use `AdSlot` for every banner and native ad:

```kotlin
AdSlot(AppAds.DetailsBanner)
```

It shows a banner or a native ad, whichever the ad is in `AppAds` (or Firebase makes it), so the code never has to
match the ad's format: change `AdProfile.banner(…)` to `AdProfile.native(…)` and the same line shows a native ad.

`AdBanner(AppAds.DetailsBanner.bannerPlacement())` and `AdNative(AppAds.HomeNative.nativePlacement())` still exist
for banner options or one screen's own native layout, but they only take an ad of their own format:
`bannerPlacement()` on a native ad, or `nativePlacement()` on a banner, throws. Use `AdSlot` unless you need them.

`AdSlot` loads by itself, shows a shimmer while it loads, and collapses if no ad comes. Its ad belongs to the screen
(the navigation destination): leaving the screen for another tab and coming back, scrolling it away in a list, or
rotating shows the same ad again, with no new request. It's destroyed only when the screen is (the destination is
popped).

```kotlin
package com.example.myapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thesobiaakbar.adroit.compose.AdSlot
import com.example.myapp.ads.AppAds

@Composable
fun DetailsScreen() {
    Column(Modifier.fillMaxSize()) {
        Text("Details", Modifier.weight(1f).padding(16.dp))
        AdSlot(AppAds.DetailsBanner, Modifier.fillMaxWidth())
    }
}
```

To replace an ad with a new one, change its `refreshKey`: `AdSlot(AppAds.HomeBanner, refreshKey = bannerRefresh)`,
then `bannerRefresh++` (the "Refresh the banner" button in `HomeScreen`). The current ad stays until the new one
arrives, and stays if the new one fails. `AdBanner` and `AdNative` take a `refreshKey` too.

In a lazy list, give each ad a `slotKey`, as below.

### Load banners and native ads before their screen

`AdSlots.load` works for banners and native ads too: load the next screen's ads early, and its composables show them
the moment it opens.

```kotlin
AdSlots.load(AppAds.DetailsBanner)
AdSlots.load(AppAds.HomeNative)
```

| When the composable appears | What happens |
|---|---|
| The ad is loaded | It shows at once. No shimmer, no new request. |
| It's still loading | The shimmer, then that same ad when it arrives. No second request. |
| It wasn't loaded, or failed | The composable loads it now, as it would without `AdSlots.load`. |

A banner loaded ahead is sized for the full screen width; a banner of another width loads its own.

### Ads in a LazyColumn

One `AdSlot` per ad item, each with its own `slotKey`. Nothing else: the ads are kept in the screen's
ViewModel store, so scrolling away and back, or rotating, shows the same ad without reloading it.

```kotlin
package com.example.myapp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import io.github.thesobiaakbar.adroit.compose.AdSlot
import com.example.myapp.ads.AppAds

@Composable
fun FeedScreen() {
    LazyColumn {
        for (position in 0 until 40) {
            when {
                // Every 10th row is a native ad, and the 25th a banner; each one's position is its slotKey.
                position % 10 == 9 -> item(key = "ad_$position") {
                    AdSlot(AppAds.FeedNative, Modifier.fillMaxWidth(), slotKey = position)
                }
                position == 24 -> item(key = "banner_$position") {
                    AdSlot(AppAds.HomeBanner, slotKey = position)
                }
                else -> item(key = "article_$position") { Text("Article $position", Modifier.padding(16.dp)) }
            }
        }
    }
}
```

Every ad item keeps its ad for as long as the screen exists, however many the list has.

### Native styles

A native ad's `style` picks its layout. Change it in `AppAds`, or from Firebase with `"style"`:

| Style | Layout |
|---|---|
| `small_button_end` | Icon, "Ad" badge, headline and body, button at the end of the row. No media. |
| `small_button_bottom` | Icon, badge, headline and body, full-width button below. No media. |
| `large_button_bottom` (default) | Icon, badge, headline and body, then media, then a full-width button. |
| `large_compact` | Like `large_button_bottom`, with a larger icon and shorter media. |
| `media_start` | Media at the start; badge, headline, body and button beside it. |

To use another template in one place only: `AdNative(placement, layout = NativeTemplates.forStyle(NativeStyle.MEDIA_START))`.
Without `layout`, the ad's style decides, so Firebase can change it.

### Remove ads

After a purchase, `Premium.setPremium(context, true)`: every ad stops at once, and the next launch has a 3-second
splash without consent or ads.

### Privacy options

Google requires a way to change consent later, where consent applies. A button that shows only then:

```kotlin
package com.example.myapp

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thesobiaakbar.adroit.Adroit

@Composable
fun PrivacyOptionsButton() {
    val activity = LocalActivity.current ?: return
    val required by Adroit.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
    if (required) {
        Button(onClick = { Adroit.privacy.showPrivacyOptions(activity) }) { Text("Privacy options") }
    }
}
```

## 10. Colors

**In the app.** One file, `res/values/ads_style.xml`. Each value replaces the plugin's value of the same name, in
every template. Compose apps use it too: native ads are drawn by Google's native ad views, inside your composable.

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- The ads' look in this app: each value replaces the plugin's value of the same name. -->
<resources>
    <color name="adroit_native_attribution">#FFC107</color>
    <color name="adroit_native_attribution_text">#FFFFFF</color>
    <color name="adroit_native_cta">#FF6D00</color>
    <color name="adroit_native_cta_end">#FFA000</color>
    <color name="adroit_native_cta_text">#FFFFFF</color>
    <color name="adroit_shimmer_card">#FFF5F5F5</color>
</resources>
```

| Name | What |
|---|---|
| `adroit_native_cta`, `adroit_native_cta_end` | The button: a gradient from the first to the second. Set only the first for a solid button. |
| `adroit_native_cta_text` | The button's text |
| `adroit_native_attribution`, `_attribution_text` | The "Ad" badge, and its text |
| `adroit_native_background`, `_stroke` | The card's fill (transparent) and border |
| `adroit_native_headline`, `_body` | The texts |
| `adroit_shimmer_card`, `_shimmer_base`, `_shimmer_highlight` | The loading shimmer: the ad area, its blocks, the moving shine |

Dark mode: the same names in `res/values-night/ads_style.xml`. Sizes (text sizes, corners, the button's height…) are
`dimen`s you override the same way, in `sdp` and `ssp` if you like: the full list is in
[Native: Styling](NATIVE.md#styling).

The texts' default colors follow your Activity's XML theme (`android:theme` in the manifest), not your Compose
`MaterialTheme`. For dark mode, give the Activity a DayNight theme, or set the text colors above in `values-night`.

**From Firebase, without a release.** In a native ad's value (for that ad), or in `AdSettings` (for all of them):

```json
{
  "ctaColors": ["#FF6D00", "#FFC107"],
  "ctaTextColor": "#FFFFFF",
  "attributionColor": "#FFC107",
  "attributionTextColor": "#FFFFFF"
}
```

`ctaColors` takes one color (a solid button) or two (a left-to-right gradient). Colors are `"#RRGGBB"` or
`"#AARRGGBB"`. A field left out keeps the app's color; a wrong value is ignored.

## 11. Your own native ad layout

When no style fits, use your own layout. Native ads are drawn by Google's native ad views, so this layout is an **XML
layout**, even in a Compose app. It's three small changes, and everything else (loading, the shimmer, the colors,
Firebase, clicks and impressions) works with it as with a built-in style.

**1. Write the layout,** for example `res/layout/ad_promo.xml`. Rules:

- Ordinary views only: **no `NativeAdView`, no `MediaView`**. The plugin wraps your layout and adds the media itself.
  (Converting an old layout: delete those two tags, keep what's inside, and turn the `MediaView` into a `FrameLayout`.)
- Give the views the plugin fills these IDs, written `@id/…` (they're the plugin's; no `+`):

| ID | View | |
|---|---|---|
| `adroit_native_headline` | `TextView` | Required |
| `adroit_native_attribution` | `TextView` saying "Ad" | Required by Google |
| `adroit_native_body` | `TextView` | |
| `adroit_native_cta` | `android.widget.Button` | Write the full name, so a Material theme doesn't repaint it |
| `adroit_native_icon` | `ImageView` | |
| `adroit_native_media` | an empty `FrameLayout`, at least 120dp tall | The image or video goes inside |
| `adroit_native_advertiser`, `_stars` (`RatingBar`), `_price`, `_store` | | Optional extras |

- Leave the top-right corner free (about 24dp): the SDK puts the AdChoices icon there.
- Views an ad has no content for are hidden. Everything else (sizes, backgrounds, fonts) is up to you. Use the
  plugin's colors (`@color/adroit_native_cta_text`…) and drawables if you want Firebase colors and your
  `ads_style.xml` to apply to it too.

A complete example:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Your own native ad layout. Ordinary views only (no NativeAdView, no MediaView): the plugin adds those.
    The views the plugin fills use its IDs: @id/adroit_native_... Everything else is up to you.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/ad_promo_card"
    android:orientation="vertical"
    android:padding="@dimen/_8sdp">

    <!-- The top-right corner stays free: the SDK puts the AdChoices icon there. -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingEnd="24dp">

        <ImageView
            android:id="@id/adroit_native_icon"
            android:layout_width="@dimen/_36sdp"
            android:layout_height="@dimen/_36sdp"
            android:contentDescription="@null" />

        <TextView
            android:id="@id/adroit_native_attribution"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/_6sdp"
            android:background="@drawable/adroit_native_attribution_background"
            android:paddingHorizontal="@dimen/_4sdp"
            android:text="Ad"
            android:textColor="@color/adroit_native_attribution_text"
            android:textSize="@dimen/_8ssp"
            android:textStyle="bold" />

        <TextView
            android:id="@id/adroit_native_headline"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/_6sdp"
            android:layout_weight="1"
            android:maxLines="2"
            android:textSize="@dimen/_12ssp"
            android:textStyle="bold" />
    </LinearLayout>

    <TextView
        android:id="@id/adroit_native_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/_4sdp"
        android:maxLines="2"
        android:textSize="@dimen/_10ssp" />

    <!-- The image or video goes in here. At least 120dp tall: the video minimum. -->
    <FrameLayout
        android:id="@id/adroit_native_media"
        android:layout_width="match_parent"
        android:layout_height="140dp"
        android:layout_marginTop="@dimen/_6sdp" />

    <!-- Written as android.widget.Button, so a Material theme keeps its background. -->
    <android.widget.Button
        android:id="@id/adroit_native_cta"
        android:layout_width="match_parent"
        android:layout_height="@dimen/_34sdp"
        android:layout_marginTop="@dimen/_6sdp"
        android:background="@drawable/adroit_native_cta_background"
        android:textAllCaps="false"
        android:textColor="@color/adroit_native_cta_text"
        android:textSize="@dimen/_12ssp"
        android:textStyle="bold" />
</LinearLayout>
```

And its card, `res/drawable/ad_promo_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFF8E1" />
    <stroke
        android:width="1dp"
        android:color="#FFFFC107" />
    <corners android:radius="@dimen/_10sdp" />
</shape>
```

**2. Register it under a name,** one line in `MyApp.onCreate` (already in step 6):

```kotlin
NativeTemplates.register("promo", R.layout.ad_promo)
```

The name is yours: letters, digits and `_`, not one of the style names.

**3. Use it,** in any of these ways:

| To… | Do this |
|---|---|
| Show an ad in it by default | In `AppAds`: `PromoNative(AdProfile.native(layout = "promo"))`, then `AdSlot(AppAds.PromoNative)` (as in `HomeScreen`). |
| Switch any native ad to it from Firebase | That ad's value: `{ "style": "promo" }`. Back with `{ "style": "large_button_bottom" }`. |
| Use it in one composable only | `AdNative(AppAds.HomeNative.nativePlacement(), layout = NativeTemplates.registered("promo"))` |

Its loading shimmer is built from your layout automatically: the whole card shimmers, with a block over each view.

To **use the same layout in another project**, copy the XML file (and any drawable it uses), and the `register` line.
Nothing else. It's the same file in XML and Compose apps.

## 12. Your own Firebase values

Anything else you want to change from Firebase (a flag, a number, a text, JSON) goes in one `object`, with the value
to use until Firebase sets one:

```kotlin
package com.example.myapp.ads

import io.github.thesobiaakbar.adroit.firebase.RemoteValue
import org.json.JSONObject

/** The app's own Firebase values (not ads), each with the value used until Firebase sets one. */
object AppConfig {
    val showRateDialog = RemoteValue.boolean("show_rate_dialog", default = false)
    val freeLevels = RemoteValue.long("free_levels", default = 5)
    val coinPrice = RemoteValue.double("coin_price", default = 0.99)
    val welcomeText = RemoteValue.string("welcome_text", default = "Welcome!")
    val features = RemoteValue.json("features", default = JSONObject().put("chat", false))
}
```

Read `.value` anywhere, in a composable too: `if (AppConfig.showRateDialog.value) RateDialog()`. It never waits. A
missing or wrong value gives your default. More in [Remote configuration](REMOTE_CONFIG.md#your-apps-own-values).

## 13. Firebase: the parameters and sample values

Each ad has one Remote Config parameter, named like its entry in `AppAds`, of type **JSON**, plus `AdSettings` for
what they share. A value needs only the fields you change; the rest keep `AppAds`'s values.

**To add one:** Firebase console → **Remote Config** → **Add parameter**: the entry's name, data type **JSON**, the
JSON as the default value → **Save**. When all are added, **Publish changes**. Users get them on their next launch.

Replace each `ca-app-pub-XXXXXXXXXXXXXXXX/…` with your ad unit ID **of that format**, or remove `adUnitIds` to keep
the IDs from `AppAds`.

**Splash ad** (`SplashAd`):

```json
{ "format": "interstitial", "enabled": true, "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/1111111111"], "splashTimeoutSeconds": 8 }
```

**Interstitial** (`LevelCompleteAd`):

```json
{
  "format": "interstitial",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/2222222222", "ca-app-pub-XXXXXXXXXXXXXXXX/3333333333"],
  "counter": 2,
  "counterStart": 0,
  "counterStep": 1,
  "counterResetOnFail": false,
  "loadAhead": 1,
  "timerSeconds": 0,
  "maxPerSession": 10,
  "loadAgain": true,
  "loadTimeoutSeconds": 8,
  "showLoading": true,
  "loadingSeconds": 1
}
```

**Rewarded** (`ExtraLifeRewarded`):

```json
{
  "format": "rewarded",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/4444444444"],
  "maxPerSession": 0,
  "loadAgain": true,
  "loadTimeoutSeconds": 8,
  "showLoading": true,
  "loadingSeconds": 1
}
```

**Rewarded interstitial** (`BonusRewardedInterstitial`):

```json
{
  "format": "rewarded_interstitial",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/5555555555"],
  "counter": 0,
  "timerSeconds": 0,
  "maxPerSession": 0,
  "loadTimeoutSeconds": 8,
  "showLoading": true,
  "loadingSeconds": 1
}
```

**App open** (`AppOpenAd`):

```json
{
  "format": "app_open",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/6666666666"],
  "timerSeconds": 240,
  "maxPerSession": 0,
  "loadAgain": true,
  "showLoading": true,
  "loadingSeconds": 1
}
```

**Banner** (`HomeBanner`, `DetailsBanner`). `style`: `adaptive`, `collapsible_bottom`, `collapsible_top`,
`rectangle`, `standard` or `inline_adaptive`.

```json
{
  "format": "banner",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/7777777777"],
  "style": "adaptive",
  "showLoading": true
}
```

**Native** (`HomeNative`, `FeedNative`, `PromoNative`). `style`: `small_button_end`, `small_button_bottom`,
`large_button_bottom`, `large_compact`, `media_start`, or your own layout's name.

```json
{
  "format": "native",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/8888888888"],
  "style": "large_button_bottom",
  "loadAgain": false,
  "showLoading": true,
  "ctaColors": ["#FF6D00", "#FFC107"],
  "ctaTextColor": "#FFFFFF",
  "attributionColor": "#FFC107",
  "attributionTextColor": "#FFFFFF"
}
```

**Every ad** (`AdSettings`):

```json
{
  "adsEnabled": true,
  "maxInterstitialsPerSession": 0,
  "minSecondsBetweenFullScreenAds": 30,
  "splashSeconds": 13,
  "premiumSplashSeconds": 3,
  "offlineSplashSeconds": 3,
  "consentTimeoutSeconds": 8,
  "loadTimeoutSeconds": 8,
  "showLoading": true,
  "loadingSeconds": 1,
  "ctaColors": ["#FF6D00", "#FFC107"],
  "attributionColor": "#FFC107"
}
```

**Your own values** (step 12) are ordinary parameters of their own type:

| Parameter | Data type | Example |
|---|---|---|
| `show_rate_dialog` | Boolean | `true` |
| `free_levels` | Number | `10` |
| `coin_price` | Number | `1.49` |
| `welcome_text` | String | `Hello again!` |
| `features` | JSON | `{"chat": true}` |

**Counter and timer** (interstitials and rewarded interstitials). Each ad follows one strategy, and you can switch
it from Firebase at any time:

| The ad's JSON has | Strategy |
|---|---|
| `"counter": 0, "timerSeconds": 0` | None: every call may show an ad. |
| a `counter` | Counter. `timerSeconds` is ignored. |
| `"counter": 0` and a `timerSeconds` | Timer. |

*Counter.* The counter starts at `counterStart`. Every time your code calls the ad, it grows by `counterStep`. When
it reaches the max counter (`counter`), the ad shows; once the ad was really shown, the counter goes back to 0.

```json
{
  "counter": [3, 2],
  "counterStart": 1,
  "counterStep": 1,
  "counterResetOnFail": false,
  "loadAhead": 1
}
```

| Field | Default | Meaning |
|---|---|---|
| `counter` | `0` | The max counter. One number (`3`: an ad on every 3rd call), or a list (`[3, 2]`: an ad when the counter reaches 3, then when it reaches 2, then 3 again, and so on in a loop). `0` turns the counter off. |
| `counterStart` | `0` | Where the counter starts when the app starts. With `1` and a max counter of 3, the first ad comes on the 2nd call. |
| `counterStep` | `1` | What each call adds. `0.5` makes a max counter of 3 take 6 calls; decimals like `1.25` work too. |
| `counterResetOnFail` | `false` | When the ad is due and couldn't be shown (not loaded, offline): `false` tries again on the very next call; `true` counts again from 0. |
| `loadAhead` | `1` | When the ad loads: `1` one call before it's due, `2` two calls before, `0` only on the call it's due. |

Where the ad loads:

| You want the ad to load… | Value |
|---|---|
| One call before it's due (the default) | `{ "loadAhead": 1 }` |
| Two calls before it's due | `{ "loadAhead": 2 }` |
| Only when it's due, then show as soon as it arrives (behind "Loading ad…") | `{ "loadAhead": 0 }` |
| Only when it's due, and never make the user wait: the next call shows it | `{ "loadAhead": 0, "loadTimeoutSeconds": 0 }` |
| Right after each ad is shown, so one is always ready | `{ "loadAgain": true }` |

*Timer.* With `{ "counter": 0, "timerSeconds": 30 }`: after the ad is shown, nothing happens for 30 seconds (the ad
isn't shown again and isn't requested). Then the next ad is requested once, and stays loaded until your code shows
it, however long that takes. When it's shown, the 30 seconds start again.

More examples and the call-by-call table: [Counter and timer](AD_PROFILES.md#counter-and-timer).

**Quick changes:**

| To… | Parameter | Value |
|---|---|---|
| Turn one ad off | the ad's | `{ "enabled": false }` |
| Turn every ad off | `AdSettings` | `{ "adsEnabled": false }` |
| Interstitial on every 3rd tap | the ad's | `{ "counter": 3 }` |
| An ad after 3 taps, then after 2, in a loop | the ad's | `{ "counter": [3, 2] }` |
| Load a counter ad only when it's due | the ad's | `{ "loadAhead": 0 }` |
| Timer instead of counter: 60 s between two shows | the ad's | `{ "counter": 0, "timerSeconds": 60 }` |
| Use two ad units in turn | the ad's | `{ "adUnitIds": ["ca-app-pub-…/111", "ca-app-pub-…/222"] }` |
| Another native layout | the native ad's | `{ "style": "media_start" }` (or your own layout's name) |
| An orange-to-amber button | a native ad's, or `AdSettings` | `{ "ctaColors": ["#FF6D00", "#FFC107"] }` |
| A golden "Ad" badge | a native ad's, or `AdSettings` | `{ "attributionColor": "#FFC107" }` |
| A native ad instead of a banner | the banner's | `{ "format": "native", "style": "large_button_bottom", "adUnitIds": ["<a native ad unit>"] }` (shown by `AdSlot`) |
| A 13-second splash | `AdSettings` | `{ "splashSeconds": 13 }` |
| No shimmer or "Loading ad…" for one ad | the ad's | `{ "showLoading": false }` |

A value with a mistake is ignored (with a warning in the log), and the ad keeps its previous settings. Every field is
in [Ad profiles](AD_PROFILES.md#the-json).

## 14. Test it

A debug build on a phone or an emulator:

- [ ] Logcat (`Adroit`): `Firebase parameter <Name>: {…}` per ad, then `Remote Config fetched`.
- [ ] The consent form (first launch), the progress bar, a test ad, then your main screen.
- [ ] Every ad is labelled "Test Ad". Full-screen ads show "Loading ad…" first; banners and native ads, a shimmer.
- [ ] Switching tabs, scrolling a list away and back, or rotating the phone doesn't reload a banner or a native ad,
  and rotating doesn't restart the splash.
- [ ] Your `ads_style.xml` colors show on the native ads, and your own layout shows where you use it.
- [ ] A Firebase change (for example `{ "style": "media_start" }` on a native ad) shows after one or two relaunches.
- [ ] In airplane mode, the splash lasts 3 seconds and the app works without ads.

## 15. Before release

- [ ] Every ad in `AppAds` has its real ad unit IDs, in `AppAds` or in Firebase.
- [ ] The manifest has **your** AdMob app ID.
- [ ] A GDPR message is published in AdMob → Privacy & messaging.
- [ ] `app-ads.txt` is set up in AdMob.
- [ ] A release build shows real ads (a new ad unit can take a few hours). Don't click your own ads.
- [ ] A premium user sees no ad and a 3-second splash.

Problems? See [Troubleshooting](TROUBLESHOOTING.md). In depth: [Jetpack Compose](COMPOSE.md),
[Ad profiles](AD_PROFILES.md), [Native ads](NATIVE.md), [Splash screen](SPLASH.md), [Consent](CONSENT.md),
[Remote configuration](REMOTE_CONFIG.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-10-05
