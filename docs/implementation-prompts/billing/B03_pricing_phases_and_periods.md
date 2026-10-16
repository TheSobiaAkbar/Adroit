# B03 — Billing periods, pricing phases and price display

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Write pure-Kotlin logic that turns Play pricing phases into the domain `PricingPhase` and `BillingPeriod` and into
display facts:
- trial length;
- intro price;
- renewal price;
- per-period price;
- approximate savings.

Nothing about durations or prices is hardcoded. Weekly, monthly, two-month, quarterly, six-month, yearly and any
other ISO-8601 period all work.

## Context
06 §4. Play gives `billingPeriod` (ISO-8601, for example `P1W`, `P1M`, `P3M`, `P6M`, `P1Y`, `P7D`), plus
`billingCycleCount`, `recurrenceMode` (`INFINITE_RECURRING`, `FINITE_RECURRING`, `NON_RECURRING`),
`priceAmountMicros`, `priceCurrencyCode` and `formattedPrice`.

## Prerequisites
B02.

## Files
- `adroit-billing/.../BillingPeriod.kt`: `parse`, `approxDays`, `toString`.
- `internal/pricing/OfferAnalysis.kt`: `freeTrial`, `introPhases`, `basePhase`, `isPrepaid`.
- `PriceText.kt`: a public helper that formats `{trial}`, `{period}`, `{price}`, `{renewalPrice}`, `{perWeek}`
  and `{savings}` placeholders, using **Android plurals resources** (`adroit_billing_period_*`) so each locale can
  translate it.
- `src/main/res/values/adroit_billing_strings.xml`: English plurals for day, week, month and year, plus "per
  {period}".
- Tests.

## Requirements
1. `BillingPeriod.parse` accepts `P{n}D`, `P{n}W`, `P{n}M`, `P{n}Y` and combinations such as `P1Y2M`. It rejects
   time parts (`PT…`) and garbage by returning null. It never throws on remote data.
2. Phase classification:
   - free trial = `priceAmountMicros == 0` and the first phase;
   - intro = paid, finite (`FINITE_RECURRING` or `NON_RECURRING`), and not the last phase;
   - base = the last phase with `INFINITE_RECURRING`;
   - prepaid base plans have **no** infinite phase, so `isPrepaid` is true and the "renews at" text is omitted.
3. `savings(plan, vs)` uses `priceMicros / approxDays` per day. It returns null when:
   - the currencies differ;
   - either price is 0;
   - either period can't be parsed.

   Round **down** to a whole percent, so savings are never overstated.
4. `{perWeek}` and `{perMonth}` are derived prices formatted with `java.text.NumberFormat.getCurrencyInstance` and
   the phase's currency code. They're only offered as opt-in display. The authoritative string is always Play's
   `formattedPrice`.
5. Two-month (`P2M`), quarterly (`P3M`) and six-month (`P6M`) plans render as "2 months", "3 months", "6 months",
   with no hardcoded "quarterly". Locale text comes from resources.

## Constraints
- No Android dependency in the parsing and analysis code (plain JVM tests). Only `PriceText` touches `Resources`.

## Edge cases
- A two-phase intro (a free trial, then 3 months at a discount, then base).
- An offer whose only phase is the base (a base-plan-only offer).
- `billingCycleCount = 0` on infinite phases.
- Currencies without decimals (JPY).
- A malformed period from a fake gives a null analysis, never a crash.

## Tests
A table-driven JUnit test with at least 25 cases: every period above, multi-phase offers, prepaid, savings
rounding, mixed currencies, and JPY formatting. Use a Robolectric test for the plurals rendering in `en`.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest
```

## Acceptance criteria
- [ ] No duration literals (7, 30, 365…) in production code except inside `approxDays` (documented as approximate).
- [ ] All tests pass. Savings are never overstated.
- [ ] `PriceText` output for P1W, P1M, P2M, P3M, P6M and P1Y is checked in tests.

## Report
B00 §D.
