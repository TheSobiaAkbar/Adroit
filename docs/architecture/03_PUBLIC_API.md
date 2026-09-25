# Part 3: Public API proposal

This is the contract the implementation prompts build. Signatures are normative. KDoc wording is illustrative.

> **Implemented in PROMPT 02; the source and `admob-core/api/admob-core.api` are authoritative where they differ from this summary.** Decisions made during implementation:
> - **Packages:** everyday types live in `com.company.admob` (facade, `AdsClient`, sub-APIs, states, results, errors, placements, opt-in annotations). Only `com.company.admob.events`, `com.company.admob.logging`, and `com.company.admob.config` are separate.
> - **Suspend shows are named `showAndAwait`,** not overloads of `show`. `show(activity, placement)` with a defaulted callback and a same-named `suspend` overload would make the fire-and-forget call fail to compile outside coroutines ("suspend function should be called only from a coroutine"). `NativeApi.load` keeps its overload because the callback form has no default.
> - `CompanyAdMob.initialize(application, config)` arrives in PROMPT 06, after PROMPT 03 adds `AdMobConfig`.
> - `AdEvent.PlacementEvent` groups placement events, with non-null `placementKey`/`format` and the effective `adUnitId`.
> - `AdError.NoFill/NetworkError/Timeout/LoadFailed/ShowFailed/SdkInitFailed` are classes carrying optional `sdkDetails`. `AdError.Internal(detail, cause)`. `SdkErrorDetails` adds `codeName`.
> - `ConsentState.Unknown` constant. `DiagnosticsReport.environment`/`testMode` are nullable (null before initialization). `AdEnvironment` lives in `com.company.admob.config`.
All public declarations use explicit API mode. **No `com.google.*` type appears in any signature here**, except in `@GoogleSdkEscapeHatch` declarations.

## 3.1 Entry point (`admob-core`, package `com.company.admob`)

```kotlin
/** Process-wide facade. Delegates to an AdsClient built from the config. */
public object CompanyAdMob : AdsClient {
  /** Application.onCreate. Main-thread friendly, idempotent, no network. */
  @JvmStatic public fun initialize(application: Application, config: AdMobConfig)
  /** The underlying client, for DI (bind AdsClient to this in a Hilt module). */
  @JvmStatic public val client: AdsClient
}

public interface AdsClient {
  public val state: StateFlow<AdsState>
  public val isReady: Boolean                                   // state.value == Ready
  /** Launch-time consent + SDK init. Idempotent; attaches to in-flight work on config change. */
  public fun start(activity: Activity, onComplete: ((AdsState) -> Unit)? = null)
  public suspend fun awaitReady(timeout: Duration = 10.seconds): Boolean

  public val privacy: PrivacyApi
  public val interstitial: InterstitialApi
  public val rewarded: RewardedApi
  public val rewardedInterstitial: RewardedInterstitialApi
  public val appOpen: AppOpenApi
  public val banner: BannerApi
  public val native: NativeApi
  public val diagnostics: DiagnosticsApi

  public val events: SharedFlow<AdEvent>
  /** Premium / purchased ad-free. Destroys caches, hides banners/native, blocks loads. */
  public fun setAdFree(adFree: Boolean)

  // --- Simple API (one-liners delegating to the sub-APIs) ---
  public fun showInterstitial(activity: Activity, placement: InterstitialPlacement,
                              onResult: (FullScreenResult) -> Unit = {})
  public fun showRewarded(activity: Activity, placement: RewardedPlacement,
                          onResult: (RewardResult) -> Unit)
  public fun preload(vararg placements: AdPlacement)
}
```

## 3.2 States, results, errors, events

