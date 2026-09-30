# Adding ads to a new app with XML and Compose screens

This page takes an Android Studio project that has **both** XML screens (Activities, Fragments, layouts) and Jetpack
Compose screens to a complete ad setup. It's the usual case for an app moving to Compose one screen at a time:

- consent on the splash screen;
- a splash ad;
- interstitial, rewarded, rewarded interstitial and app open ads, the same calls from XML and Compose screens;
- banners and native ads as XML views in XML screens and as composables in Compose screens, which can load before
  their screen opens;
- Compose inside a Fragment, Compose inside an XML layout, and XML inside a Compose screen;
- native ads in lists, kept across scrolling and rotation;
- native ads in your own layouts and colors;
- a "remove ads" purchase;
- every ad, and any other value you choose, controlled from Firebase without a new release.

Your app needs two of the plugin's modules: `admob-compose` (the composables) and `admob-firebase` (Firebase Remote
Config). `admob-compose` brings `admob-views` (the XML views) with it, so one dependency covers both toolkits. They
bring everything else: `admob-core`, Google's ads and consent SDKs, and the [sdp/ssp](https://github.com/intuit/sdp)
sizes. Don't add `admob-testing`, the sample apps, `play-services-ads` or `ads-mobile-sdk`.

The finished result is the `sample-mixed` app in the AdsPlugin project: XML splash, Home and Settings, a Compose feed
inside a Fragment, and a Compose Details screen that embeds an XML layout. An app with only XML screens:
[Adding ads to a new Kotlin + XML app](NEW_APP_XML.md). Only Compose:
[Adding ads to a new Jetpack Compose app](NEW_APP_COMPOSE.md).

**The one rule of a mixed app:** there's one ads library, started once, with one list of ads. XML views and
composables for the same ad (`AppAds.HomeBanner`) are the same ad, with the same Firebase parameter. Only the way you
put it on screen changes with the toolkit.

## What changes from one project to the next

Most of the setup is the same in every app: copy it once, then change only this.

