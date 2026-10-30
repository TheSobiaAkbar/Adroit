# AdMob ads and in-app purchases in a XML + Kotlin app

This guide takes a new Android Studio project (XML + Kotlin) to a release-ready app with **AdMob + in-app purchases**.
Follow it from top to bottom: every file is complete, and every step ends with a check. You don't need any other guide.

Another kind of app? [AdMob only, Jetpack Compose + Kotlin](ADS_COMPOSE.md) · [AdMob only, XML + Kotlin](ADS_XML.md) ·
[in-app purchases only, Jetpack Compose + Kotlin](IAP_COMPOSE.md) · [in-app purchases only, XML + Kotlin](IAP_XML.md) ·
[AdMob + in-app purchases, Jetpack Compose + Kotlin](ADS_IAP_COMPOSE.md). Not sure: [Getting
started](../GETTING_STARTED.md).

The `sample-xml` app in this repository is this guide's finished result.

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

- A splash screen that checks Google Play for an update, asks for consent, shows a splash ad, then the launch paywall.
  Firebase can keep the splash ad for the paywall's close button instead.
- Every AdMob format, plus subscriptions, a lifetime purchase and coins. A premium user sees no ads, through one line of
  code.
- Ads, paywalls and the app update all controlled from one Firebase project, without a new release.

**Before you start**

| What | Where | Example |
|---|---|---|
| Your app's package name | `applicationId` in `app/build.gradle.kts` | `com.example.myapp` |
| An AdMob app ID | AdMob → Apps → your app → App settings | `ca-app-pub-1234567890123456~1234567890` |
| One ad unit ID per ad | AdMob → Apps → your app → Ad units, each of the right format | `ca-app-pub-1234567890123456/1234567890` |
| Your products | Play Console → your app → Monetize ([Play Console setup](../PLAY_CONSOLE_BILLING_SETUP.md)) | `premium_sub`, `premium_lifetime` |
| A Firebase project | [console.firebase.google.com](https://console.firebase.google.com) | |

You can start without the IDs and products: an ad without ad unit IDs shows Google's test ads, and debug builds always
do; debug builds use demo billing (made-up products) until Play Console has yours.

## 2. Dependencies

Adroit is on Maven Central: no account and no token. Check that `settings.gradle.kts` lists `mavenCentral()`, as a new
Android Studio project already does.

This setup needs these Adroit artifacts, and only these:

| Artifact | What for |
|---|---|
| `adroit-views` | the ad views (`AdSlotView`, …); brings `adroit-core` and Google's ads and consent SDKs |
| `adroit-firebase` | every ad controlled from Firebase Remote Config, and ad events in Analytics |
| `adroit-billing` | products, purchases, entitlements and paywall rules; brings Google Play Billing |
| `adroit-billing-views` | the paywall screens (`AdroitPaywall`, `PaywallView`) |
| `adroit-billing-firebase` | every paywall controlled from Firebase, and purchase events in Analytics |
| `adroit-update` | Google Play's in-app update, controlled by the Firebase `AppUpdate` parameter |

Don't add `play-services-ads`, `ads-mobile-sdk` or `com.android.billingclient:billing` yourself: the Adroit artifacts
bring the versions they are tested with.

In `gradle/libs.versions.toml`, add to the existing sections:

```toml
[versions]
adroit = "1.0.0-rc7"
googleServices = "4.5.0"

[libraries]
adroit-views = { module = "io.github.thesobiaakbar:adroit-views", version.ref = "adroit" }
adroit-firebase = { module = "io.github.thesobiaakbar:adroit-firebase", version.ref = "adroit" }
adroit-billing = { module = "io.github.thesobiaakbar:adroit-billing", version.ref = "adroit" }
adroit-billing-views = { module = "io.github.thesobiaakbar:adroit-billing-views", version.ref = "adroit" }
adroit-billing-firebase = { module = "io.github.thesobiaakbar:adroit-billing-firebase", version.ref = "adroit" }
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
    alias(libs.plugins.google.services) // reads app/google-services.json
}

android {
    // ... namespace, defaultConfig and the rest, as Android Studio wrote them
    compileSdk = 36
    defaultConfig {
        minSdk = 24 // Adroit needs 24 or higher
    }
    buildFeatures {
        buildConfig = true // for BuildConfig.DEBUG
    }
}

dependencies {
    implementation(libs.adroit.views)
    implementation(libs.adroit.firebase)
    implementation(libs.adroit.billing)
    implementation(libs.adroit.billing.views)
    implementation(libs.adroit.billing.firebase)
    implementation(libs.adroit.update)

    // Your usual AndroidX libraries (the "Empty Views Activity" template adds most of them):
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
}
```

(Your plugin aliases may have other names: keep the ones Android Studio wrote, and add `google-services`.)

In `app/src/main/AndroidManifest.xml`, register the Application class (step 5) and make the splash (step 6) the
launcher:

```xml
<application
    android:name=".AdsIapApp"
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
   yes (purchase and paywall events go there).
2. **Add app** → Android, with your **exact** package name, then **Register app**.
3. Download `google-services.json` into your project's `app/` folder.
4. Skip the console's "Add Firebase SDK" step: the Adroit artifacts bring the Firebase libraries.

**Debug builds with another package name.** If your debug build type has an `applicationIdSuffix` (for example
`.debug`), register that package as a second Android app in the same Firebase project, and download
`google-services.json` again: one file holds both apps. Both read the same Remote Config parameters.

Remote Config needs nothing else in code: the first Adroit Firebase module the app uses fetches it once per launch, in
the background, for ads and billing together. Nothing waits for it. Step 8 lists the parameters to create.

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

**What you sell and the paywalls**, in `billing/Billing.kt`:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/Billing.kt#billing-enum -->
```kotlin
/** Everything the app sells. The IDs are the product IDs in Play Console. */
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    /** A subscription with base plans "monthly" and "yearly" in Play Console. */
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
    Coins100(ProductSpec.consumable("coins_100")),
}

