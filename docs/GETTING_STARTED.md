# Getting started

This page takes an app from nothing to its first banner and interstitial. Each later page covers one topic in depth.

## 1. Add the repository

Adroit is published privately to GitHub Packages, so you need access first: see [Getting access](ACCESS.md). Then put
your credentials in `~/.gradle/gradle.properties`, never in the project:

```properties
gpr.user=<your GitHub user>
gpr.key=<a personal access token with read:packages>
```

Then in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
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

## 2. Add the dependencies

```kotlin
dependencies {
    implementation("io.github.thesobiaakbar:adroit-compose:1.0.0-rc2") // Compose apps (brings adroit-core and adroit-views)
    // implementation("io.github.thesobiaakbar:adroit-views:1.0.0-rc2") // XML/View apps without Compose (brings adroit-core)
    testImplementation("io.github.thesobiaakbar:adroit-testing:1.0.0-rc2")
}
```

Don't add `play-services-ads` or `ads-mobile-sdk` yourself: `adroit-core` brings the Google Mobile Ads Next-Gen SDK and
UMP at runtime, and no Google type is needed to use it. If another dependency brings `play-services-ads` (for example a
mediation adapter), see [Mediation](MEDIATION.md).

## 3. Add the app ID to the manifest

Both the Google Mobile Ads SDK and UMP read your AdMob app ID from the manifest
([UMP setup](https://developers.google.com/admob/android/next-gen/privacy)). Without it the library reports
`APP_ID_MISSING` (see [Configuration](CONFIGURATION.md#validation)).

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

While developing you can use Google's sample app ID, `ca-app-pub-3940256099942544~3347511713`. PRODUCTION rejects it.

## 4. Declare your placements

A placement is a place in your app that shows an ad. It has a stable key (used by remote config and analytics) and a
format. Declare them once:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/GettingStarted.kt#placements -->
```kotlin
/** Every place the app shows an ad. Keys are stable identifiers: remote overrides and analytics use them. */
object Placements {
    val HomeBanner = BannerPlacement("home_banner")
    val LevelComplete = InterstitialPlacement("level_complete")
    val ExtraLife = RewardedPlacement("extra_life")
    val FeedNative = NativePlacement("feed_native")
}
```

## 5. Initialize in `Application.onCreate`

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/GettingStarted.kt#initialize -->
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cheap and synchronous: validates the configuration, no network and no Google SDK call.
        Adroit.initialize(
            this,
            adMobConfig {
                environment = if (isDebugBuild) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                placements {
                    banner(Placements.HomeBanner, "ca-app-pub-0000000000000000/0000000001")
                    interstitial(Placements.LevelComplete, "ca-app-pub-0000000000000000/0000000002")
                    rewarded(Placements.ExtraLife, "ca-app-pub-0000000000000000/0000000003")
                    native(Placements.FeedNative, "ca-app-pub-0000000000000000/0000000004")
                }
                frequency { minIntervalBetweenFullScreenAds = 30.seconds }
            },
        )
    }
}
```

In DEVELOPMENT the library always requests Google's demo ad units, whatever IDs you configure, so it's safe from day
one. [Configuration](CONFIGURATION.md) covers environments and every option.

## 6. Start from the launcher Activity

`start()` gathers consent with UMP (showing the form when required), then initializes the Google Mobile Ads SDK. No ad
is requested before consent allows it. See [Consent](CONSENT.md).

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/GettingStarted.kt#start -->
```kotlin
class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Gathers consent (UMP), then initializes the Google Mobile Ads SDK. Safe to call again after a rotation:
        // it joins the work already in progress.
        Adroit.start(this) { state ->
            // Ready, or Unavailable/Failed: continue into the app either way.
        }
    }
}
```

## 7. A first banner

XML:

<!-- snippet: docs-snippets/src/main/res/layout/snippet_banner.xml#xml-banner -->
```xml
<io.github.thesobiaakbar.adroit.views.AdBannerView
    android:id="@+id/home_banner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="home_banner"
    app:adroit_bannerSize="anchoredAdaptive" />
```

Compose:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Banner.kt#compose-banner -->
```kotlin
@Composable
fun HomeBottomBar() {
    var state by remember { mutableStateOf<BannerState?>(null) }
    AdBanner(
        placement = Placements.HomeBanner,
        modifier = Modifier.fillMaxWidth(),
        options = BannerOptions(BannerSize.AnchoredAdaptive),
        placeholder = { Text("Advertisement", Modifier.padding(8.dp)) },
        onStateChange = { state = it },
    )
}
```

Both follow the screen's lifecycle and are destroyed with it. See [Banner](BANNER.md).

## 8. A first interstitial

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/GettingStarted.kt#first-interstitial -->
```kotlin
fun onLevelComplete(activity: Activity, goToNextLevel: () -> Unit) {
    Adroit.showInterstitial(activity, Placements.LevelComplete) {
        // Called exactly once, whether an ad was shown or not: always continue the flow here.
        goToNextLevel()
    }
}
```

The callback is always called, exactly once, on the main thread, whether an ad was shown or not. Continue your flow in
it. See [Interstitial](INTERSTITIAL.md).

## 9. Dependency injection

Everything is reachable from the `Adroit` object, which also implements `AdsClient`. Code that receives an
`AdsClient` can be tested with `FakeAdsClient` ([Testing](TESTING.md)).

Without a DI framework, default the parameter to `Adroit.client`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/GettingStarted.kt#without-hilt -->
```kotlin
/** A ViewModel that receives the client instead of reaching for the Adroit object: easy to fake in tests. */
class LevelViewModel(private val ads: AdsClient = Adroit.client) : ViewModel() {
    fun onLevelComplete(activity: Activity) {
        ads.showInterstitial(activity, Placements.LevelComplete) { /* continue */ }
    }
}
```

With Hilt, bind it once, in a `SingletonComponent` module. Keep `initialize` in `Application.onCreate`.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AdsModule {
    @Provides
    fun ads(): AdsClient = Adroit.client
}
```

(The Hilt module isn't compiled with the other snippets, because this repository doesn't use Hilt.)

## Next steps

- Setting up a new app with ads tuned from Firebase? Follow the [Integration guide](INTEGRATION_GUIDE.md) checklist.

- Read [Consent](CONSENT.md) and set up your messages in the AdMob UI before going to production.
- Pick your formats: [Banner](BANNER.md), [Interstitial](INTERSTITIAL.md), [Rewarded](REWARDED.md),
  [Rewarded interstitial](REWARDED_INTERSTITIAL.md), [App open](APP_OPEN.md), [Native](NATIVE.md).
- Before release: switch to PRODUCTION with your real IDs ([Configuration](CONFIGURATION.md#environments)) and check
  [Troubleshooting](TROUBLESHOOTING.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-28
