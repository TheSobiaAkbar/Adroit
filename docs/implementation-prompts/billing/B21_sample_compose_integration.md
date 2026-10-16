# B21 — sample-compose: real billing integration (Kotlin + Jetpack Compose)

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
The Compose sample gets the same billing features as B20, built the idiomatic Compose way. Both samples must stay
functionally equivalent, as they are for ads today.

## Context
`sample-compose` mirrors `sample-xml` (Navigation Compose, its own `PremiumStore`, the `SampleAds` enum, the splash).
Read B20's completion report and the `sample-xml` billing code first, and reuse the **same** `SampleProducts`/
`SamplePaywalls` definitions (copy the file; the samples don't share a module).

## Prerequisites
B17, B20.

## Files
- `sample-compose/build.gradle.kts`: add `adroit-billing`, `adroit-billing-compose`, `adroit-billing-firebase`.
- `.../billing/SampleBilling.kt` (the same enums as XML).
- Application initialization and the ads bridge, as in B20. Delete `PremiumStore.kt`.
- Navigation: a `paywall/{name}/{placement}` destination hosting `Paywall(...)`. The onboarding route goes there
  after the splash when `shouldShow`. The exit paywall pops and pushes.
- Home "Upgrade" card: an inline `Paywall` with a custom `content` built from `PaywallScope` blocks (it shows the
  building blocks).
- Settings: the same rows as XML (status, upgrade, restore, manage, debug demo switch).
- Credits: `rememberPaywallController(SamplePaywalls.CreditsStore, "credits")` with a fully custom UI (proving that
  custom UIs only need the controller), plus consumable collection with `confirm()`.
- `MainActivity`: `LifecycleResumeEffect { AdroitBilling.showInAppMessages(activity) }`.
- A `ViewModel` example: `PremiumViewModel(billing: BillingClientApi = AdroitBilling)`, showing DI-friendly
  injection, and a unit test using `FakeBilling` (B19).

## Requirements
1. The same manual matrix as B20 passes.
2. Rotation on the paywall destination keeps the countdown and selection.
3. No Activity is stored in any ViewModel.

## Verify
```
./gradlew :sample-compose:assembleDebug :sample-compose:lintDebug :sample-compose:testDebugUnitTest
```

Then the device matrix as in B20.

## Acceptance criteria
- [ ] Feature parity with `sample-xml` (a table in the report).
- [ ] The custom-UI path (controller only) works.
- [ ] The `FakeBilling` ViewModel test passes.

## Report
B00 §D, plus the parity table and manual matrix.
