# Billing: in-app purchases and paywalls

Adroit Billing sells subscriptions, one-time products and consumables through Google Play Billing, and shows paywalls
you can change from Firebase without a release: which plans, which offer, when the paywall shows, when its close
button appears, its texts and colors.

It works with or without the Adroit ads libraries. In an app with ads, one line makes premium users ad-free.

**Start here:** [a new Kotlin + XML app](BILLING_NEW_APP_XML.md) or [a new Jetpack Compose app](BILLING_NEW_APP_COMPOSE.md),
from zero to a first purchase. Whoever runs the paywalls from Firebase reads
[Paywalls in Firebase](PAYWALL_REMOTE_CONFIG.md); no code there.

## Which module do I add?

| Module | What it has | Add it when |
|---|---|---|
| `adroit-billing` | Products, purchases, entitlements, restore, the paywall rules and `PaywallController` (no UI) | Always. The UI modules bring it. |
| `adroit-billing-views` | `AdroitPaywall.show`, the paywall Activity, `PaywallView` for layouts | Your app uses XML layouts and Views |
| `adroit-billing-compose` | The `Paywall` composable, its blocks for custom layouts, `rememberPaywallController` | Your app uses Jetpack Compose |
| `adroit-billing-firebase` | `FirebaseBillingConfig` (paywalls from Remote Config) and `FirebaseBillingEvents` (Analytics) | You want Firebase control or Analytics (recommended) |
| `adroit-billing-testing` | `FakeBilling` and builders for products and paywalls | `testImplementation` only |

The billing modules never depend on the ads modules, and the ads modules never depend on billing: a billing-only app
doesn't get the Mobile Ads SDK, and an ads-only app doesn't get Play Billing.

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.thesobiaakbar:adroit-billing-views:1.0.0-rc6") // or adroit-billing-compose
    implementation("io.github.thesobiaakbar:adroit-billing-firebase:1.0.0-rc6")
    testImplementation("io.github.thesobiaakbar:adroit-billing-testing:1.0.0-rc6")
}
```

## Requirements

| What | Version |
|---|---|
| minSdk | 24 |
| compileSdk | 34 or higher (`adroit-billing-compose`: 37, like the Compose BOM it uses) |
| Google Play Billing Library | 9.1.0, brought by `adroit-billing`. Don't add `billing` or `billing-ktx` yourself. |
| Kotlin | 2.2 or newer in the app (the libraries are built with 2.4.20, metadata 2.2) |
| Java | 11 bytecode or newer |
| A device or emulator | With the Play Store and a Google account, for real purchases. Demo mode works without them. |

## How it fits together

1. **One Kotlin file** lists your products (by their Play Console IDs) and your paywalls, as two enums. Each
   paywall's name is its Firebase parameter.
2. **`AdroitBilling.initialize`** in `Application.onCreate` registers them. It reads the saved entitlements at once,
   so a premium user is premium before Play answers.
3. **`AdroitBilling.entitlements`** says what the user owns now (`has("premium")`, `adFree`) and how sure that is:
   fresh from Play, cached while offline, or unknown.
4. **Paywalls** open with `AdroitPaywall.show` (Views) or `Paywall(…)` (Compose). They check their show rules, pick
   the offers Play gives this user, run the purchase and close themselves.
5. **Firebase** changes any paywall field by field, and the `AdroitBilling` parameter changes app-wide settings.

Every purchase carries its **placement**, the place in your app it came from (`"onboarding"`, `"settings"`), to
Analytics and to your backend, so you can see which paywall sells.

## What the app can and can't know

The client sees only what Google Play reports on the device. In particular:

- **Prices, trials and intro prices** come from Play Console. Firebase chooses among the offers Play returns for
  this user; it can't create or change one. The paywall shows only prices and trials Play gave.
- **Eligibility** for a trial or intro price is Play's decision. A trial that Play doesn't give is never advertised.
- **Expiry, renewals, refunds and cancellations** aren't visible while the app isn't running. The app sees active
  (or suspended) subscriptions and unconsumed one-time products. For exact expiry, grace period and refunds, a backend
  uses the Play Developer API and real-time notifications: see [the backend contract](BILLING_BACKEND.md).
- **Offline:** a subscription confirmed by Play stays active for `offlineGraceHours` (72 by default) without a new
  confirmation; lifetime products stay active. `entitlements.value.status` says which case you're in.
- **Placement** isn't stored in Play's transaction: Play has no field for it. The library keeps it on the device and
  matches it when Play reports the purchase, also after the app was killed.

## Security

> - **Client-only billing can be fooled on a rooted device.** Without a backend that verifies purchases with the Play
>   Developer API, treat entitlements as convenience, not as protection for valuable server-side content. Add a
>   `PurchaseVerifier` ([the backend contract](BILLING_BACKEND.md)) when it matters.
> - **No secrets in the app or in Remote Config.** No service-account key, API secret or license key: anyone can read
>   an APK and every Remote Config value. The backend holds the credentials.
> - **A pending purchase grants nothing.** The library grants only when Play reports the purchase as completed, and
>   acknowledges it then (Play refunds purchases not acknowledged within 3 days). Never unlock anything on
>   `PurchaseResult.Pending`.
> - **Firebase can't grant premium.** Remote Config changes presentation only; access comes only from Play purchases
>   (or your backend's `EntitlementProvider`).
> - **No tokens in logs.** Purchase tokens and account IDs are never logged or sent to Analytics; the account ID goes
>   to Play only as a SHA-256 hash.

## Pages

| Page | For |
|---|---|
| [New XML app](BILLING_NEW_APP_XML.md) | Developers: every step, with code, for Views |
| [New Compose app](BILLING_NEW_APP_COMPOSE.md) | Developers: every step, with code, for Compose |
| [Paywalls in Firebase](PAYWALL_REMOTE_CONFIG.md) | Anyone running the paywalls: every Firebase field |
| [Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md) | Creating products and offers, and testing purchases |
| [Backend contract](BILLING_BACKEND.md) | Verifying purchases on a server |
| [Troubleshooting and FAQ](BILLING_TROUBLESHOOTING.md) | When something doesn't show or doesn't sell |
| [Testing](TESTING.md#billing-with-adroit-billing-testing) | `FakeBilling` in unit tests |
| [Billing architecture](architecture/06_BILLING_ARCHITECTURE.md) | How it works inside |

The samples use everything: `sample-xml` and `sample-compose`, in demo mode on any device.

Last verified against: Play Billing Library 9.1.0, 2026-10-20
