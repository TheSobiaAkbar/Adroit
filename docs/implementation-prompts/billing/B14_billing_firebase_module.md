# B14 — adroit-billing-firebase: Remote Config source and Firebase Analytics adapter

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Make Firebase Remote Config the source of the billing and paywall JSON (`FirebaseBillingConfig.source()`), with a
fetch-once of its own that never blocks. Add `FirebaseBillingEvents`, a `BillingEventListener` that logs to
Firebase Analytics.

## Context
**B01 decision:** this module must not depend on `adroit-firebase`, which brings `adroit-core` and the Mobile Ads SDK
into billing-only apps. It depends on `firebase-config` and `firebase-analytics` directly (see
06_BILLING_ARCHITECTURE §2), and the dependency guard enforces it.

Read `adroit-firebase`'s `RemoteConfigSession` and `FirebaseAnalyticsEventListener`. Copy their behaviour, not the
code dependency:
- fetch once per process;
- retry on the next Activity or network return;
- never wait.

In an app that also runs `RemoteConfigSession`, Firebase answers the second `fetchAndActivate` from its cache (within
`minimumFetchInterval`), so the network is hit once.

## Prerequisites
B11, B10, B01.

## Files (`adroit-billing-firebase/.../billing/firebase/`)
- `FirebaseBillingConfig.kt`: `source(remoteConfig = FirebaseRemoteConfig.getInstance()): BillingConfigSource`.
  `raw(key)` reads `getValue(key)` only when its source isn't `VALUE_SOURCE_STATIC`. `updates` emits after this
  module's own `fetchAndActivate` succeeds (once per process; retried on the next Activity `onCreate` and on network
  return, at most one attempt at a time, skipped while offline).
- `FirebaseBillingEvents.kt`: maps `BillingEvent.toParams()` to `FirebaseAnalytics.logEvent`. Optionally logs
  `purchase` with `value` and `currency` on `PurchaseCompleted` only when `logRevenue = true` (default **false**,
  because Firebase/Play linking may already import revenue and double-counting is easy; document this).
- `src/main/res/xml/adroit_billing_remote_config_defaults.xml`? **No.** Defaults live in the enums. Don't add a
  defaults XML.
- Tests with a fake `RemoteConfigSource`, following `adroit-firebase`'s tests.

## Requirements
1. Zero waiting: the source answers from the active values at once. Before the first fetch, it uses the last
   session's values or null (so the enum defaults apply).
2. A value larger than 64 KB, or one that isn't JSON, is handled by B11's validator. Nothing special here.
3. Analytics event names and params pass Firebase limits: names of 40 characters or fewer with `[a-zA-Z0-9_]`, at
   most 25 params, values of 100 characters or fewer.
4. The Firebase Analytics adapter is opt-in (the app adds it to `BillingSettings.events`).

## Edge cases
- Firebase isn't initialized (no google-services in a debug variant): `source()` logs once and behaves as
  `BillingConfigSource.none()`, and never throws.
- Remote Config activates while a paywall is open: the next opening uses the new value (B11 rule).

## Tests
Fake source:
- `raw` with remote/default/static values;
- updates propagate;
- not-initialized fallback;
- event mapping and limits;
- `logRevenue` off by default.

## Verify
```
./gradlew :adroit-billing-firebase:testDebugUnitTest :adroit-billing-firebase:apiCheck checkModuleDependencies
```

## Acceptance criteria
- [ ] At most one `fetchAndActivate` per process from billing, proven by a test. It never blocks the caller.
- [ ] `adroit-billing-firebase` resolves no ads module and no Mobile Ads SDK, and the core has no Firebase dependency
      (the guard passes).
- [ ] `adroit-firebase` is unchanged.

## Report
B00 §D.
