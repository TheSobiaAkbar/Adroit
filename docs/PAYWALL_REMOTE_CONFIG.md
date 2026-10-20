# Paywalls in Firebase: every setting, without a release

This page is for whoever runs the app's paywalls from the Firebase console: product managers, marketers, anyone. You
don't need to read code. You'll change when the paywall shows, when its close button appears, which plans and offers
it shows, its texts and colors, all without a new version of the app.

The app's developer sets everything up once ([Billing](BILLING.md)). After that, each paywall is one Firebase
parameter holding a small piece of JSON, and one more parameter, `AdroitBilling`, holds the settings for all of them.

## Before you start: the names

Each paywall has a name, written once by the developer in the app's paywall file. That name is the Firebase
parameter. The developer's file looks like this (you only need the names in it: `Onboarding`, `Settings`, `Coins`):

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/Billing.kt#billing-enum -->
```kotlin
/** Everything the app sells. The IDs are the product IDs in Play Console. */
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    /** A subscription with base plans "monthly" and "yearly" in Play Console. */
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
    Coins100(ProductSpec.consumable("coins_100")),
}

/** Every paywall. Each name is also its Firebase parameter: a JSON value there changes it without a release. */
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    Onboarding(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            close = CloseBehavior(delaySeconds = 3, showCountdown = true, back = BackBehavior.AFTER_DELAY),
            show = ShowRules(maxPerSession = 1),
            texts = PaywallTexts(title = "Go Premium", features = listOf("No ads", "Every level unlocked")),
        ),
    ),
    Settings(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            showManage = true,
        ),
    ),
    Coins(
        PaywallSpec(
            plans = listOf(PlanSpec(AppProducts.Coins100)),
            template = PaywallTemplate.SINGLE,
            showRestore = false,
            successAction = SuccessAction.STAY_WITH_MESSAGE,
        ),
    ),
}
```

Two kinds of names appear in the JSON:

- **Paywall names** (`Onboarding`) are Firebase parameter names, and the value of `exitPaywall`.
- **Product names** (`PremiumSub`, `PremiumLifetime`, `Coins100`) go in `plans`. Use the name on the left, not the
  ID in quotes.

What the file sets is the paywall's **starting value**. Firebase overrides it **field by field**: a JSON with only
`{"close": {"delaySeconds": 5}}` changes the close button's delay and leaves everything else as the app set it. When
there's no Firebase value, or Firebase can't be reached, the app's own values apply.

## A complete example

The JSON of the `Onboarding` paywall above, with every field set:

```json
{
  "enabled": true,
  "template": "plans",
  "plans": [
    { "product": "PremiumSub", "basePlan": "yearly", "badge": "BEST_VALUE", "savingsVs": "monthly" },
    { "product": "PremiumSub", "basePlan": "monthly", "offer": { "tags": ["trial"] } },
    { "product": "PremiumLifetime" }
  ],
  "selected": 0,
  "onOfferUnavailable": "BASE_PLAN",
  "close": { "delaySeconds": 3, "showCountdown": true, "position": "TOP_END", "back": "AFTER_DELAY" },
  "show": { "counter": 0, "timerSeconds": 0, "maxPerSession": 1, "maxPerDay": 0, "firstAfterLaunches": 0 },
  "texts": { "title": "Go Premium", "features": ["No ads", "Every level unlocked"] },
  "style": { "accent": "#6750A4" },
  "showRestore": true,
  "showManage": false,
  "showTerms": true,
  "successAction": "CLOSE"
}
```

Your developer can give you the exact JSON of every paywall as the app sets it (`toJson()` on each paywall): start
from that, and change only what you need.

## The close button

