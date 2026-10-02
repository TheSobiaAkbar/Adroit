# Adding ads to a new Kotlin + XML app

This page takes a new Android Studio project (Kotlin, XML layouts) to a complete ad setup:

- consent on the splash screen;
- a splash ad;
- interstitial, rewarded, rewarded interstitial and app open ads;
- banners and native ads, which can load before their screen opens;
- native ads in your own layouts and colors;
- a "remove ads" purchase;
- every ad, and any other value you choose, controlled from Firebase without a new release.

Your app needs two of the plugin's modules: `adroit-views` (banners and native ads in XML) and `adroit-firebase`
(Firebase Remote Config). They bring everything else: `adroit-core`, Google's ads and consent SDKs, and the
[sdp/ssp](https://github.com/intuit/sdp) sizes. Don't add `adroit-compose`, `adroit-testing`, the sample apps,
`play-services-ads` or `ads-mobile-sdk`.

## What changes from one project to the next

Most of the setup is the same in every app: copy it once, then change only this.

| File | What you change in each project | Step |
|---|---|---|
| `settings.gradle.kts` | Add the Adroit repository | [2](#2-add-the-adroit-repository) |
| `build.gradle.kts` (root) and `app/build.gradle.kts` | Add the Google services plugin and 2 dependencies | [3](#3-connect-firebase), [4](#4-add-the-dependencies) |
| `app/google-services.json` | This app's file, from its Firebase project | [3](#3-connect-firebase) |
| `AndroidManifest.xml` | This app's AdMob app ID | [7](#7-the-manifest) |
| `ads/AppAds.kt` | This app's ads and their ad unit IDs | [5](#5-list-your-ads-appadskt) |
| `MyApp.kt`, `SplashActivity.kt`, `activity_splash.xml` | Copy them. Change only the `package` line (and the splash's look, if you like). | [6](#6-start-everything-the-application-class), [8](#8-the-splash-screen) |
| `res/values/ads_style.xml` | Optional: this app's ad colors | [10](#10-colors) |
| Your screens | One line or one XML view per ad | [9](#9-show-the-ads) |
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
    id("com.google.gms.google-services") version "4.5.0" apply false
}
```

With a version catalog (`gradle/libs.versions.toml`), add the plugin there, as Android Studio does for the others.

## 4. Add the dependencies

In `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("com.google.gms.google-services") // reads app/google-services.json
}

android {
    // ...
    defaultConfig {
        minSdk = 24 // the plugin needs 24 or higher
    }
    buildFeatures {
        viewBinding = true // used in the examples below
        buildConfig = true // for BuildConfig.DEBUG
    }
}

dependencies {
    implementation("io.github.thesobiaakbar:adroit-views:1.0.0-rc3")
    implementation("io.github.thesobiaakbar:adroit-firebase:1.0.0-rc3")

    // Your usual AndroidX libraries (Android Studio adds most of them):
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
}
```

**Check:** *Sync Project with Gradle Files* succeeds. If `io.github.thesobiaakbar:adroit-views` isn't found, check step 2: the repository
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

- **The entry name is the ad's name everywhere:** in code (`AppAds.HomeBanner`), in XML (`"HomeBanner"`) and in
  Firebase (a parameter called `HomeBanner`). Never rename one after release.
- **The format** is the function: `AdProfile.interstitial`, `rewarded`, `rewardedInterstitial`, `appOpen`, `banner`
  or `native`.
- **Ad unit IDs:** add `ids = listOf("ca-app-pub-…/…")` when you have them, or set them in Firebase only
  ([step 13](#13-firebase-the-parameters-and-sample-values)). No IDs: Google's test ads. Two IDs or more are used in
  turn.
- **One splash ad at most** (`splash = true`). **`excludedActivities`** lists the screens the app open ad must never
  cover: always your splash.
- **Native styles:** `NativeStyle.SMALL_BUTTON_END`, `SMALL_BUTTON_BOTTOM`, `LARGE_BUTTON_BOTTOM` (the default),
  `LARGE_COMPACT` or `MEDIA_START` ([what each looks like](#native-styles)), or your own layout:
  `AdProfile.native(layout = "promo")` ([step 11](#11-your-own-native-ad-layout)).
- **Banner styles:** `BannerSize.AnchoredAdaptive` (default, full width), `BannerSize.Collapsible(…BOTTOM)`,
  `BannerSize.MediumRectangle` (300×250) or `BannerSize.Standard` (320×50).
- Every argument is optional. The main ones:

| Argument | Formats | Meaning |
|---|---|---|
| `counter = 3` | interstitial, rewarded interstitial | Shows an ad only on every 3rd `show` call. |
| `timerSeconds = 60` | interstitial, rewarded interstitial, app open | At least 60 s between two shows of this ad. |
| `maxPerSession = 5` | full-screen ads | At most 5 shows per app launch. |
| `loadAgain = true` | all but banners | Loads the next ad right after each show. For ads shown again and again. |
| `loadTimeoutSeconds = 8` | full-screen ads | How long a show waits for an ad that isn't loaded yet. |
| `showLoading = false` | all | No "Loading ad…" screen before this full-screen ad, and no shimmer on this banner or native ad. |
| `enabled = false` | all | Turns this ad off. |

`AdSettings` holds what every ad shares: `splashSeconds` (the splash timer, default 11),
`minSecondsBetweenFullScreenAds`, `maxInterstitialsPerSession`, and the defaults of `loadTimeoutSeconds`,
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

## 7. The manifest

In `app/src/main/AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    ...>

    <!-- The splash screen is the launcher. -->
    <activity
        android:name=".SplashActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <activity android:name=".MainActivity" />

    <!-- Your AdMob app ID (AdMob → Apps → App settings). This one is Google's sample ID for testing. -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3940256099942544~3347511713" />
</application>
```

Remove the `intent-filter` from `MainActivity` if Android Studio put it there. The plugin adds the `INTERNET`
permission itself.

**Check:** run a debug build and filter logcat by `Adroit`: one line `Firebase parameter <Name>: {…}` per ad.

## 8. The splash screen

The splash does everything at app start, and always ends on your main screen:

1. **Consent:** Google's consent form, when the user's region needs it.
2. **The timer:** a progress bar for `splashSeconds` (13 in `AppAds.settings`); 3 seconds offline or for premium users.
3. **The splash ad,** if it loads before the timer ends.

It never gets stuck, even offline. Copy these two files as they are; change the layout's look if you like.

`res/layout/activity_splash.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/app_name"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="MyApp"
        android:textSize="28sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/progress_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        android:visibility="gone"
        app:layout_constraintBottom_toTopOf="@id/progress"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/progress"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="48dp"
        android:layout_marginBottom="56dp"
        android:max="100"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

`SplashActivity.kt`:

```kotlin
package com.example.myapp

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.splash.SplashState
import com.example.myapp.ads.AppAds
import com.example.myapp.databinding.ActivitySplashBinding
import kotlinx.coroutines.launch

/** Keeps the splash running across rotation: consent, timer and splash ad happen once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow = SplashFlow.create(application, AppAds.SplashAd, Premium.isPremium(application))

    override fun onCleared() = flow.cancel()
}

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private lateinit var binding: ActivitySplashBinding
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.flow.start(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flow.state.collect { state ->
                    when (state) {
                        SplashState.Preparing -> showProgress(null) // consent form may be on screen
                        is SplashState.Loading -> showProgress(state.progress) // 0..100
                        SplashState.ShowingAd -> showProgress(100) // the splash ad is on screen
                        is SplashState.Finished -> goToMain()
                    }
                }
            }
        }
    }

    private fun showProgress(percent: Int?) {
        binding.progress.isVisible = percent != null
        binding.progressText.isVisible = percent != null
        if (percent != null) {
            binding.progress.setProgressCompat(percent, true)
            binding.progressText.text = "$percent%"
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

**Consent in debug builds.** A debug build behaves as if you were in Europe, so the consent form shows on your phone
or emulator wherever you are. Once answered, it doesn't show again: to see it again, clear the app's storage
(Settings → Apps → MyApp → Storage → Clear storage). Release builds use the user's real location. The form itself
comes from AdMob: publish a GDPR message in **AdMob → Privacy & messaging**, or no form shows.

**Check:** launch a debug build: the consent form, then the progress bar, then a test ad, then your main screen.

## 9. Show the ads

Two rules cover every ad:

1. **Ads load when you ask.** Ask when the screen that shows them opens: `AdSlots.load` in `onResume`. Banners and
   native ads in a layout ask by themselves.
2. **Every show calls you back, whether an ad showed or not.** Continue your app's flow inside the callback.

### Interstitial

```kotlin
override fun onResume() {
    super.onResume()
    // Load this screen's full-screen ads, so they are ready when a button is tapped.
    // Does nothing for an ad that is already loaded or loading.
    AdSlots.load(AppAds.LevelCompleteAd)
    AdSlots.load(AppAds.ExtraLifeRewarded)
    AdSlots.load(AppAds.BonusRewardedInterstitial)
    // The Details screen's banner, loaded ahead: it shows the moment Details opens.
    AdSlots.load(AppAds.DetailsBanner)
}
```

```kotlin
binding.levelComplete.setOnClickListener {
    AdSlots.showFullScreen(this, AppAds.LevelCompleteAd) { result: FullScreenResult ->
        // Runs whether the ad showed or not: always continue here.
        startActivity(Intent(this, DetailsActivity::class.java))
    }
}
```

| Situation | What the user sees |
|---|---|
| The ad is loaded | "Loading ad…" for about a second, then the ad. Your callback runs when it closes. |
| It's still loading | "Loading ad…" for at most `loadTimeoutSeconds` (8), then the ad, or your callback if it didn't come. |
| The load failed, or the user is offline | Your callback runs at once. |
| `counter = 2` and this is the 1st call | No ad; your callback runs. The 2nd, 4th, 6th… calls show an ad. |
| A limit (`timerSeconds`, `maxPerSession`, the gap between ads) or a premium user | No ad; your callback runs. |

### Rewarded

Give the reward only for `RewardResult.Earned`:

```kotlin
binding.extraLife.setOnClickListener {
    Adroit.showRewarded(this, AppAds.ExtraLifeRewarded.rewardedPlacement()) { result ->
        when (result) {
            is RewardResult.Earned -> giveCoins(result.reward.amount)
            RewardResult.NotEarned -> toast("Watch the whole ad to get the reward")
            is RewardResult.Failed -> toast("No ad right now, try again later")
        }
    }
}
```

### Rewarded interstitial

Google requires an intro screen first, which offers the reward and lets the user say no. Show the ad after they
accept:

```kotlin
Adroit.rewardedInterstitial.show(this, AppAds.BonusRewardedInterstitial.rewardedInterstitialPlacement()) { result ->
    if (result is RewardResult.Earned) giveCoins(result.reward.amount)
}
```

### App open

It shows by itself when the user comes back to the app, never over `excludedActivities`. Load it once, when your
main screen opens: `AdSlots.load(AppAds.AppOpenAd)`. With `loadAgain = true`, the next one loads after each show.

### Banners and native ads: one view in the layout

Put an `AdSlotView` in the XML with the ad's name. It shows a banner or a native ad, whichever the ad is (Firebase
can even swap them), in its style, with a shimmer while it loads:

```xml
<io.github.thesobiaakbar.adroit.views.AdSlotView
    android:id="@+id/home_banner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="HomeBanner" />
```

(`xmlns:app="http://schemas.android.com/apk/res-auto"` on the layout's root.) That's all. It loads by itself, hides
itself if no ad comes, and never requests a second ad while it has one. Once loaded, the ad stays: switching tabs,
rebuilding a Fragment's view or rotating shows the same ad, with no new request. It's destroyed only when its screen
closes for good (the Activity finishes, the Fragment is removed).

To replace the ad with a new one: `binding.homeBanner.load(refresh = true)`. The current one stays until the new one
arrives, then it's destroyed; if the new one fails, the current one stays. To get a new ad each time the view is
created again, add `app:adroit_refresh="true"` (false by default).

A banner or a native ad from code, into an empty `FrameLayout`:

```kotlin
findViewById<FrameLayout>(R.id.banner_container).attachBanner(this, AppAds.DetailsBanner.bannerPlacement())
findViewById<FrameLayout>(R.id.native_container).attachNativeAd(this, AppAds.HomeNative.nativePlacement())
```

(In a Fragment, pass `viewLifecycleOwner` instead of `this`.) Give the container an `android:id`: that's how the screen
keeps its ad. Pass `refresh = true` to replace the ad the same way as above.

### Load banners and native ads before their screen

`AdSlots.load` works for banners and native ads too: load the next screen's ads early, and its views show them the
moment it opens.

```kotlin
AdSlots.load(AppAds.DetailsBanner)
AdSlots.load(AppAds.HomeNative)
```

| When the view appears | What happens |
|---|---|
| The ad is loaded | It shows at once. No shimmer, no new request. |
| It's still loading | The shimmer, then that same ad when it arrives. No second request. |
| It wasn't loaded, or failed | The view loads it now, as it would without `AdSlots.load`. |

A banner loaded ahead is sized for the full screen width; a banner view of another width loads its own.

### Native ads in a RecyclerView

Keep one `NativeAdHolder` per ad row in the ViewModel, and bind it to the row's container:

```kotlin
/** Keeps one native ad per ad row, across scrolling and rotation. */
class FeedViewModel : ViewModel() {
    private val holders = mutableMapOf<Int, NativeAdHolder>()

    fun adFor(position: Int): NativeAdHolder =
        holders.getOrPut(position) { NativeAdHolder(AppAds.FeedNative.nativePlacement()) }

    override fun onCleared() {
        holders.values.forEach { it.clear() } // destroys the ads
    }
}
```

```kotlin
// In onBindViewHolder, for an ad row whose layout is an empty FrameLayout (R.id.ad_container):
viewModel.adFor(position).bindTo(holder.itemView.findViewById<FrameLayout>(R.id.ad_container), owner)
```

`owner` is the Activity, or `viewLifecycleOwner` in a Fragment. Scrolling back shows the same ad; it never reloads.

### Native styles

A native ad's `style` picks its layout. Change it in `AppAds`, or from Firebase with `"style"`:

| Style | Layout |
|---|---|
| `small_button_end` | Icon, "Ad" badge, headline and body, button at the end of the row. No media. |
| `small_button_bottom` | Icon, badge, headline and body, full-width button below. No media. |
| `large_button_bottom` (default) | Icon, badge, headline and body, then media, then a full-width button. |
| `large_compact` | Like `large_button_bottom`, with a larger icon and shorter media. |
| `media_start` | Media at the start; badge, headline, body and button beside it. |

### Remove ads

After a purchase, `Premium.setPremium(this, true)`: every ad stops at once, and the next launch has a 3-second splash
without consent or ads.

### Privacy options

Google requires a way to change consent later, where consent applies. A button that shows only then:

```kotlin
binding.privacyOptions.setOnClickListener { Adroit.privacy.showPrivacyOptions(this) }
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        Adroit.privacy.isPrivacyOptionsRequired.collect { binding.privacyOptions.isVisible = it }
    }
}
```

## 10. Colors

**In the app.** One file, `res/values/ads_style.xml`. Each value replaces the plugin's value of the same name, in
every template:

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

When no style fits, use your own layout. It's three small changes, and everything else (loading, the shimmer, the
colors, Firebase, clicks and impressions) works with it as with a built-in style.

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

**2. Register it under a name,** one line in `MyApp.onCreate` (already in step 6):

```kotlin
NativeTemplates.register("promo", R.layout.ad_promo)
```

The name is yours: letters, digits and `_`, not one of the style names.

**3. Use it,** in any of these ways:

| To… | Do this |
|---|---|
| Show an ad in it by default | In `AppAds`: `PromoNative(AdProfile.native(layout = "promo"))`, then an `AdSlotView` with `app:adroit_placement="PromoNative"`. |
| Switch any native ad to it from Firebase | That ad's value: `{ "style": "promo" }`. Back with `{ "style": "large_button_bottom" }`. |
| Use it in one view only | `<io.github.thesobiaakbar.adroit.views.AdNativeView app:adroit_placement="HomeNative" app:adroit_layout="@layout/ad_promo" … />` |

Its loading shimmer is built from your layout automatically: the whole card shimmers, with a block over each view.

To **use the same layout in another project**, copy the XML file (and any drawable it uses), and the `register` line.
Nothing else.

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

Read `.value` anywhere: `if (AppConfig.showRateDialog.value) showRateDialog()`. It never waits. A missing or wrong
value gives your default. More in [Remote configuration](REMOTE_CONFIG.md#your-apps-own-values).

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

**Quick changes:**

| To… | Parameter | Value |
|---|---|---|
| Turn one ad off | the ad's | `{ "enabled": false }` |
| Turn every ad off | `AdSettings` | `{ "adsEnabled": false }` |
| Interstitial on every 3rd tap | the ad's | `{ "counter": 3 }` |
| At least 60 s between two shows of an ad | the ad's | `{ "timerSeconds": 60 }` |
| Use two ad units in turn | the ad's | `{ "adUnitIds": ["ca-app-pub-…/111", "ca-app-pub-…/222"] }` |
| Another native layout | the native ad's | `{ "style": "media_start" }` (or your own layout's name) |
| An orange-to-amber button | a native ad's, or `AdSettings` | `{ "ctaColors": ["#FF6D00", "#FFC107"] }` |
| A golden "Ad" badge | a native ad's, or `AdSettings` | `{ "attributionColor": "#FFC107" }` |
| A native ad instead of a banner | the banner's | `{ "format": "native", "style": "large_button_bottom", "adUnitIds": ["<a native ad unit>"] }` |
| A 13-second splash | `AdSettings` | `{ "splashSeconds": 13 }` |
| No shimmer or "Loading ad…" for one ad | the ad's | `{ "showLoading": false }` |

A value with a mistake is ignored (with a warning in the log), and the ad keeps its previous settings. Every field is
in [Ad profiles](AD_PROFILES.md#the-json).

## 14. Test it

A debug build on a phone or an emulator:

- [ ] Logcat (`Adroit`): `Firebase parameter <Name>: {…}` per ad, then `Remote Config fetched`.
- [ ] The consent form (first launch), the progress bar, a test ad, then your main screen.
- [ ] Every ad is labelled "Test Ad". Full-screen ads show "Loading ad…" first; banners and native ads, a shimmer.
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

Problems? See [Troubleshooting](TROUBLESHOOTING.md). In depth: [Ad profiles](AD_PROFILES.md),
[Native ads](NATIVE.md), [Splash screen](SPLASH.md), [Consent](CONSENT.md), [Remote configuration](REMOTE_CONFIG.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-29
