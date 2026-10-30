---
name: adroit-billing
description: Implement in-app purchases in an Android app (Kotlin, Jetpack Compose or XML) with Adroit Billing, the in-house Google Play Billing library (io.github.thesobiaakbar adroit-billing, -compose, -views, -firebase, -testing). Covers subscriptions with base plans, offers and free trials, lifetime and consumable products, entitlements and premium state, paywalls (built-in templates, custom Compose or XML UI, multi-step onboarding paywalls, launch paywall over the splash, exit offers, remove-ads, feature gates, "watch ad or subscribe" unlocks), Firebase-controlled paywalls and kill switches, restore, manage subscription, upgrades, demo billing, FakeBilling tests, Play Console setup and release. Use this skill whenever the user wants to add, change, debug or plan IAP, subscriptions, a paywall, premium, a Pro screen, "remove ads", restore purchases, Play Billing or entitlements in an Android app, even if they don't name Adroit. When the app also shows ads, use adroit-admob alongside it.
---

# Adroit Billing: in-app purchases and paywalls

Adroit Billing wraps Google Play Billing Library 9.1 behind one Kotlin API:
- **Products** are listed once, in an enum. An **entitlement** is what they unlock (`"premium"`), and the app only
  ever asks whether the user has it.
- **Paywalls** are listed once, in another enum. Each paywall is also a Firebase Remote Config parameter, so its plans,
  close delay, show rules and texts change without a release.
- **What the library handles:** purchases, acknowledgement, pending payments, restore, offline grace, idempotency, and
  the rules for when a paywall shows.
- **What the app writes:** the products, the paywalls, and where they open.

**Version.** This skill is verified against Adroit `1.0.0-rc7` (2026-10). If the project pins a
newer version, check the library's `CHANGELOG.md` before you trust a signature here.

## 1. Settle the scenario first

Read the answers from the project wherever you can, and ask only for what it can't tell you.

| Question | Why it matters |
|---|---|
| Compose, XML, or both? | `adroit-billing-compose` (the `Paywall` composable) or `adroit-billing-views` (`AdroitPaywall`, `PaywallView`). |
| Does the app also show ads? | Yes → one bridge line makes premium users ad-free, and the ad close button opens a remove-ads paywall. Use **adroit-admob** too. |
| What is sold? | Subscription with base plans (weekly/monthly/yearly)? Trials or intro offers? Lifetime? Consumables (coins)? Several tiers (several entitlements)? |
| Hard or soft paywall? | Hard: the app is unusable without paying (the IAP-only splash pattern). Soft: the app works, and premium removes limits or ads. |
| Where do paywalls open? | Launch/onboarding, a feature gate, the ad close button, a Pro button, Settings, an exit offer. Each one is a `PaywallDefinition` entry with its own placement name. |
| Is Firebase in the app? | Yes → `adroit-billing-firebase`. Every paywall becomes remote-controlled, and a `PremiumEnabled` kill switch is possible. |
| Single module, or multi-module with Hilt? | Multi-module → read `references/architecture.md` first. |
| Do the products exist in Play Console yet? | No → `DemoBilling(BuildConfig.DEBUG)` gives made-up products in debug, so nothing is blocked. |
| Is there a backend? | Optional server verification (`products-purchases.md` §8). Without one, Play's on-device report is trusted. |

## 2. Where to read

| You are doing | Read |
|---|---|
| Dependencies, `BillingSettings`, demo billing, the ad-free bridge, the import map | `references/setup.md` |
| Products, entitlements, purchase, restore, offers, upgrades, consumables, account hold, server verification | `references/products-purchases.md` |
| `PaywallSpec` and every field, show rules, the `Paywall` composable, custom UI, XML, `PriceText` | `references/paywalls.md` |
| Ready recipes: launch/onboarding, multi-step, exit offer, remove-ads, feature gate, ad-or-subscribe unlock, Pro screen, Settings rows, hard paywall, kill switch | `references/scenarios.md` |
| Firebase JSON for paywalls and `AdroitBilling`, merge rules, what Firebase can't do | `references/remote-config.md` |
| Multi-module, Hilt, MVI: feature-owned paywall interfaces, the `PaywallSale` wrapper | `references/architecture.md` |
| `FakeBilling`, `TestProducts`, `TestPaywalls`, `PaywallController.forModel` | `references/testing.md` |
| Play Console products, base plans, offers, license testers, troubleshooting, the release checklist | `references/play-console-release.md` |

## 3. The model, in one screen