```kotlin
public sealed interface AdsState { NotInitialized; AwaitingStart; GatheringConsent; InitializingSdk; Ready;
                                   Unavailable(reason: UnavailableReason); Failed(error: AdError) }
public enum class UnavailableReason { CONSENT_NOT_OBTAINED, DISABLED_BY_CONFIG, DISABLED_BY_REMOTE, AD_FREE_USER }

public sealed interface AdAvailability { Unavailable; Loading; Ready; Failed(error: AdError); Disabled }

public sealed interface FullScreenResult {
  public data class Dismissed(val clicked: Boolean) : FullScreenResult
  public data class NotShown(val error: AdError) : FullScreenResult
  public val wasShown: Boolean
}
public sealed interface RewardResult {
  public data class Earned(val reward: Reward, val rewardId: String) : RewardResult
  public data object NotEarned : RewardResult               // shown & dismissed early
  public data class Failed(val error: AdError) : RewardResult  // never shown
}
public data class Reward(val type: String, val amount: Int)

public sealed interface AdError { val message: String; val sdkDetails: SdkErrorDetails?
  // NotInitialized, ConsentRequired, InvalidConfiguration, Disabled, NoFill, NetworkError, Timeout,
  // LoadFailed, NotReady, Expired, ShowFailed, ActivityUnavailable, AlreadyShowing,
  // FrequencyCapped(retryAfter), SdkInitFailed, Internal
}
public data class SdkErrorDetails(val domain: String?, val code: Int, val message: String, val responseId: String?)

public sealed interface AdEvent { val placementKey: String?; val format: AdFormat?; val timestampMillis: Long
  // LoadRequested, Loaded, LoadFailed, ShowRequested, NotShown, Shown, Impression, Clicked,
  // Dismissed, RewardEarned, Paid(revenue: AdRevenue, source: AdSourceInfo?), Expired,
  // Destroyed, ConsentUpdated, StateChanged, ConfigurationWarning
}
public data class AdRevenue(val valueMicros: Long, val currencyCode: String, val precision: RevenuePrecision)
public enum class RevenuePrecision { UNKNOWN, ESTIMATED, PUBLISHER_PROVIDED, PRECISE }
public data class AdSourceInfo(val name: String?, val id: String?, val instanceName: String?, val instanceId: String?,
  val adapterClassName: String?, val responseId: String?, val mediationGroupName: String?,
  val abTestName: String?, val abTestVariant: String?)
public enum class AdFormat { BANNER, INTERSTITIAL, REWARDED, REWARDED_INTERSTITIAL, APP_OPEN, NATIVE }
```

## 3.3 Placements

```kotlin
public sealed interface AdPlacement { public val key: String; public val format: AdFormat }
public class BannerPlacement(key: String) : AdPlacement
public class InterstitialPlacement(key: String) : AdPlacement
public class RewardedPlacement(key: String) : AdPlacement
public class RewardedInterstitialPlacement(key: String) : AdPlacement
public class AppOpenPlacement(key: String) : AdPlacement
public class NativePlacement(key: String) : AdPlacement
// key: [a-z0-9_]{1,40}, validated in init; equals/hashCode by (format,key)
```

## 3.4 Format APIs