The most asked-about setting. It's in `close`:

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `close.delaySeconds` | When the close button (the X) appears, counted while the paywall is on screen. **0** = at once. **5** = after 5 seconds. **-1** = never: the user must buy or use back. | -1 to 60 (more than 60 counts as 60) | 0 | `"delaySeconds": 5` |
| `close.showCountdown` | Shows a small countdown where the X will appear. | `true`, `false` | `false` | `"showCountdown": true` |
| `close.position` | The corner of the X: the top end (right in English, left in Arabic) or the top start. | `TOP_END`, `TOP_START` | `TOP_END` | `"position": "TOP_START"` |
| `close.back` | When the phone's back gesture closes the paywall. `ALWAYS`; `AFTER_DELAY`, once the X has appeared; `NEVER`. | `ALWAYS`, `AFTER_DELAY`, `NEVER` | `ALWAYS` | `"back": "AFTER_DELAY"` |

Examples:

```json
{ "close": { "delaySeconds": 0 } }
```

The X shows at once.

```json
{ "close": { "delaySeconds": 5, "showCountdown": true, "back": "AFTER_DELAY" } }
```

A countdown from 5, then the X; back works only once the X is there.

```json
{ "close": { "delaySeconds": -1, "back": "ALWAYS" } }
```

No X at all, but back still closes it.

Good to know:

- The count stops while the app is in the background, and continues when the user returns.
- A paywall that can't load (no internet, products missing) **always** shows an X at once, whatever you set. Nobody
  gets stuck on a broken paywall.
- `"delaySeconds": -1` with `"back": "NEVER"` is a hard paywall: the only way out is to buy or leave the app. Google
  Play's policies and your users dislike it, especially on a first launch. The app logs a warning about it.

## Plans and offers

`plans` is the list of plans on the paywall, in order. When you set `plans`, your list **replaces** the app's whole
list: write every plan you want.

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `plans[].product` | The product: one of the product names. A plan with an unknown name is left out. | A product name | (required) | `"product": "PremiumSub"` |
| `plans[].basePlan` | For a subscription, which base plan of Play Console: `weekly`, `monthly`, `yearly`… Leave it out for a one-time product. | A base plan ID from Play Console | Play's first base plan | `"basePlan": "yearly"` |
| `plans[].offer.ids` | Offers by their ID in Play Console, in order of preference: the first one Play gives this user wins. | A list of offer IDs | none | `"ids": ["summer-50"]` |
| `plans[].offer.tags` | An offer that has **all** these tags in Play Console. | A list of tags | none | `"tags": ["trial"]` |
| `plans[].offer.trialDays` | An offer whose free trial lasts exactly this many days. | 1 or more | any | `"trialDays": 7` |
| `plans[].offer.policy` | Which offer to prefer when the fields above don't decide. `PREFER_FREE_TRIAL`; `PREFER_INTRO_PRICE`, a reduced first price; `LOWEST_FIRST_PAYMENT`; `BASE_ONLY`, never an offer. | One of the four | `PREFER_FREE_TRIAL` | `"policy": "BASE_ONLY"` |
| `plans[].badge` | A short label on the plan. `_` shows as a space. | Up to 300 characters | none | `"badge": "BEST_VALUE"` |
| `plans[].savingsVs` | Shows "Save N%" against another base plan, for example yearly against monthly. Shown only when Play's prices make it true. | A base plan ID | none | `"savingsVs": "monthly"` |
| `selected` | Which plan is selected when the paywall opens: 0 is the first. | 0 or more (too high selects the last) | 0 | `"selected": 1` |
| `onOfferUnavailable` | What happens when a plan asks for an offer this user can't get (see below). `BASE_PLAN` shows the plan at its normal price; `HIDE_PLAN` leaves the plan out; `HIDE_PAYWALL` doesn't show the paywall at all. | One of the three | `BASE_PLAN` | `"onOfferUnavailable": "HIDE_PLAN"` |

### Picking an offer

An **offer** (a free trial, an introductory price) is created in Play Console, on a base plan
([Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md)). Firebase can't create one; it only **chooses** among the
offers Play gives this user. The rules, first match wins:

1. `ids`: the first listed offer Play gives this user.
2. Otherwise `tags` and `trialDays` narrow the list: only offers with all the tags, and with exactly that trial.
3. `policy` picks among what is left.

Examples:

```json
{ "plans": [ { "product": "PremiumSub", "basePlan": "monthly", "offer": { "trialDays": 7 } } ] }
```

The monthly plan with its 7-day trial.

```json
{ "plans": [ { "product": "PremiumSub", "basePlan": "yearly", "offer": { "tags": ["intro"], "policy": "PREFER_INTRO_PRICE" } } ] }
```

The yearly plan's offer tagged `intro`.

```json
{ "plans": [ { "product": "PremiumSub", "basePlan": "monthly", "offer": { "policy": "BASE_ONLY" } } ] }
```

Monthly at its regular price, never a trial.

### When a trial isn't available

Google Play decides who can get a trial or an introductory price: usually only people who never had one for this
subscription. When the offer you asked for (`ids`, `tags` or `trialDays`) isn't available to this user, the app
**never** shows another offer in its place, and never shows trial text it can't honor. Instead:

- `"onOfferUnavailable": "BASE_PLAN"` (the default) shows the plan at its regular price. The button says "Continue",
  not "Start free trial".
- `"HIDE_PLAN"` leaves that plan out; the paywall shows the others.
- `"HIDE_PAYWALL"` doesn't show the paywall. Use it for a paywall that only makes sense with the trial ("Try 7 days
  free").

The renewal terms under the button always describe what the user will really pay.

## When the paywall shows

The app asks "may this paywall show now?" at its places, for example after the splash screen. These rules answer.
A button the user taps ("Upgrade" in settings) usually opens the paywall without asking them.

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `enabled` | Turns this paywall off or on. Off, it never shows; purchases elsewhere still work. | `true`, `false` | `true` | `"enabled": false` |
| `show.counter` | Shows on every Nth time the app asks: 3 means the 3rd, 6th, 9th… **0** = off. | 0 or more | 0 | `"counter": 3` |
| `show.timerSeconds` | At least this many seconds between two showings. **0** = off. Ignored while a counter is set. | 0 or more | 0 | `"timerSeconds": 86400` |
| `show.maxPerSession` | At most this many showings per app session (from opening the app to closing it). **0** = no limit. | 0 or more | 0 | `"maxPerSession": 1` |
| `show.maxPerDay` | At most this many showings per calendar day. **0** = no limit. | 0 or more | 0 | `"maxPerDay": 2` |
| `show.firstAfterLaunches` | Doesn't show before the app's Nth launch, to let new users look around first. **0** = from the first launch. | 0 or more | 0 | `"firstAfterLaunches": 3` |
| `show.hideIfEntitled` | Doesn't show to users who already have one of these. For example `["premium"]`. An empty list `[]` shows it to everyone, premium users included (useful for an "upgrade to lifetime" paywall). | A list of entitlements | What its plans give | `"hideIfEntitled": []` |

