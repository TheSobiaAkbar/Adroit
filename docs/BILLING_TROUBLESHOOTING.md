# Billing troubleshooting and FAQ

Start with logcat. The library logs under the tag `AdroitBilling` (warnings and errors by default; set
`BillingSettings(logLevel = BillingLogLevel.DEBUG)` in a debug build for more). Every purchase, paywall and error is
also a `BillingEvent`: add a `BillingEventListener` that logs them, as the samples do, to see each step with its
placement. Purchase tokens are never logged.

## A product is missing

Logcat: `Product PremiumSub (premium_sub) missing: not found in Play Console, or inactive (status 3)`. The paywall
leaves the plan out, and skips a paywall with no plan left (`NO_PLANS`).

Check, in this order:

1. **The ID.** The string in `ProductSpec.subscription("premium_sub", …)` is exactly the product ID in Play Console.
2. **Active.** The product, and for a subscription its base plan, are **active** in Play Console.
3. **Same app.** The app's `applicationId` is the one in Play Console (not a debug `.debug` suffix).
4. **Uploaded.** A build with billing is uploaded to a track (internal testing is enough), and the phone's Google
   account is a license tester or a tester of that track. Products can take a few hours to appear after creation.
5. **The Play Store.** Signed in, up to date. On an emulator, use an image "with Google Play".
6. **Demo mode?** With demo mode on, products are made up and every declared product is there. Missing products with
   demo mode on mean the app isn't debuggable (demo is ignored in release builds).

## `DEVELOPER_ERROR`

Play rejected the request as malformed. `PurchaseResult.Failed(BillingError.DeveloperError(…))`, with Play's message
in `debugMessage`. Common causes:

