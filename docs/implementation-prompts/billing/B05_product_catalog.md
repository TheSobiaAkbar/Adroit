# B05 — Product catalog: querying, mapping and caching

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Load the `ProductDetails` of every `ProductDefinition` from Play and expose them as
`AdroitBilling.catalog: StateFlow<CatalogState>`. Each product is mapped to `ProductInfo` with its plans, offers and
pricing phases. The offer tokens stay internal.

## Prerequisites
B04.

## Files
- `internal/catalog/CatalogRepository.kt`
- `internal/play/PlayMappers.kt` (extend)
- `CatalogState.kt` (fill in): `Loading`, `Ready(products: Map<ProductDefinition, ProductInfo>, missing:
  Set<ProductDefinition>)`, `Failed(error)`
- Tests

## Requirements
1. Query subscriptions and one-time products in **two** `queryProductDetailsAsync` calls (the product type is per
   call). Batch IDs per call. Read `QueryProductDetailsResult.unfetchedProductList` (verify the name). Report each
   unfetched product in `missing`, logging its status code at WARN with the reason.
2. One-time products: map `oneTimePurchaseOfferDetailsList` (PBL 8+, multiple purchase options) when present, and
   fall back to `oneTimePurchaseOfferDetails`. Keep the purchase option ID and the offer token for launch.
3. Subscriptions: for each `subscriptionOfferDetails` entry, produce `basePlanId`, `offerId` (null for the base
   offer), `offerTags`, phases (B03) and the internal offer token. Group by base plan. `SubscriptionPlan.basePhase` is
   taken from the base-plan-only offer.
4. When to load:
   - on first `Connected`;
   - when `refresh()` is called;
   - when the app returns to the foreground and the cache is older than `catalogMaxAge` (default 6 h; a
     `BillingSettings` field).

   Single flight: concurrent requests share one load.
5. Keep the previous `Ready` while a refresh runs. A failed refresh keeps the old `Ready` and logs the error, and only
   emits `Failed` when there was nothing before.
6. Add `AdroitBilling.product(def): ProductInfo?` (a synchronous snapshot) and
   `suspend fun awaitProduct(def, timeout)`.

## Constraints
- No disk cache of prices. Prices are locale and account sensitive and must come fresh from Play each process.

## Edge cases
- A product ID in the enum that isn't created in Play Console, or is inactive, ends up in `missing`. Any paywall plan
  referencing it is hidden (B12), never shown with a fake price.
- A subscription with zero offers returned (the user is ineligible for everything, or there's no active base plan)
  is reported in `missing`.
- The user switches the Play account while the app is in the background: the next foreground refresh replaces the
  catalog.

## Tests
With `FakePlayBillingPort`:
- the mapping of a weekly, monthly+trial and yearly+intro subscription, a non-consumable with two purchase options,
  and a consumable;
- unfetched products;
- single flight;
- stale-while-refresh;
- failure with no previous catalog;
- the max-age refresh on foreground under virtual time.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] The catalog emits `Ready` with correct plans and phases for every fixture.
- [ ] Missing products never crash and are surfaced.
- [ ] Offer tokens don't appear in any public type (`apiDump` diff reviewed).

## Report
B00 §D.
