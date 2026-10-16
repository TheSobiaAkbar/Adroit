# B10 — Billing events, listener and log redaction

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Finish the `BillingEvent` model and the `BillingEventListener` sink. Every stage so far emits through an internal
`EventBus`; make it complete, safe, and impossible to crash billing.

## Context
06 §8. Client-observed events and server-confirmed lifecycle events are different things. The client never claims
"renewed", "expired" or "cancelled" unless a backend provider said so.

## Prerequisites
B08, B09.

## Files
- `BillingEvent.kt`: a sealed class
- `BillingEventListener.kt`: `fun interface`
- `internal/events/EventBus.kt`
- `internal/log/BillingLog.kt` (if not finished)
- Tests

## Requirements
1. Events. Each carries `timestampMillis` and, where known, `attribution`, `productId`, `basePlanId`, `offerId`,
   `campaignId`, `paywallId` and `placement`:
   - `ConnectionStarted`, `ConnectionEstablished`, `ConnectionFailed(error)`;
   - `CatalogLoaded(count, missing)`, `CatalogFailed`;
   - `PaywallShown`, `PaywallClosed(reason: CLOSE_BUTTON|BACK|PURCHASED|ERROR, secondsVisible)`,
     `PaywallCloseButtonShown(afterSeconds)`, `PaywallSuppressed(reason)` (B13);
   - `OfferShown`, `OfferUnavailable(requested, fallback)`;
   - `PurchaseInitiated`, `PurchaseCancelled`, `PurchasePending`, `PurchaseCompleted`, `PurchaseFailed(error)`,
     `PurchaseVerified`, `EntitlementActivated(entitlement)`, `EntitlementLost(entitlement, reason)`;
   - `RestoreInitiated`, `RestoreCompleted(restored)`;
   - `ConfigLoaded(version)`, `ConfigInvalid(key, reason)`;
   - `ServerLifecycle(kind, source = BACKEND)`, emitted only from `EntitlementProvider` data.
2. `toParams(): Map<String, Any>` with snake_case keys of 40 characters or fewer and string values of 100 characters
   or fewer, ready for analytics adapters. Use Firebase-friendly event names (`adroit_billing_purchase_completed`).
3. Listener calls go to main, each wrapped in `try/catch`. An exception is logged once per listener class and never
   propagated.
4. Multiple listeners: `BillingSettings.events` takes a list, and `AdroitBilling.addEventListener` and
   `removeEventListener` work at runtime.
5. Log redaction: `BillingLog` has a single `redact(token)` path. Add a lint-style unit test that scans `src/main`
   for `purchaseToken` used in a log or string template, and fails if it finds one.
6. Price data in events: `priceMicros` and `currency` only on `PurchaseCompleted` (for revenue). The formatted
   string is never sent.

## Tests
- every event's params;
- the length limits;
- a listener that throws doesn't break purchase processing;
- events are emitted in order for a happy purchase (`Initiated` → `Completed` → `Verified` →
  `EntitlementActivated`);
- the token scan test.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] No event contains a token, a raw account ID or a formatted price.
- [ ] No event says "renewed", "expired" or "cancelled" from client-only data.

## Report
B00 §D.