/** Every paywall. Each name is also its Firebase parameter: a JSON value there changes it without a release. */
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    Onboarding(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            close = CloseBehavior(delaySeconds = 3, showCountdown = true, back = BackBehavior.AFTER_DELAY),
            show = ShowRules(maxPerSession = 1),
            texts = PaywallTexts(title = "Go Premium", features = listOf("No ads", "Every level unlocked")),
        ),
    ),
    Settings(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            showManage = true,
        ),
    ),
    Coins(
        PaywallSpec(
            plans = listOf(PlanSpec(AppProducts.Coins100)),
            template = PaywallTemplate.SINGLE,
            showRestore = false,
            successAction = SuccessAction.STAY_WITH_MESSAGE,
        ),
    ),
}
```

- **Products.** The string is the product ID in Play Console. An **entitlement** (`"premium"`) is what a product
  unlocks; several products can unlock the same one, and your code asks only for the entitlement:
  `AdroitBilling.entitlements.value.has("premium")`.
- **Paywalls.** `basePlanId` is a base plan of the subscription in Play Console; `OfferRule(tags = …)` picks the offer
  with those tags when Play gives this user one. Every field is explained, for Firebase, in [Paywalls in
  Firebase](../PAYWALL_REMOTE_CONFIG.md); in code the names are the same.
- **`Onboarding`** shows after the splash, once per launch (`maxPerSession = 1`), with its close button after 3 seconds.
  **`Settings`** opens from the Upgrade button. **`Coins`** sells a consumable (step 7).

**Check:** the app builds. In a debug build, `AdroitBilling.initialize` (next step) throws with every problem it finds
in these enums (an unknown product in a paywall, duplicate IDs…).

## 5. The Application class

`AdsIapApp.kt`, next to your activities, registered in the manifest in step 2:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/Apps.kt#guide-ads-iap-app -->
```kotlin
class AdsIapApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Firebase Remote Config: debug builds see a console change on the next launch. Adroit fetches it once per
        // launch, for ads and billing together, in the background: nothing waits for it.
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
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AppAds.settings)
                remoteConfig = FirebaseRemoteConfigProvider()
                logging { level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN }
            },
        )
        // Billing: reads the saved purchases at once and asks Google Play in the background.
        AdroitBilling.initialize(
            this,
            BillingSettings(
                products = AppProducts.entries,
                paywalls = AppPaywalls.entries,
                config = FirebaseBillingConfig.source(),
                events = listOf(FirebaseBillingEvents(this)),
                demo = DemoBilling(BuildConfig.DEBUG),
            ),
        )
        // The one line between billing and ads: premium means no ads, from the next ad request on. The saved
        // purchases are already read, so a premium user is ad-free before the splash asks for its ad.
        appScope.launch { AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) } }
    }
}
```

- **Debug builds** (`DEVELOPMENT`) show only Google's test ads, and ask for consent as if you were in Europe. **Release
  builds** (`PRODUCTION`) show your real ads.
- Inside `adMobConfig { }`, `remoteConfig` is the library's setting: don't name a variable of your own `remoteConfig`
  there.
- **Demo billing** (`DemoBilling(BuildConfig.DEBUG)`) replaces Google Play with made-up products, prices marked
  "(DEMO)", and a dialog where you pick "Buy", "Pending payment" or "Cancel". It works on any device and emulator before
  Play Console has your products. A release build ignores it.
- **The bridge line** (`Adroit.setAdFree(it.adFree)`) is the only code that connects billing and ads. A premium user
  sees no ad from the next ad request on, and on the next launch the splash skips consent and its ad, because the saved
  purchases are read before the splash asks. `adFree` is true while the user has any entitlement (the `AdroitBilling`
  Firebase parameter can narrow it, with `adFreeEntitlements`).
- There's no Remote Config fetch call: the Adroit Firebase modules start one, once per launch.

**Check:** filter logcat by `Adroit`: one line `Firebase parameter <Name>: {…}` per ad; logcat (tag `AdroitBilling`)
shows `DEMO billing: made-up products…` in a debug build.

## 6. The splash screen

The splash runs everything at app start, and always ends somewhere:

1. **The update check:** Google Play's update, as the Firebase `AppUpdate` parameter says (flexible by default). A user
   who refuses a forced update leaves the app.
2. **Consent:** Google's consent form, where the user's region needs it. The splash waits for the answer.
3. **The timer and the splash ad:** a progress bar for `splashSeconds`, and the splash ad if it loads in time. Premium
   users, offline users and a splash ad turned off get a 3-second splash without an ad.
4. **The routing:** the `Onboarding` paywall when its show rules allow, then the main screen.

It never gets stuck, even offline, and a rotation never restarts it: everything is kept in its ViewModel.

The layout, `res/layout/activity_splash.xml` (change its look as you like; keep the IDs):

<!-- snippet: docs-snippets/src/main/res/layout/activity_splash.xml#guide-splash-layout -->
```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/app_name"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="MyApp"
        android:textSize="28sp" />

    <!-- Hidden while the update check and consent run; then the splash timer, 0 to 100. -->
    <LinearLayout
        android:id="@+id/progress_group"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:layout_marginHorizontal="48dp"
        android:layout_marginBottom="56dp"
        android:gravity="center_horizontal"
        android:orientation="vertical"
        android:visibility="gone">

        <TextView
            android:id="@+id/progress_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <ProgressBar
            android:id="@+id/progress"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:max="100" />
    </LinearLayout>
