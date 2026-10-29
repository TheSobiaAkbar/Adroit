# Setup: dependencies, initialization, demo billing, the ad bridge

## Contents
1. Dependencies
2. `BillingSettings`, every field
3. Initialization behaviour
4. Demo billing
5. The ad-free bridge (apps with ads)
6. Config sources (without Firebase)
7. Import map

## 1. Dependencies

Maven Central is the default. For JitPack pinning to an unreleased commit, see the adroit-admob skill's `setup.md` §1:
same repo, group `com.github.thesobiaakbar.adroit`.

```toml
[libraries]
adroit-billing          = { module = "io.github.thesobiaakbar:adroit-billing",          version.ref = "adroit" } # core, no UI
adroit-billing-compose  = { module = "io.github.thesobiaakbar:adroit-billing-compose",  version.ref = "adroit" } # Paywall composable
adroit-billing-views    = { module = "io.github.thesobiaakbar:adroit-billing-views",    version.ref = "adroit" } # AdroitPaywall, PaywallView
adroit-billing-firebase = { module = "io.github.thesobiaakbar:adroit-billing-firebase", version.ref = "adroit" } # Firebase paywalls + Analytics
adroit-billing-testing  = { module = "io.github.thesobiaakbar:adroit-billing-testing",  version.ref = "adroit" } # testImplementation
adroit-update           = { module = "io.github.thesobiaakbar:adroit-update",           version.ref = "adroit" } # optional: Play update on the splash
```

| Module | Brings | Notes |
|---|---|---|
| `adroit-billing` | Play Billing `billing:9.1.0`, coroutines | Products, purchases, entitlements, paywall rules, `PaywallController`, `PriceText`, `HttpPurchaseVerifier`, demo mode. |
| `adroit-billing-compose` | Compose and Material 3 | `Paywall`, `rememberPaywallController`, the `PaywallScope` blocks, `PaywallTheme`. Needs compileSdk 37 and AGP 9.1 (Compose 1.12). |
| `adroit-billing-views` | `androidx.activity` only (no AppCompat, Material or Fragment) | `AdroitPaywall` (`show` / `intent` / `contract`), `AdroitPaywallActivity`, `PaywallView`. |
| `adroit-billing-firebase` | Firebase Remote Config and Analytics, `adroit-remote-config` | `FirebaseBillingConfig.source()` and `FirebaseBillingEvents`. Doesn't bring the ads SDK. |
| `adroit-billing-testing` | | `FakeBilling`, `TestProducts`, `TestPaywalls`. |

Notes:
- **Don't add `com.android.billingclient:billing` yourself.**
- The billing and ads modules never depend on each other. A feature module can depend on `adroit-billing` without
  pulling in ads.
- **Requirements:** minSdk 24, Kotlin 2.2 or newer, Java 11. An app with ads and billing needs Kotlin 2.2, even
  though the ads modules alone accept 2.1.
- `adroit-billing-compose` and `adroit-billing-views` both bring `adroit-billing` with them.
- **Remote Config:** `FirebaseBillingConfig.source()` starts the shared Remote Config session (one fetch per launch)
  by itself, so an IAP-only app needs no fetch code either. `FirebaseRemoteConfig` and `RemoteConfigSession` are
  available through `adroit-billing-firebase`.
- **compileSdk 35 or newer.** Play Billing brings `androidx.core` 1.15, which needs it. Use 37 with
  `adroit-billing-compose`.
- The library adds the `BILLING` permission.

## 2. `BillingSettings`