```kotlin
public interface FullScreenAdApi<P : AdPlacement> {
  public fun preload(placement: P)
  public fun availability(placement: P): StateFlow<AdAvailability>
  public fun isReady(placement: P): Boolean
  public fun clear(placement: P)                                    // drop cached ad, cancel retries
}
public interface InterstitialApi : FullScreenAdApi<InterstitialPlacement> {
  public fun show(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit = {})
  public suspend fun showAndAwait(activity: Activity, placement: InterstitialPlacement): FullScreenResult
}
public interface RewardedApi : FullScreenAdApi<RewardedPlacement> {
  public fun show(activity: Activity, placement: RewardedPlacement, options: RewardedShowOptions = RewardedShowOptions(),
                  onResult: (RewardResult) -> Unit)
  public suspend fun showAndAwait(activity: Activity, placement: RewardedPlacement,
                          options: RewardedShowOptions = RewardedShowOptions()): RewardResult
  /** Earned results whose suspend caller was cancelled. Single consumer. */
  public val unclaimedResults: Flow<RewardResult.Earned>
}
public data class RewardedShowOptions(val serverSideVerificationCustomData: String? = null)
public interface RewardedInterstitialApi : FullScreenAdApi<RewardedInterstitialPlacement> { /* same show shapes as RewardedApi */ }

public interface AppOpenApi {
  public fun showOnColdStart(activity: Activity, maxWait: Duration = 4.seconds, onComplete: (FullScreenResult) -> Unit)
  public fun skipNextForeground()
  public fun suppressWhile(owner: LifecycleOwner)                   // suppressed while owner ≥ STARTED
  public fun setEnabled(enabled: Boolean)
  public val availability: StateFlow<AdAvailability>
}

public interface BannerApi {
  public fun create(activity: Activity, placement: BannerPlacement, options: BannerOptions = BannerOptions()): BannerAdHandle
}
public data class BannerOptions(val size: BannerSize = BannerSize.AnchoredAdaptive, val reserveSpace: Boolean = true)
public sealed interface BannerSize { AnchoredAdaptive; InlineAdaptive(maxHeightDp: Int); MediumRectangle; Standard }
public interface BannerAdHandle {
  public val view: View                                  // add this to your container
  public val state: StateFlow<BannerState>
  public val expectedHeightPx: Int
  public fun bindTo(owner: LifecycleOwner)               // destroy on ON_DESTROY
  public fun destroy()                                   // idempotent
}
public sealed interface BannerState { Loading; Loaded; Failed(error: AdError); Hidden(reason: UnavailableReason?) }

public interface NativeApi {
  public fun preload(placement: NativePlacement)
  public fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit)
  public suspend fun load(placement: NativePlacement): NativeAdResult
  public fun render(handle: NativeAdHandle, container: ViewGroup, layout: NativeAdLayout,
                    renderer: NativeAdRenderer? = null): View
}
public sealed interface NativeAdResult { Loaded(handle: NativeAdHandle); Failed(error: AdError) }
public interface NativeAdHandle : Closeable {
  public val placement: NativePlacement
  public val assets: NativeAdAssets
  public val isDestroyed: Boolean
  public fun destroy(); override fun close() = destroy()
}
public data class NativeAdAssets(val headline: String, val body: String?, val callToAction: String?,
  val advertiser: String?, val starRating: Double?, val price: String?, val store: String?,
  val icon: Drawable?, val hasVideo: Boolean, val mediaAspectRatio: Float?)
public data class NativeAdLayout(@LayoutRes val layoutRes: Int, @IdRes val headlineId: Int,
  @IdRes val bodyId: Int = 0, @IdRes val callToActionId: Int = 0, @IdRes val iconId: Int = 0,
  @IdRes val mediaContainerId: Int = 0, @IdRes val advertiserId: Int = 0, @IdRes val starRatingId: Int = 0,
  @IdRes val priceId: Int = 0, @IdRes val storeId: Int = 0, @IdRes val attributionId: Int = 0)
public fun interface NativeAdRenderer { public fun onBind(root: View, assets: NativeAdAssets) }
```

## 3.5 Privacy

```kotlin
public interface PrivacyApi {
  public val consentState: StateFlow<ConsentState>
  public val isPrivacyOptionsRequired: StateFlow<Boolean>
  public fun showPrivacyOptions(activity: Activity, onDismissed: (AdError?) -> Unit = {})
  public fun resetConsentForTesting()                   // no-op + error log in PRODUCTION
}
public data class ConsentState(val status: ConsentStatus, val canRequestAds: Boolean,
                               val privacyOptionsRequired: Boolean, val lastError: AdError?)
public enum class ConsentStatus { UNKNOWN, REQUIRED, NOT_REQUIRED, OBTAINED }
```

## 3.6 Configuration

