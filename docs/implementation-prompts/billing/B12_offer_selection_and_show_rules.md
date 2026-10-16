# B12 — Offer selection, trial campaigns and paywall show rules

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
1. Resolve each configured paywall plan to a **real, eligible** Play offer, or apply `onOfferUnavailable`. A trial or
   intro price that Play doesn't give this user is never advertised.
2. Decide whether a paywall should show now: `AdroitBilling.paywalls.shouldShow(def, placement)`. This uses the
   counter, timer, per-session/per-day caps, launch count, campaign window, entitlement hiding, and the global
   enabled flag, with the same semantics as the ad profile counter and timer.

## Context
06 §5.2 (offer rules), §1 "not allowed" items 1 and 4, and the ads' counter/timer semantics in `docs/AD_PROFILES.md`
(counter N = show on every Nth call; when a counter is set, the timer is ignored; the timer = the minimum seconds
between two showings of *this* paywall).

## Prerequisites
B05, B11.

## Files
- `OfferSelector.kt` (public `fun interface`, an app override): `select(plan: SubscriptionPlan, rule: OfferRule,
  context): OfferInfo?`
- `internal/offers/DefaultOfferSelector.kt`
- `OfferEligibility.kt` (public `fun interface`, optional): veto for *developer-determined* offers (risk R6)
- `internal/paywall/PaywallResolver.kt`: `ResolvedPaywall` + catalog → `PaywallModel` (plans with the chosen offer,
  price texts from B03, badges, the selected index)
- `internal/paywall/ShowRules.kt` + `ShowCounterStore` (SharedPreferences: per-paywall counts, last shown, the day
  bucket, the launch count)
- `Paywalls.kt` (public): `shouldShow(def, placement): ShowDecision` (`Show` | `Skip(reason)`), `model(def):
  StateFlow<PaywallModelState>`, `recordShown(def, placement)` (UI modules call it), `recordClosed(...)`
- Tests

## Requirements
1. Offer selection order: `ids` → `tags` (all must match) → `trialDays` (a first phase free with a period equal to N
   days, where `P1W` = 7) → `policy`. Policies:
   - `PREFER_FREE_TRIAL`;
   - `PREFER_INTRO_PRICE`;
   - `LOWEST_FIRST_PAYMENT`;
   - `BASE_ONLY`.

   Ties go to the first in Play's order.
2. Eligibility: only offers in the user's returned `subscriptionOfferDetails`. If the chosen offer has a tag in
   `BillingSettings.developerDeterminedTags` (default `["dev"]`), ask `OfferEligibility`. If there's no hook, refuse
   it.
3. `onOfferUnavailable`:
   - `BASE_PLAN`: use the base offer, and the trial/intro copy is replaced by normal copy;
   - `HIDE_PLAN`: remove the plan;
   - `HIDE_PAYWALL`: `shouldShow` gives `Skip(OFFER_UNAVAILABLE)`.

   Emit `OfferUnavailable(requested, fallback)` each time.
4. Text placeholders resolve only from the chosen offer. `ctaTrial` is used only when the chosen offer has a free
   trial. `{savings}` appears only when B03 returns a value.
5. Show rules (in order; the first failing one gives `Skip(reason)`):
   1. global disabled;
   2. paywall disabled;
   3. outside the campaign window (the device clock, documented as tamperable and fine for presentation only);
   4. the user has any `hideIfEntitled` entitlement (the default is the paywall's products' entitlements);
   5. `firstAfterLaunches` not reached;
   6. `maxPerSession`;
   7. `maxPerDay` (local-date bucket);
   8. counter (counts every `shouldShow` call for that paywall; due on multiples of N);
   9. timer (when the counter is 0);
   10. no purchasable plan after resolution (the catalog is Ready and empty, giving `NO_PLANS`). If the catalog is
       still Loading, return `Show`, and the UI shows its loading state (B17).

   A `Skip` emits `PaywallSuppressed(reason)`.
6. The exit paywall: `recordClosed(def, reason = CLOSE_BUTTON|BACK)` returns the `exitPaywall` definition if it is
   set, if `shouldShow` allows it, and if it hasn't been shown this session already. Otherwise it returns null.
7. Launch count: incremented once per process in `initialize`.

## Edge cases
- The same product in two plans with different base plans (monthly and yearly).
- A trial requested (`trialDays: 7`) where Play only has a 3-day trial: no match, so the fallback applies. **Never
  show "7 days".**
- A user who used a trial before: Play omits the trial offer, so the fallback applies.
- A clock change backwards: the timer uses `SystemClock.elapsedRealtime` within a process, and wall time across
  processes. Document it.

## Tests
Table-driven:
- every selection path and policy;
- the developer-determined veto;
- every `onOfferUnavailable` branch;
- placeholder resolution (no trial text without a trial);
- each show rule individually and in combination;
- counter semantics identical to the ads (3 → 3rd, 6th, 9th);
- the exit paywall once per session;
- day rollover under a virtual clock.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] No test or code path can display a trial or intro price that isn't in the chosen eligible offer.
- [ ] Show rule semantics match `docs/AD_PROFILES.md` (counter, timer, caps), cross-checked in tests.

## Report
B00 §D.