The rules combine: the paywall shows only when every rule allows it. The counter and timer work like the ads'
([Ad profiles, counter and timer](AD_PROFILES.md#counter-and-timer)).

## Campaigns

A campaign shows the paywall only between two dates, and tags the purchases made from it in Analytics.

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `campaign.id` | The campaign's name, sent with every purchase and event from this paywall. Required when the app's file has no campaign. | Text | none | `"id": "black_friday_2026"` |
| `campaign.start` | Doesn't show before this moment. | A date and time with its time zone | already started | `"start": "2026-11-27T00:00:00Z"` |
| `campaign.end` | Doesn't show after this moment. Must be after `start`. | A date and time with its time zone | no end | `"end": "2026-12-01T00:00:00+01:00"` |
| `campaign.analyticsId` | An extra ID for your analytics, if you need one. | Text | none | `"analyticsId": "cmp-1234"` |

```json
{
  "campaign": { "id": "black_friday_2026", "start": "2026-11-27T00:00:00Z", "end": "2026-12-01T00:00:00Z" },
  "plans": [ { "product": "PremiumSub", "basePlan": "yearly", "offer": { "tags": ["blackfriday"] } } ]
}
```

Dates use the phone's clock, so a user who changes the date can see a campaign early or late. That's fine for
showing a paywall; it never gives anyone premium.

## Texts

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `texts.title` | The big title. | Up to 300 characters, no HTML | The library's | `"title": "Go Premium"` |
| `texts.subtitle` | A line under the title. | Up to 300 characters, no HTML | none | `"subtitle": "Join 1M players"` |
| `texts.features` | The list of benefits, each with a check mark. | A list of texts | none | `"features": ["No ads", "All levels"]` |
| `texts.cta` | The purchase button. | Up to 300 characters, no HTML | "Continue" | `"cta": "Unlock now"` |
| `texts.ctaTrial` | The purchase button when the selected plan has a free trial. | Up to 300 characters, no HTML | "Start free trial" | `"ctaTrial": "Try {trial} free"` |
| `texts.footer` | A line above the renewal terms (which are always shown). | Up to 300 characters, no HTML | none | `"footer": "Cancel anytime."` |

### Placeholders

Texts can contain placeholders filled from Google Play's real prices, in the user's currency and language:

| Placeholder | Becomes | Example |
|---|---|---|
| `{trial}` | The free trial's length | "7 days" |
| `{price}` | The first payment (an introductory price, else the regular price) | "€0.99" |
| `{period}` | What `{price}` pays for | "month" |
| `{renewalPrice}` | What it renews at | "€4.99" |
| `{renewalPeriod}` | How often it renews | "month" |
| `{perWeek}`, `{perMonth}` | The renewal price spread over a week or a month | "€1.25" |
| `{savings}` | How much cheaper than the plan's `savingsVs` | "33%" |

A text whose placeholder can't be filled (`{trial}` on a plan without a trial, or a typo like `{prise}`) isn't
shown: the app shows its own text instead, so a user never sees a wrong price or a broken text.

## Colors and layout

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `template` | The layout: `plans`, a full screen with every plan; `single`, a full screen for one plan; `bottom_sheet`, a panel from the bottom; `dialog`, a window in the middle. | `plans`, `single`, `bottom_sheet`, `dialog` | `plans` | `"template": "dialog"` |
| `style.accent` | The main color: the button, the selected plan, the check marks. | `"#RRGGBB"`, or `"#AARRGGBB"` with transparency | The app's theme | `"accent": "#E91E63"` |
| `style.background` | The background color. | `"#RRGGBB"` or `"#AARRGGBB"` | The app's theme | `"background": "#101010"` |
| `style.dark` | `true` for light text on a dark background, `false` for dark on light. Leave it out to follow the phone's dark mode. | `true`, `false` | The phone's setting | `"dark": true` |
| `style.headerImage` | A picture at the top. Shown only if the app's developer added image loading. | An `https://` address | none | `"headerImage": "https://example.com/hero.png"` |
| `showRestore` | Shows "Restore purchases". | `true`, `false` | `true` | `"showRestore": false` |
| `showManage` | Shows "Manage subscription", which opens Google Play. | `true`, `false` | `false` | `"showManage": true` |
| `showTerms` | Shows the terms and privacy links. | `true`, `false` | `true` | `"showTerms": true` |
| `successAction` | After a purchase: `CLOSE` closes the paywall; `STAY_WITH_MESSAGE` keeps it open with a thank-you (for coins and other things bought again and again). | `CLOSE`, `STAY_WITH_MESSAGE` | `CLOSE` | `"successAction": "STAY_WITH_MESSAGE"` |

## The exit paywall

When the user closes a paywall (with the X or back, not after buying), another paywall can show right after: a
last offer.

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `exitPaywall` | The paywall shown when the user closes this one. It shows at most once per session, and only if its own rules allow it. | The name of another paywall | none | `"exitPaywall": "ExitDiscount"` |

```json
{ "exitPaywall": "ExitDiscount" }
```

on `Onboarding`, and on `ExitDiscount`:

```json
{
  "template": "dialog",
  "plans": [ { "product": "PremiumSub", "basePlan": "yearly", "offer": { "tags": ["intro"], "policy": "PREFER_INTRO_PRICE" } } ],
  "close": { "delaySeconds": 5, "showCountdown": true },
  "show": { "maxPerDay": 1 },
  "texts": { "title": "Wait! Your first year for {price}" }
}
```

## The `AdroitBilling` parameter: all paywalls at once

One more parameter, named `AdroitBilling`, holds settings for the whole app:

| Field | What it does | Allowed values | Default | Example |
|---|---|---|---|---|
| `enabled` | `false` stops **every** paywall from showing, for example during an incident. Purchases still work. | `true`, `false` | `true` | `"enabled": false` |
| `adFreeEntitlements` | Which entitlements remove the ads. | A list of entitlements | Every entitlement | `"adFreeEntitlements": ["premium"]` |
| `offlineGraceHours` | How long a subscription stays active while the phone can't reach Google Play. | 1 to 720 | 72, or the app's | `"offlineGraceHours": 48` |
| `products.<ProductName>.entitlements` | Changes what a product unlocks, by product name. It can only change products the app already sells; it can't create one or give anything for free. | A list of entitlements | What the app's file says | `"products": { "PremiumLifetime": { "entitlements": ["premium", "vip"] } }` |
| `configVersion` | Your own version label for this setup, reported in Analytics, to tell which setup a user had. | A number or text | none | `"configVersion": 12` |

```json
{ "enabled": true, "adFreeEntitlements": ["premium"], "configVersion": "2026-10-20" }
```

## Putting it in Firebase

1. Open the [Firebase console](https://console.firebase.google.com), your project, **Remote Config**.
2. **Add parameter**. The parameter name is the paywall's name, exactly: `Onboarding`, with the same capital
   letters. For the whole-app settings, `AdroitBilling`.
3. Data type: **JSON**. Paste the JSON as the default value. Firebase checks that it's valid JSON.
4. To give different users different values (a country, an A/B test), add a **conditional value** to the same
   parameter.
5. **Save**, then **Publish changes**.

When users see it:

- The app fetches the values once per app session, without making anyone wait. The first paywall of a session may
  still use the last session's values; the next paywall uses the new ones. A paywall already open never changes.
- Firebase may keep values for up to 12 hours before fetching again (its default cache). To check a change now, ask
  the developer for a debug build: it fetches every launch.
- A field with a wrong value (a typo, `"delaySeconds": "five"`, a color without `#`) is ignored, and the app's value
  for that field applies. The rest of your JSON still works. A field the app doesn't know is ignored too.

## What Firebase can NOT do

> - **Prices, trials and introductory prices** are set in Google Play Console only. Firebase picks among the offers
>   Play gives each user; it can't create a trial, change a price, or give a trial to someone Play says can't
>   have one.
> - **Give anyone premium.** Only a real Google Play purchase unlocks anything. No Firebase value grants access, and
>   `products` can only rename what an existing purchase unlocks.
> - **Add a product.** New products need the developer and a new app version, after creating them in Play Console.
> - **Show text Play's data can't back.** A placeholder that can't be filled falls back to the app's text.
> - **Hold secrets.** Everything in Remote Config can be read by anyone who has the app. Never put keys or passwords
>   in it.

Related: [Billing](BILLING.md), [Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md),
[Billing troubleshooting](BILLING_TROUBLESHOOTING.md).

Last verified against: Play Billing Library 9.1.0, 2026-10-20
