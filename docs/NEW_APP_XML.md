# Adding ads to a new Kotlin + XML app

This page takes a brand-new Android Studio project (Kotlin, XML layouts, no Compose) to a finished ad setup:

- consent on the splash screen;
- a splash ad;
- interstitial, rewarded, rewarded interstitial and app open ads;
- banners and native ads;
- a "remove ads" purchase;
- every ad, and any other value you choose, controlled from Firebase without a new release.

Follow the steps in order. Each one ends with a way to check that it worked.

Your app needs only two of the plugin's modules, and Gradle downloads the third by itself:

| Module | Why |
|---|---|
| `admob-views` | Banners and native ads in XML layouts. |
| `admob-firebase` | Firebase Remote Config and Analytics. |
| `admob-core` | Everything else. Comes automatically with both modules above. |

Don't add `admob-compose`, `admob-testing`, the sample apps, `play-services-ads` or `ads-mobile-sdk`. The plugin
brings the Google Mobile Ads and consent (UMP) SDKs itself.

**Contents**

1. [What you need](#1-what-you-need)
2. [Get the plugin onto your computer](#2-get-the-plugin-onto-your-computer)
3. [Connect Firebase](#3-connect-firebase)
4. [Add the dependencies](#4-add-the-dependencies)
5. [List your ads: AppAds.kt](#5-list-your-ads-appadskt)
6. [Start everything: the Application class](#6-start-everything-the-application-class)
7. [The manifest](#7-the-manifest)
8. [The splash screen: consent, timer, splash ad](#8-the-splash-screen-consent-timer-splash-ad)
9. [Show the ads](#9-show-the-ads)
10. [Your own Firebase values](#10-your-own-firebase-values)
11. [Firebase: the parameters and sample values](#11-firebase-the-parameters-and-sample-values)
12. [Test it](#12-test-it)
13. [Before release](#13-before-release)

## 1. What you need

| What | Where | Example |
|---|---|---|
| Your app's package name | `applicationId` in `app/build.gradle.kts` | `com.example.myapp` |
| An AdMob app ID | AdMob → Apps → your app → App settings | `ca-app-pub-1234567890123456~1234567890` |
| One ad unit ID per ad | AdMob → Apps → your app → Ad units. Create each with the right format. | `ca-app-pub-1234567890123456/1234567890` |
| A Firebase project | [console.firebase.google.com](https://console.firebase.google.com) | |
| This plugin's project | The AdsPlugin repository, opened or cloned on your computer | |

You can start without ad unit IDs. An ad without IDs shows Google's test ads, and debug builds always show test ads.

## 2. Get the plugin onto your computer

Until the plugin is released to a shared repository, publish it to your computer's local Maven folder (`~/.m2`).
In a terminal, **in the AdsPlugin project folder**, run:

```bash
./gradlew publishToMavenLocal
```

(On Windows `cmd`, run `gradlew publishToMavenLocal`.) This publishes version `1.0.0-rc1`, the version set in
AdsPlugin's `gradle.properties` (`companyAdmob.version`). Run the command again whenever you update the plugin.

Then, **in your app's project**, open `settings.gradle.kts` and add `mavenLocal()` to the repositories:

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

Once the plugin is released to GitHub Packages or the company repository, replace `mavenLocal()` with that
repository ([Getting started](GETTING_STARTED.md#1-add-the-repository)). Nothing else changes.

## 3. Connect Firebase

1. In the [Firebase console](https://console.firebase.google.com), create a project (or open yours). Google
   Analytics can stay on.
2. **Add app** → Android. Enter your **exact** package name (`com.example.myapp`), then **Register app**.
3. Download `google-services.json` and put it in your project's `app/` folder, next to `app/build.gradle.kts`.
4. Skip the SDK steps the console shows next: the plugin adds the Firebase libraries. You only add the Gradle plugin
   below.

In the **project's** `build.gradle.kts` (the one at the root):

```kotlin
plugins {
    id("com.android.application") version "9.1.1" apply false // already there, with your version
    id("com.google.gms.google-services") version "4.5.0" apply false
}
```

If your project uses a version catalog (`gradle/libs.versions.toml`), add the plugin there instead, as Android
Studio does for its other plugins.

## 4. Add the dependencies

In **`app/build.gradle.kts`**:

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
        viewBinding = true // optional, used in the examples below
        buildConfig = true // needed for BuildConfig.DEBUG
    }
}

dependencies {
    implementation("com.company:admob-views:1.0.0-rc1")
    implementation("com.company:admob-firebase:1.0.0-rc1")

    // Your usual AndroidX libraries (Android Studio adds most of them):
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
}
```

**Check:** *Sync Project with Gradle Files* succeeds. If `com.company:admob-views` isn't found, step 2's
`publishToMavenLocal` didn't run, or `mavenLocal()` is missing.

## 5. List your ads: AppAds.kt

All your ads live in **one file**. Create `app/src/main/java/com/example/myapp/ads/AppAds.kt`, with one entry per
place that shows an ad:

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
    HomeNative(AdProfile.native(style = NativeStyle.MEDIUM)),
    FeedNative(AdProfile.native(style = NativeStyle.SMALL, loadAgain = true)),
    ;

    companion object {
        /** Settings shared by every ad (the Firebase parameter "AdSettings"). */
        val settings = AdSettings(splashSeconds = 13, minSecondsBetweenFullScreenAds = 30)
    }
}
```

The rules:

- **The entry name is the ad's name everywhere:** in code (`AppAds.HomeBanner`), in XML (`"HomeBanner"`) and in
  Firebase (a parameter called `HomeBanner`). Never rename an entry after release.
- **The format** comes from the function: `AdProfile.interstitial`, `rewarded`, `rewardedInterstitial`, `appOpen`,
  `banner` or `native`.
- **Ad unit IDs:** add `ids = listOf("ca-app-pub-…/…")` when you have them. Leave `ids` out to get Google's test
  ads. Two or more IDs are used in turn, one per load. You can also set the IDs later, from Firebase only
  ([step 11](#11-firebase-the-parameters-and-sample-values)).
- **One splash ad at most** (`splash = true`).
- **`excludedActivities`** lists screens the app open ad must never cover. Always include your splash screen.
- Every argument is optional. The main ones:

| Argument | Formats | Meaning |
|---|---|---|
| `counter = 3` | interstitial, rewarded interstitial | Shows an ad only on every 3rd `show` call. |
| `timerSeconds = 60` | interstitial, rewarded interstitial, app open | At least 60 s between two shows of this ad. |
| `maxPerSession = 5` | full-screen ads | At most 5 shows per app launch. |
| `loadAgain = true` | all but banners | Loads the next ad right after each show. Use it for ads shown again and again. |
| `loadTimeoutSeconds = 8` | full-screen ads | How long a show waits for an ad that isn't loaded yet. Default 8. |
| `showLoading = false` | all | No "Loading ad…" screen before this full-screen ad, and no shimmer on this banner or native ad. |
| `style = …` | banner, native | Banner size or native template (below). |
| `enabled = false` | all | Turns this ad off. |

Banner `style`: `BannerSize.AnchoredAdaptive` (default, full width), `BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM)`,
`BannerSize.MediumRectangle` (300×250), `BannerSize.Standard` (320×50). Native `style`: one of 11 layouts, for example
`NativeStyle.SMALL_BUTTON_BOTTOM` or `NativeStyle.SIDE_MEDIA_BUTTON_TOP` ([the list](NATIVE.md#templates-and-custom-layouts)),
or `AdProfile.native(layout = "promo")` for your own layout ([step 9](#native-styles-colors-and-your-own-layouts)).

`AdSettings` holds what every ad shares. The main settings:

- `splashSeconds`: the splash timer, default 11.
- `minSecondsBetweenFullScreenAds`: the gap between any two full-screen ads.
- `maxInterstitialsPerSession`: at most this many interstitials per launch.
- `loadTimeoutSeconds`, `showLoading` and `loadingSeconds`: the defaults of the arguments above.

## 6. Start everything: the Application class

Create `MyApp.kt` next to your activities:

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

        // 3. A user who bought "remove ads": no ads at all.
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

- **Debug builds** (`DEVELOPMENT`) show only Google's test ads, whatever IDs you set. You can't click your own real
  ads by mistake. They also ask for consent as if you were in Europe ([step 8](#8-the-splash-screen-consent-timer-splash-ad)).
- **Release builds** (`PRODUCTION`) show your real ads.
- Name the Firebase variable anything **except** `remoteConfig`. Inside `adMobConfig { }`, `remoteConfig` is the
  plugin's setting, and a variable with that name hides it.
- Replace `Premium` with your real purchase check (Google Play Billing). What matters is calling
  `CompanyAdMob.setAdFree(true)` for a premium user, at startup and right after a purchase.

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

Remove the `intent-filter` from `MainActivity` if Android Studio put it there. You don't need to add the `INTERNET`
permission: the plugin adds it.

**Check:** run the app in debug and filter logcat by `CompanyAdMob`. You see one line `Firebase parameter <Name>: {…}`
per ad. Without the app ID, the log says `APP_ID_MISSING` instead.

## 8. The splash screen: consent, timer, splash ad

The splash screen does everything at app start, in this order, and always ends on your main screen:

1. **Consent.** It shows Google's consent form when the user's region requires it (Europe, the UK, some US states).
2. **The timer.** A progress bar runs for `splashSeconds` (13 in `AppAds.settings`). Offline or premium users get 3
   seconds.
3. **The splash ad.** It shows if it loads before the timer ends.

The splash screen never gets stuck, even offline. Premium users skip consent and the ad.

**Layout** `res/layout/activity_splash.xml`: your logo and name, and a progress bar, hidden at first:

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

**Code** `SplashActivity.kt`: a small ViewModel (so a rotation doesn't restart the splash) and the Activity:

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

**Consent in debug builds.** A debug build always behaves as if you were in Europe, so the consent form shows on the
splash on your emulator or phone, wherever you are. Once you answer it, it doesn't show again. To see it again,
clear the app's storage (Settings → Apps → MyApp → Storage → Clear storage), or call
`CompanyAdMob.privacy.resetConsentForTesting()` from a debug button and restart the app. To test with your real
location instead, add `privacy { debugBuildGeography = DebugGeography.DISABLED }` inside `adMobConfig { }`.
Release builds always use the user's real location.

The form itself comes from AdMob: publish a GDPR message in **AdMob → Privacy & messaging** for your app, or no form
shows.

**Check:** launch a debug build. The consent form shows first. The progress bar then runs, and a test ad shows before
your main screen. Launch again: no form this time, the bar runs, and the test ad shows again.

## 9. Show the ads

Two rules cover every ad:

1. **Ads load only when you ask.** Ask when the screen that shows them opens (`AdSlots.load` in `onResume`). Banners
   and native ads in a layout ask by themselves.
2. **Every show calls you back, whether an ad showed or not.** Always continue your app's flow inside the callback.

### Interstitial

Load in `onResume`, and show where the ad belongs:

```kotlin
override fun onResume() {
    super.onResume()
    // Load this screen's full-screen ads, so they are ready when a button is tapped.
    // Does nothing for an ad that is already loaded or loading.
    AdSlots.load(AppAds.LevelCompleteAd)
    AdSlots.load(AppAds.ExtraLifeRewarded)
    AdSlots.load(AppAds.BonusRewardedInterstitial)
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

What happens on a show:

| Situation | What the user sees |
|---|---|
| The ad is loaded | "Loading ad…" for about 1 second, then the ad. Your callback runs when it closes. |
| It's still loading | "Loading ad…" while it finishes, for at most `loadTimeoutSeconds` (8). Then the ad, or your callback if it didn't come in time. An ad that arrives later is kept for the next show. |
| The load failed, or the user is offline | Your callback runs at once. |
| `counter = 2` and this is the 1st call | No ad; your callback runs at once. The 2nd, 4th, 6th… calls show an ad. |
| `timerSeconds`, `maxPerSession`, the gap between ads, or a premium user | No ad; your callback runs at once. |

`result` says what happened (`FullScreenResult.Dismissed` or `NotShown(reason)`), if you want to log it. You don't
need it to continue.

### Rewarded

Give the reward only for `RewardResult.Earned`:

```kotlin
binding.extraLife.setOnClickListener {
    CompanyAdMob.showRewarded(this, AppAds.ExtraLifeRewarded.rewardedPlacement()) { result ->
        when (result) {
            is RewardResult.Earned -> giveCoins(result.reward.amount)
            RewardResult.NotEarned -> toast("Watch the whole ad to get the reward")
            is RewardResult.Failed -> toast("No ad right now, try again later")
        }
    }
}
```

To disable the button while no ad is ready, collect
`CompanyAdMob.rewarded.availability(AppAds.ExtraLifeRewarded.rewardedPlacement())` ([Rewarded](REWARDED.md)).

### Rewarded interstitial

Google requires an intro screen first: tell the user what they will get, with a way to say no. Show the ad only
after they accept:

```kotlin
CompanyAdMob.rewardedInterstitial.show(this, AppAds.BonusRewardedInterstitial.rewardedInterstitialPlacement()) { result ->
    if (result is RewardResult.Earned) giveCoins(result.reward.amount)
}
```

### App open

It shows by itself when the user comes back to the app, never over the screens in `excludedActivities`. Load it
once, when your main screen opens:

```kotlin
// MainActivity.onCreate
AdSlots.load(AppAds.AppOpenAd)
```

With `loadAgain = true` (as in `AppAds`), a new one loads after each show, ready for the next return. It waits 4
minutes between two shows (`timerSeconds`).

### Banner in a layout

Put an `AdSlotView` in the XML with the ad's name. The size comes from the ad's `style`:

```xml
<com.company.admob.views.AdSlotView
    android:id="@+id/home_banner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:company_admob_placement="HomeBanner" />
```

That's all:

- **Loading:** it loads by itself and shows a grey shimmer shaped like the ad until it arrives.
- **No ad:** if the load fails, it hides itself (`GONE`).
- **Cleanup:** it destroys the ad with the screen.
- **One ad at a time:** it never asks for a second ad while it has one. Rotating the screen or coming back to it
  keeps the same ad.

Put the root layout's `xmlns:app="http://schemas.android.com/apk/res-auto"` in place for the `app:` attribute.

**Refresh:** to replace the banner with a new one, call `load(refresh = true)`. The current banner stays on screen
(no shimmer) until the new one has loaded; if the new one fails, the current one stays.

```kotlin
binding.refreshBanner.setOnClickListener { binding.homeBanner.load(refresh = true) }
```

### Banner from code

Put an empty `FrameLayout` in the layout (`@+id/banner_container`) and attach the banner to it:

```kotlin
class DetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        // A banner added from code into an empty FrameLayout. It's destroyed with this screen.
        findViewById<FrameLayout>(R.id.banner_container).attachBanner(this, AppAds.DetailsBanner.bannerPlacement())
    }
}
```

In a Fragment, pass `viewLifecycleOwner` instead of `this`.

### Native ad in a layout

The same `AdSlotView`, with a native ad's name. The layout comes from its `style`:

```xml
<com.company.admob.views.AdSlotView
    android:id="@+id/home_native"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:company_admob_placement="HomeNative" />
```

Refresh works the same way: `binding.homeNative.load(refresh = true)`.

`AdSlotView` also follows a format swap from Firebase: if you change `HomeBanner` to a native ad in the console, the
same view shows a native ad, with no new release ([step 11](#swap-a-format)).

### Native styles, colors and your own layouts

A native ad's `style` picks its layout. The 11 built-in ones:

| Firebase `"style"` | Layout |
|---|---|
| `small` | Icon, headline and button in one row |
| `medium` | Header, body, media, store, price and button |
| `large` | Media first, then everything else |
| `small_button_bottom` | Icon, headline and body, button below. No media. |
| `small_button_top` | Button on top, then icon, headline and body. No media. |
| `small_button_end` | Icon, headline and body, button at the end of the row. No media. |
| `large_button_bottom` | Icon, headline and body, media, button below |
| `large_button_top` | Button on top, icon, headline and body, media |
| `large_compact` | Like `large_button_bottom`, with shorter media |
| `side_media_button_bottom` | Text beside the media, button below |
| `side_media_button_top` | Button on top, text beside the media |

**Colors and shapes.** Every color, size and shape is a resource you override by declaring one with the same name in
your app. For example, a golden "Ad" badge and an orange button, in `res/values/ads_style.xml`:

```xml
<resources>
    <color name="company_admob_native_attribution">#FFC107</color>
    <color name="company_admob_native_attribution_text">#FFFFFF</color>
    <color name="company_admob_native_cta">#FF6D00</color>
    <color name="company_admob_native_cta_text">#FFFFFF</color>
    <color name="company_admob_native_background">#FFFFFF</color>
    <dimen name="company_admob_native_card_corner_radius">12dp</dimen>
</resources>
```

Every resource you can override is listed in [Native: Styling](NATIVE.md#styling). You can also replace a whole shape:
the card background is the drawable `company_admob_native_card`, the button `company_admob_native_cta_background`, and
the badge `company_admob_native_attribution_background`.

**Your own layout.** Design any layout with ordinary views (no `NativeAdView` or `MediaView`: the plugin adds them).
Give its views these IDs: `@id/company_admob_native_headline`, `_body`, `_cta` (write it as
`<android.widget.Button>`), `_icon`, `_media` (an empty `FrameLayout`, at least 120dp tall) and `_attribution` (a
`TextView` saying "Ad"). Then register it by name in `MyApp.onCreate`:

```kotlin
NativeTemplates.register("promo", R.layout.my_native_ad)
```

Use it by default for an ad (`HomeNative(AdProfile.native(layout = "promo"))`), or switch any native ad to it from
Firebase: `{ "style": "promo" }`. Its shimmer is built from your layout automatically: the whole card shimmers, with a
block over each view inside it. [Native: your own layout](NATIVE.md#your-own-layout-picked-by-name) has the details.

### Native ads in a RecyclerView

Keep one `NativeAdHolder` per ad row in a ViewModel, and bind it to the row's container. Scrolling back shows the
same ad; it never loads a new one:

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

The ad row's layout is just a container:

```xml
<!-- A list row for a native ad: the ad is drawn inside this container. -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/ad_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="8dp" />
```

and in the adapter's `onBindViewHolder`:

```kotlin
// Binding again (scrolling back) shows the same ad; it never loads a new one.
viewModel.adFor(position).bindTo(holder.itemView.findViewById<FrameLayout>(R.id.ad_container), owner)
```

`owner` is the Activity, or `viewLifecycleOwner` in a Fragment. With `loadAgain = true` on `FeedNative`, the next ad
starts loading as soon as one is shown, so the next ad row gets it faster.

### Remove ads (premium)

After a successful purchase:

```kotlin
Premium.setPremium(this, true) // saves it and calls CompanyAdMob.setAdFree(true)
```

Every ad stops at once: banners and native ads hide, and full-screen shows call back without an ad. On the next
launch, the splash lasts 3 seconds with no consent form and no ad.

### Privacy options button

When the user is in a region with consent, Google requires a way to change their choice later. Add a button (in
Settings, say) that shows only when needed:

```kotlin
binding.privacyOptions.setOnClickListener { CompanyAdMob.privacy.showPrivacyOptions(this) }
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        CompanyAdMob.privacy.isPrivacyOptionsRequired.collect { binding.privacyOptions.isVisible = it }
    }
}
```

## 10. Your own Firebase values

Any other value you want to change from Firebase (a flag, a number, a text, a JSON object) goes in one `object`,
with the value to use until Firebase sets one:

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

Read `.value` anywhere:

```kotlin
binding.welcome.text = AppConfig.welcomeText.value
if (level >= AppConfig.freeLevels.value) openPaywall()
if (AppConfig.showRateDialog.value) showRateDialog()
val chatEnabled = AppConfig.features.value.optBoolean("chat")
```

- `.value` never waits. It's the value from the latest fetch: this launch's once it lands, before that the last
  launch's, and your default on the very first launch.
- A parameter missing from Firebase, or with a wrong value (text in a number), gives your default.
- For a JSON object with a fixed shape, read it into your own class with `RemoteValue.custom`
  ([Remote configuration](REMOTE_CONFIG.md#your-apps-own-values)).

## 11. Firebase: the parameters and sample values

Each ad has one Remote Config parameter, named exactly like its entry in `AppAds`, of type **JSON**. One more,
`AdSettings`, holds the shared settings. A parameter only needs the fields you want to change. The rest keep the
values from `AppAds`.

**To add a parameter:** Firebase console → **Remote Config** → **Add parameter** (or **Create configuration** the
first time).

1. **Parameter name:** the entry name, for example `LevelCompleteAd`.
2. **Data type:** JSON.
3. **Default value:** the JSON.
4. **Save**, and when all are added, **Publish changes**.

Users get the new values on their next launch. Debug builds, with `minimumFetchIntervalInSeconds = 0`, do too.
Release builds may keep Firebase's cached values for up to 12 hours.

Below is a complete value for each kind of ad. Replace each `ca-app-pub-XXXXXXXXXXXXXXXX/…` with your real ad unit
ID of **that format**, or remove `adUnitIds` to keep the IDs from `AppAds`. An ID that isn't valid is skipped.

### Splash ad (`SplashAd`)

```json
{
  "format": "interstitial",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/1111111111"],
  "splashTimeoutSeconds": 8
}
```

`splashTimeoutSeconds` is how long the splash waits for this ad after the timer. The timer itself is `splashSeconds`
in `AdSettings`. `"splash": true` can only be set in `AppAds`.

### Interstitial (`LevelCompleteAd`)

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

### Rewarded (`ExtraLifeRewarded`)

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

### Rewarded interstitial (`BonusRewardedInterstitial`)

```json
{
  "format": "rewarded_interstitial",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/5555555555"],
  "counter": 0,
  "timerSeconds": 0,
  "maxPerSession": 0,
  "loadAgain": false,
  "loadTimeoutSeconds": 8,
  "showLoading": true,
  "loadingSeconds": 1
}
```

### App open (`AppOpenAd`)

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

`excludedActivities` can only be set in `AppAds`.

### Banner (`HomeBanner`, `DetailsBanner`)

```json
{
  "format": "banner",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/7777777777"],
  "style": "adaptive",
  "showLoading": true
}
```

`style`: `adaptive`, `collapsible_bottom`, `collapsible_top`, `rectangle` (300×250), `standard` (320×50) or
`inline_adaptive`.

### Native (`HomeNative`, `FeedNative`)

```json
{
  "format": "native",
  "enabled": true,
  "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/8888888888"],
  "style": "medium",
  "loadAgain": false,
  "showLoading": true
}
```

`style`: any of the 11 styles (`small`, `medium`, `large`, `small_button_bottom`, `small_button_top`,
`small_button_end`, `large_button_bottom`, `large_button_top`, `large_compact`, `side_media_button_bottom`,
`side_media_button_top`), or the name of your own registered layout.

### Settings for every ad (`AdSettings`)

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
  "loadingSeconds": 1
}
```

`0` means "no limit" for `maxInterstitialsPerSession`. An ad's own `loadTimeoutSeconds`, `showLoading` and
`loadingSeconds` override the ones here.

### Your own values (step 10)

These are ordinary parameters with the matching data type, not JSON objects:

| Parameter name | Data type | Example value |
|---|---|---|
| `show_rate_dialog` | Boolean | `true` |
| `free_levels` | Number | `10` |
| `coin_price` | Number | `1.49` |
| `welcome_text` | String | `Hello again!` |
| `features` | JSON | `{"chat": true, "limit": 3}` |

### Swap a format

An ad can change format from Firebase within these pairs: interstitial ↔ app open, banner ↔ native. Rewarded ads
never change. Give ad unit IDs **of the new format** in the same value, because an AdMob ad unit serves one format
only:

```json
{ "format": "native", "style": "large", "adUnitIds": ["ca-app-pub-XXXXXXXXXXXXXXXX/9999999999"] }
```

`AdSlotView` and `AdSlots.showFullScreen` follow the swap. Remove `format` to swap back.

### Quick changes

| To… | Parameter | Value |
|---|---|---|
| Turn one ad off | the ad's | `{ "enabled": false }` |
| Turn every ad off | `AdSettings` | `{ "adsEnabled": false }` |
| Interstitial on every 3rd tap | the ad's | `{ "counter": 3 }` |
| At least 60 s between two shows of an ad | the ad's | `{ "timerSeconds": 60 }` |
| At least 30 s between any two full-screen ads | `AdSettings` | `{ "minSecondsBetweenFullScreenAds": 30 }` |
| Use two ad units in turn | the ad's | `{ "adUnitIds": ["ca-app-pub-…/111", "ca-app-pub-…/222"] }` |
| Collapsible banner | the banner's | `{ "style": "collapsible_bottom" }` |
| Splash of 13 seconds | `AdSettings` | `{ "splashSeconds": 13 }` |
| Wait at most 5 s for a full-screen ad | `AdSettings` | `{ "loadTimeoutSeconds": 5 }` |
| No "Loading ad…" screen before one ad, or no shimmer on one banner | the ad's | `{ "showLoading": false }` |
| Load the next ad right after each show | the ad's | `{ "loadAgain": true }` |

A value with a mistake is ignored (with a warning in the log), and the ad keeps working with its previous settings.
Every field is described in [Ad profiles](AD_PROFILES.md#the-json).

## 12. Test it

Run a debug build on a phone or an emulator:

- [ ] Logcat (filter `CompanyAdMob`) shows `Firebase parameter <Name>: {…}` for each ad, and later `Remote Config
  fetched`.
- [ ] The splash shows the consent form (first launch), then the progress bar, then a test ad.
- [ ] Every ad is labelled "Test Ad".
- [ ] Full-screen ads show "Loading ad…" first. Banners and native ads show a shimmer while they load.
- [ ] Changing a parameter in Firebase (for example `{ "enabled": false }` on `HomeBanner`) and relaunching twice
  changes the app. The first launch fetches, the second uses the value, or the first if the fetch is quick.
- [ ] Airplane mode: the splash lasts 3 seconds and the app works without ads.

## 13. Before release

- [ ] Every entry in `AppAds` has its real ad unit IDs, in `AppAds` or in Firebase. A release build logs a warning
  for each ad still on test ads.
- [ ] The manifest has **your** AdMob app ID, not Google's sample one.
- [ ] A GDPR message (and a US states message if you need one) is published in AdMob → Privacy & messaging.
- [ ] `app-ads.txt` is set up in AdMob.
- [ ] Install a release build: it shows real ads (a new ad unit can take a few hours to serve). Don't click your own
  ads.
- [ ] A premium user sees no ad and a 3-second splash.

Problems? See [Troubleshooting](TROUBLESHOOTING.md). Each part in depth: [Ad profiles](AD_PROFILES.md),
[Splash screen](SPLASH.md), [Consent](CONSENT.md), [Remote configuration](REMOTE_CONFIG.md), [XML and Views](XML.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-29
