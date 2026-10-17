# 06 — Adroit Billing: research, architecture and decisions

Status: **proposed** (2026-10-16). Becomes ADR-019 once accepted. The implementation prompts live in
`docs/implementation-prompts/billing/`.

Adroit Billing is a second library family in this repository, next to the ads modules. It adds Google Play in-app
purchases and subscriptions, entitlements, purchase attribution by placement, and paywalls controlled from Firebase,
following the same habits as the ads:

- one enum per app declares each item with typed defaults;
- the enum entry name is the Firebase Remote Config key;
- the remote JSON overrides the defaults;
- Firebase is fetched once per session and never blocks the app.

---

## 1. Research findings (verified 2026-10-16)

| Item | Finding | Source |
|---|---|---|
| Play Billing Library | **9.1.0** (2026-06-18) is the latest stable, after 9.0.0 (2026-05-19). `com.android.billingclient:billing` and `billing-ktx` are both published. | Google Maven `maven-metadata.xml`, release notes |
| Play deadline | New apps and updates must use **PBL 8+** by 2026-08-31 (extension to 2026-11-01). We target 9.x. | release notes |
| minSdk | PBL 8.1+ needs **minSdk 23**. Adroit is minSdk 24, so this is fine. | release notes 8.1.0 |
| AndroidX core | PBL 9.0 needs **androidx.core 1.9+**, which turns a blocked Play Store into `BILLING_UNAVAILABLE`. | release notes 9.0.0 |
| Kotlin | PBL 8.1+ is built for Kotlin 2.2.0. Adroit's libraries use languageVersion/apiVersion 2.2, so it's compatible. | release notes 8.1.0 |
| Removed in 8.0 | `queryPurchaseHistory()`, `querySkuDetailsAsync()`, the no-arg `enablePendingPurchases()`, and `queryPurchasesAsync(String, …)`. **Don't use them.** | release notes 8.0.0 |
| Added in 8.0 | `enableAutoServiceReconnection()`. `queryProductDetailsAsync` returns a `QueryProductDetailsResult` that includes **unfetched products** with a per-product status. One-time products have **multiple purchase options and offers**. `launchBillingFlow` has sub-response codes (`PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS`, `USER_INELIGIBLE`). | release notes 8.0.0 |
| Added in 8.1 | **Suspended subscriptions**: a `queryPurchasesAsync` parameter includes them, and `Purchase.isSuspended()` is true for them. Don't grant access; send the user to the subscriptions center. `SubscriptionProductReplacementParams` (per-product replacement, `KEEP_EXISTING` mode) replaces `SubscriptionUpdateParams#setSubscriptionReplacementMode`, which will be deprecated. | release notes 8.1.0 |
| 8.2 / 8.3 / 9.1 | External offer / external payment / Billing Choice APIs. **Out of scope** (alternative billing is a legal and program decision per app). | release notes |
| Firebase BoM | The repo pins 34.19.0, used by `adroit-firebase`. Billing reuses it. | `gradle/libs.versions.toml` |

**Verified in B01 (2026-10-16)** from the 9.1.0 AAR (`javap` on `classes.jar`, POM and manifest) and the API
reference:

- **Artifact:** `com.android.billingclient:billing:9.1.0`, the Java artifact.
  - Not `billing-ktx`: its POM raises `kotlin-stdlib` to 2.2.10, above the library's `coreLibrariesVersion` 2.2.0, for
    four suspend wrappers the library writes itself with `suspendCancellableCoroutine`.
- **Manifest:** `minSdkVersion 23`, and it merges `com.android.vending.BILLING`.
- **Transitive dependencies:**
  - `androidx.core:core:1.15.0`. Its AAR metadata has `minCompileSdk=35`, so **apps using billing need compileSdk ≥
    35**, while ads-only apps still need ≥ 34.
  - `androidx.activity:1.2.3`.
  - `play-services-base`/`basement`/`location`/`tasks`.
  - datatransport, which brings `com.google.firebase:firebase-encoders*`. These are serializers, not Firebase
    products; the dependency guard allows them.