> **Implemented in PROMPT 03; the source and `admob-core.api` are authoritative.** Decisions made during implementation:
> - `AdMobConfig.Builder` exposes each option group both as a property (`testing`, `privacy`, `frequency`, `logging`, `advanced`, `placements`) and as a DSL block function. Java uses the chainable `environment(…)`, `appId(…)`, `addTestDeviceId(…)`, `addEventListener(…)`, `remoteConfig(…)` and `addBanner/addInterstitial/addRewarded/addRewardedInterstitial/addAppOpen/addNative(placement, adUnitId)`, or the group getters (`getPrivacy().setMaxAdContentRating(…)`). `Duration` setters are mangled and not callable from Java (ADR-013).
> - The default `environment` is **DEVELOPMENT** (fail-safe: a forgotten setting shows test ads, and non-debuggable builds log an error-level warning).
> - Environment-dependent defaults are nullable and resolved internally: `testing.useDemoAdUnits` (null → true in STAGING), `logging.level` (null → DEBUG/WARN), `advanced.failFast` (null → true outside PRODUCTION).
> - Per ADR-011, option groups (`TestingOptions`, `PrivacyOptions`, `FrequencyOptions`, `LoggingOptions`, `AdvancedOptions`), `PlacementConfig` subclasses, `AdOverrides` and `PlacementOverride` are regular classes with `equals`/`hashCode`. `RetryPolicy` and `FrequencyRule` stay data classes. `PlacementConfig` constructors are internal; placements are created only through `placements { }`.
> - `frequency.includeRewardedInGlobalInterval` (default false) makes the rewarded exclusion configurable, as PROMPT 09 requires.
> - `privacy.consentSyncId: String?` (default null; blank is treated as null) passes UMP's consent sync ID through. Added in PROMPT 05. `toString` reports it only as set or unset, because it identifies a user.
> - `GoogleRequestCustomizer` lives in the root package next to `GoogleSdkEscapeHatch`. Remote types live in `com.company.admob.config.remote`. `@AdMobConfigDsl` is the DSL marker.
> - Duplicate placement keys (across all formats) build fine and are reported by validation, never thrown.
> - `AdMobConfig`'s constructor is `internal` (not `private`) so the nested `Builder` can call it without a public synthetic accessor in the binary API; externally it's equally inaccessible.

```kotlin
public fun adMobConfig(block: AdMobConfig.Builder.() -> Unit): AdMobConfig
public class AdMobConfig private constructor(...) { public class Builder { /* DSL + Java setters */ } }
public enum class AdEnvironment { DEVELOPMENT, STAGING, PRODUCTION }
public enum class AgeTreatment { UNSPECIFIED, CHILD, TEEN }
public enum class MaxAdContentRating { G, PG, T, MA }
public enum class DebugGeography { DISABLED, EEA, REGULATED_US_STATE, OTHER }
public enum class PreloadStrategy { AFTER_READY, ON_DEMAND, NONE }
public data class RetryPolicy(val initialDelay: Duration, val multiplier: Double, val maxDelay: Duration,
                              val maxAttempts: Int, val jitterRatio: Double)
public data class FrequencyRule(val minInterval: Duration = Duration.ZERO, val maxPerSession: Int = Int.MAX_VALUE)
@JvmInline public value class AdUnitId(public val value: String)
public fun interface AdConfigProvider { public fun overrides(): Flow<AdOverrides> }
public object StaticConfigProvider : AdConfigProvider
public fun interface AdEventListener { public fun onEvent(event: AdEvent) }
public fun interface AdLogger { public fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) }
public enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR, NONE }
```

## 3.7 Diagnostics

```kotlin
public interface DiagnosticsApi {
  public fun snapshot(): DiagnosticsReport          // data class; toString() = redacted multi-line report
  public fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit = {})
  public val recentEvents: List<AdEvent>              // ring buffer (last 100), DEVELOPMENT/STAGING only
}
public data class DiagnosticsReport(val libraryVersion: String, val googleSdkVersion: String?, val environment: AdEnvironment,
  val state: AdsState, val consent: ConsentState, val testMode: TestModeInfo, val placements: List<PlacementDiagnostics>,
  val activeFullScreen: String?, val currentActivity: String?, val lastError: AdError?)
```

## 3.8 Opt-in markers