</FrameLayout>
```

A small helper for the progress bar, `SplashProgress.kt`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/SplashProgress.kt#guide-splash-progress-xml -->
```kotlin
/** Shows the splash timer's progress (0..100), or hides the bar with null (update check, consent). */
fun Activity.showSplashProgress(percent: Int?) {
    findViewById<View>(R.id.progress_group).isVisible = percent != null
    if (percent == null) return
    findViewById<ProgressBar>(R.id.progress).progress = percent
    findViewById<TextView>(R.id.progress_text).text = "$percent%"
}
```

`SplashActivity.kt`, with its ViewModel:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/adsiap/xml/AdsIapSplashXml.kt#guide-ads-iap-splash-xml -->
```kotlin
/** Keeps the splash across rotations: the update check, consent, the timer, the splash ad and the routing run once. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // A premium user skips consent and the ad: SplashFlow reads Adroit.isAdFree, set by the billing bridge.
    val flow = SplashFlow.create(application, AppAds.SplashAd)

    /** The routing after the splash has started (the paywall is up): a rotation doesn't repeat it. */
    var routing = false

    /** The routing is over: open the main screen, from whichever Activity is current. */
    val goToMain = MutableStateFlow(false)

    private var update: Job? = null

    /** Google Play's update check, once per launch: a rotation waits for the same check. */
    suspend fun checkForUpdate(activity: Activity) {
        val job = update ?: viewModelScope.launch { AdroitUpdate.check(activity) }.also { update = it }
        job.join()
    }

    override fun onCleared() = flow.cancel()
}

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false

    // Registered in every onCreate: after a rotation, the paywall's result arrives here.
    private val paywall = registerForActivityResult(AdroitPaywall.contract()) { viewModel.goToMain.value = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        lifecycleScope.launch {
            // The update first: a forced update must not let consent or an ad start.
            viewModel.checkForUpdate(this@SplashActivity)
            viewModel.flow.start(this@SplashActivity) // after a rotation, hands the new Activity to the running flow
        }
        lifecycleScope.launch {
            // RESUMED, not STARTED: never navigate while the splash is in the background. Android can take
            // seconds to stop an Activity after Home, and a paused Activity is still STARTED.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.flow.state.collect { state ->
                        when (state) {
                            SplashState.Preparing -> showSplashProgress(null) // update check, consent: no bar yet
                            is SplashState.Loading -> showSplashProgress(state.progress) // 0..100
                            SplashState.ShowingAd -> showSplashProgress(100) // the splash ad is on screen
                            is SplashState.Finished -> route()
                        }
                    }
                }
                viewModel.goToMain.first { it }
                goToMain()
            }
        }
    }

    /** Once per launch: the launch paywall if its rules allow, else straight to main. */
    private fun route() {
        if (viewModel.routing) return
        viewModel.routing = true
        if (AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "onboarding") == ShowDecision.Show) {
            paywall.launch(PaywallLaunch(AppPaywalls.Onboarding, "onboarding"))
        } else {
            viewModel.goToMain.value = true
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

- **The update check runs in the ViewModel,** once per launch: after a rotation, the new Activity waits for the same
  check instead of starting a second one.
- **`flow.start(this)` in `onCreate`:** after a rotation it hands the new Activity to the running flow. Consent, the
  timer and the splash ad never restart.
- **The splash pauses in the background:** its timer stops while the screen isn't resumed, and the user comes back to
  the splash where they left it. Navigation also waits for `RESUMED`, not `STARTED`: after Home, Android can take
  seconds to stop the Activity.

**A splash without an ad.** To keep the splash (the update check, consent, the timer) but drop its ad, turn the ad
off in Firebase with `{ "enabled": false }` in `SplashAd`, or pass `null` instead of `AppAds.SplashAd` to
`SplashFlow.create`. The splash then lasts `premiumSplashSeconds` (3 s) ([Splash
screen](../SPLASH.md#a-splash-without-an-ad)).

Whether an ad shows somewhere else instead, for example when the paywall closes, is your app's choice: call
`AdSlots.showFullScreen` there with any full-screen ad. Wrap it in `lifecycleScope.launch { withResumed { … } }`: the
paywall's result arrives before the splash is back in front, and a full-screen ad never shows over a screen in the
background.

**Consent in debug builds.** A debug build behaves as if you were in Europe, so the consent form shows wherever you are.
Until it's answered it shows on every launch; once answered, clear the app's storage to see it again. The form itself
comes from AdMob: publish a GDPR message in **AdMob → Privacy & messaging**, or no form shows
([Consent](../CONSENT.md)).

**Check:** launch a debug build: the consent form, the progress bar, a test ad, the Onboarding paywall with "(DEMO)"
prices, then your main screen.

## 7. The screens

Two rules cover every ad:

1. **Ads load when you ask.** Ask when the screen that shows them comes to the front (`AdSlots.load`). Banner and native
   slots ask by themselves.
2. **Every show calls you back, whether an ad showed or not.** Continue the app's flow in the callback.

### The main screen and its ads

`res/layout/activity_main.xml`:

<!-- snippet: docs-snippets/src/main/res/layout/activity_main.xml#guide-ads-main-layout -->
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root"
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
                android:text="Extra life (watch an ad)" />

            <!-- Shown only where Google requires a way to change consent. -->
            <Button
                android:id="@+id/privacy_options"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Privacy options"
                android:visibility="gone" />

            <!-- A native ad inside the page: its template comes from HomeNative's style. -->
            <io.github.thesobiaakbar.adroit.views.AdSlotView
                android:id="@+id/home_native"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                app:adroit_placement="HomeNative" />
        </LinearLayout>
    </ScrollView>

    <!-- The banner, pinned to the bottom. A banner or a native ad, whichever Firebase says. -->
    <io.github.thesobiaakbar.adroit.views.AdSlotView
        android:id="@+id/home_banner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:adroit_placement="HomeBanner" />
</LinearLayout>
```

`MainActivity.kt`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/ads/xml/AdsMainXml.kt#guide-ads-main-xml -->
```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Interstitial: the callback runs whether an ad showed or not. Always continue the app's flow in it.
        findViewById<Button>(R.id.level_complete).setOnClickListener {
            AdSlots.showFullScreen(this, AppAds.LevelCompleteAd) { openNextLevel() }
        }

        // Rewarded: give the reward only for Earned.
        findViewById<Button>(R.id.extra_life).setOnClickListener {
            Adroit.showRewarded(this, AppAds.ExtraLifeRewarded.rewardedPlacement()) { result ->
                when (result) {
                    is RewardResult.Earned -> giveLives(result.reward.amount)
                    RewardResult.NotEarned -> toast("Watch the whole ad to get the reward")
                    is RewardResult.Failed -> toast("No ad right now, try again later")
                }
            }
        }

        // Privacy options: Google requires a way to change consent, where consent applies.
        val privacy = findViewById<Button>(R.id.privacy_options)
        privacy.setOnClickListener { Adroit.privacy.showPrivacyOptions(this) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Adroit.privacy.isPrivacyOptionsRequired.collect { privacy.isVisible = it }
            }
        }

        // A flexible update downloaded in the background: offer "Restart".
        offerDownloadedUpdate(findViewById<View>(R.id.root))

        // The app open ad shows by itself when the user comes back to the app. Load it once.
        AdSlots.load(AppAds.AppOpenAd)
    }

    override fun onResume() {
        super.onResume()
        // Ads load only when asked: this screen's full-screen ads, so they're ready when a button is tapped, and the
        // next screen's banner, so it shows the moment that screen opens. Nothing happens if one is already loaded.
        AdSlots.load(AppAds.LevelCompleteAd)
        AdSlots.load(AppAds.ExtraLifeRewarded)
        AdSlots.load(AppAds.DetailsBanner)
    }

    private fun openNextLevel() = Unit // your app's next screen

    private fun giveLives(amount: Int) = Unit // your app's reward

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
```

- **`AdSlotView`** shows a banner or a native ad, whichever the ad is (Firebase can even swap them), with a shimmer
  while it loads. It hides itself if no ad comes, and keeps its ad across rotation and tab switches. The same from code,
  into any `ViewGroup`: `container.attachAd(AppAds.DetailsBanner)`. New ad on demand: `load(refresh = true)` ([XML and
  Views](../XML.md)).
- **Native ads in a RecyclerView:** one `NativeAdHolder` per ad row, kept in the ViewModel
  ([Native](../NATIVE.md#recyclerview)).
- **What the user sees on `showFullScreen`:** "Loading ad…" for about a second, then the ad. Not loaded yet: "Loading
  ad…" for at most `loadTimeoutSeconds` (8), then the ad or your callback. Not due (`counter`), capped, offline or
  premium: no ad, your callback runs at once.
- **The app open ad** shows by itself when the user returns to the app, never over the splash ([App
  open](../APP_OPEN.md)). **Rewarded interstitials** need an intro screen first ([Rewarded
  interstitial](../REWARDED_INTERSTITIAL.md)).
- **Your ads' look:** colors in `res/values/ads_style.xml` or from Firebase, and your own native layout registered by
  name ([Native: Styling](../NATIVE.md#styling)).

### Settings: upgrade, restore, manage

In your settings screen (here a Fragment), three buttons:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-restore-xml -->
```kotlin
private fun onRestoreClicked() {
    viewLifecycleOwner.lifecycleScope.launch {
        val message = when (val result = AdroitBilling.restore()) {
            is RestoreResult.Restored -> if (result.newlyActive.isEmpty()) "Your purchases are up to date" else "Restored"
            RestoreResult.NothingToRestore -> "Nothing to restore"
            is RestoreResult.Failed -> "Couldn't reach Google Play. Try again later."
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}

private fun onManageClicked() {
    // Play's subscription page; false when no app can open it.
    AdroitBilling.openManageSubscriptions(requireActivity(), AppProducts.PremiumSub)
}

private fun onUpgradeClicked() {
    // force = true: the user asked, so the show rules don't apply.
    AdroitPaywall.show(requireActivity(), AppPaywalls.Settings, "settings", force = true)
}
```

To know how a paywall closed, use the Activity result contract instead of `show`:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-contract-xml -->
```kotlin
private val upgrade = registerForActivityResult(AdroitPaywall.contract()) { reason ->
    if (reason == PaywallCloseReason.PURCHASED) Toast.makeText(this, "Welcome to Premium!", Toast.LENGTH_LONG).show()
}

private fun onUpgradeClicked() {
    // The user asked: no show rules, so launch it directly.
    upgrade.launch(PaywallLaunch(AppPaywalls.Settings, "settings_button"))
}
```

- **Restore** asks Play again for what the user owns, on this Google account. Purchases are restored at every launch
  too; the button is for users who expect one.
- **Manage** opens Play's subscription page, where users cancel or change payment.
- **The status:** collect `AdroitBilling.entitlements`: `has("premium")`, and `status` (`Fresh` from Play, `Cached`
  while offline, `Unknown` before the first answer of a new install).

**Play's messages** ("your payment was declined"): in `MainActivity.onResume`, call
`AdroitBilling.showInAppMessages(this)`. It shows nothing when there's none.

### A custom paywall (optional)

**In a layout.** `PaywallView` puts a paywall inside your own screen:

<!-- snippet: docs-snippets/src/billing/res/layout/snippet_paywall_view.xml#billing-paywall-view -->
```xml
<io.github.thesobiaakbar.adroit.billing.views.PaywallView
    android:id="@+id/coins_paywall"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:adroit_billing_paywall="Coins"
    app:adroit_billing_placement="coins" />
```

Give it an id (it keeps its state across rotations by id), and say what happens when it closes:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-paywall-view-close -->
```kotlin
view.findViewById<PaywallView>(R.id.coins_paywall).onClose = { parentFragmentManager.popBackStack() }
```

**Your own views.** Drive them from the paywall's controller; it still reads Firebase, picks the offers and runs the
purchase:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-custom-xml -->
```kotlin
/** The coins paywall's controller, kept across rotations. */
class CoinsViewModel : ViewModel() {
    val store = AdroitBilling.paywalls.controller(AppPaywalls.Coins, "coins_screen", viewModelScope)
}

class CoinsFragment : Fragment(R.layout.snippet_coins) {
    private val viewModel: CoinsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val price = view.findViewById<TextView>(R.id.coins_price)
        val buy = view.findViewById<Button>(R.id.coins_buy)
        val store = viewModel.store
        buy.setOnClickListener { store.purchase(requireActivity()) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.state.collect { state ->
                    when (state) {
                        is PaywallUiState.Content -> {
                            price.text = PriceText.planPrice(resources, state.selectedPlan)
                            buy.text = PriceText.cta(resources, state.selectedPlan)
                            buy.isEnabled = state.busy == null
                        }
                        is PaywallUiState.Purchased -> price.text = "Thank you!"
                        is PaywallUiState.Loading, is PaywallUiState.Unavailable, is PaywallUiState.Closed -> buy.isEnabled = false
                    }
                }
            }
        }
        store.onShown() // on screen now: reported once per opening
    }
}
```

`PriceText` formats Play's prices and the button text the way the built-in paywalls do: never type a price or a trial
into the app.

### Consumables (optional)

`Coins100` is bought again and again. The app credits each purchase, then **confirms** it, which lets Play sell it
again. Collect them for the whole app, from `Application.onCreate` (`Coins.start(this, appScope)`):

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingApp.kt#billing-consumables -->
```kotlin
/** The coins bought with AppProducts.Coins100. A real app keeps the balance on its server. */
object Coins {
    private val mutableBalance = MutableStateFlow(0)
    val balance: StateFlow<Int> = mutableBalance.asStateFlow()

    /** Call once, from Application.onCreate, so a purchase is credited whichever screen is open. */
    fun start(context: Context, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences("coins", Context.MODE_PRIVATE)
        mutableBalance.value = prefs.getInt("balance", 0)
        scope.launch {
            AdroitBilling.consumables.collect { grant ->
                val credited = prefs.getStringSet("credited", emptySet()).orEmpty()
                if (grant.orderIdHash !in credited) { // each order once, even when it is delivered again
                    mutableBalance.value += 100 * grant.quantity
                    prefs.edit(commit = true) {
                        putInt("balance", mutableBalance.value)
                        putStringSet("credited", credited + grant.orderIdHash)
                    }
                }
                grant.confirm() // now Play may sell it again
            }
        }
    }
}
```

A grant not confirmed (the app was killed in between) comes again at the next launch: that's why the wallet remembers
each `orderIdHash`. Credit once, confirm every time.

### The update message

When a flexible update has downloaded in the background, offer the restart that installs it. `UpdateMessage.kt`, called
from `MainActivity.onCreate` with the root view (the ads `MainActivity` above already does):

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/UpdateSnackbar.kt#guide-update-xml -->
```kotlin
/** Call from the main Activity's onCreate: once a flexible update has downloaded, offer the restart that installs it. */
fun AppCompatActivity.offerDownloadedUpdate(root: View) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            AdroitUpdate.readyToInstall.first { it }
            Snackbar.make(root, "An update is ready", Snackbar.LENGTH_INDEFINITE)
                .setAction("Restart") { AdroitUpdate.install() }
                .show()
        }
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