- **Methods that exist:**
  - `PendingPurchasesParams.Builder.enableOneTimeProducts()` and `.enablePrepaidPlans()`;
  - `BillingClient.Builder.enableAutoServiceReconnection()` and `enablePendingPurchases(PendingPurchasesParams)`;
  - `QueryPurchasesParams.Builder.includeSuspendedSubscriptions(boolean)`;
  - `QueryProductDetailsResult.getProductDetailsList()` and `getUnfetchedProductList()`;
  - `OneTimePurchaseOfferDetails.getPurchaseOptionId()`, `getOfferId()`, `getOfferToken()` and `getOfferTags()`;
  - `ProductDetailsParams.Builder.setSubscriptionProductReplacementParams(...)`, with `ReplacementMode`
    `WITH_TIME_PRORATION`, `CHARGE_PRORATED_PRICE`, `WITHOUT_PRORATION`, `CHARGE_FULL_PRICE`, `DEFERRED` and
    `KEEP_EXISTING`;
  - `Purchase.isSuspended()` and `getPendingPurchaseUpdate()`.
- **`setObfuscatedProfileId`:** "Setting this field requests the user's obfuscated account id to be pass[ed] via
  `setObfuscatedAccountId`." So `attributionToken = PROFILE_ID` must also send an account ID. Use the
  `setAccount` hash, or a random per-install ID when the app has no accounts. Both fields allow up to 64
  characters and no PII.

Every prompt that touches the Play API must still check the API reference
(`https://developer.android.com/reference/com/android/billingclient/api/package-summary`) and the integration guide
before writing code.

### Things Google Play does not allow or the client cannot do
1. **Firebase can't create or change Play prices, trials or intro pricing.** Those are base plans and offers in Play
   Console. Firebase only *chooses among* the offers Play returns for this user, and controls how they're presented.
2. **The client can't see renewal, expiry, refunds or cancellations** while the app isn't running. It sees only
   `Purchase` objects: active (or suspended) subscriptions and unconsumed one-time products. Expiry date, cancellation
   and grace period need the **Play Developer API on a backend** (`purchases.subscriptionsv2.get`) or **RTDN**.
3. **Arbitrary placement keys can't be attached to a Play transaction.** The only developer fields are
   `obfuscatedAccountId` and `obfuscatedProfileId` (64 chars each, no PII). Attribution is kept locally and sent to
   analytics and the backend (§6).
4. **Offer eligibility is Play's call.** `subscriptionOfferDetails` lists only offers the user is eligible for, except
   *developer-determined* offers. Those are always returned, and the app (via Firebase rules) decides.
5. **Pending purchases must not grant access.** Purchases must be **acknowledged within 3 days** or Play refunds them.
6. **Client-only verification isn't fraud-proof.** Without a backend, a rooted device can fake entitlements. This is
   documented, not hidden.

---

## 2. Modules

```
                ┌──────────────────────┐
                │    adroit-billing    │  domain, public API, Play adapter (internal port),
                │                      │  entitlements, attribution, paywall config + rules,
                │                      │  PaywallController (UI-agnostic state)
                └───────▲──────▲───────┘
           ┌────────────┘      └────────────┐
┌──────────┴───────────┐        ┌───────────┴───────────┐
│ adroit-billing-views │        │ adroit-billing-compose│   optional UI, one toolkit each
└──────────────────────┘        └───────────────────────┘
┌────────────────────────┐
│ adroit-billing-firebase│──▶ firebase-config + firebase-analytics (NOT adroit-firebase: it brings the ads SDK)
└────────────────────────┘    RC source (fetch-once of its own) + Analytics adapter
┌────────────────────────┐
│ adroit-billing-testing │  FakeBilling, product/purchase builders (test-only consumers)
└────────────────────────┘
samples: sample-xml, sample-compose (both depend on billing + ads; the bridge is 1 line in the app)
```

Decisions:
- **Core and Play together in one module** (`adroit-billing`). This differs from the master prompt's separate
  `billing-play`. A second store is speculative; Play sits behind an **internal** port (`PlayBillingPort`), exactly
  like `AdsSdk` in `adroit-core`, so tests use a fake. If another store is ever needed, the port becomes public then.
- **No dependency between ads and billing.** Ad-only apps don't pull in billing, and billing-only apps don't pull in
  ads. The bridge is the app's single line: `AdroitBilling.entitlements.adFree.onEach(Adroit::setAdFree)` (shown in
  the samples and guides).