```kotlin
BillingSettings(
    products: List<ProductDefinition>,                       // AppProducts.entries
    paywalls: List<PaywallDefinition> = emptyList(),         // AppPaywalls.entries
    config: BillingConfigSource = BillingConfigSource.none(),// FirebaseBillingConfig.source() for remote paywalls
    overrides: Map<String, String> = emptyMap(),             // param name → JSON; beats config (QA builds)
    verifier: PurchaseVerifier? = null,                      // server verification (HttpPurchaseVerifier)
    entitlementProvider: EntitlementProvider? = null,        // server is the source of truth for grants
    events: List<BillingEventListener> = emptyList(),        // FirebaseBillingEvents(context)
    adFreeEntitlements: Set<String>? = null,                 // null = ANY entitlement makes the user ad-free
    offlineGraceHours: Int = 72,                             // 1..720: how long a saved subscription is trusted offline
    catalogMaxAgeHours: Int = 6,
    attributionToken: AttributionToken = AttributionToken.LOCAL_ONLY,  // or PROFILE_ID
    logLevel: BillingLogLevel = BillingLogLevel.WARN,
    offerSelector: OfferSelector? = null,                    // replace the library's offer choice
    offerEligibility: OfferEligibility? = null,              // who gets developer-determined offers (tag "dev")
    developerDeterminedTags: Set<String> = setOf("dev"),
    demo: DemoBilling = DemoBilling(),                       // DemoBilling(BuildConfig.DEBUG)
)
```

Typical `Application.onCreate`:
```kotlin
class MyApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        val firebase = FirebaseApp.getApps(this).isNotEmpty()        // a flavour without google-services.json still runs
        AdroitBilling.initialize(this, BillingSettings(
            products = AppProducts.entries,
            paywalls = AppPaywalls.entries,
            config = if (firebase) FirebaseBillingConfig.source() else BillingConfigSource.none(),
            events = if (firebase) listOf(FirebaseBillingEvents(this)) else emptyList(),
            demo = DemoBilling(BuildConfig.DEBUG),
        ))
        // With ads: see §5. With consumables: Coins.start(this, appScope) (products-purchases.md §6).
    }
}
```
- `FirebaseBillingEvents(context, logRevenue = false)` sends `adroit_billing_*` events to Analytics. Keep
  `logRevenue` off when Play is linked to Firebase, or revenue is counted twice.

## 3. Initialization behaviour

- `initialize` reads the saved entitlements **synchronously**, so `AdroitBilling.entitlements.value` is right as soon
  as it returns. It then connects to Play in the background.
- **Validation:** an unknown product in a paywall, duplicate IDs, or a bad ID format (products
  `[a-z0-9][a-z0-9._]{0,138}`, entitlements `[A-Za-z0-9_.]{1,40}`, base plans and offers `[a-z0-9][a-z0-9-]{0,62}`).
  - A debuggable app throws `IllegalStateException`, listing every problem. That is your step-1 check.
  - A release build logs, and billing stays off: every call returns `BillingError.NotInitialized`.
- A second call with equal settings does nothing. With different settings, it throws in debug and keeps the first in
  release.

## 4. Demo billing

`demo = DemoBilling(BuildConfig.DEBUG)` replaces Google Play in debuggable builds. It works on any device or emulator
before Play Console has the products.
- **Prices** are made-up USD, marked **"(DEMO)"**.
- **Subscriptions** get the base plans `weekly`, `monthly` (with a 3-day trial offer tagged `trial`) and `yearly`
  (with an intro offer tagged `intro`), plus any base plan the paywall defaults name.
- **The demo offers are fixed.** Demo has only the `trial` offer on `monthly` and the `intro` offer on `yearly`. A
  paywall that asks for a trial on another base plan shows that plan's base price in demo. Check the real offer on
  Play's test track.
- **A purchase** opens a dialog with three choices: **Buy**, **Pending payment** (completes 15 s later) and **Cancel**.
- **Purchases are kept** in SharedPreferences until they are consumed. To start over, clear the app's data.
- A release build ignores demo billing and logs an ERROR. Real testing needs `DemoBilling(false)`, a testing track and
  license testers (`play-console-release.md`).

## 5. The ad-free bridge (apps with ads)