| File | What you change in each project | Step |
|---|---|---|
| `settings.gradle.kts` | Add `mavenLocal()` | [2](#2-get-the-plugin-onto-your-computer) |
| `build.gradle.kts` (root) and `app/build.gradle.kts` | Add the Google services plugin and the dependencies | [3](#3-connect-firebase), [4](#4-add-the-dependencies) |
| `app/google-services.json` | This app's file, from its Firebase project | [3](#3-connect-firebase) |
| `AndroidManifest.xml` | This app's AdMob app ID | [7](#7-the-manifest) |
| `ads/AppAds.kt` | This app's ads and their ad unit IDs | [5](#5-list-your-ads-appadskt) |
| `MyApp.kt`, `SplashActivity.kt`, `activity_splash.xml` | Copy them. Change only the `package` line (and the splash's look, if you like). | [6](#6-start-everything-the-application-class), [8](#8-the-splash-screen) |
| `res/values/ads_style.xml` | Optional: this app's ad colors, for both toolkits | [10](#10-colors) |
| Your screens | One XML view, one composable or one line per ad | [9](#9-show-the-ads) |
| Firebase console | This app's parameters, one per ad | [13](#13-firebase-the-parameters-and-sample-values) |

**Contents**

1. [What you need](#1-what-you-need)
2. [Get the plugin onto your computer](#2-get-the-plugin-onto-your-computer)
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
16. [Mediation (optional)](#16-mediation-optional)

## 1. What you need

| What | Where | Example |
|---|---|---|
| Your app's package name | `applicationId` in `app/build.gradle.kts` | `com.example.myapp` |
| An AdMob app ID | AdMob → Apps → your app → App settings | `ca-app-pub-1234567890123456~1234567890` |
| One ad unit ID per ad | AdMob → Apps → your app → Ad units, each of the right format | `ca-app-pub-1234567890123456/1234567890` |
| A Firebase project | [console.firebase.google.com](https://console.firebase.google.com) | |
| The AdsPlugin project | Cloned or opened on your computer | |

You can start without ad unit IDs: an ad without IDs shows Google's test ads, and debug builds always do.

## 2. Get the plugin onto your computer

Until the plugin is published to a shared repository, publish it to your computer's local Maven folder. In a
terminal, **in the AdsPlugin folder**:

```bash
./gradlew publishToMavenLocal
```

(`gradlew publishToMavenLocal` in Windows `cmd`.) This publishes version `1.0.0-rc2`, the `companyAdmob.version` in
AdsPlugin's `gradle.properties`. Run it again whenever the plugin changes.

**In your app**, add `mavenLocal()` to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal() // the ads plugin, from ./gradlew publishToMavenLocal
        google()
        mavenCentral()
    }
}
```

Once the plugin is released to GitHub Packages or the company repository, use that repository instead
([Getting started](GETTING_STARTED.md#1-add-the-repository)). Nothing else changes.

## 3. Connect Firebase

1. In the [Firebase console](https://console.firebase.google.com), create a project, or open yours.
2. **Add app** → Android, with your **exact** package name, then **Register app**.
3. Download `google-services.json` into your project's `app/` folder.
4. Skip the console's SDK steps: the plugin brings the Firebase libraries.

In the root `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "9.1.1" apply false // already there, with your version
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false // already there if you use Compose
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
        viewBinding = true // for the XML screens below
        compose = true // for the Compose screens
        buildConfig = true // for BuildConfig.DEBUG
    }
}

dependencies {
    implementation("com.company:admob-compose:1.0.0-rc2") // the composables, and the XML views with them
    implementation("com.company:admob-firebase:1.0.0-rc2")

    // Your usual XML libraries:
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // And your Compose libraries:
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
}
```

`admob-compose` needs a `compileSdk` of 37 (its Compose version asks for it). With an older one, the build says so:
raise `compileSdk` in `android { }`.

**Check:** *Sync Project with Gradle Files* succeeds. If `com.company:admob-compose` isn't found, run step 2's
command, and check `mavenLocal()` is in `settings.gradle.kts`.

## 5. List your ads: AppAds.kt

Every ad of the app is one line of **one file**, `app/src/main/java/com/example/myapp/ads/AppAds.kt`, for XML and
Compose screens alike:

```kotlin
package com.example.myapp.ads

import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.NativeStyle
import com.company.admob.config.AdDefinition
import com.company.admob.config.AdProfile
import com.company.admob.config.AdSettings

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

- **The entry name is the ad's name everywhere:** in code (`AppAds.HomeBanner`), in XML (`"HomeBanner"`), in
  composables (`AdSlot(AppAds.HomeBanner)`) and in Firebase (a parameter called `HomeBanner`). Never rename one after
  release.
- **The format** is the function: `AdProfile.interstitial`, `rewarded`, `rewardedInterstitial`, `appOpen`, `banner`
  or `native`.
- **Ad unit IDs:** add `ids = listOf("ca-app-pub-…/…")` when you have them, or set them in Firebase only
  ([step 13](#13-firebase-the-parameters-and-sample-values)). No IDs: Google's test ads. Two IDs or more are used in
  turn.
- **One splash ad at most** (`splash = true`). **`excludedActivities`** lists the Activities the app open ad must
  never cover: always your splash. It works by Activity, whether the Activity draws XML or Compose.
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
`showLoading` and `loadingSeconds`. Limits such as `minSecondsBetweenFullScreenAds` count every full-screen ad,
whether an XML or a Compose screen showed it.

## 6. Start everything: the Application class

`MyApp.kt`, next to your activities. Copy it; change only the `package` line and, if you have your own native
layouts, step 3. It's the **only** place the ads library starts: XML and Compose screens share it.

```kotlin
package com.example.myapp

import android.app.Application
import android.content.Context
import com.company.admob.CompanyAdMob
import com.company.admob.config.AdEnvironment
import com.company.admob.config.adMobConfig
import com.company.admob.firebase.FirebaseRemoteConfigProvider
import com.company.admob.firebase.RemoteConfigSession
import com.company.admob.logging.LogLevel
import com.company.admob.views.NativeTemplates
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
        CompanyAdMob.initialize(
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
        if (Premium.isPremium(this)) CompanyAdMob.setAdFree(true)
    }
}

/** Your own purchase check. Here: a saved flag. */
object Premium {
    fun isPremium(context: Context): Boolean =
        context.getSharedPreferences("premium", Context.MODE_PRIVATE).getBoolean("premium", false)

    fun setPremium(context: Context, premium: Boolean) {
        context.getSharedPreferences("premium", Context.MODE_PRIVATE).edit().putBoolean("premium", premium).apply()
        CompanyAdMob.setAdFree(premium)
    }
}
```

- **Debug builds** (`DEVELOPMENT`) show only Google's test ads, and ask for consent as in Europe (step 8).
  **Release builds** (`PRODUCTION`) show your real ads.
- Name the Firebase variable anything **except** `remoteConfig`: inside `adMobConfig { }` that name is the plugin's
  setting, and a variable called that hides it.
- Replace `Premium` with your real purchase check. What matters is `CompanyAdMob.setAdFree(true)` for a premium user,
  at startup and right after a purchase.

## 7. The manifest

In `app/src/main/AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    android:theme="@style/Theme.Material3.DayNight.NoActionBar"
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
    <!-- XML: bottom navigation over Fragments (one of them draws Compose). -->
    <activity android:name=".MainActivity" />
    <!-- Compose, with an XML layout inside. -->
    <activity android:name=".DetailsActivity" />

    <!-- Your AdMob app ID (AdMob → Apps → App settings). This one is Google's sample ID for testing. -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3940256099942544~3347511713" />
</application>
```

Move the `intent-filter` Android Studio put on `MainActivity` to `SplashActivity`. The plugin adds the `INTERNET`
permission itself. The theme must be an AppCompat (Material) theme: the XML screens use `AppCompatActivity`.

**Check:** run a debug build and filter logcat by `CompanyAdMob`: one line `Firebase parameter <Name>: {…}` per ad.

## 8. The splash screen

The splash does everything at app start, and always ends on your main screen:

1. **Consent:** Google's consent form, when the user's region needs it. The splash waits for the answer.
2. **The timer:** a progress bar for `splashSeconds` (13 in `AppAds.settings`); 3 seconds offline or for premium users.
3. **The splash ad,** if it loads before the timer ends.

It never gets stuck, even offline. Write it in the toolkit your app starts with. Here it's XML: copy these two files
as they are, and change the layout's look if you like. (A Compose splash is one file instead:
[the Compose guide's step 8](NEW_APP_COMPOSE.md#8-the-splash-screen). Nothing else on this page changes.)

`res/layout/activity_splash.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/layout/activity_splash.xml -->
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
import com.company.admob.splash.SplashFlow
import com.company.admob.splash.SplashState
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
or emulator wherever you are. Until it's answered it shows on every launch; once answered, it doesn't show again: to
see it again, clear the app's storage (Settings → Apps → MyApp → Storage → Clear storage). Release builds use the
user's real location. The form itself comes from AdMob: publish a GDPR message in **AdMob → Privacy & messaging**, or
no form shows.

**Check:** launch a debug build: the consent form, then the progress bar, then a test ad, then your main screen.

## 9. Show the ads

Two rules cover every ad, in both toolkits:

1. **Ads load when you ask.** Ask when the screen that shows them comes to the front: `AdSlots.load` in `onResume`
   (XML) or in a `LifecycleResumeEffect` (Compose). Banner and native views and composables ask by themselves.
2. **Every show calls you back, whether an ad showed or not.** Continue your app's flow inside the callback.

### Which API where

| | In an XML screen | In a Compose screen |
|---|---|---|
| Banner or native, as Firebase says | `<com.company.admob.views.AdSlotView app:company_admob_placement="HomeBanner" />` | `AdSlot(AppAds.HomeBanner)` |
| Banner | `AdBannerView`, or `attachBanner(owner, AppAds.DetailsBanner.bannerPlacement())` from code | `AdBanner(AppAds.DetailsBanner.bannerPlacement())` |
| Native ad | `<com.company.admob.views.AdNativeView app:company_admob_placement="HomeNative" />` | `AdNative(AppAds.HomeNative.nativePlacement())` |
| Ads in a list | `NativeAdHolder` per row, in a `RecyclerView` (native ads only) | `AdNative(…, slotKey = …)` or `AdBanner(…, slotKey = …)` per item, in a `LazyColumn` |
| Interstitial | `AdSlots.showFullScreen(activity, AppAds.LevelCompleteAd) { … }` | the same, with `LocalActivity.current` |
| Rewarded | `CompanyAdMob.showRewarded(activity, AppAds.ExtraLifeRewarded.rewardedPlacement()) { … }` | the same, or `rememberFullScreenAdLauncher().showRewarded(…)` |
| Replace a banner or native ad with a new one | `binding.homeBanner.load(refresh = true)` | change the composable's `refreshKey` |

Where the two toolkits meet, each ad follows the screen it's on:

| You put… | …in… | Its ads are kept (across rotation and scrolling) by, and destroyed with |
|---|---|---|
| Compose (a `ComposeView`) | a Fragment | the Fragment |
| Compose (a `ComposeView`) | an XML layout of an Activity | the Activity |
| XML (`AndroidView`) | a Compose screen | the XML view, destroyed with the Activity: a rotation loads a new one |

This page's app has all three: Home is an XML Fragment with a Compose native ad inside, Feed is a Compose screen in a
Fragment, and Details is a Compose Activity with an XML layout inside.

### The main screen (XML)

`MainActivity.kt`: bottom navigation over Fragments, as in most XML apps. It also loads the app open ad.

```kotlin
package com.example.myapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.company.admob.AdSlots
import com.example.myapp.ads.AppAds
import com.example.myapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            show(if (item.itemId == R.id.nav_feed) FeedFragment() else HomeFragment())
            true
        }
        if (savedInstanceState == null) show(HomeFragment())
        // The app open ad, for the user's first return to the app. loadAgain loads the next one after each show.
        AdSlots.load(AppAds.AppOpenAd)
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.commit { replace(R.id.fragment_container, fragment) }
    }
}
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/layout/activity_main.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    android:orientation="vertical">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/fragment_container"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_navigation"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:menu="@menu/bottom_navigation" />
</LinearLayout>
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/menu/bottom_navigation.xml -->
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_home"
        android:icon="@android:drawable/ic_menu_view"
        android:title="Home" />
    <item
        android:id="@+id/nav_feed"
        android:icon="@android:drawable/ic_menu_agenda"
        android:title="Feed" />
</menu>
```

### An XML screen: interstitial, rewarded, rewarded interstitial, banner

`HomeFragment.kt`, with its layout below. The rewards go to a ViewModel shared with the rest of the app
([Rewarded](#rewarded)).

```kotlin
package com.example.myapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.company.admob.AdSlots
import com.company.admob.CompanyAdMob
import com.company.admob.compose.AdNative
import com.company.admob.config.nativePlacement
import com.company.admob.config.rewardedInterstitialPlacement
import com.company.admob.config.rewardedPlacement
import com.example.myapp.ads.AppAds
import com.example.myapp.databinding.FragmentHomeBinding

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val coins: CoinsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        val activity = requireActivity()

        binding.levelComplete.setOnClickListener {
            AdSlots.showFullScreen(activity, AppAds.LevelCompleteAd) {
                // Runs whether the ad showed or not: always continue here.
                activity.startActivity(Intent(activity, DetailsActivity::class.java))
            }
        }
        binding.extraLife.setOnClickListener {
            CompanyAdMob.showRewarded(activity, AppAds.ExtraLifeRewarded.rewardedPlacement()) { coins.onRewardResult(it) }
        }
        binding.bonusRound.setOnClickListener {
            // Google requires an intro that offers the reward and lets the user say no.
            AlertDialog.Builder(activity)
                .setMessage("Watch a short ad to earn bonus coins?")
                .setPositiveButton("Watch") { _, _ ->
                    CompanyAdMob.rewardedInterstitial.show(
                        activity,
                        AppAds.BonusRewardedInterstitial.rewardedInterstitialPlacement(),
                    ) { coins.onRewardResult(it) }
                }
                .setNegativeButton("No thanks", null)
                .show()
        }
        // A new banner loads in the background; the shown one stays until it's loaded.
        binding.refreshBanner.setOnClickListener { binding.homeBanner.load(refresh = true) }

        // Compose inside this XML layout: a native ad composable in a ComposeView.
        binding.homeNative.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.homeNative.setContent {
            MaterialTheme { AdNative(AppAds.HomeNative.nativePlacement()) }
        }
    }

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
}
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/layout/fragment_home.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <Button
                android:id="@+id/level_complete"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Level complete" />

            <Button
                android:id="@+id/extra_life"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Extra life" />

            <Button
                android:id="@+id/bonus_round"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Bonus round" />

            <Button
                android:id="@+id/refresh_banner"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Refresh the banner" />

            <!-- Compose inside XML: HomeFragment puts an AdNative in it. -->
            <androidx.compose.ui.platform.ComposeView
                android:id="@+id/home_native"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp" />
        </LinearLayout>
    </ScrollView>

    <!-- The banner, pinned to the bottom. Its size comes from HomeBanner's style. -->
    <com.company.admob.views.AdSlotView
        android:id="@+id/home_banner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:company_admob_placement="HomeBanner" />
</LinearLayout>
```

- An `AdSlotView` or `AdNativeView` in a layout loads by itself, hides itself if no ad comes, never requests a second
  ad while it has one, and destroys its ad with the screen (in a Fragment, with the Fragment's view).
- A `ComposeView` in a Fragment's layout: set `DisposeOnViewTreeLifecycleDestroyed`, as above. Its banners and
  native ads are kept by the Fragment, across rotation, and destroyed with it.
- A banner from code, into an empty `FrameLayout`: `bannerContainer.attachBanner(viewLifecycleOwner,
  AppAds.DetailsBanner.bannerPlacement())` (in an Activity, `this` instead of `viewLifecycleOwner`).

| Situation after `showFullScreen` | What the user sees |
|---|---|
| The ad is loaded | "Loading ad…" for about a second, then the ad. Your callback runs when it closes. |
| It's still loading | "Loading ad…" for at most `loadTimeoutSeconds` (8), then the ad, or your callback if it didn't come. |
| The load failed, or the user is offline | Your callback runs at once. |
| `counter = 2` and this is the 1st call | No ad; your callback runs. The 2nd, 4th, 6th… calls show an ad. |
| A limit (`timerSeconds`, `maxPerSession`, the gap between ads) or a premium user | No ad; your callback runs. |

### Rewarded

Give the reward only for `RewardResult.Earned`, and in a ViewModel: a rotation recreates the screen, not the
ViewModel. This one is the Activity's (`activityViewModels()` in a Fragment, `viewModel()` in a composable of the
same Activity), so every screen of the Activity shares the coins.

```kotlin
package com.example.myapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.admob.CompanyAdMob
import com.company.admob.RewardResult
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
            merge(CompanyAdMob.rewarded.unclaimedResults, CompanyAdMob.rewardedInterstitial.unclaimedResults)
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

In a Compose screen, `rememberFullScreenAdLauncher()` shows full-screen ads without passing an Activity:
`launcher.showRewarded(AppAds.ExtraLifeRewarded.rewardedPlacement(), coins::onRewardResult)`. See
[the Compose guide](NEW_APP_COMPOSE.md#rewarded), with a Compose intro dialog for the rewarded interstitial.

### App open

It shows by itself when the user comes back to the app, never over `excludedActivities`. `MainActivity` loads it
once: `AdSlots.load(AppAds.AppOpenAd)`. With `loadAgain = true`, the next one loads after each show. It covers XML
and Compose Activities alike.

### A Compose screen inside a Fragment: ads in a LazyColumn

`FeedFragment.kt`: a screen already moved to Compose, still reached through the XML navigation. The Fragment's view
is a `ComposeView`; one `AdNative` per ad item, each with its own `slotKey`.

```kotlin
package com.example.myapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.company.admob.compose.AdNative
import com.company.admob.config.nativePlacement
import com.example.myapp.ads.AppAds

class FeedFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            // The composition, and its ads, end with the Fragment's view.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    LazyColumn {
                        for (position in 0 until 30) {
                            if (position % 10 == 9) {
                                // Every 10th row is an ad; its position is its slotKey.
                                item(key = "ad_$position") {
                                    AdNative(AppAds.FeedNative.nativePlacement(), Modifier.fillMaxWidth(), slotKey = position)
                                }
                            } else {
                                item(key = "article_$position") { Text("Article $position", Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
}
```

The ads are kept by the Fragment: scrolling away and back, or rotating, shows the same ad without reloading it. They're
destroyed when the Fragment is gone for good. An `AdBanner(…, slotKey = …)` item works the same way.

In an XML list, a `RecyclerView`, keep one `NativeAdHolder` per ad row in the ViewModel instead
([the XML guide](NEW_APP_XML.md#native-ads-in-a-recyclerview)).

### An XML layout inside a Compose screen

`DetailsActivity.kt`: a Compose screen that still uses an XML layout for its article. The banner is a composable;
the native ad is an `AdNativeView` in the XML, shown through `AndroidView`.

```kotlin
package com.example.myapp

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.company.admob.compose.AdBanner
import com.company.admob.config.bannerPlacement
import com.example.myapp.ads.AppAds
import com.example.myapp.databinding.DetailsArticleBinding

class DetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Text("Details", style = MaterialTheme.typography.headlineSmall)
                            // The XML part, with its AdNativeView.
                            AndroidView(
                                factory = { context -> DetailsArticleBinding.inflate(LayoutInflater.from(context)).root },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // The banner, a composable. Home loaded it ahead: it shows at once.
                        AdBanner(AppAds.DetailsBanner.bannerPlacement())
                    }
                }
            }
        }
    }
}
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/layout/details_article.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="The article's text." />

    <!-- A native ad in your own layout (step 11). Loads for the hosting Activity; GONE if nothing loads. -->
    <com.company.admob.views.AdNativeView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        app:company_admob_placement="PromoNative" />
</LinearLayout>
```

An XML ad view inside `AndroidView` belongs to the Activity: it's destroyed with it, and a rotation (which recreates
the view) loads a new ad. Where a native ad must survive rotation in a Compose screen, use `AdNative` instead.

### Load banners and native ads before their screen

`AdSlots.load` works for banners and native ads too, from either toolkit, for either toolkit: load the next screen's
ads early, and its views or composables show them the moment it opens.

```kotlin
AdSlots.load(AppAds.DetailsBanner)
AdSlots.load(AppAds.HomeNative)
```

| When the view or composable appears | What happens |
|---|---|
| The ad is loaded | It shows at once. No shimmer, no new request. |
| It's still loading | The shimmer, then that same ad when it arrives. No second request. |
| It wasn't loaded, or failed | It loads it now, as it would without `AdSlots.load`. |

A banner loaded ahead is sized for the full screen width; a banner of another width loads its own.

### Native styles

A native ad's `style` picks its layout, in XML views and composables alike. Change it in `AppAds`, or from Firebase
with `"style"`:

| Style | Layout |
|---|---|
| `small_button_end` | Icon, "Ad" badge, headline and body, button at the end of the row. No media. |
| `small_button_bottom` | Icon, badge, headline and body, full-width button below. No media. |
| `large_button_bottom` (default) | Icon, badge, headline and body, then media, then a full-width button. |
| `large_compact` | Like `large_button_bottom`, with a larger icon and shorter media. |
| `media_start` | Media at the start; badge, headline, body and button beside it. |

### Remove ads

After a purchase, `Premium.setPremium(context, true)`: every ad stops at once, on every screen, and the next launch
has a 3-second splash without consent or ads.

### Privacy options

Google requires a way to change consent later, where consent applies. A button that shows only then, in an XML
Fragment:

```kotlin
binding.privacyOptions.setOnClickListener { CompanyAdMob.privacy.showPrivacyOptions(requireActivity()) }
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        CompanyAdMob.privacy.isPrivacyOptionsRequired.collect { binding.privacyOptions.isVisible = it }
    }
}
```

In a composable: `val required by CompanyAdMob.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()`,
then a button shown `if (required)` ([the Compose guide](NEW_APP_COMPOSE.md#privacy-options)).

## 10. Colors

**In the app.** One file, `res/values/ads_style.xml`. Each value replaces the plugin's value of the same name, in
every template, whether an XML view or a composable shows it: both draw native ads with the same Android views.

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/values/ads_style.xml -->
<!-- The ads' look in this app: each value replaces the plugin's value of the same name. -->
<resources>
    <color name="company_admob_native_attribution">#FFC107</color>
    <color name="company_admob_native_attribution_text">#FFFFFF</color>
    <color name="company_admob_native_cta">#FF6D00</color>
    <color name="company_admob_native_cta_end">#FFA000</color>
    <color name="company_admob_native_cta_text">#FFFFFF</color>
    <color name="company_admob_shimmer_card">#FFF5F5F5</color>
</resources>
```

| Name | What |
|---|---|
| `company_admob_native_cta`, `company_admob_native_cta_end` | The button: a gradient from the first to the second. Set only the first for a solid button. |
| `company_admob_native_cta_text` | The button's text |
| `company_admob_native_attribution`, `_attribution_text` | The "Ad" badge, and its text |
| `company_admob_native_background`, `_stroke` | The card's fill (transparent) and border |
| `company_admob_native_headline`, `_body` | The texts |
| `company_admob_shimmer_card`, `_shimmer_base`, `_shimmer_highlight` | The loading shimmer: the ad area, its blocks, the moving shine |

Dark mode: the same names in `res/values-night/ads_style.xml`. Sizes (text sizes, corners, the button's height…) are
`dimen`s you override the same way, in `sdp` and `ssp` if you like: the full list is in
[Native: Styling](NATIVE.md#styling). The texts' default colors follow the Activity's XML theme, also in Compose
screens.

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

When no style fits, use your own layout: an XML layout, which XML views and composables both use. It's three small
changes, and everything else (loading, the shimmer, the colors, Firebase, clicks and impressions) works with it as with
a built-in style.

**1. Write the layout,** for example `res/layout/ad_promo.xml`. Rules:

- Ordinary views only: **no `NativeAdView`, no `MediaView`**. The plugin wraps your layout and adds the media itself.
  (Converting an old layout: delete those two tags, keep what's inside, and turn the `MediaView` into a `FrameLayout`.)
- Give the views the plugin fills these IDs, written `@id/…` (they're the plugin's; no `+`):

| ID | View | |
|---|---|---|
| `company_admob_native_headline` | `TextView` | Required |
| `company_admob_native_attribution` | `TextView` saying "Ad" | Required by Google |
| `company_admob_native_body` | `TextView` | |
| `company_admob_native_cta` | `android.widget.Button` | Write the full name, so a Material theme doesn't repaint it |
| `company_admob_native_icon` | `ImageView` | |
| `company_admob_native_media` | an empty `FrameLayout`, at least 120dp tall | The image or video goes inside |
| `company_admob_native_advertiser`, `_stars` (`RatingBar`), `_price`, `_store` | | Optional extras |

- Leave the top-right corner free (about 24dp): the SDK puts the AdChoices icon there.
- Views an ad has no content for are hidden. Everything else (sizes, backgrounds, fonts) is up to you. Use the
  plugin's colors (`@color/company_admob_native_cta_text`…) and drawables if you want Firebase colors and your
  `ads_style.xml` to apply to it too.

A complete example:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/layout/ad_promo.xml -->
<!--
    Your own native ad layout. Ordinary views only (no NativeAdView, no MediaView): the plugin adds those.
    The views the plugin fills use its IDs: @id/company_admob_native_... Everything else is up to you.
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
            android:id="@id/company_admob_native_icon"
            android:layout_width="@dimen/_36sdp"
            android:layout_height="@dimen/_36sdp"
            android:contentDescription="@null" />

        <TextView
            android:id="@id/company_admob_native_attribution"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/_6sdp"
            android:background="@drawable/company_admob_native_attribution_background"
            android:paddingHorizontal="@dimen/_4sdp"
            android:text="Ad"
            android:textColor="@color/company_admob_native_attribution_text"
            android:textSize="@dimen/_8ssp"
            android:textStyle="bold" />

        <TextView
            android:id="@id/company_admob_native_headline"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/_6sdp"
            android:layout_weight="1"
            android:maxLines="2"
            android:textSize="@dimen/_12ssp"
            android:textStyle="bold" />
    </LinearLayout>

    <TextView
        android:id="@id/company_admob_native_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/_4sdp"
        android:maxLines="2"
        android:textSize="@dimen/_10ssp" />

    <!-- The image or video goes in here. At least 120dp tall: the video minimum. -->
    <FrameLayout
        android:id="@id/company_admob_native_media"
        android:layout_width="match_parent"
        android:layout_height="140dp"
        android:layout_marginTop="@dimen/_6sdp" />

    <!-- Written as android.widget.Button, so a Material theme keeps its background. -->
    <android.widget.Button
        android:id="@id/company_admob_native_cta"
        android:layout_width="match_parent"
        android:layout_height="@dimen/_34sdp"
        android:layout_marginTop="@dimen/_6sdp"
        android:background="@drawable/company_admob_native_cta_background"
        android:textAllCaps="false"
        android:textColor="@color/company_admob_native_cta_text"
        android:textSize="@dimen/_12ssp"
        android:textStyle="bold" />
</LinearLayout>
```

And its card, `res/drawable/ad_promo_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/drawable/ad_promo_card.xml -->
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
| Show an ad in it by default | In `AppAds`: `PromoNative(AdProfile.native(layout = "promo"))`, then `app:company_admob_placement="PromoNative"` on an `AdNativeView` or `AdSlotView` (as in `details_article.xml`), or `AdSlot(AppAds.PromoNative)` in Compose. |
| Switch any native ad to it from Firebase | That ad's value: `{ "style": "promo" }`. Back with `{ "style": "large_button_bottom" }`. |
| Use it in one place only | XML: `app:company_admob_layout="@layout/ad_promo"` on an `AdNativeView`. Compose: `AdNative(placement, layout = NativeTemplates.registered("promo"))`. |

Its loading shimmer is built from your layout automatically: the whole card shimmers, with a block over each view.

To **use the same layout in another project**, copy the XML file (and any drawable it uses), and the `register` line.
Nothing else.

## 12. Your own Firebase values

Anything else you want to change from Firebase (a flag, a number, a text, JSON) goes in one `object`, with the value
to use until Firebase sets one:

```kotlin
package com.example.myapp.ads

import com.company.admob.firebase.RemoteValue
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

Read `.value` anywhere, in an XML screen or a composable: `if (AppConfig.showRateDialog.value) showRateDialog()`. It
never waits. A missing or wrong value gives your default. More in
[Remote configuration](REMOTE_CONFIG.md#your-apps-own-values).

## 13. Firebase: the parameters and sample values

Each ad has one Remote Config parameter, named like its entry in `AppAds`, of type **JSON**, plus `AdSettings` for
what they share. A value needs only the fields you change; the rest keep `AppAds`'s values. One parameter controls
its ad on XML and Compose screens alike.

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
| A native ad instead of a banner | the banner's | `{ "format": "native", "style": "large_button_bottom", "adUnitIds": ["<a native ad unit>"] }` (shown by `AdSlotView` or `AdSlot`) |
| A 13-second splash | `AdSettings` | `{ "splashSeconds": 13 }` |
| No shimmer or "Loading ad…" for one ad | the ad's | `{ "showLoading": false }` |

A value with a mistake is ignored (with a warning in the log), and the ad keeps its previous settings. Every field is
in [Ad profiles](AD_PROFILES.md#the-json).

## 14. Test it

A debug build on a phone or an emulator:

- [ ] Logcat (`CompanyAdMob`): `Firebase parameter <Name>: {…}` per ad, then `Remote Config fetched`.
- [ ] The consent form (first launch), the progress bar, a test ad, then your main screen.
- [ ] Every ad is labelled "Test Ad". Full-screen ads show "Loading ad…" first; banners and native ads, a shimmer,
  in XML and Compose screens.
- [ ] Scrolling or rotating doesn't reload a banner or native ad drawn by a composable (the Compose feed, Home's
  `ComposeView`), and rotating doesn't restart the splash.
- [ ] Your `ads_style.xml` colors show on the native ads of both toolkits, and your own layout shows where you use it.
- [ ] A Firebase change (for example `{ "style": "media_start" }` on a native ad) shows after one or two relaunches.
- [ ] In airplane mode, the splash lasts 3 seconds and the app works without ads.

## 15. Before release

- [ ] Every ad in `AppAds` has its real ad unit IDs, in `AppAds` or in Firebase.
- [ ] The manifest has **your** AdMob app ID.
- [ ] A GDPR message is published in AdMob → Privacy & messaging.
- [ ] `app-ads.txt` is set up in AdMob.
- [ ] A release build shows real ads (a new ad unit can take a few hours). Don't click your own ads.
- [ ] A premium user sees no ad and a 3-second splash.

## 16. Mediation (optional)

Ads from other networks (AppLovin, Meta…) through AdMob Mediation need no code: add the network's adapter to
`app/build.gradle.kts`, and exclude Google's old ads SDK, which every adapter still depends on and which clashes with
the plugin's:

```kotlin
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

dependencies {
    implementation("com.google.ads.mediation:applovin:13.6.4.2")
}
```

Then set up the network in AdMob → Mediation. `sample-mixed` builds this way with `-PwithMediation=true`, and its
Settings tab opens Ad Inspector, which lists the adapters. More in [Mediation](MEDIATION.md).

Problems? See [Troubleshooting](TROUBLESHOOTING.md). In depth: [XML and Views](XML.md), [Jetpack Compose](COMPOSE.md),
[Ad profiles](AD_PROFILES.md), [Native ads](NATIVE.md), [Splash screen](SPLASH.md), [Consent](CONSENT.md),
[Remote configuration](REMOTE_CONFIG.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-30
