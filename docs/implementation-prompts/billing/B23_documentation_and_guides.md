# B23 — Documentation and integration guides

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Write the complete billing documentation set, with code that compiles against the final API. That includes two
step-by-step integration guides (XML and Compose) and a **non-technical Firebase guide** that explains every paywall
setting, especially the close button delay.

## Context
The ads docs set the style: `docs/INTEGRATION_GUIDE.md` (non-technical), `NEW_APP_XML.md`, `NEW_APP_COMPOSE.md`,
`AD_PROFILES.md`, and `docs-snippets` (compiled snippets, `-PincludeDocsSnippets`). Read them and match their tone
and structure.

## Prerequisites
B22 (the API is final).

## Files
- `docs/BILLING.md`: overview, the modules table, which artifact to add, requirements (minSdk 24, compileSdk, PBL
  version, Kotlin), what the client can and can't know, and links.
- `docs/BILLING_NEW_APP_XML.md` and `docs/BILLING_NEW_APP_COMPOSE.md`: from zero to a first purchase:
  1. Gradle;
  2. the one `Billing.kt` file;
  3. `initialize`;
  4. the ads bridge;
  5. show a paywall;
  6. a custom paywall;
  7. restore/manage;
  8. consumables;
  9. testing.
- `docs/PAYWALL_REMOTE_CONFIG.md` (**non-technical**): every JSON field in a table with "what it does / allowed
  values / default / example". It must include:
  - **the close button**: `delaySeconds` 0 = immediately, 5 = after 5 seconds, −1 = never, plus `showCountdown`,
    `position`, `back`;
  - plans and how to pick an offer by `trialDays`/`tags`;
  - what happens when a trial isn't available;
  - show rules (counter, timer, caps);
  - campaigns with dates;
  - texts and placeholders;
  - colors;
  - the exit paywall.

  Then: how to paste the JSON in the Firebase console (the parameter name = the enum name), publish, and when users
  see it (next launch, fetch-once). Finish with a "what Firebase can NOT do" box (prices, trials, granting premium).
- `docs/PLAY_CONSOLE_BILLING_SETUP.md`:
  - creating subscriptions, base plans (weekly … yearly, prepaid), offers (free trial, intro, tags, eligibility
    incl. developer-determined), and one-time products;
  - license testers;
  - the internal track;
  - test cards (approve/decline/slow);
  - testing pending, ineligible trial, expiry (test subscription renewals are accelerated; check the current
    table in the Play docs) and grace/hold.
- `docs/BILLING_BACKEND.md` (from B15; review it).
- `docs/BILLING_TROUBLESHOOTING.md` + FAQ:
  - product missing;
  - `DEVELOPER_ERROR`;
  - "item already owned";
  - the trial not showing;
  - the paywall not showing (each `Skip` reason);
  - offline premium;
  - account switching.
- Add the billing pages to `README.md`'s doc index, `docs/MIGRATION.md` (API policy: billing follows ADR-007, with
  binary compatibility within 1.x), `CHANGELOG.md` (Unreleased) and `docs/TESTING.md` (the `FakeBilling` section).
- `docs-snippets`: add a billing snippet source set so every guide snippet compiles.

## Requirements
1. Every code block is either a compiled snippet or copied verbatim from a sample. No invented methods.
2. The non-technical guide has no Kotlin beyond the enum file. It's understandable to someone who only uses the
   Firebase console.
3. Security notes are visible in BILLING.md: client-only limits, no secrets in the app or Remote Config, never grant
   on pending.

## Verify
```
./gradlew -PincludeDocsSnippets :docs-snippets:build
```

Also do a link check of the markdown (relative links resolve).

## Acceptance criteria
- [ ] Every field accepted by the B11 parser appears in `PAYWALL_REMOTE_CONFIG.md`, and nothing more. Diff them
      programmatically: a unit test that lists parser keys vs. the doc table.
- [ ] The snippets compile.
- [ ] Both new-app guides were followed end to end against a scratch app (`consumer-smoke` can host it). Report the
      results.

## Report
B00 §D.