The only code that links billing and ads:
```kotlin
// Application.onCreate, after both Adroit.initialize and AdroitBilling.initialize
Adroit.setAdFree(AdroitBilling.entitlements.value.adFree)            // synchronous: the splash reads it right away
appScope.launch { AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) } }
```
Apps with an `AD_FREE` build field for benchmark builds (adroit-admob, `architecture.md`) wrap both lines in
`if (!BuildConfig.AD_FREE)`.

- **A subscriber who reinstalls, or clears data, sees ads on the first launch.** The saved entitlements are gone, so
  the snapshot is `Unknown` until Play answers. That first splash may show consent and an ad. From then on (and on the
  next launch) the user is ad-free.
  - This is unavoidable without a backend that knows the user.
  - Don't try to delay the splash for Play: it may never answer offline.
- `adFree` is true while the user has **any** entitlement. To narrow it, set `adFreeEntitlements = setOf("premium")`,
  in code or in the Firebase `AdroitBilling` parameter. Do this when, for example, a "coins" entitlement must not
  remove ads.
- `Adroit.setAdFree` isn't persisted, which is why the bridge runs on every launch.
- **Effects:**
  - No new ad request after the user becomes premium. Banners hide immediately.
  - `SplashFlow` skips consent and the ad, and gives a short splash.
  - The paywalls stop showing: `shouldShow` returns `Skip(ENTITLED)`.

## 6. Config sources without Firebase

```kotlin
BillingConfigSource.of(mapOf("Onboarding" to """{"close":{"delaySeconds":5}}"""))  // fixed values
BillingConfigSource.local(context, R.raw.billing_config)   // one JSON object whose top-level keys are parameter names
```
Layers merge field by field, in this order: `overrides` beat the source, and the source beats the enum defaults.

## 7. Import map

| Package | Types and functions |
|---|---|
| `io.github.thesobiaakbar.adroit.billing` | `AdroitBilling`, `BillingClientApi`, `BillingSettings`, `DemoBilling`, `ProductDefinition`, `ProductSpec`, `PaywallDefinition`, `PaywallSpec`, `PlanSpec`, `OfferRule`, `OfferPolicy`, `OfferFallback`, `CloseBehavior`, `CloseButtonPosition`, `BackBehavior`, `ShowRules`, `Campaign`, `PaywallTexts`, `PaywallStyle`, `PaywallTemplate`, `SuccessAction`, `ShowDecision`, `SkipReason`, `PaywallController`, `PaywallUiState`, `PaywallModel`, `PaywallPlan`, `PaywallCloseReason`, `Busy`, `UiMessage`, `EntitlementSnapshot`, `EntitlementGrant`, `SnapshotStatus`, `GrantState`, `PurchaseRequest`, `OfferSelection`, `PurchaseResult`, `PurchaseUpdate`, `RestoreResult`, `ConsumableGrant`, `SubscriptionReplacement`, `ReplacementMode`, `ProductInfo`, `OfferInfo`, `Price`, `BillingPeriod`, `PriceText`, `BillingConfigSource`, `PurchaseVerifier`, `EntitlementProvider`, `HttpPurchaseVerifier` |
| `io.github.thesobiaakbar.adroit.billing.compose` | `Paywall`, `rememberPaywallController`, `PaywallScope`, `PaywallTheme`, `PaywallDefaults` |
| `io.github.thesobiaakbar.adroit.billing.views` | `AdroitPaywall`, `PaywallLaunch`, `PaywallView`, `AdroitPaywallActivity` |
| `io.github.thesobiaakbar.adroit.billing.firebase` | `FirebaseBillingConfig`, `FirebaseBillingEvents` |
| `io.github.thesobiaakbar.adroit.billing.testing` | `FakeBilling`, `TestProducts`, `TestPaywalls` |
| `io.github.thesobiaakbar.adroit.firebase` (from `adroit-firebase`) | `RemoteValue`, for the app's own switches such as `PremiumEnabled` |