- **`adroit-billing-firebase` depends on Firebase directly, not on `adroit-firebase`** (decided in B01).
  `adroit-firebase` has `api(adroit-core)`, which would put the Mobile Ads SDK into billing-only apps, and that SDK
  needs an AdMob app ID in the manifest. Instead, billing-firebase:
  - reads the activated values;
  - runs its own once-per-process `fetchAndActivate`.

  In an app that also uses `RemoteConfigSession`, Firebase answers the second call from its fetch cache (within
  `minimumFetchInterval`), so there's still one network fetch per session. The dependency guard forbids every edge
  from a billing module to an ads module or to the Mobile Ads SDK.
- **No analytics module.** `BillingEventListener` is a `fun interface` in core; the Firebase Analytics adapter lives
  in `adroit-billing-firebase`.
- Package root: `io.github.thesobiaakbar.adroit.billing`. Resource prefix: `adroit_billing_`. Explicit API mode,
  `internal` by default, **no Play types in public signatures**, and API dumps checked (ADR-007).
- Dependencies of `adroit-billing`: `billing` 9.1.x (androidx.core comes with it), coroutines,
  lifecycle-process/runtime, and androidx.annotation. **No** Firebase, Compose, AppCompat, Fragment, Room, Retrofit or DI.
  Persistence uses `SharedPreferences` plus `org.json` (already used by the ad profiles).

---

## 3. Public API (proposal, finalised in B02)

```kotlin
// One file per app: products + paywalls, mirroring SampleAds.
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf(Entitlements.PREMIUM))),
    Lifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf(Entitlements.PREMIUM))),
    Coins100(ProductSpec.consumable("coins_100")),
}
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    Onboarding(PaywallSpec(plans = listOf(PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly")), closeDelaySeconds = 3)),
    Settings(PaywallSpec(...)),
    ExitDiscount(PaywallSpec(...)),
}

// Application.onCreate
AdroitBilling.initialize(
    application,
    BillingSettings(
        products = AppProducts.entries,
        paywalls = AppPaywalls.entries,
        config = FirebaseBillingConfig.source(),   // or BillingConfigSource.local(R.raw.billing_config); default: in-code only
        verifier = null,                           // PurchaseVerifier for a backend; null = client-only (documented)
        events = FirebaseBillingEvents(),          // BillingEventListener; optional
        logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN,
    ),
)

AdroitBilling.entitlements          // StateFlow<EntitlementSnapshot>; .has("premium"), .adFree, .status
AdroitBilling.connection            // StateFlow<BillingConnectionState>
AdroitBilling.catalog               // StateFlow<CatalogState>: Loading | Ready(products) | Failed(error)
AdroitBilling.purchaseUpdates       // SharedFlow<PurchaseUpdate>, including out-of-session and pending→done
suspend fun AdroitBilling.purchase(activity, PurchaseRequest): PurchaseResult
suspend fun AdroitBilling.restore(): RestoreResult
suspend fun AdroitBilling.refresh()
fun AdroitBilling.openManageSubscriptions(activity, product: ProductDefinition? = null)
fun AdroitBilling.setAccount(obfuscatedAccountId: String?)      // user switching
AdroitBilling.consumables           // Flow<ConsumableGrant>; app persists, then grant.confirm() → consumed
AdroitBilling.paywalls              // shouldShow(def, placement), controller(def, placement), recordShown…

// UI (optional)
Paywall(AppPaywalls.Onboarding, placement = "onboarding", onClose = …)          // adroit-billing-compose
AdroitPaywall.show(activity, AppPaywalls.Onboarding, placement = "onboarding")  // adroit-billing-views
```

`PurchaseRequest(product, basePlanId = null, offer = OfferSelection.Auto, placement, campaignId = null, paywallId = null)`.

### Threading and hosts
| Call | Needs | Where |
|---|---|---|
| `initialize`, `setAccount` | Application | `Application.onCreate` |
| `entitlements`, `catalog`, `connection`, `purchaseUpdates`, `refresh`, `restore`, `paywalls.shouldShow` | nothing | anywhere, including a ViewModel |
| `purchase`, `openManageSubscriptions`, `AdroitPaywall.show` | an **Activity**, used only during the call and never kept | UI layer; a ViewModel passes it through a host callback |
| Compose `Paywall` | composition | any screen |