```kotlin
const val PREMIUM = "premium"

/** Everything the app sells. The string is the Play Console product ID. Never rename an entry after release. */
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf(PREMIUM))), // base plans weekly/monthly/yearly in Play
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf(PREMIUM))),
}

/** Every paywall. Each name is also its Firebase parameter. */
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    Onboarding(PaywallSpec(
        plans = listOf(
            PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
            PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
            PlanSpec(AppProducts.PremiumLifetime),
        ),
        close = CloseBehavior(delaySeconds = 3, showCountdown = true, back = BackBehavior.AFTER_DELAY),
        show = ShowRules(maxPerSession = 1),
    )),
    Settings(PaywallSpec(plans = listOf(PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly")), showManage = true)),
}

// Application.onCreate
AdroitBilling.initialize(this, BillingSettings(
    products = AppProducts.entries, paywalls = AppPaywalls.entries,
    config = FirebaseBillingConfig.source(),                  // adroit-billing-firebase; else omit
    events = listOf(FirebaseBillingEvents(this)),
    demo = DemoBilling(BuildConfig.DEBUG),
))
// With ads: the only line between billing and ads (set it synchronously, then follow changes)
Adroit.setAdFree(AdroitBilling.entitlements.value.adFree)
appScope.launch { AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) } }

// Anywhere
val premium = AdroitBilling.entitlements.value.has(PREMIUM)       // or collectAsStateWithLifecycle()
if (AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "launch") == ShowDecision.Show) { /* show it */ }
Paywall(AppPaywalls.Settings, placement = "settings", onClose = { upgrade = false })   // Compose
AdroitPaywall.show(activity, AppPaywalls.Settings, "settings", force = true)            // XML
```

## 4. Rules that are never optional

1. **Ask for entitlements, never for products.** Use `entitlements.value.has(PREMIUM)`, and never test "owns
   premium_sub". Several products can grant one entitlement, and Firebase can remap them.
2. **Never type a price, trial length or currency.** Every price string comes from Play, through `PriceText` or the
   built-in blocks. A hard-coded "$4.99" is wrong in 170 countries, and it violates Play policy.
3. **Keep the renewal terms (`Disclosure()` / `PriceText.disclosure`) next to the buy button.** Play requires the
   price, period and auto-renewal terms before a subscription purchase. A custom layout that drops them gets the app
   rejected.
4. **Apply ad-free synchronously in `Application.onCreate`, before collecting.** The splash reads `Adroit.isAdFree`
   right away. Without the synchronous line, a premium user sees consent and a splash ad on every cold start.
5. **`Paywall(...)` doesn't check the show rules; you do.** For an automatic placement (launch, ad close, feature
   gate), call `shouldShow` first. For a paywall the user asked for (a button), don't call it: open the paywall
   directly (XML: `force = true`).
6. **Credit each consumable once, keyed by `orderIdHash`, then call `confirm()` every time.** An unconfirmed grant is
   delivered again on the next launch, and Play won't sell the item again until it is confirmed.
7. **Continue the flow on every paywall outcome:** purchased, closed, unavailable (offline, product missing, already
   entitled). A paywall with no answer from Play must still be closable, unless it is a deliberate hard paywall.
8. **Enum entry names are Firebase parameter names, and product IDs are Play IDs.** Never rename either after release.
9. **Demo billing is for debug only.** Release ignores it and logs an error. Real purchases need a Play testing track
   and license testers.
10. **Use a different placement string for each entry point** (`"launch"`, `"ad_close"`, `"pro_button"`). Show rules,
    analytics and purchase attribution are all per placement.

## 5. Workflow

1. **Catalogue.** Write `AppProducts` and `AppPaywalls` from the scenario. List the base plan and offer IDs the
   paywalls name, for the Play Console setup.
   *Check:* a debug build's `initialize` throws, listing every problem (unknown product, duplicate ID, bad ID format).
   No throw means the catalogue is consistent.
2. **Setup** (`setup.md`): the dependencies, `initialize`, and the ad bridge if the app has ads.
   *Check:* logcat `AdroitBilling` shows `DEMO billing: made-up products…` in debug.
3. **Entry points** (`scenarios.md`): each paywall where the plan says, with `shouldShow` for automatic ones.
   *Check:* each opens with "(DEMO)" prices, and Buy, Pending and Cancel each end in the right place.
4. **Premium gating:** every premium check reads entitlements. Get Pro / Upgrade entry points are drawn only while
   the user is not premium.
   *Check:* after a demo purchase, the gates open, the entry points disappear, and with ads no ad shows (the next
   splash is short, with no consent).
5. **Settings:** restore purchases, manage subscription, and Play's in-app messages on the main screen.
6. **Remote config** (`remote-config.md`): generate each paywall's JSON with `toJson()` and hand it over.
7. **Tests** (`testing.md`), then the **Play Console setup and release checklist** (`play-console-release.md`).

When you finish, tell the user what still needs them:
- the products, base plans and offers to create and **activate** in Play Console, with the exact IDs;
- license testers;
- an upload to a testing track;
- the Firebase parameters to publish;
- anything not run on a device.