- **The app isn't from Play and the account isn't a license tester.** Play Billing works for apps installed from
  Play, or sideloaded by a license tester ([Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md#license-testers)).
- **Signed with another key** than the one Play knows (for a build installed outside Play by a non-tester).
- **A second subscription.** The library refuses to start a subscription while the user has an active one sharing an
  entitlement (`"active subscription premium_sub; pass replace"`): changing plans needs
  `PurchaseRequest(replace = …)`, so Play prorates instead of charging twice.
- **An offer that no longer exists** because it was deactivated between loading the paywall and tapping the button.
  Reopening the paywall fixes it.

## "Item already owned"

The user tapped buy on something they already have: a lifetime product, or a consumable not consumed yet.

- The library returns `PurchaseResult.AlreadyOwned`, syncs with Play, and the user gets what they own. Nothing to do.
- **A consumable that stays "already owned"** wasn't confirmed: the app must call `grant.confirm()` after crediting it
  ([consumables](guides/ADS_IAP_XML.md#consumables-optional)). Unconfirmed grants are delivered again at every launch until
  confirmed.
- By default, paywalls don't show to users who own what they sell (`show.hideIfEntitled`), so this mostly comes from
  your own buttons.

## The free trial doesn't show

The paywall shows "Continue" and the regular price instead of "Start free trial":

1. **The user isn't eligible.** Play gives a trial only to users who never had one for this subscription (or any of
   yours, depending on the offer's eligibility). The library never shows a trial Play didn't give. Test with a new
   tester account, or a tester that never subscribed.
2. **The offer isn't active,** or is on another base plan than the paywall's `basePlan`.
3. **The paywall asks for another offer.** Its `offer.tags`, `offer.ids` or `offer.trialDays` don't match the offer
   in Play Console: a tag `trial` in Firebase needs the tag `trial` on the offer. Logcat shows an `OfferUnavailable`
   event with what was requested.
4. **Developer-determined offer.** Shown only when `BillingSettings.offerEligibility` says yes.
5. **`"policy": "BASE_ONLY"`** in Firebase hides offers on purpose.

What happens instead is up to `onOfferUnavailable` ([Paywalls in Firebase](PAYWALL_REMOTE_CONFIG.md#when-a-trial-isnt-available)).

## The paywall doesn't show

`AdroitPaywall.show` and `AdroitBilling.paywalls.shouldShow` return `ShowDecision.Skip(reason)`, and each skip is a
`PaywallSuppressed` event with the reason in lowercase (`reason=entitled`):

| Reason | Why | What to do |
|---|---|---|
| `NOT_INITIALIZED` | `AdroitBilling.initialize` hasn't run | Call it in `Application.onCreate`, and register the Application in the manifest |
| `NOT_REGISTERED` | The paywall isn't in `BillingSettings(paywalls = …)` | Pass `AppPaywalls.entries` |
| `GLOBAL_DISABLED` | The `AdroitBilling` Firebase parameter has `"enabled": false` | Set it back to `true` |
| `PAYWALL_DISABLED` | This paywall's `"enabled": false`, in code or Firebase | |
| `OUTSIDE_CAMPAIGN` | Before `campaign.start` or after `campaign.end` (phone clock) | Check the dates and their time zone |
| `ENTITLED` | The user has one of `show.hideIfEntitled`: by default, what the paywall sells | Expected for premium users. `"hideIfEntitled": []` shows it to everyone. |
| `LAUNCHES_NOT_REACHED` | Fewer app launches than `show.firstAfterLaunches` | |
| `SESSION_CAP` | Shown `show.maxPerSession` times this session | Restart the app to test again |
| `DAILY_CAP` | Shown `show.maxPerDay` times today | Wait for tomorrow, or clear the app's data |
| `COUNTER_NOT_REACHED` | `show.counter` isn't reached: it shows on every Nth time asked | |
| `TIMER` | Less than `show.timerSeconds` since it last showed | |
| `NO_PLANS` | No plan can be bought: every product is missing from Play, or every plan was hidden | See [a product is missing](#a-product-is-missing) |
| `OFFER_UNAVAILABLE` | A plan's offer isn't available and `onOfferUnavailable` is `HIDE_PAYWALL` | Expected for users without the trial |

A button the user taps should open the paywall whatever the rules: `force = true` (Views), or show `Paywall` without
asking `shouldShow` (Compose). A Firebase change applies from the next paywall opening after the app fetched it
([when users see it](PAYWALL_REMOTE_CONFIG.md#putting-it-in-firebase)).

## Premium while offline

- At launch, `initialize` reads the last confirmed entitlements from the device, so a premium user is premium before
  Play answers, and offline. `entitlements.value.status` is then `Cached(atMillis)`.
- A **subscription** stays active offline for `offlineGraceHours` after the last successful check with Play (72 hours
  by default; Firebase can change it, 1 to 720). After that it's dropped until Play confirms it again. **Lifetime**
  products stay active offline.
- A new install starts `Unknown`, with nothing granted, until Play answers.
- Play's answer replaces the cache: a subscription that ended while offline is removed then, and
  `EntitlementLost` is reported.

## Switching accounts

Google Play purchases belong to the **Google account** on the phone, not to your app's sign-in.

- Call `AdroitBilling.setAccount(userId)` when your user signs in; signing out needs no call. The ID goes to Play
  only as a SHA-256 hash, and new purchases are tagged with it.
- When a **different** account signs in, the library clears the saved entitlements and purchase attempts, then asks
  Play again. Play still returns the Google account's purchases: two app accounts on one phone see the same Play
  purchases.
- To give premium per app account (bought on one phone, used on another, or not shared between accounts on one
  phone), use a backend: `EntitlementProvider` and `PurchaseVerifier` ([the backend contract](BILLING_BACKEND.md)).

## FAQ

**Does the paywall need Firebase?** No. Without `adroit-billing-firebase`, or without Firebase in the app, each
paywall uses its values from `Billing.kt`.

**Can Firebase set prices or trials?** No: only Play Console can. Firebase picks among the offers Play gives each
user ([what Firebase can't do](PAYWALL_REMOTE_CONFIG.md#what-firebase-can-not-do)).

**A pending purchase: when does the user get premium?** When Play reports the payment completed, even days later and
even if the app was closed: it's granted at the next launch or sync, with its original placement if that's within
72 hours of the purchase. Never before.

**Why does `purchase()` return `Cancelled` after a rotation?** It doesn't: the result outlives the screen. A purchase
whose caller is gone arrives on `AdroitBilling.purchaseUpdates`, and the entitlements update either way.

**Refunds?** Play removes a refunded purchase from what it reports; the next sync drops it. A backend learns it at once
from real-time notifications.

**Is demo mode safe to leave in the code?** Yes: `DemoBilling(true)` is ignored in a non-debuggable build, with an
error in logcat.

**How do I see which paywall sells?** Every purchase event carries `placement`, `paywall_id`, `offer_id` and
`campaign_id`; with `FirebaseBillingEvents` they're in Firebase Analytics.

Related: [Billing](BILLING.md), [Troubleshooting (ads)](TROUBLESHOOTING.md).

Last verified against: Play Billing Library 9.1.0, 2026-10-20
