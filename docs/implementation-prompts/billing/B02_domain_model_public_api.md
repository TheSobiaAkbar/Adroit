# B02 — Domain model and public API skeleton

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Add every public domain type from 06_BILLING_ARCHITECTURE §3–4 to `adroit-billing`, with KDoc and tests, plus the
`AdroitBilling` facade whose methods throw `IllegalStateException("not initialized")` until B06/B08 wire them. Freeze
the shape of the API early, so later stages only fill in behaviour.

## Context
This mirrors the ads API style: `Adroit` object facade, `AdDefinition` enum interface with `defaults`,
`AdProfile` specs. Read `adroit-core/.../config/AdDefinition.kt` and `Adroit.kt` and copy their conventions:
- naming;
- `toString`;
- equals on spec classes;
- `toJson()`.

## Prerequisites
B01.

## Files (in `adroit-billing/src/main/kotlin/io/github/thesobiaakbar/adroit/billing/`)
- `AdroitBilling.kt`: the facade object, delegating to an internal `BillingRuntime?`.
- `BillingClientApi.kt`: the public interface the facade implements, so apps can inject it (DI-friendly, and
  fakeable in B20).
- `BillingSettings.kt`.
- `ProductDefinition.kt`, `ProductSpec.kt`, `ProductType.kt`.
- `PaywallDefinition.kt`, `PaywallSpec.kt`, `PlanSpec.kt`, `OfferSelection.kt`, `CloseBehavior.kt`, `ShowRules.kt`.
  These are the in-code defaults; the fields equal the JSON in §5.2.
- `ProductInfo.kt`, `SubscriptionPlan.kt`, `OfferInfo.kt`, `PricingPhase.kt`, `BillingPeriod.kt`.
- `Entitlement.kt`: `EntitlementSnapshot`, `EntitlementGrant`, `GrantState`, `GrantSource`, `SnapshotStatus`.
- `PurchaseRequest.kt`, `PurchaseResult.kt`, `PurchaseUpdate.kt`, `RestoreResult.kt`, `ConsumableGrant.kt`.
- `BillingError.kt`, `BillingConnectionState.kt`, `CatalogState.kt`, `Attribution.kt`.
- `PurchaseVerifier.kt`, `EntitlementProvider.kt`, `BillingEventListener.kt`, `BillingEvent.kt`.
- `BillingConfigSource.kt`: `fun interface` returning a raw JSON string for a key, plus an `updates` flow.
- Tests under `src/test/...`.

## Requirements
1. `ProductSpec` factories:
   - `subscription(id, entitlements)`;
   - `nonConsumable(id, entitlements)`;
   - `consumable(id)`.

   Validate: the ID matches Play's rules (`[a-z0-9][a-z0-9._]*`, ≤ 139 chars). Throw `IllegalArgumentException`
   with a clear message.
2. `PaywallSpec` defaults:
   - `enabled = true`;
   - `closeDelaySeconds = 0` (the X shows at once);
   - `back = ALWAYS`;
   - `showRestore = true`;
   - `onOfferUnavailable = BASE_PLAN`;
   - `maxPerSession = 0` (unlimited);
   - everything else off.

   Document every field with its JSON name.
3. `PurchaseRequest(product: ProductDefinition, placement: String, basePlanId: String? = null,
   offer: OfferSelection = OfferSelection.Auto, campaignId: String? = null, paywallId: String? = null)`.
   `placement` is required and non-blank (`[A-Za-z0-9_.-]{1,64}`).
4. `BillingError` is a sealed class with the cases in 06 §4, `isRetryable`, and a `debugMessage`. No Play int codes
   in the public API; a `playResponseCode: Int?` is allowed as plain data.
5. `EntitlementSnapshot.has(entitlement)`, `adFree`, `status`, and `EMPTY_UNKNOWN`. `has` returns false whenever the
   status is `Unknown`.
6. Facade surface: exactly the members listed in 06 §3. Suspend functions return results and never throw for
   expected billing failures (only for programmer errors such as not initialized).
7. `BillingPeriod.parse("P1W")` etc. arrives in B03. Declare the type here with the fields only.

## Constraints
- No Play types anywhere public. No `data class` in the public API: use classes with explicit
  `equals`/`hashCode`/`toString`, matching the ads modules (ADR-007 binary compatibility).
- Mark future-facing types that might change with `@ExperimentalAdroitBillingApi` (create the opt-in annotation).

## Edge cases
- Duplicate product IDs across enum entries: `BillingSettings` validation fails fast at `initialize` in debug and logs
  in release.
- A `PlanSpec` that references a product of type CONSUMABLE in a subscription slot gets a validation error.

## Tests
Unit tests for:
- `ProductSpec` validation;
- `PurchaseRequest` placement validation;
- `EntitlementSnapshot.has` with Unknown, Cached and Fresh;
- `BillingError.isRetryable` for every case;
- `BillingSettings` duplicate detection;
- equals/hashCode of the specs.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiDump :adroit-billing:apiCheck :adroit-billing:lint
```

## Acceptance criteria
- [ ] Every type in 06 §3–4 exists, is documented, and appears in the `.api` dump.
- [ ] No `com.android.billingclient` import outside `internal`. Check with `grep -r "billingclient" src/main` and look
      only for `internal/` hits.
- [ ] Tests pass.
- [ ] The facade methods throw a clear "call AdroitBilling.initialize first" error.

## Report
B00 §D. Don't modify unrelated working code.
