# B04 — Internal Play port, fake, and connection lifecycle

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Put every Google Play Billing call behind an internal `PlayBillingPort`. Implement it with the real `BillingClient`
(`PlayBillingAdapter`) and with an in-memory `FakePlayBillingPort` for tests. Add a `ConnectionManager` that owns the
single `BillingClient`, exposes `StateFlow<BillingConnectionState>`, and survives disconnects.

## Context
06 §2–3. This is the same pattern as `adroit-core`'s internal `AdsSdk` port plus Google adapter; read it and match
it.

PBL 8+ provides `enableAutoServiceReconnection()`, and `enablePendingPurchases(PendingPurchasesParams)` is
required.

## Prerequisites
B02, B03.

## Files (`adroit-billing/.../internal/play/`)
- `PlayBillingPort.kt`: suspend functions returning internal result types:
  - `connect()`;
  - `queryProducts(ids, type)`;
  - `queryPurchases(type, includeSuspended)`;
  - `launch(activity, LaunchParams)`;
  - `acknowledge(token)`;
  - `consume(token)`;
  - `isFeatureSupported(feature)`;
  - `showInAppMessages(activity)`;
  - a `purchasesUpdates: Flow<PlayPurchasesUpdate>`.
- `PlayBillingAdapter.kt`: the only file family that imports `com.android.billingclient`.
- `PlayMappers.kt`: `BillingResult` → `BillingError`, `ProductDetails` → `ProductInfo`, and `Purchase` →
  `PlayPurchase` (internal).
- `ConnectionManager.kt`.
- `src/test/.../FakePlayBillingPort.kt` (moves to `adroit-billing-testing` in B19).

## Requirements
1. `BillingClient.newBuilder(appContext)` with:
   - `.setListener(…)`;
   - `.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())`;
   - `.enableAutoServiceReconnection()`.

   Verify each method exists in the catalog's PBL version.
2. Exactly **one** client per process, created by the runtime with the application context.
3. Connection states:
   - `Disconnected`;
   - `Connecting`;
   - `Connected`;
   - `Unavailable(error)`: billing unavailable or Play Store blocked;
   - `Error(error, retryAt?)`.
4. Retries: `SERVICE_UNAVAILABLE`, `SERVICE_DISCONNECTED` and `NETWORK_ERROR` use bounded exponential backoff (1 s,
   2 s, 4 s, 8 s, 16 s, max 5 tries), then wait for the next trigger:
   - the app comes to the foreground (`ProcessLifecycleOwner` `ON_START`);
   - an explicit `refresh()`.

   `BILLING_UNAVAILABLE` and `DEVELOPER_ERROR` aren't retried automatically.
5. Every port call first awaits `Connected`, with a timeout (default 10 s). If the timeout passes, return
   `ServiceDisconnected`. Never hang.
6. Error mapping table (in KDoc and tests) for every `BillingResponseCode`, plus the 8.0 sub-response codes:
   - `PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS` → `PaymentDeclined`;
   - `USER_INELIGIBLE` → `UserIneligible`.
7. Callbacks are resumed on main through `suspendCancellableCoroutine` or the ktx extensions (per the B01 decision).
   A late callback after cancellation is ignored safely.
8. Logging goes through the library's logger (reuse the Adroit `LogLevel` style; billing has its own `BillingLog`).
   Purchase tokens are hashed.

## Constraints
- `launch()` takes the Activity as a parameter and doesn't keep it.
- No public API changes, except filling in `connection` on the facade if B02 left it as a stub.

## Edge cases
- `onBillingServiceDisconnected` while a query is in flight: the query returns `ServiceDisconnected` (retryable) once
  the reconnect fails, or it is re-run once on reconnect.
- Play Store missing or disabled (emulator without Play) gives `Unavailable`, and the app keeps working (catalog
  `Failed`, entitlements from the cache).
- `endConnection` is only called on an explicit `AdroitBilling.shutdownForTests()` (internal), never on Activity
  destroy.

## Tests
With a Robolectric or JVM fake `BillingClient` wrapper, or test through the port with `FakePlayBillingPort` for the
manager:
- state transitions;
- the backoff schedule under virtual time;
- the 10 s timeout;
- no retry on `BILLING_UNAVAILABLE`;
- a foreground trigger re-connects;
- the mapping table covers every response code.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:lint
```

## Acceptance criteria
- [ ] `grep -rl "com.android.billingclient" adroit-billing/src/main` lists only `internal/play/` files.
- [ ] The connection never retries without bounds. Proven by a test.
- [ ] The response-code mapping has 100% case coverage in tests.

## Report
B00 §D.
