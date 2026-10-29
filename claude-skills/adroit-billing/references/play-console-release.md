# Play Console setup, troubleshooting, release

## Play Console setup (give the user this list, filled with the app's IDs)

**Before you start:**
- Upload a build that includes billing to a track (internal testing is enough). Products can't be created before
  that.
- Set up a payments profile.
- **IDs can never be changed or reused.** The library checks them:
  - **Product IDs:** lowercase letters, digits, `.` and `_` (`premium_sub`, `gems_50`).
  - **Base plan and offer IDs:** lowercase letters, digits and `-` (`monthly`, `trial-7d`).

1. **Subscriptions** (Monetize with Play → Products → Subscriptions):
   - Create one product per `ProductSpec.subscription` ID.
   - Add one **base plan** per period the paywalls name (`weekly`, `monthly`, `yearly`, or a prepaid one such as
     `month-pass`).
   - Set the prices, then **activate** each base plan. An inactive base plan is never returned to the app, so the
     paywall drops that plan.
2. **Offers**, on a base plan:
   - Set the offer ID, the eligibility (new customer, upgrade, or developer-determined), the phases (free trial,
     intro price) and the **tags** (`trial`, `intro`, `blackfriday`).
   - Activate it.
   - Paywalls pick offers by tag, so marketing can replace an offer without an app release.
   - Developer-determined offers show only with `BillingSettings.offerEligibility`, and when tagged as in
     `developerDeterminedTags` (default `dev`).
3. **One-time products** (lifetime, coins):
   - Create, set the price and activate.
   - Play has no consumable flag; `ProductSpec.consumable` decides that in code.
4. **License testers** (developer account Settings → License testing):
   - Add the testers' Gmail addresses, with the response set to `RESPOND_NORMALLY`.
   - Each tester's address must be the **first account** on the phone.
   - License testers can sideload a debug build that has the release `applicationId`. Everyone else installs from a
     track, and pays real money unless they are also a license tester.
5. **Internal testing track:**
   1. Upload the `.aab`.
   2. Add the testers.
   3. Share the opt-in link.

   The first availability can take hours.

**Test cards** (in the Play sheet, for license testers):

| Card | Result |
|---|---|
| Always approves | Purchased |
| Always declines | Failed |
| Slow approve | Pending, then completed |
| Slow decline | Pending, then cancelled |

**Accelerated renewals:**

| Period or feature | In testing |
|---|---|
| Weekly or monthly | renews every 5 min |
| Yearly | renews every 30 min |
| Trial | lasts 3 min |
| Grace period | 5 min |
| Account hold | 10 min |

A test subscription renews at most 6 times.

## Troubleshooting (logcat tag `AdroitBilling`)

| Symptom | Causes |
|---|---|
| A product or plan is missing from the paywall (`catalog.missing`) | Product ID typo; the product, base plan or offer isn't **active**; the build's `applicationId` has a `.debug` suffix (Play doesn't know that package); no build uploaded to any track yet; an emulator without the Play Store; the device account isn't a tester. |
| `DEVELOPER_ERROR` | The app isn't from Play and the account isn't a license tester; a signing key Play doesn't know; a second subscription without `replace`; an offer deactivated after the paywall loaded. |
| The trial doesn't show | The user isn't eligible (they had one before); the offer is inactive or on another base plan; the tags or IDs don't match; it is developer-determined without `offerEligibility`; or the rule is `BASE_ONLY`. |
| The paywall never shows | Check the `SkipReason` from `shouldShow`: `ENTITLED` (already premium), `SESSION_CAP`, `DAILY_CAP`, `LAUNCHES_NOT_REACHED`, `OUTSIDE_CAMPAIGN`, `GLOBAL_DISABLED` (Firebase `AdroitBilling.enabled`), or `NO_PLANS` (nothing active in Play). A button-opened paywall should skip `shouldShow`, or use `force = true` in XML. |
| A consumable is "already owned" | `grant.confirm()` was never called. |
| Premium is lost offline | Offline for longer than `offlineGraceHours` (72). |
| A premium user saw an ad or consent on launch | The bridge lacks the **synchronous** `Adroit.setAdFree(entitlements.value.adFree)` before collecting. |
| `initialize` throws in debug | Read the message: it lists every catalogue problem (unknown product in a paywall, duplicate ID, bad ID). |
| Every call returns `NotInitialized` in release | Same catalogue problem; release logs it instead of throwing. |
| Purchases on another Google account don't appear | Purchases belong to the Play account. Premium tied to the app's own account needs a backend. |
| "(DEMO)" prices in release | Impossible: release ignores demo billing. Check that it really is a release build. |

## Release checklist

- [ ] Every product, base plan and offer that the code or Firebase names exists in Play Console **and is active**.
- [ ] A license tester bought, restored, cancelled and resubscribed on a **release build from the internal track**,
      using the approve, decline and slow test cards.
- [ ] `DemoBilling(BuildConfig.DEBUG)`, so release never runs demo billing (it would be ignored anyway, with an error
      log).
- [ ] No hard-coded price, period or trial text anywhere. Every custom paywall shows `Disclosure()` /
      `PriceText.disclosure` next to its button.
- [ ] Restore purchases is reachable: in Settings, and on any hard paywall.
- [ ] Manage subscription opens Play's page; Play's in-app messages run on the main screen.
- [ ] The Firebase paywall parameters are published, or left out so the code defaults apply. `PremiumEnabled` has the
      intended value for this phase.
- [ ] With ads: after a purchase no ad shows, and the next cold start is a short splash with no consent and no ad.
- [ ] R8: the billing modules ship empty consumer rules on purpose (no reflection), and Play Billing ships its own.
      The app adds nothing. Still run the minified release once: see adroit-admob, `release-troubleshooting.md`, for
      the AGP 9 `packageScope` crash.
- [ ] Privacy policy and terms URLs are in the app (Play requires them for subscriptions), and the Play data safety
      form covers purchase history.