The library holds the `BillingClient` in an application-scoped runtime. It connects on `initialize` (lazily, on a
background dispatcher). It reconnects automatically (`enableAutoServiceReconnection`) and re-syncs purchases on
connect and on `ProcessLifecycleOwner` `ON_START`.

---

## 4. Domain model

- `ProductType`: `SUBSCRIPTION`, `NON_CONSUMABLE`, `CONSUMABLE`.
- `ProductInfo`: id, type, title, description, plus `oneTime: OneTimeOption?` / `plans: List<SubscriptionPlan>`.
- `SubscriptionPlan`: `basePlanId`, `offers: List<OfferInfo>`, and `basePhase: PricingPhase`.
- `OfferInfo`: `offerId?`, `offerTags`, `phases: List<PricingPhase>`, `freeTrial: BillingPeriod?`,
  `introPhases`, `token` (internal).
- `PricingPhase`: `formattedPrice`, `priceMicros`, `currency`, `period: BillingPeriod` (parsed ISO-8601 `P1W`, `P3M`,
  `P1Y`…), `cycles`, and `recurrence` (INFINITE / FINITE / NON_RECURRING). **Nothing is hardcoded**: a plan's
  duration and price come only from these fields.
- `BillingPeriod(years, months, weeks, days)` with `approxDays` (for "save x%"; it's marked *approximate* and is
  shown only when the config asks).
- `EntitlementSnapshot`: `active: Map<String, EntitlementGrant>`, `status: Fresh | Cached(since) | Unknown`, and
  `adFree`. `EntitlementGrant` holds product, type, `state: ACTIVE | GRACE_FROM_BACKEND | SUSPENDED | PENDING`,
  source (CLIENT / BACKEND) and `purchaseTimeMillis`.
- `PurchaseResult`: `Purchased(grant, attribution)`, `Pending`, `Cancelled`, `AlreadyOwned`, and
  `Failed(BillingError)`.
- `BillingError` (sealed): `Unavailable`, `FeatureNotSupported`, `ServiceDisconnected`, `ProductNotFound`,
  `OfferNotFound`, `NetworkError`, `ItemAlreadyOwned`, `ItemNotOwned`, `DeveloperError`, `PaymentDeclined`,
  `UserIneligible`, `VerificationFailed`, `AcknowledgeFailed`, `ConsumeFailed`, `BackendError`, `NoActivity`,
  `PurchaseInProgress`, and `Unknown(code, debugMessage)`. Each has `isRetryable`.
- `Attribution`: `attemptId`, `placement`, `paywallId?`, `campaignId?`, `productId`, `basePlanId?`, `offerId?`, and
  `recovered: Boolean`. The placement can be `Attribution.UNKNOWN`.

### Purchase state machine (per attempt)
```
Idle ─purchase()─▶ Launching ─launchBillingFlow OK─▶ AwaitingPlay
AwaitingPlay ─USER_CANCELED─▶ Cancelled
AwaitingPlay ─error─▶ Failed(err)
AwaitingPlay ─PURCHASED─▶ Verifying (verifier, if any) ─ok─▶ Acknowledging/Consuming ─▶ Granted
                                                  └─reject─▶ Failed(VerificationFailed)   (no grant, no ack)
AwaitingPlay ─PENDING─▶ Pending ─(later, via purchaseUpdates or sync)─▶ Verifying …  |  dropped → Failed/expired
Any other attempt while Launching/AwaitingPlay → Failed(PurchaseInProgress)   (single flight per app)
```
Idempotency: a processed-token ledger (a SHA-256 hash of the token, never the raw token) in SharedPreferences. A
token is acknowledged or consumed at most once per process. The consumable grant is delivered until the app
`confirm()`s it, and only then consumed, so a crash between grant and consume re-delivers instead of losing coins.

---

## 5. Firebase configuration (the part you control without a release)

Two kinds of Remote Config keys. Both are optional, and a missing or invalid value falls back to the enum default.

