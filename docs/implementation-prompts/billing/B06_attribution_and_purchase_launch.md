# B06 — Placement attribution and purchase launch

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Implement `AdroitBilling.purchase(activity, PurchaseRequest): PurchaseResult` up to the moment Play reports a result.
That covers:
- choosing the offer token;
- a single-flight launch;
- persisting the attribution *before* launch;
- mapping the `PurchasesUpdatedListener` result back to the attempt.

Granting, acknowledging and consuming come in B07. This stage returns `Purchased` from a stubbed processor B07
replaces, or B06 and B07 can share one internal `PurchaseProcessor` interface.

## Context
06 §4 (state machine) and §6 (attribution). Placement, paywall ID, campaign ID, product ID, base plan ID and offer
ID are separate fields.

## Prerequisites
B05.

## Files
- `internal/purchase/PurchaseLauncher.kt`
- `internal/attribution/AttemptStore.kt`: SharedPreferences `adroit_billing_attempts` storing JSON records.
- `internal/attribution/AttributionMatcher.kt`
- `Attribution.kt` (fill in)
- `BillingSettings.attributionToken`: `LOCAL_ONLY` (default) or `PROFILE_ID`
- Tests

## Requirements
1. Resolve the request:
   - the product must be in the catalog (`awaitProduct`, timeout 5 s). If it isn't, return `Failed(ProductNotFound)`.
   - For subscriptions, find the base plan (`request.basePlanId`, or the paywall's / the first plan) and pick the
     offer:
     - `OfferSelection.Auto` uses the policy from B12 if available, else `BASE_ONLY`;
     - `OfferSelection.Id(offerId)`;
     - `OfferSelection.Tags(...)`;
     - `OfferSelection.BaseOnly`.

     Not found gives `Failed(OfferNotFound)`. **Never** silently substitute another offer.
2. Single flight: a second `purchase()` while one is between `Launching` and a result returns
   `Failed(PurchaseInProgress)` at once.
3. Create an `attemptId` (`UUID.randomUUID()`). Persist `Attribution` plus `createdAt` **synchronously** (`commit()`)
   before `launchBillingFlow`. Records older than 72 h are pruned.
4. `BillingFlowParams`:
   - `ProductDetailsParams` with the offer token;
   - `setObfuscatedAccountId(hashOf(account))` when `setAccount` was called (SHA-256 hex of the app's account ID,
     never the raw ID);
   - `setObfuscatedProfileId(attemptId without dashes)` only when `attributionToken == PROFILE_ID`. Check B01's
     finding on whether an account ID is then required.
5. The launch result: if the `BillingResult` isn't OK, map it to an error and mark the attempt finished. If it's OK,
   suspend until the matching `PurchasesUpdated` arrives. If the coroutine is cancelled (the screen left), the attempt
   stays open and its outcome later reaches `purchaseUpdates` (a `SharedFlow`, replay 0, buffered 16).
6. Matching a `Purchase` to an attempt:
   1. a `profileId` equal to the attemptId;
   2. else the newest open attempt whose product ID is in `purchase.products`;
   3. else `Attribution.unknown(product)` with `recovered = true`.
7. `USER_CANCELED` gives `Cancelled` (not an error). `ITEM_ALREADY_OWNED` gives `AlreadyOwned`, then triggers a
   purchase sync (B08) so the entitlement appears.
8. Events (B11 fills the sink): `PurchaseInitiated`, `PurchaseCancelled`, `PurchaseFailed`, all with the attribution.

## Constraints
- The Activity is used only inside `purchase()`. Check `activity.isFinishing || isDestroyed`: such an Activity gives
  `Failed(NoActivity)`.
- `purchase()` must be called on main (`@MainThread`). Assert it in debug.

## Edge cases
- Rotation during the Play sheet: the Activity is recreated, and the suspended call is cancelled with the old
  ViewModel scope if the app used `lifecycleScope`. The result still reaches `purchaseUpdates` with the correct
  attribution. Document the recommendation to call from a ViewModel scope.
- Process death during the Play sheet: on the next start, B08 sync finds the purchase, and the matcher recovers the
  attribution from prefs.
- Two placements buying the same product in quick succession: single flight prevents it.
- A placement with characters outside `[A-Za-z0-9_.-]` is rejected at `PurchaseRequest` creation.

## Tests
With the fake port:
- a happy launch;
- launch errors;
- cancel;
- already owned;
- in progress;
- offer not found (no substitution);
- attribution persisted before launch (assert the store was written before the fake's `launch` was invoked);
- matching by profile ID / product / unknown;
- the 72 h prune under a virtual clock;
- a cancelled caller still delivered to `purchaseUpdates`.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] No path assigns a placement that wasn't recorded for that product. Proven by the "unknown" test.
- [ ] No raw account ID or token is stored or logged.
- [ ] Single flight is proven.

## Report
B00 §D.
