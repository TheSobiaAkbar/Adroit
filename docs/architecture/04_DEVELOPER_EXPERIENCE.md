# Part 7: Developer integration experience (target state after 1.0.0)

This is what a developer at the company does to integrate the released AAR. Every step below is a requirement the implementation must meet.

## 1. Gradle

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    google(); mavenCentral()
    maven("https://maven.pkg.github.com/<org>/AdsPlugin") {          // or the company Artifactory URL
      credentials { username = providers.gradleProperty("gpr.user").get(); password = providers.gradleProperty("gpr.key").get() }
    }
  }
}

// app/build.gradle.kts
dependencies {
  implementation("com.company:admob-core:1.0.0")
  implementation("com.company:admob-views:1.0.0")     // XML / View apps
  implementation("com.company:admob-compose:1.0.0")   // Compose apps (brings views for templates)
  testImplementation("com.company:admob-testing:1.0.0")
}
// Only if you add mediation adapters (the Next-Gen SDK requires this):
configurations.configureEach {
  exclude(group = "com.google.android.gms", module = "play-services-ads")
  exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
```

Requirements: minSdk 24+, Kotlin 2.1+ (per ADR-010), and no direct `play-services-ads` dependency.

## 2. Manifest (one line; the UMP SDK requires it)

```xml
<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}"/>
```

## 3. Declare placements once

```kotlin
object Placements {
  val HomeBanner    = BannerPlacement("home_banner")
  val LevelComplete = InterstitialPlacement("level_complete")
  val ExtraLife     = RewardedPlacement("extra_life")
  val AppOpen       = AppOpenPlacement("app_open")
  val ArticleNative = NativePlacement("article_native")
}
```

## 4. Configure and initialize (Application)

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    CompanyAdMob.initialize(this, adMobConfig {
      environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
      privacy { maxAdContentRating = MaxAdContentRating.T }
      placements {
        banner(Placements.HomeBanner, adUnitId = BuildConfig.AD_HOME_BANNER)
        interstitial(Placements.LevelComplete, adUnitId = BuildConfig.AD_LEVEL_COMPLETE) {
          frequency { minInterval = 60.seconds }
        }
        rewarded(Placements.ExtraLife, adUnitId = BuildConfig.AD_EXTRA_LIFE)
        appOpen(Placements.AppOpen, adUnitId = BuildConfig.AD_APP_OPEN) { excludeActivities(SplashActivity::class.java) }
        native(Placements.ArticleNative, adUnitId = BuildConfig.AD_ARTICLE_NATIVE)
      }
    })
  }
}
```

In debug builds, every placement automatically uses Google's demo ad units, so developers can't click a live ad by mistake. A release build that contains a demo ID fails validation and turns ads off. It never crashes.

## 5. Start (launcher / splash Activity): consent, then SDK

```kotlin
class SplashActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CompanyAdMob.start(this)                                        // UMP form if required → SDK init
    CompanyAdMob.appOpen.showOnColdStart(this, maxWait = 4.seconds) { goToMain() }  // optional
  }
}
```

## 6. Show ads

```kotlin
// Interstitial: the app flow continues whatever happens
CompanyAdMob.showInterstitial(activity, Placements.LevelComplete) { openNextLevel() }

// Rewarded: deterministic result, reward granted exactly once
CompanyAdMob.showRewarded(activity, Placements.ExtraLife) { result ->
  when (result) {
    is RewardResult.Earned -> viewModel.grantLife(result.rewardId, result.reward.amount)
    RewardResult.NotEarned, is RewardResult.Failed -> Unit
  }
}

// Or with coroutines (safe across rotation: see unclaimedResults)
lifecycleScope.launch { val result = CompanyAdMob.rewarded.showAndAwait(this@GameActivity, Placements.ExtraLife) }
```

## 7. Banner and native

```kotlin
// XML
<com.company.admob.views.AdBannerView android:layout_width="match_parent" android:layout_height="wrap_content"
    app:company_admob_placement="home_banner"/>

// Fragment (programmatic)
binding.adContainer.attachBanner(viewLifecycleOwner, Placements.HomeBanner)
binding.nativeContainer.attachNativeAd(viewLifecycleOwner, Placements.ArticleNative, NativeTemplates.Medium)

// Compose
AdBanner(Placements.HomeBanner, Modifier.fillMaxWidth())
AdNative(Placements.ArticleNative, Modifier.padding(16.dp), layout = NativeTemplates.Large)
```

## 8. Privacy options (settings screen)

```kotlin
val required by CompanyAdMob.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
if (required) TextButton(onClick = { CompanyAdMob.privacy.showPrivacyOptions(activity) }) { Text("Privacy options") }
```

## 9. Premium users

```kotlin
billingRepository.isPremium.collect { CompanyAdMob.setAdFree(it) }   // hides banners/native, stops all loading
```

## 10. Analytics and remote config (optional)

```kotlin
adMobConfig {
  eventListeners += AdEventListener { event -> if (event is AdEvent.Paid) analytics.logRevenue(event.revenue) }
  remoteConfig = FirebaseRemoteConfigProvider(Firebase.remoteConfig)   // from admob-firebase, optional
}
```

## 11. Hilt (optional)

```kotlin
@Module @InstallIn(SingletonComponent::class)
object AdsModule { @Provides fun ads(): AdsClient = CompanyAdMob.client }
// ViewModel tests: inject FakeAdsClient from admob-testing
```

## 12. Diagnostics

```kotlin
Log.d("Ads", CompanyAdMob.diagnostics.snapshot().toString())   // redacted in PRODUCTION
CompanyAdMob.diagnostics.openAdInspector(activity)             // test devices only
```

## What developers never touch
Google SDK classes, `AdRequest`, the `LoadAdError` codes, consent sequencing, `MobileAds.initialize` threading, ad expiry, retries, the full-screen single-show rules, app-open lifecycle observers, `NativeAd.destroy()`, or `AdView.destroy()`.
