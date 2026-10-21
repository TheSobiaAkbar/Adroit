# Play Console: products, offers and test purchases

Everything a user can buy, every price and every trial is created here, in [Google Play
Console](https://play.google.com/console). The app's `Billing.kt` (step 4 of your
[guide](GETTING_STARTED.md)) refers to these products by their IDs, and Firebase
([Paywalls in Firebase](PAYWALL_REMOTE_CONFIG.md)) chooses among the offers you create here.

Before the products exist, the app can run in demo mode: made-up products with the same IDs, on any device.

## Before you start

- The app must be in Play Console, with a version that includes Google Play Billing (any build with the library)
  uploaded to a track, at least internal testing. Products can't be created before that.
- A payments profile is set up for the developer account (Play Console, Settings, Payments profile).
- IDs (product, base plan, offer) can't be changed or reused after creating them. Use lowercase letters, digits and
  `-`, as the library checks: `premium_sub` for a product, `monthly`, `yearly` for base plans, `trial-7d` for an
  offer.

## Subscriptions

Play Console, your app, **Monetize with Play, Products, Subscriptions**, **Create subscription**.

1. **Product ID**: the ID in `Billing.kt`, for example `premium_sub`. **Name**: what users see in Play.
2. Add **base plans**: one per billing period you sell. Each base plan has an ID, a type and prices per country.

| Base plan | ID (example) | Type | Notes |
|---|---|---|---|
| Weekly | `weekly` | Auto-renewing, 1 week | |
| Monthly | `monthly` | Auto-renewing, 1 month | |
| Yearly | `yearly` | Auto-renewing, 1 year | Usually with a lower price per month, which the paywall can show as "Save N%" (`savingsVs`) |
| Prepaid | `month-pass` | Prepaid, a fixed period | No automatic renewal; users top up. The paywall shows it as "€9.99 / 1 month". |

3. **Activate** each base plan. An inactive base plan isn't returned to the app: the paywall leaves that plan out.

The paywall's plans name the base plan: `PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly")` in the app, or
`"basePlan": "yearly"` in Firebase.

## Offers: free trials and introductory prices

An offer belongs to one base plan: open the base plan, **Add offer**.

| Field | What to set |
|---|---|
| Offer ID | For example `trial-7d`, `intro-yearly`. Firebase can ask for it by ID (`offer.ids`). |
| Eligibility | **New customer acquisition** (never had this subscription, or never had any of your subscriptions), **Upgrade**, or **Developer determined**. Play checks the first two itself. |
| Phases | A **free trial** (for example 7 days), and/or an **introductory price** (a lower price for a few periods). |
| Tags | Free labels, for example `trial`, `intro`, `blackfriday`. Firebase picks offers by tag (`offer.tags`). |

How it reaches the user:

- Play gives the app only the offers **this user** is eligible for. Someone who had a trial before doesn't get the
  trial offer: the paywall then shows the plan's regular price ([what happens](PAYWALL_REMOTE_CONFIG.md#when-a-trial-isnt-available)).
- **Developer-determined** offers are returned to everyone: the app decides. The library shows them only when the app
  says the user is eligible (`BillingSettings.offerEligibility`) and treats offers tagged `dev` as developer-determined
  (`developerDeterminedTags`). Without that, they're never shown.
- An offer must be **active**, like base plans.

Tags let Firebase follow your changes without a new app version: tag the current trial `trial`, and the paywall's
`"offer": { "tags": ["trial"] }` picks whichever trial offer has that tag.

## One-time products and consumables

**Monetize with Play, Products, One-time products** (called In-app products in older consoles), **Create product**.

- **Product ID**: the ID in `Billing.kt`: `premium_lifetime`, `coins_100`.
- Set a price and **activate** it.
- Play Console doesn't mark a product as consumable: the app does. `ProductSpec.consumable("coins_100")` makes the
  library deliver it to the app and consume it once the app confirms it; `ProductSpec.nonConsumable(…)` keeps it
  owned forever.

## License testers

License testers buy with test cards and are never charged.

1. Play Console, **Settings** (of the developer account, not the app), **License testing**.
2. Add the Gmail addresses of your testers (or a Google Group). The address must be the **first account** on the
   test phone, the one Play uses.
3. **License response**: `RESPOND_NORMALLY`.

License testers can install a debug build from Android Studio (with the release's `applicationId`) and buy: Play
doesn't require them to install from Play. Everyone else must install from a track.

## The internal testing track

1. **Testing, Internal testing, Create new release**: upload a release build (an `.aab`).
2. **Testers**: add a list of emails (up to 100). Copy the **opt-in link**.
3. Each tester opens the opt-in link, accepts, then installs the app from Play. It can take a few hours after the
   first upload before the app is available.

Testers who aren't license testers pay real money on a track. Add every tester as a license tester too.

## Test cards

In the Play purchase sheet, a license tester sees test payment methods:

| Payment method | What happens | What to check in the app |
|---|---|---|
| Test card, always approves | The purchase completes at once | Premium immediately; the paywall closes |
| Test card, always declines | The purchase fails | An error message on the paywall; nothing granted |
| Slow test card, approves after a few minutes | A **pending** purchase that completes later | "Payment pending" on the paywall, nothing granted; a few minutes later premium arrives, even if the app was closed in between (at the next launch) |
| Slow test card, declines after a few minutes | A pending purchase that is cancelled | Nothing granted, ever |

## Testing subscriptions over time

Test subscriptions renew much faster than real ones, and at most six times (trials and intro periods don't count).
From Google's [testing page](https://developer.android.com/google/play/billing/test) on 2026-10-20 (check it for
changes):

| Real period | Test renewal |
|---|---|
| 1 week | 5 minutes |
| 1 month | 5 minutes |
| 3 months | 10 minutes |
| 6 months | 15 minutes |
| 1 year | 30 minutes |

| Feature | Test duration |
|---|---|
| Free trial | 3 minutes |
| Grace period | 5 minutes |
| Account hold | 10 minutes |
| Introductory price period | Same as the subscription's test period |
| Purchase acknowledgement deadline | 5 minutes |

Scenarios:

- **Expiry.** Buy, cancel in Play (Manage subscription), wait for the test period to end, open the app: `premium`
  is gone (after the next sync with Play: at launch, or when the app returns to the foreground).
- **Trial not available.** Buy the trial with a tester, cancel, wait for it to end, then open the paywall again: the
  monthly plan shows its regular price and "Continue", not "Start free trial".
- **Grace period and account hold.** With Play Billing Lab, or by switching the tester's payment method to the
  declining test card before a renewal, the subscription enters grace period, then account hold. During account hold
  Play reports the subscription as suspended: the library doesn't grant it (`needsPaymentFix` is true), and
  `showInAppMessages` lets Play ask the user to fix the payment. During the grace period the device can't tell it
  apart from an active subscription; a backend can ([the backend contract](BILLING_BACKEND.md)).
- **Restore.** Uninstall, reinstall, open the app: premium comes back by itself at launch; "Restore purchases" says
  the purchases are up to date.

Related: [Billing](BILLING.md), [Billing troubleshooting](BILLING_TROUBLESHOOTING.md).

Last verified against: Play Billing Library 9.1.0, 2026-10-20