```kotlin
@RequiresOptIn(level = RequiresOptIn.Level.WARNING) public annotation class ExperimentalCompanyAdMobApi
@RequiresOptIn(level = RequiresOptIn.Level.ERROR,
  message = "Exposes Google Mobile Ads SDK types; may break on any Google SDK upgrade.")
public annotation class GoogleSdkEscapeHatch

@GoogleSdkEscapeHatch
public fun interface GoogleRequestCustomizer {       // registered in config.advanced { }
  public fun customize(placement: AdPlacement, builder: Any /* AdRequest.Builder | BannerAdRequest.Builder | NativeAdRequest.Builder */)
}
```
*(`builder: Any` keeps even the escape hatch out of the binary API dump. The implementation casts to the Next-Gen builder type. This is documented as unstable.)*

## 3.9 `admob-views`

```kotlin
public class AdBannerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout
//   attrs: company_admob_placement (string key), company_admob_bannerSize (enum), company_admob_collapseWhenEmpty (bool)
public fun ViewGroup.attachBanner(owner: LifecycleOwner, placement: BannerPlacement,
                                  options: BannerOptions = BannerOptions()): BannerAdHandle
public fun ViewGroup.attachNativeAd(owner: LifecycleOwner, placement: NativePlacement,
                                    layout: NativeAdLayout = NativeTemplates.Medium,
                                    renderer: NativeAdRenderer? = null,
                                    onResult: (NativeAdResult) -> Unit = {})
public class AdNativeView : FrameLayout                     // XML: company_admob_placement, company_admob_template
public object NativeTemplates { public val Small: NativeAdLayout; public val Medium: NativeAdLayout; public val Large: NativeAdLayout }
public class NativeAdHolder(placement: NativePlacement) {   // keep in a ViewModel
  public fun bindTo(container: ViewGroup, owner: LifecycleOwner, layout: NativeAdLayout = NativeTemplates.Medium)
  public fun clear()
}
```

## 3.10 `admob-compose`

```kotlin
@Composable public fun AdBanner(placement: BannerPlacement, modifier: Modifier = Modifier,
  options: BannerOptions = BannerOptions(), placeholder: (@Composable () -> Unit)? = null,
  onStateChange: (BannerState) -> Unit = {})
@Composable public fun AdNative(placement: NativePlacement, modifier: Modifier = Modifier,
  layout: NativeAdLayout = NativeTemplates.Medium, slotKey: Any = Unit,
  renderer: NativeAdRenderer? = null, placeholder: (@Composable () -> Unit)? = null)
@Composable public fun rememberNativeAd(placement: NativePlacement, slotKey: Any = Unit): State<NativeAdResult?>
@Composable public fun rememberAdAvailability(placement: AdPlacement): State<AdAvailability>
@Composable public fun rememberFullScreenAdLauncher(): FullScreenAdLauncher
public interface FullScreenAdLauncher {
  public fun showInterstitial(placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit = {})
  public fun showRewarded(placement: RewardedPlacement, onResult: (RewardResult) -> Unit)
  public fun showRewardedInterstitial(placement: RewardedInterstitialPlacement, onResult: (RewardResult) -> Unit)
}
```

## 3.11 `admob-testing`

```kotlin
public class FakeAdsClient(initialState: AdsState = AdsState.Ready) : AdsClient {
  public fun setState(state: AdsState)
  public fun enqueueInterstitialResult(placement: InterstitialPlacement, result: FullScreenResult)
  public fun enqueueRewardResult(placement: RewardedPlacement, result: RewardResult)
  public fun setAvailability(placement: AdPlacement, availability: AdAvailability)
  public val recordedCalls: List<FakeCall>
  public fun emit(event: AdEvent)
}
```

## 3.12 Threading and callback contract (documented on every API)

- Every public function may be called from any thread.
- Every callback and Flow emission to the host arrives on the **main thread**.
- `show` callbacks are called **exactly once**.
- No public function throws for ad-related failures. Only documented programmer errors throw (for example, calling a `show` before `initialize()` with `failFast`).