### Paywalls

**`AdroitBilling`** (all paywalls at once)

```json
{ "enabled": true, "adFreeEntitlements": ["premium"], "offlineGraceHours": 72, "configVersion": "2026-10-1" }
```

**`Onboarding`**

```json
{
  "enabled": true,
  "template": "plans",
  "plans": [
    { "product": "PremiumSub", "basePlan": "yearly", "badge": "BEST_VALUE", "savingsVs": "monthly" },
    { "product": "PremiumSub", "basePlan": "monthly", "offer": { "tags": ["trial"] } },
    { "product": "PremiumLifetime" }
  ],
  "close": { "delaySeconds": 3, "showCountdown": true, "back": "AFTER_DELAY" },
  "show": { "maxPerSession": 1 },
  "texts": { "title": "Go Premium", "features": ["No ads", "Every level unlocked"] }
}
```

**`Settings`**

```json
{
  "enabled": true,
  "template": "plans",
  "plans": [
    { "product": "PremiumSub", "basePlan": "monthly" },
    { "product": "PremiumSub", "basePlan": "yearly", "savingsVs": "monthly" },
    { "product": "PremiumLifetime" }
  ],
  "showManage": true
}
```

**`Coins`**

```json
{
  "enabled": true,
  "template": "single",
  "plans": [{ "product": "Coins100" }],
  "showRestore": false,
  "successAction": "STAY_WITH_MESSAGE"
}
```

