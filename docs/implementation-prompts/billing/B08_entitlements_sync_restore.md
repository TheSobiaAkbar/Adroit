# B08 — Entitlements, reconciliation, restore and account switching

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Implement `AdroitBilling.entitlements: StateFlow<EntitlementSnapshot>` as the single answer to "what does this user
own now". It's rebuilt from Play (and optionally a backend), cached for offline use, and exposed synchronously for the
splash. Also implement `restore()`, `refresh()`, `setAccount()` and the `initialize()` runtime wiring.

## Context
06 §4, §5.1 (`adFreeEntitlements`, `offlineGraceHours`, product→entitlement overrides), and §7
(`EntitlementProvider`).

Today the samples use `PremiumStore` (SharedPreferences) as a stand-in. B20/B21 replace it with this.

## Prerequisites
B07.

## Files
- `internal/entitlement/EntitlementEngine.kt`
- `internal/entitlement/EntitlementCache.kt`
- `internal/BillingRuntime.kt`: wires the connection, catalog, launcher, processor, engine, config and events
- `AdroitBilling.kt` (wire `initialize`, `entitlements`, `refresh`, `restore`, `setAccount`)
- `EntitlementProvider.kt` (fill in)
- Tests

## Requirements
1. Sync = `queryPurchasesAsync` for SUBS (including suspended, using the B01-verified parameter) and for INAPP.
   Run every purchase through B07, then rebuild the snapshot:
   - active = granted subscriptions (not suspended) plus owned non-consumables;
   - map each product to its entitlements via the enum `ProductSpec` and the remote `AdroitBilling.products`
     override (only for products declared in the enum);
   - `adFree = any(adFreeEntitlements)`.
2. Sync triggers:
   - `initialize` (after connect);
   - each `Connected` after a disconnect;
   - `ProcessLifecycleOwner` `ON_START` (debounced to at most once every 30 s);
   - `refresh()`;
   - `restore()`;
   - after `ITEM_ALREADY_OWNED`.
3. The cache stores the last *Fresh* snapshot (product IDs, entitlements, state, purchaseTime, and the fresh-at
   timestamp) in SharedPreferences. **No tokens.** At `initialize`, the cache is published at once with status
   `Cached(since)`, so `AdroitBilling.entitlements.value` is meaningful synchronously at app start, for example for
   splash and ads decisions.
4. Offline grace: a cached subscription grant older than `offlineGraceHours` (remote, default 72) without a fresh
   sync is dropped from `active`, and the status becomes `Unknown`. Non-consumables stay cached.
5. A failed sync never removes grants that were Fresh within the grace window, and never adds grants.
6. `restore()`: force a sync and return `RestoreResult.Restored(newlyActive: Set<String>)`, `NothingToRestore`, or
   `Failed(error)`. Emit `RestoreInitiated` and `RestoreCompleted`.
7. `setAccount(id)`: store the SHA-256 of the ID. When it changes from one non-null value to another, clear the cache
   and the attempt store, and re-sync. Document that Play purchases follow the **Google account**, not the app
   account, and that binding a purchase to an app account needs the backend.
8. `EntitlementProvider` (optional, set in `BillingSettings`): when set, its `suspend fun fetch(accountIdHash):
   Set<EntitlementGrant>?` result is **merged** with the Play grants. Backend grants can add `GRACE_FROM_BACKEND`
   and hold states. A null result means "unavailable", and the Play-only result is kept.
9. `initialize(application, settings)`: idempotent (a second call with equal settings is a no-op, with different
   settings it throws). It never blocks the main thread longer than reading prefs.

## Constraints
- Snapshots are emitted on main, and `distinctUntilChanged`.
- No public `clear()`. Tests use an internal reset.

## Edge cases
- A subscription that was active yesterday and was cancelled with time left: Play still returns it, so it stays
  active. When it expires, Play stops returning it and the next fresh sync drops it.
- A refund: Play stops returning it, so it's dropped on the next sync. The authoritative path is the backend RTDN,
  documented.
- Billing unavailable (no Play Store): the cached snapshot is kept within the grace window.
- An Unknown status means `has()` is false. The app must not treat Unknown as "free user forever": document how to
  show a neutral state.
- Two devices on the same Google account: both see the purchase via the query. Fine.

## Tests
- cold start from cache (a synchronous value);
- grace expiry under a virtual clock;
- a sync failure keeps grants;
- the remote mapping override can't add undeclared products;
- the `adFree` mapping;
- account switch clears and re-syncs;
- provider merge and provider null;
- restore results;
- ON_START debounce.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck :adroit-billing:lint
```

## Acceptance criteria
- [ ] `AdroitBilling.entitlements.value` is available synchronously right after `initialize`.
- [ ] No grant is ever invented by a failure path or by Firebase. Each has its own test.
- [ ] The cache holds no tokens: a test reads the prefs XML.

## Report
B00 §D.
