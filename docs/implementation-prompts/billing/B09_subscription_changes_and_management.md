# B09 — Upgrades, downgrades, replacement, manage subscription and in-app messages

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Support plan changes between subscription products and base plans, and the "manage subscription" deep link. Also
show Play's in-app messages, which cover payment declined, account hold and price-increase opt-in.

## Context
PBL 8.1 added `BillingFlowParams.ProductDetailsParams.setSubscriptionProductReplacementParams(
SubscriptionProductReplacementParams)` with `setOldProductId` and `setReplacementMode` (including `KEEP_EXISTING`).
The older `SubscriptionUpdateParams#setSubscriptionReplacementMode` is headed for deprecation. **Verify both in the
reference for the catalog's PBL version, and use the newer one.**

## Prerequisites
B08.

## Files
- `PurchaseRequest.kt`: add `replace: SubscriptionReplacement? = null` (`ReplacementMode` enum mirrored in the
  domain: `WITH_TIME_PRORATION`, `CHARGE_PRORATED_PRICE`, `CHARGE_FULL_PRICE`, `WITHOUT_PRORATION`, `DEFERRED`,
  `KEEP_EXISTING`, limited to what the verified API offers)
- `internal/purchase/PurchaseLauncher.kt`
- `AdroitBilling.openManageSubscriptions(activity, product?)`
- `AdroitBilling.showInAppMessages(activity)`
- Tests

## Requirements
1. `purchase(..., replace = SubscriptionReplacement(oldProduct, mode))`: the old product must be an active grant in
   the snapshot. If it isn't, return `Failed(ItemNotOwned)`.
2. **Same product, different base plan** (monthly → yearly) and **different products** (basic → pro) both work.
   Document which replacement modes Play allows for each case. Pass Play's `DEVELOPER_ERROR` through as
   `DeveloperError` with Play's debug message.
3. Auto-detect: if the user has an active subscription in the same "group" (products sharing an entitlement), and
   the app calls `purchase` for another plan without `replace`, return `Failed(DeveloperError("active subscription
   X; pass replace"))` in debug, and log a WARN in release. Never start a second parallel subscription by accident.
4. `openManageSubscriptions`: an `ACTION_VIEW` of
   `https://play.google.com/store/account/subscriptions?sku=<id>&package=<pkg>`, or without the `sku` when it's
   null. Catch `ActivityNotFoundException`, log it, and return false.
5. `showInAppMessages(activity)`: `BillingClient.showInAppMessages` with
   `InAppMessageParams.newBuilder().addInAppMessageCategoryToShow(TRANSACTIONAL)`. When the response says the
   subscription status was updated, trigger a sync. Recommend calling it in the app's main Activity `onResume`
   (the samples do this in B20/B21).
6. Suspended grants in the snapshot expose `needsPaymentFix = true`. UIs (B17/B18) show a "Fix payment" action that
   calls `openManageSubscriptions`.

## Edge cases
- `DEFERRED`: the new plan starts at renewal. The snapshot keeps the old product until Play reports the new one.
  Document it.
- Prepaid plans can't be replaced with some modes. Pass Play's error through.

## Tests
Fake port:
- replacement params built with the correct old product and mode;
- not-owned error;
- the parallel-subscription guard;
- the manage URL for both variants;
- an in-app message "updated" triggers a sync.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] Only the non-deprecated replacement API is used, or the report explains why not.
- [ ] Nothing can accidentally create a second parallel subscription in the same entitlement group.

## Report
B00 §D.