Every paywall field, with examples, for the people who change them: [Paywalls in Firebase](../PAYWALL_REMOTE_CONFIG.md).

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
- [ ] **Demo billing:** the paywall shows "(DEMO)" prices. "Continue", then "Buy (success)" closes it, and the next
      launch knows the user is premium. Try "Pending payment" (it completes 15 seconds later) and "Cancel" too.
- [ ] After a demo purchase, no ad shows anywhere, and the next splash is 3 seconds without consent or ad.
- [ ] **Real Google Play:** turn demo billing off, add yourself as a license tester, and install from the internal
      testing track. Test cards approve, decline or delay payments without charging you ([Play Console
      setup](../PLAY_CONSOLE_BILLING_SETUP.md)).
- [ ] **The update check** returns at once in a debug build (it isn't installed from Play). To see the real flows,
      upload two builds to Internal App Sharing or the internal testing track, the second with a higher `versionCode`,
      install the first from Play, then open it ([In-app update: Testing](../APP_UPDATE.md#testing)).

**Unit tests without Google Play.** Add
`testImplementation("io.github.thesobiaakbar:adroit-billing-testing:1.0.0-rc7")`. Code that takes `BillingClientApi`
(here `PremiumViewModel`) gets `FakeBilling` in tests:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/PremiumViewModel.kt#billing-viewmodel -->
```kotlin
/** Settings' premium rows. It takes BillingClientApi, so a test passes FakeBilling. It keeps no Activity. */
class PremiumViewModel(private val billing: BillingClientApi = AdroitBilling) : ViewModel() {
    private val results = Channel<RestoreResult>(Channel.BUFFERED)

    val entitlements: StateFlow<EntitlementSnapshot> = billing.entitlements

    /** One result per restore(), delivered once, even across a rotation. */
    val restoreResults: Flow<RestoreResult> = results.receiveAsFlow()

    fun restore() {
        viewModelScope.launch { results.send(billing.restore()) }
    }

    fun manage(activity: Activity): Boolean = billing.openManageSubscriptions(activity, AppProducts.PremiumSub)
}
```

<!-- snippet: docs-snippets/src/test/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingTestingSnippets.kt#billing-fake-test -->
```kotlin
/** Plain JVM: no Play, no device, no Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {
    private val billing = FakeBilling()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `premium follows what the user owns`() {
        val vm = PremiumViewModel(billing)
        assertFalse(vm.entitlements.value.has("premium"))

        billing.own(AppProducts.PremiumLifetime)

        assertTrue(vm.entitlements.value.has("premium"))
    }

    @Test
    fun `restore reports its result once`() = runTest {
        billing.restoreResult = RestoreResult.NothingToRestore
        val vm = PremiumViewModel(billing)

        vm.restore()

        assertEquals(RestoreResult.NothingToRestore, vm.restoreResults.first())
    }
}
```

More in [Testing](../TESTING.md#billing-with-adroit-billing-testing).

Ads in unit tests: `adroit-testing`'s `FakeAdsClient` ([Testing](../TESTING.md)).

## 10. Release checklist

- [ ] Every ad in `AppAds` has its real ad unit IDs, in the enum or in Firebase. A release build warns in logcat about
      each ad still on test ads.
- [ ] The manifest has **your** AdMob app ID, not Google's sample one.
- [ ] A GDPR message is published in AdMob → Privacy & messaging, and `app-ads.txt` is set up.
- [ ] `AdSettings` in Firebase has `"adsEnabled": true` (or isn't set). Don't click your own real ads.
- [ ] Every product is created and **active** in Play Console, with the base plans and offers your paywalls name ([Play
      Console setup](../PLAY_CONSOLE_BILLING_SETUP.md)).
- [ ] The app is uploaded to a testing track, and a license tester bought, restored and cancelled with real Google Play.
      Demo billing can't run in release: the library ignores it there.
- [ ] The Firebase parameters are published, and your `google-services.json` is the release app's.
- [ ] A release build (R8 on, `isMinifyEnabled = true`) starts and works: Adroit ships its own R8 rules, you add none.
- [ ] **Every release raises `versionCode`.** `AppUpdate` compares versions by it: with `forceBelowVersionCode`, set it
      to the first version every user must have.

Problems? [Troubleshooting](../TROUBLESHOOTING.md) and [Billing troubleshooting](../BILLING_TROUBLESHOOTING.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, Play Billing Library 9.1.0, Play In-App Updates 2.1.0, 2026-10-21
