# B11 — Paywall and billing config: JSON schema, parser, validator, local source

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Make every paywall setting in 06 §5 controllable from a JSON value per key:
- the global `AdroitBilling` key;
- one key per `PaywallDefinition` entry name.

Parse it defensively. Merge it over the enum defaults field by field, and expose the resolved, validated config to
the rest of the library. The **close button delay** (`close.delaySeconds`), countdown, back behaviour, plans,
offers, texts, style, show rules, campaign window and exit paywall all live here.

## Context
This mirrors the ad profiles exactly. Read these and copy their parsing style (`org.json`, a lenient per-field
fallback, logged invalid fields, `toJson()` of the defaults):
- `adroit-core/.../internal/config/AdProfiles.kt`;
- `AdDefinitionJson.kt`;
- `docs/AD_PROFILES.md`.

## Prerequisites
B02 (types), B08 (the runtime to plug into).

## Files
- `PaywallSpec.kt` and related (complete every field in §5.2 with defaults)
- `BillingConfigSource.kt`:
  - `fun interface BillingConfigSource { fun raw(key: String): String? }` plus `val updates: Flow<Unit>` (a default
    empty flow);
  - factories: `BillingConfigSource.none()`, `BillingConfigSource.local(context, @RawRes res)` (one JSON object
    whose top-level keys are the RC keys), and `BillingConfigSource.of(map)`.
- `internal/config/PaywallJson.kt`: parse and merge
- `internal/config/BillingGlobalJson.kt`
- `internal/config/ResolvedPaywall.kt`: immutable, validated
- `PaywallDefinition.toJson()`: prints the defaults as JSON for pasting into Firebase (the same feature as ads)
- `BillingSettings.config`, plus programmatic overrides: `BillingSettings.overrides: Map<String, String>`, applied
  after remote (the app always wins)
- Tests, plus `src/test/resources/paywall_*.json` fixtures

## Requirements
1. Precedence (per field): app overrides > remote source > enum defaults. A field that's missing or invalid in a
   layer falls through to the next layer.
2. Validation (invalid gives a fallback plus `ConfigInvalid` event plus a WARN log, never a crash):
   - `close.delaySeconds`: integer −1…60 (larger is clamped to 60);
   - `close.back`: `ALWAYS|AFTER_DELAY|NEVER`;
   - `close.position`: `TOP_START|TOP_END`;
   - `plans[].product` must name a `ProductDefinition` entry (by enum name), else the plan is dropped;
   - `selected` is clamped into range;
   - `show.*` are non-negative integers;
   - `campaign.start` and `campaign.end` are ISO-8601 instants (`java.time` is fine: minSdk 24 with core library
     desugaring? **Check.** If desugaring isn't enabled in the library, parse with `SimpleDateFormat` UTC);
   - `style` colors are `#RRGGBB` or `#AARRGGBB`;
   - `headerImage` must be `https://`;
   - texts are at most 300 characters each, with no HTML.
3. A hard paywall (`delaySeconds = -1` and `back = NEVER`) is allowed, logs a WARN once per process, and emits a
   `ConfigInvalid`-like `HardPaywall` diagnostic.
4. Unknown fields are ignored (forward compatibility). `configVersion` is recorded in `ConfigLoaded`.
5. Re-resolve when `source.updates` emits. Already-open paywalls keep the config they opened with; the next opening
   uses the new one.
6. Global key: `enabled` (false = `paywalls.shouldShow` always false, while purchases still work), `adFreeEntitlements`,
   `offlineGraceHours` (1…720), `products{Name:{entitlements}}` (declared products only).
7. `docs/architecture/06_BILLING_ARCHITECTURE.md` §5 must stay identical to what the parser accepts. Update it if you
   had to change anything.

## Constraints
- No Firebase here (that's B14). No reflection, no kotlinx.serialization (keep `org.json`, as in the ads).

## Edge cases
- The remote value is an empty string, `null`, a JSON array, or a 1 MB string (refuse anything over 64 KB).
- A remote paywall that lists only products missing from the catalog: B13 decides whether to hide it. The config
  just carries it.
- Remote texts containing `{trial}` on a non-trial plan: carry it; B13/B17 resolve it.

## Tests
- every field: valid, invalid, missing, for each layer;
- precedence;
- the hard paywall warning;
- the size limit;
- `toJson()` round-trip (defaults → JSON → parse equals defaults);
- the local raw source;
- updates re-resolve.

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] Every field in 06 §5.2 parses, and each has a test for valid and invalid input.
- [ ] Malformed remote JSON can't crash or change prices or entitlements.
- [ ] `AppPaywalls.Onboarding.toJson()` prints a complete, pasteable JSON.

## Report
B00 §D.
