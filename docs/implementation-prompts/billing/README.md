# Adroit Billing: implementation roadmap

The design is in `docs/architecture/06_BILLING_ARCHITECTURE.md`. The standing rules are in `B00_WORKING_RULES.md`.

## How to run the prompts
1. Open a Claude Code session at the repo root. Paste **one** prompt file, or say "execute
   `docs/implementation-prompts/billing/B05_product_catalog.md`".
2. Claude reads B00 and the architecture doc, implements only that stage, and ends with the B00 §D report.
3. Check the report's acceptance checklist. **Don't start the next prompt until every item is ✅.**
4. Commit after each stage: `feat(billing): B05 product catalog`.

## Order, dependencies and gates
| # | Prompt | Depends on | Gate (must be green before moving on) |
|---|---|---|---|
| B01 | Modules, Gradle, version verification | ads build green | 🚦 `./gradlew build`, `apiCheck` and the dependency guard pass |
| B02 | Domain model + public API skeleton | B01 | API dump committed |
| B03 | Billing periods, pricing phases, price text | B02 | |
| B04 | Play port, fake, connection lifecycle | B03 | |
| B05 | Product catalog | B04 | |
| B06 | Attribution + purchase launch | B05 | |
| B07 | Processing: verify, ack, consume, pending, idempotency | B06 | |
| B08 | Entitlements, sync, restore, account switch | B07 | 🚦 **Milestone 1: billing engine.** All core unit tests pass. |
| B09 | Upgrades/downgrades, manage, in-app messages | B08 | |
| B10 | Events + log redaction | B09 | |
| B11 | Paywall/billing JSON config | B08 | |
| B12 | Offer selection + show rules | B05, B11 | |
| B13 | PaywallController (close delay, back, purchase states) | B12 | 🚦 **Milestone 2: everything Firebase-controllable is testable on the JVM.** |
| B14 | `adroit-billing-firebase` | B10, B11 | |
| B15 | Backend contract + `HttpPurchaseVerifier` | B08 | |
| B16 | *(optional)* Reference backend (Cloud Functions) | B15 | `npm test` green |
| B17 | Compose paywall UI | B13 | |
| B18 | XML/Views paywall UI | B13 | |
| B19 | `adroit-billing-testing` | B13 | |
| B20 | sample-xml integration | B14, B18, B19 | 🚦 **Milestone 3: the XML sample works on a device (demo mode).** |
| B21 | sample-compose integration | B17, B20 | 🚦 Parity with XML |
| B22 | Hardening: R8, process death, leaks, device | B20, B21 | 🚦 Release builds work |
| B23 | Docs + integration guides (incl. the non-technical Firebase guide) | B22 | Snippets compile |
| B24 | Publishing + release | B23 | Awaits your approval to publish |
| B-FINAL | Production readiness audit | B24 | No FAIL |

Parallel options:
- B14, B15 and B16 can run in parallel with B17–B19 once B13 is done.
- B17 and B18 touch different modules, so they can run in separate sessions.

## External things only you can do
- **Play Console** (needed for real purchases, not for demo mode):
  - upload a sample build to an internal track;
  - create `premium_sub` (base plans weekly/monthly/yearly; a 3-day trial offer tagged `trial`; an intro offer
    tagged `intro`), `premium_lifetime` and `coins_100`;
  - add license testers.

  Steps are in `docs/PLAY_CONSOLE_BILLING_SETUP.md` (written in B23; a draft list is in B20).
- **Firebase console**: paste the paywall JSON keys (`docs/firebase/sample_billing_remote_config.json`, from B20) and
  publish.
- **Backend** (optional): a Google Cloud service account linked to Play Console. Never commit its key.

## Reporting failures
Paste back the failing acceptance item and the command output:

> "B07 acceptance 'acknowledged at most once' ❌ — `PurchaseProcessorTest.concurrentDelivery` fails: <output>. Fix
> and re-report."

If the repo differs from what a prompt expects (a renamed file or a different module), the prompt already tells
Claude to follow the real structure and report it. Later prompts read the earlier reports and the code, not this
plan.
