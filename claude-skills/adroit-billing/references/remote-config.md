# Remote Config for paywalls

## How it works

- **Wiring:** `config = FirebaseBillingConfig.source()` in `BillingSettings` (`adroit-billing-firebase`). With
  `adroit-firebase` as well, ads and billing share one fetch per session. Nothing waits for it.
- **One JSON parameter per paywall**, named exactly like its `AppPaywalls` entry, and one global parameter,
  `AdroitBilling`.
- **A value carries only the fields it changes.** Nested objects merge field by field. `plans` is the exception: a
  `plans` list replaces the whole list.
- **Layers:** `BillingSettings.overrides`, then Firebase, then the enum defaults, field by field.
- **Bad input:**
  - An invalid field is ignored, emits a `ConfigInvalid` event, and that field's code value applies.
  - A value over 64 KB, or one that isn't a JSON object, is ignored entirely.
  - Unknown fields are ignored.
- **Timing:** a paywall that is already open keeps the values it opened with. The first paywall of a session may use
  the previous session's values.
- **Generating values:** `AppPaywalls.X.toJson()` prints the complete default value. Generate the console values (or
  the phase files) from it, never by hand.

## Per-paywall JSON

```json
{
  "enabled": true,
  "template": "plans",
  "plans": [
    { "product": "PremiumSub", "basePlan": "yearly", "badge": "BEST_VALUE", "savingsVs": "monthly" },
    { "product": "PremiumSub", "basePlan": "monthly", "offer": { "tags": ["trial"], "policy": "PREFER_FREE_TRIAL" } },
    { "product": "PremiumLifetime" }
  ],
  "selected": 0,
  "onOfferUnavailable": "BASE_PLAN",
  "close": { "delaySeconds": 3, "showCountdown": true, "position": "TOP_END", "back": "AFTER_DELAY" },
  "show": { "counter": 0, "timerSeconds": 0, "maxPerSession": 1, "maxPerDay": 0, "firstAfterLaunches": 0, "hideIfEntitled": ["premium"] },
  "campaign": { "id": "blackfriday", "start": "2026-11-27T00:00:00+01:00", "end": "2026-12-01T23:59:59+01:00", "analyticsId": "bf26" },
  "exitPaywall": "ExitDiscount",
  "texts": { "title": "Go Premium", "subtitle": "", "features": ["No ads"], "cta": "", "ctaTrial": "", "footer": "" },
  "style": { "accent": "#FF6D00", "background": "#101010", "headerImage": "https://…", "dark": true },
  "showRestore": true, "showManage": false, "showTerms": true,
  "successAction": "CLOSE"
}
```

- **`product`** is the `AppProducts` **entry name** (`PremiumSub`), not the Play product ID.
- **`basePlan` and `offer`** must exist in Play. Firebase only chooses among Play's offers.
- **`template`** is one of `plans`, `single`, `bottom_sheet` or `dialog`.
- **`close.delaySeconds`** runs from -1 to 60. `-1` means no close button.
- **A paywall that can't load** (offline, products missing) always shows a close button at once, whatever the
  settings say.
- **`texts`** are plain text, up to 300 characters each, with no HTML. They can use `PriceText` placeholders such as
  `{trial}` or `{price}`. A placeholder that can't be filled makes the app fall back to its own text.

## The `AdroitBilling` parameter (every paywall at once)

```json
{ "enabled": true, "adFreeEntitlements": ["premium"], "offlineGraceHours": 72,
  "products": { "PremiumLifetime": { "entitlements": ["premium", "vip"] } }, "configVersion": "2026-10-20" }
```
- `enabled: false` stops every paywall from showing (`GLOBAL_DISABLED`), for example during an incident. Purchases
  still work.
- `products` can only remap products the app already declares.
- `configVersion` is reported in Analytics.

## What Firebase can NOT do

- Create or change prices, trials or intro offers. Those are Play Console only.
- Give anyone premium. Only a real purchase unlocks anything.
- Add a product. That needs Play Console **and** a new app version.
- Remove the renewal disclosure.
- Hold secrets. Remote Config is readable by anyone who has the APK.

## The app's own switches

Use these for `PremiumEnabled`, an A/B test of the unlock flow, or a launch-run shape:
- **With `adroit-firebase` (ads apps):** `RemoteValue.boolean("PremiumEnabled", default = false)` and
  `RemoteValue.custom(...)`. See the adroit-admob skill, `remote-config.md` §5.
- **IAP-only apps:** `adroit-billing-firebase` brings `adroit-remote-config`, but not `RemoteValue`, which lives in
  `adroit-firebase` and would bring the ads SDK.
  - `FirebaseBillingConfig.source()` already starts the shared fetch, once per launch.
  - Read the flag with `RemoteConfigSession.current()?.string("PremiumEnabled")?.toBooleanStrictOrNull() ?: false`,
    which is null until the first value arrives.
  - Or use `FirebaseRemoteConfig.getInstance().getBoolean("PremiumEnabled")`, with an in-app default set through
    `setDefaultsAsync`.

## Console steps (for the marketer)

1. Remote Config → **Add parameter**: the paywall's name, exactly, data type **JSON**. Paste the value, then **Save**.
2. Different users get different values (country, A/B test) through a **conditional value** on the same parameter.
3. **Publish changes.** Users get them on their next session. Debug builds with `minimumFetchIntervalInSeconds = 0`
   get them on the next launch.
4. **"Publish from a file" replaces every parameter.** Use the generated phase file, which includes the ads.

## Quick changes

| To… | Parameter | Value |
|---|---|---|
| Stop all paywalls now | `AdroitBilling` | `{ "enabled": false }` |
| Close button after 5 s | the paywall's | `{ "close": { "delaySeconds": 5, "showCountdown": true } }` |
| Show the launch paywall from the 3rd launch, at most once a day | the paywall's | `{ "show": { "firstAfterLaunches": 2, "maxPerDay": 1 } }` |
| Sell yearly instead of monthly | the paywall's | `{ "plans": [{ "product": "PremiumSub", "basePlan": "yearly" }] }` |
| Use the trial offer | the paywall's | `{ "plans": [{ "product": "PremiumSub", "basePlan": "monthly", "offer": { "tags": ["trial"] } }] }` |
| Only "premium" removes ads | `AdroitBilling` | `{ "adFreeEntitlements": ["premium"] }` |
