# B07 — Purchase processing: verification, acknowledge, consume, pending, idempotency

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Turn every `Purchase` Play reports (from the launch flow, `queryPurchasesAsync`, or out-of-session updates) into
exactly one correct outcome:
- grant or don't grant;
- acknowledge or consume exactly once;
- deliver consumables to the app before consuming;
- handle pending and suspended purchases;
- call the optional `PurchaseVerifier`.

## Context
06 §4, §7. Play refunds purchases that aren't acknowledged within 3 days. Consumables must be consumed so they can be
bought again.

## Prerequisites
B06.

## Files
- `internal/purchase/PurchaseProcessor.kt`
- `internal/purchase/TokenLedger.kt`: SharedPreferences set of `sha256(token)` → state (`GRANTED`,
  `ACKNOWLEDGED`, `CONSUMED`, `REJECTED`), capped at 500 entries (LRU)
- `ConsumableGrant.kt` (fill in): `product`, `quantity`, `attribution`, `orderIdHash`,
  `suspend fun confirm()`
- `PurchaseVerifier.kt` (fill in): `PurchaseToVerify(productIds, token, type, attribution, accountIdHash)`,
  `VerificationResult.Verified(acknowledgedByBackend)`, `Rejected(reason)`, `Unreachable`
- Tests

## Requirements
1. `PurchaseState.PENDING` emits `PurchaseUpdate.Pending(attribution)` and returns `PurchaseResult.Pending`. No grant,
   no acknowledge. When it later turns `PURCHASED` (seen via the listener or a sync), process it normally and emit
   `PurchaseUpdate.Completed`.
2. `UNSPECIFIED_STATE` is ignored and logged.
3. `isSuspended` gives no grant. Emit `PurchaseUpdate.Suspended(product)`. B08 shows it in the snapshot as
   `SUSPENDED`.
4. `PURCHASED`:
   1. If a verifier is set, call it with a timeout (default 15 s):
      - `Rejected`: no grant, no ack, ledger `REJECTED`, `Failed(VerificationFailed)`;
      - `Unreachable` or a timeout: no grant **unless** the purchase was already granted earlier (ledger); the
        purchase stays unacknowledged and is retried on the next sync; `Failed(BackendError)`.
   2. Non-consumable or subscription: grant (B08 updates the snapshot). If `!isAcknowledged` and not
      `acknowledgedByBackend`, call `acknowledge`. Retry with bounded backoff (3 tries), and then on the next sync.
      Never acknowledge the same token twice in the process (ledger).
   3. Consumable: emit a `ConsumableGrant` on `AdroitBilling.consumables` (a replaying hot flow of unconfirmed
      grants). Only after the app calls `grant.confirm()` does the library `consume`. An unconfirmed grant is
      re-delivered after a process restart (the ledger holds `GRANTED` and the purchase is still unconsumed in Play).
      `quantity` comes from `purchase.quantity`.
5. Events: `PurchaseCompleted`, `PurchasePending`, `PurchaseVerified`, `EntitlementActivated`,
   `AcknowledgeFailed`, `ConsumeFailed`, each with the attribution and **no token**.
6. `PurchaseResult.Purchased` is returned to the waiting `purchase()` caller only after the grant (or after the
   `ConsumableGrant` is emitted, for consumables).

## Constraints
- Processing is serialized per token (a `Mutex` keyed by the token hash), so the listener and a sync running together
  can't double-process.
- Ledger writes for a grant happen **before** the acknowledge call.

## Edge cases
- The app crashes after the grant and before the acknowledge: the next sync sees `!isAcknowledged`, the ledger says
  `GRANTED`, so it acknowledges without a second grant event.
- The app crashes after `confirm()` and before the consume finishes: Play still lists the purchase. The ledger says
  `CONSUMED`-pending, so it consumes without re-delivering. Document the trade-off: the app must make its own credit
  write idempotent by `orderIdHash`.
- A consume fails with `ITEM_NOT_OWNED`: mark it consumed (already done elsewhere).
- An acknowledge fails 3 times: keep retrying on each foreground sync (well within the 3-day window). Emit an event,
  never crash.
- A multi-product purchase (`purchase.products.size > 1`): grant for each product.

## Tests
The full matrix with the fake port and a fake verifier:
- pending → purchased;
- suspended;
- verified, rejected, unreachable;
- acknowledge retries;
- a double delivery (the listener and a sync running together) gives one grant and one acknowledge;
- the consumable confirm flow;
- consumable re-delivery after a "restart" (a new runtime instance with the same prefs);
- ledger LRU;
- no grant on pending.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] Each token is acknowledged or consumed at most once. Proven under concurrency.
- [ ] No grant for pending, suspended or rejected purchases.
- [ ] Consumables are never consumed before `confirm()`.
- [ ] No token appears in logs or events: a test captures log output and asserts it.

## Report
B00 §D.
