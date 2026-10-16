# B19 — adroit-billing-testing: fakes for app developers

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Publish `FakeBilling`, an implementation of the public `BillingClientApi`, plus builders, so apps can unit-test their
premium logic without Play. Move the internal test fake from `adroit-billing/src/test` to share code where possible.

## Context
`adroit-testing` does the same for ads (`FakeAdsClient`). Mirror its API style.

## Prerequisites
B13 (the public API is complete).

## Files (`adroit-billing-testing/.../billing/testing/`)
- `FakeBilling.kt`:
  - settable `entitlements`, `catalog` and `connection`;
  - `nextPurchaseResult`;
  - `emitPurchaseUpdate`;
  - `consumables`;
  - records `purchaseRequests`, so placements can be asserted.
- `TestProducts.kt`:
  - `subscription(id) { basePlan("monthly") { trial("P7D"); recurring("P1M", "$4.99", 4_990_000, "USD") } }`;
  - `oneTime(id, price)`.
- `TestPaywalls.kt`: a `PaywallModel` builder.
- `README` section in `docs/TESTING.md` (billing part) — written in B22. Leave a KDoc example here.

## Requirements
1. `FakeBilling` behaves like the real API contract: `purchase` returns the configured result; `Purchased` updates
   the entitlements; `Pending` does not.
2. It doesn't depend on Robolectric (a plain JVM is usable).
3. The `adroit-billing` internal tests may use these builders through `testImplementation(project(":adroit-billing-testing"))`,
   provided this creates no cycle. If it would, keep a copy and report it.

## Tests
A sample consumer-style test inside the module: a ViewModel that gates a feature on `has("premium")` and buys from
placement `"settings"`, asserted with `FakeBilling`.

## Verify
```
./gradlew :adroit-billing-testing:testDebugUnitTest :adroit-billing-testing:apiCheck
```

## Acceptance criteria
- [ ] A consumer can write a JVM test of their premium flow in under 20 lines (show it in KDoc).

## Report
B00 §D.
