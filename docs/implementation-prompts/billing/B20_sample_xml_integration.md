# B20 — sample-xml: real billing integration (Kotlin + XML/ViewBinding)

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Integrate Adroit Billing into `sample-xml` the way a real app would. It replaces the `PremiumStore` stand-in and
demonstrates:
- one products/paywalls file;
- Firebase-controlled paywalls (close delay, plans, texts);
- several placements for one product;
- the ads bridge;
- the splash premium path;
- restore and manage;
- a consumable.

## Context
In `sample-xml` today:
- `PremiumStore` (SharedPreferences) and `Adroit.setAdFree` in `SampleApplication`;
- the Settings "Ad-free" switch;
- `SplashFlow` premium path (`SplashViewModel` reads `PremiumStore.isPremium`);
- the `SampleAds` enum;
- real Firebase (`google-services.json`, `RemoteConfigSession`).

Read all of these first. Keep the ads behaviour unchanged for free users.

## Prerequisites
B14, B18, B19.

## Play Console prerequisite (manual, done by the user — state it in the report)
Real purchases need the sample's application ID to exist in Play Console, with:
- an uploaded build on an internal testing track;
- license testers;
- products `premium_sub` (base plans `weekly`, `monthly` with a 3-day trial offer tagged `trial`, `yearly` with an
  intro offer tagged `intro`), `premium_lifetime` (one-time) and `coins_100` (consumable).

Until then, the catalog reports them missing. So also add **demo mode** (below).

## Files
- `sample-xml/build.gradle.kts`: add `adroit-billing`, `adroit-billing-views`, `adroit-billing-firebase`.
- `sample-xml/.../billing/SampleBilling.kt`: `enum class SampleProducts : ProductDefinition`, `enum class
  SamplePaywalls : PaywallDefinition` (`Onboarding`, `HomeUpgrade`, `SettingsUpgrade`, `ExitDiscount`,
  `CreditsStore`). This is the **one file** an app edits.
- `SampleApplication.kt`:
  - `AdroitBilling.initialize(...)` with `FirebaseBillingConfig.source()` and `FirebaseBillingEvents()`;
  - collect `entitlements` → `Adroit.setAdFree(it.adFree)` (the one-line bridge).
- **Delete** `PremiumStore.kt`. `SplashViewModel` uses `AdroitBilling.entitlements.value.adFree` (synchronous cached
  value, B08).
- Onboarding: after the splash, if `SamplePaywalls.Onboarding` `shouldShow`, call `AdroitPaywall.show(this,
  SamplePaywalls.Onboarding, "onboarding")`.
- Home: an "Upgrade" banner card calls `AdroitPaywall.show(..., SamplePaywalls.HomeUpgrade, "home_banner")`. The
  same product is reachable from a different placement.
- Settings:
  - replace the "Ad-free" switch with a premium status row (entitlement + status Fresh/Cached/Unknown);
  - "Upgrade" (`force = true`, placement `"settings"`);
  - "Restore purchases";
  - "Manage subscription";
  - in **debug only**, a "Demo billing" switch (see below).
- A credits screen: a `PaywallView` embedded in XML (`app:adroit_billing_paywall="CreditsStore"`), a coin balance
  stored in prefs, and `AdroitBilling.consumables` collected → add coins idempotently by `orderIdHash` →
  `grant.confirm()`.
- `MainActivity.onResume`: `AdroitBilling.showInAppMessages(this)`.
- `res/raw/billing_defaults.json` is optional. Prefer the enum defaults; don't add it unless needed.
- `docs/firebase/sample_billing_remote_config.json`: a ready-to-import example of every key (`AdroitBilling`,
  `Onboarding`, `HomeUpgrade`, …) with `close.delaySeconds` values 0, 3 and 5 across paywalls.

## Demo mode
Make it a library feature if missing, added minimally in `adroit-billing`:
`BillingSettings.demo = DemoBilling(enabled)`. When enabled **and** the app is debuggable (`FLAG_DEBUGGABLE`), the
runtime uses an internal demo port with fake products and prices (clearly labelled "DEMO" in the paywall disclosure)
and simulated success, cancel and pending results. In a non-debuggable app it is ignored with an ERROR log. This lets
the UI and Firebase config be demoed without Play Console. Tests prove it can't activate in a release build.

## Requirements
1. The ads bridge: premium → no ads anywhere, including the splash (consent skipped, the 3 s premium splash, as
   today).
2. Rotation and process death during a purchase keep the attribution (manual check list in the report).
3. LeakCanary (already in the sample's debug build) reports no leak after opening and closing each paywall 5 times.

## Verify
```
./gradlew :sample-xml:assembleDebug :sample-xml:lintDebug
```

On a device (see the memory note on adb), run the manual matrix with demo mode:
- onboarding paywall with delay 3 (X after 3 s);
- delay 0;
- delay −1 + back AFTER_DELAY;
- exit discount after closing;
- purchase success → ads disappear;
- cancel;
- pending;
- restore;
- the credits consumable.

Real Play purchases need the Play Console setup above.

## Acceptance criteria
- [ ] `PremiumStore` is gone. Premium comes only from `AdroitBilling.entitlements`.
- [ ] Changing `Onboarding.close.delaySeconds` in Firebase changes the sample's paywall on the next launch without a
      rebuild (verified by the user, or documented as pending).
- [ ] The same product bought from `home_banner` and `settings` yields events with the right placement (logcat).
- [ ] The free-user ads behaviour is unchanged.

## Report
B00 §D, plus the manual matrix results (✅/❌/not run).