### 5.1 Global key `AdroitBilling`
```json
{
  "enabled": true,
  "adFreeEntitlements": ["premium", "ad_free"],
  "offlineGraceHours": 72,
  "products": {
    "PremiumSub": { "entitlements": ["premium", "ad_free"] },
    "Lifetime":   { "entitlements": ["premium", "ad_free"] }
  },
  "configVersion": 3
}
```
- `products` can change only the **entitlement mapping** of products declared in the enum. Firebase can't add a
  product ID that grants access, so a bad console edit can't hand out premium.
- `offlineGraceHours`: how long a cached subscription entitlement is honoured without a successful Play query (then
  the status becomes `Unknown`, which isn't granted). Non-consumables stay cached indefinitely.

### 5.2 One key per paywall (the enum entry name, for example `Onboarding`)
```json
{
  "enabled": true,
  "template": "plans",                       // plans | single | bottom_sheet | dialog
  "plans": [
    { "product": "PremiumSub", "basePlan": "monthly",
      "offer": { "ids": ["trial-7d"], "trialDays": 7, "tags": ["trial"], "policy": "PREFER_FREE_TRIAL" },
      "badge": "POPULAR" },
    { "product": "PremiumSub", "basePlan": "yearly",
      "offer": { "policy": "LOWEST_FIRST_PAYMENT" }, "badge": "BEST_VALUE", "savingsVs": "monthly" },
    { "product": "Lifetime" }
  ],
  "selected": 1,
  "onOfferUnavailable": "BASE_PLAN",        // BASE_PLAN | HIDE_PLAN | HIDE_PAYWALL

  "close": {
    "delaySeconds": 3,                       // 0 = the X shows at once; N = after N seconds; -1 = no X
    "showCountdown": true,                   // a ring/number counts down until the X appears
    "position": "TOP_END",                   // TOP_START | TOP_END
    "back": "AFTER_DELAY"                    // ALWAYS | AFTER_DELAY | NEVER (system back / predictive back)
  },
  "show": {                                  // same counter/timer semantics as the ad profiles
    "counter": 0, "maxPerSession": 1, "maxPerDay": 3, "timerSeconds": 0,
    "firstAfterLaunches": 0, "hideIfEntitled": ["premium"]
  },
  "campaign": { "id": "autumn26", "start": "2026-10-01T00:00:00Z", "end": "2026-10-31T23:59:59Z",
                "analyticsId": "autumn26_onb" },
  "exitPaywall": "ExitDiscount",             // shown when the user closes this one (at most once per session)
  "texts": { "title": "Go Premium", "subtitle": "…", "features": ["No ads", "All filters"],
             "cta": "Continue", "ctaTrial": "Start {trial} free trial", "footer": "…" },
  "style": { "accent": "#7C4DFF", "background": "#101014", "headerImage": "https://…", "dark": true },
  "showRestore": true, "showManage": false, "showTerms": true,
  "successAction": "CLOSE"                   // CLOSE | STAY_WITH_MESSAGE
}
```
Every field is optional. `{trial}`, `{price}`, `{period}`, `{renewalPrice}` and `{savings}` are filled from **Play
data only**. A text that references `{trial}` on a plan without an eligible free-trial offer falls back to `cta`,
so a trial is never advertised that Play won't give.

**Offer selection** (`offer`) only ever picks among the offers Play returned for this user, minus
developer-determined ones (a tag in `BillingSettings.developerDeterminedTags`, default `dev`) that the app's
`OfferEligibility` didn't allow:
1. explicit `ids`: the first listed ID Play gave wins; none is a miss;
2. otherwise `tags` (all must match) and `trialDays` (a free first phase of exactly N days, `P1W` = 7) narrow the
   offers; nothing left is a miss, so a 7-day trial request never shows Play's 3-day trial;
3. `policy` picks among what is left (among every offer, base plan included, when neither is set):
   `PREFER_FREE_TRIAL`, `PREFER_INTRO_PRICE` (each falling back to the base plan), `LOWEST_FIRST_PAYMENT` (a free
   trial counts as 0), or `BASE_ONLY`. Ties go to Play's order.

A miss applies `onOfferUnavailable` and emits `OfferUnavailable`. The app can replace steps 1–3 with an
`OfferSelector`, whose answer must still be one of the eligible offers. `OfferSelection.Auto` in a manual purchase
with a `paywallId` uses the same selection, falling back to the base plan.

**Show rules** (`AdroitBilling.paywalls.shouldShow`), first failing one wins, each a `PaywallSuppressed` event:
global `enabled`, paywall `enabled`, the campaign window (device clock), `hideIfEntitled` (default: the paywall's
products' entitlements), `firstAfterLaunches`, `maxPerSession`, `maxPerDay` (local date), `counter` (every call counts,
due after N calls since the last showing, as with the ads), `timerSeconds` (only without a counter;
`elapsedRealtime` within a process, wall clock across processes), then no purchasable plan (`NO_PLANS`) or
`OFFER_UNAVAILABLE` once the catalog is ready. While it loads, the paywall shows its loading state.

**Rules for disclosures (always on, not configurable):**
- the renewal price and period are always shown under a trial or intro price;
- "cancel anytime in Google Play" plus a manage link is shown;
- the price strings are Play's `formattedPrice`.

**Firebase can't:**
- grant entitlements;
- change prices;
- invent a trial;
- disable the renewal disclosure.

`close.back = NEVER` together with `delaySeconds = -1` is a hard paywall. It's allowed for apps whose whole value is
premium, and logs a warning. The guides advise against it for onboarding.

### 5.3 Layers and validation (what the parser accepts)
Each field comes from the first layer that sets it **validly**: `BillingSettings.overrides` (the app) > the
`BillingConfigSource` (Firebase, or `BillingConfigSource.local(context, R.raw.…)`: one JSON object whose top-level keys
are the parameter names) > the enum defaults. Nested objects (`close`, `show`, `campaign`, `texts`, `style`) merge field
by field; `plans` replaces the whole list. An invalid field is ignored with a WARN log and a `ConfigInvalid` event and
the next layer's value applies; nothing throws. Unknown fields are ignored. `PaywallDefinition.toJson()` prints the
complete defaults in this JSON.

| Field | Accepted |
|---|---|
| whole value | a JSON object of at most 64 KB; empty means unset |
| `enabled`, `show*`, `showCountdown`, `dark` | `true` / `false` |
| `template` | `plans`, `single`, `bottom_sheet`, `dialog` (any case) |
| `plans[].product` | a `ProductDefinition` entry name, else the plan is dropped |
| `plans[].basePlan` | a Play ID on a subscription, else the plan is dropped |
| `plans[].offer.ids`, `savingsVs` | Play IDs (`[a-z0-9][a-z0-9-]*`) |
| `plans[].offer.trialDays` | a whole number, 1 or more |
| `plans` | at least one valid plan, else the field is ignored |
| `selected` | a whole number, 0 or more, clamped to the last plan |
| `onOfferUnavailable`, `close.position`, `close.back`, `offer.policy`, `successAction` | the names above (any case) |
| `close.delaySeconds` | a whole number from −1; above 60 is clamped to 60 |
| `show.*` counts | whole numbers, 0 or more |
| `campaign.start` / `end` | ISO-8601 with a zone (`2026-10-01T00:00:00Z`, `…+02:00`, optional `.SSS`); end after start |
| `campaign.id` | required when the defaults have no campaign |
| `exitPaywall` | the name of another registered paywall |
| `texts.*`, `badge` | up to 300 characters, no HTML tags or entities; placeholders are carried as is |
| `style.accent`, `style.background` | `#RRGGBB` or `#AARRGGBB` |
| `style.headerImage` | an `https://` URL |
| `AdroitBilling.offlineGraceHours` | 1 to 720 |
| `AdroitBilling.products` | only declared product names; others are ignored |
| `AdroitBilling.configVersion` | a number or text, reported in `ConfigLoaded` |

`AdroitBilling.enabled = false` stops every paywall from showing; purchases still work. A config update (the source's
`updates`) re-resolves everything; an open paywall keeps the settings it opened with. A hard paywall is reported once
per process with a WARN and a `HardPaywall` event.

---

## 6. Placement attribution
- A **placement** (`"onboarding"`, `"home_banner"`, `"settings"`, `"exit_dialog"`) is an app string, separate from
  the product ID, base plan, offer ID, paywall ID (enum name) and campaign ID. One product can be bought from any
  number of placements.
- Every `purchase()` creates an `attemptId` (UUID). The `Attribution` record is persisted in SharedPreferences
  **before** `launchBillingFlow`, so it survives rotation and process death.
- Matching a `Purchase` to an attempt: same product ID, the newest unexpired attempt (TTL 72 h, which covers slow
  pending payments). No match means `placement = UNKNOWN`, `recovered = true`. A placement is never guessed.
- Opt-in `attributionToken = PROFILE_ID`: put the `attemptId` in `obfuscatedProfileId`, so Play echoes it back on
  `Purchase.accountIdentifiers` (exact matching, also visible to the backend). It's off by default, because Google
  intends that field as a profile identifier and fraud signals may use it (risk R4).
- Events and the backend `PurchaseVerifier` both receive the `Attribution`. Raw purchase tokens never go into logs
  or analytics.

---

## 7. Security and backend
- `PurchaseVerifier` (`suspend fun verify(PurchaseToVerify): VerificationResult`). The app supplies it to call its
  backend; the backend verifies with the Play Developer API (`purchases.subscriptionsv2.get` /
  `purchases.productsv2`), binds the token to the user, de-duplicates, and returns entitlements.
- With a verifier, the library grants only after `Verified`. On `Rejected` it doesn't grant or acknowledge. On a
  network error the purchase stays unacknowledged, and the library retries on the next sync. Acknowledgement can be
  done by the backend (`acknowledgedByBackend = true`) or by the client.
- An optional `EntitlementProvider` lets a backend be the authoritative source of the snapshot (grace period,
  account hold, expiry, refunds through RTDN).
- No service-account keys, API secrets or license keys go in the app or in Remote Config. Remote Config JSON is
  validated; unknown fields are ignored and invalid values fall back to defaults.
- Reference backend (optional prompt B16): Firebase Cloud Functions (TypeScript) with verify, an RTDN Pub/Sub
  handler and Firestore entitlements.

---

## 8. Testing strategy
- **Unit (JVM)**: period parsing, pricing-phase interpretation, offer selection, config parsing and validation,
  show rules (counter, timer, caps), the close-delay state, attribution matching, the error mapping table, the
  idempotency ledger, the entitlement mapping, the offline grace period, and event generation. Virtual time uses
  `kotlinx-coroutines-test` with an injectable `Clock`.
- **Robolectric**: the runtime with `FakePlayBillingPort` (connect, disconnect, reconnect, query, purchase, pending,
  ack/consume, restore, suspended, process death through re-creation from prefs), and the views paywall lifecycle and
  rotation.
- **Compose UI tests**: the paywall renders Play prices, the countdown reveals the X, back handling, and accessibility
  semantics.
- **Device (manual + documented)**: license testers, the internal testing track, test cards (always approves,
  declines, slow pending), "Test instruments" for pending, and an ineligible-trial account.

---

## 9. Risk register / open decisions
| # | Risk / decision | Default taken |
|---|---|---|
| R1 | Client-only apps can be tampered with | Documented. `PurchaseVerifier` is available. |
| R2 | Expiry and cancellation are invisible on the client | The snapshot is rebuilt from `queryPurchasesAsync` on every start and resume. The backend `EntitlementProvider` gives the authoritative status. |
| R3 | Offline premium users | `offlineGraceHours` 72, remotely tunable |
| R4 | `obfuscatedProfileId` used for attempt IDs | Opt-in only |
| R5 | Hard paywall (no close) hurts reviews and policy | Allowed, warns, not the default (the default delay is 0) |
| R6 | Developer-determined offers are always returned by Play | They're used only if config names them by `ids` or `tags`, and the app's `OfferEligibility` hook agrees |
| R7 | PBL 9.x signatures differ from these notes | B01 re-verifies them; prompts follow the real API and report differences |
| R8 | Multi-quantity one-time purchases | Out of scope v1 (quantity is reported, grants use `quantity`) |
| R9 | Alternative billing / Billing Choice | Out of scope |
| D1 | Publishing | Same pipeline as the ads (`adroit.publishing`, Maven Central), and versioned with Adroit |
