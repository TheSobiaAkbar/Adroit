# Scenarios: ready recipes

Each recipe says when to use it, gives the definitions, and shows the wiring. They combine freely: a typical
ads + IAP app uses 1, 4, 5, 7, 8 and 9.

## Contents
1. Launch / onboarding paywall over the splash
2. Hard paywall (subscription-only app, no ads)
3. Multi-step onboarding paywall
4. Remove-ads paywall on an ad's close button
5. Feature gate (premium content behind a paywall)
6. "Watch an ad or subscribe" unlock sheet (A/B)
7. Pro screen and Get Pro entry points
8. Settings: restore, manage, fix payment, Play messages
9. `PremiumEnabled` kill switch and phased rollout
10. Exit discount, seasonal campaign, tiers, lifetime "remove ads"

## 1. Launch / onboarding paywall over the splash

**When:** once per launch, after the splash (and its ad), for users who aren't premium. `maxPerSession` counts in
memory, per process. A "session" ends when Android kills the app, not when it goes to the background.

```kotlin
Onboarding(PaywallSpec(plans = …, close = CloseBehavior(delaySeconds = 3, showCountdown = true, back = BackBehavior.AFTER_DELAY),
    show = ShowRules(maxPerSession = 1 /*, firstAfterLaunches = 0, maxPerDay = 0 */))),
```
**Compose.** Draw it inside `SplashActivity`, over the splash. The splash itself is in the adroit-admob skill,
`splash-consent-update.md`. The ad that may follow then shows from the Activity that is already in front.
```kotlin
sealed interface SplashRoute {
    data object Splash : SplashRoute
    data class Paywall(val definition: PaywallDefinition) : SplashRoute
    data object Main : SplashRoute
}
// in SplashViewModel: var route: SplashRoute by mutableStateOf(SplashRoute.Splash)

setContent {
    AppTheme {
        SplashScreen(progress)
        (viewModel.route as? SplashRoute.Paywall)?.let { r ->
            key(r.definition) {
                Paywall(r.definition, placement = "launch",
                    onClose = { viewModel.route = SplashRoute.Main },
                    onExitPaywall = { exit -> viewModel.route = SplashRoute.Paywall(exit) })
            }
        }
    }
}
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.RESUMED) {
        launch { viewModel.flow.state.collect { if (it is SplashState.Finished) route() } }
        snapshotFlow { viewModel.route }.first { it == SplashRoute.Main }
        goToMain()
    }
}
private fun route() {
    if (viewModel.route != SplashRoute.Splash) return
    val show = AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "launch") == ShowDecision.Show
    viewModel.route = if (show) SplashRoute.Paywall(AppPaywalls.Onboarding) else SplashRoute.Main
}
```
**XML:**
- Register `registerForActivityResult(AdroitPaywall.contract()) { viewModel.goToMain.value = true }` in `onCreate`.
- On `SplashState.Finished`, launch it if `shouldShow == Show`; otherwise go to main.
- Keep the "go to main" flag in the ViewModel, so a rotation keeps it.

**Splash ad before or after the paywall?** A splash ad right before a paywall annoys users. Move the launch ad after
the paywall (the launch interstitial pattern in the adroit-admob skill, §4 of `splash-consent-update.md`), and let
Firebase choose.

## 2. Hard paywall (subscription-only app, no ads)

**When:** the app is unusable without a subscription.
```kotlin
Subscribe(PaywallSpec(plans = …, close = CloseBehavior(delaySeconds = -1, back = BackBehavior.NEVER), showRestore = true)),
```
- `delaySeconds = -1` with `back = NEVER` logs a warning and emits a `HardPaywall` event, on purpose. Make sure the
  user really means it.
- **The splash, with no `SplashFlow` (no ads):**
  1. Run `AdroitUpdate.check(activity)` in the ViewModel.
  2. Branch on `entitlements.value.has(PREMIUM)`: go to main if premium, otherwise to the paywall.
  3. When the paywall closes: premium goes in; anything else **leaves the app** (`finish()`).
- **Keep "Restore purchases" visible.** A reinstalled subscriber may see the paywall before Play answers. This is also
  a store-review requirement.
- In Compose, draw `Paywall(AppPaywalls.Subscribe, "launch", onClose = { vm.onPaywallClosed() })` over the splash.
  Navigate only from `repeatOnLifecycle(RESUMED)`.

## 3. Multi-step onboarding paywall

**When:** the design has several paywall screens in a row (for example I2 → I3 → I4, each selling a different plan),
and the run ends at the first purchase.

- Define **one paywall per step**, so Firebase can change each step's plan and close delay:
```kotlin
LaunchImagesPaywall(launchStep(basePlanId = "monthly")),
LaunchVideosPaywall(launchStep(basePlanId = "yearly")),
LaunchDocumentsPaywall(launchStep(basePlanId = "weekly")),

private fun launchStep(basePlanId: String) = PaywallSpec(
    plans = listOf(PlanSpec(AppProducts.PremiumSub, basePlanId = basePlanId, offer = OfferRule(policy = OfferPolicy.BASE_ONLY))),
    template = PaywallTemplate.SINGLE,
    close = CloseBehavior(delaySeconds = 3, back = BackBehavior.AFTER_DELAY),
    showRestore = false,
)
```
- **The ViewModel** opens one controller per step and maps the states:

  | State | What to do |
  |---|---|
  | `Content` | draw the step |
  | `Purchased` | finish the run |
  | `Unavailable(ENTITLED)` | finish the run |
  | `Unavailable(null)` (offline) | show the step without a price, closable at once |
  | `Unavailable(other)` | skip to the next step |
  | `Closed` | next step; after the last, finish |

  Also finish as soon as `billing.entitlements.first { it.active.isNotEmpty() }` returns, for example after a restore
  elsewhere.
- **Gate the whole run once,** with `shouldShow` on the first step, or a "launch flow" `RemoteValue` read once per
  launch.

## 4. Remove-ads paywall on an ad's close button

**When:** banners and native ads have a close button (`AdCloseButton.TOP_END`), and tapping it offers premium.
```kotlin
RemoveAds(PaywallSpec(
    plans = listOf(PlanSpec(AppProducts.PremiumSub, basePlanId = "weekly", offer = OfferRule(policy = OfferPolicy.BASE_ONLY))),
    template = PaywallTemplate.SINGLE,                    // or DIALOG / BOTTOM_SHEET for a window over the screen
    close = CloseBehavior(delaySeconds = 0, back = BackBehavior.ALWAYS),
    show = ShowRules(maxPerSession = 2, maxPerDay = 4),   // don't nag on every tap
    showRestore = false,
)),
```
```kotlin
// One collector for the whole app (AppNavHost / MainActivity)
LaunchedEffect(Unit) {
    Adroit.events.filterIsInstance<AdEvent.CloseClicked>().collect {
        if (PremiumEnabled.value &&
            AdroitBilling.paywalls.shouldShow(AppPaywalls.RemoveAds, "ad_close") == ShowDecision.Show) {
            navController.navigate(RemoveAdsRoute) { launchSingleTop = true }
        }
    }
}
```
- A `SINGLE` template can be a full-page route. PhotoRecovery draws it as its own route, on a dark backdrop.
- A purchase makes the user ad-free through the bridge, so every ad disappears.
- Alternative product: a one-time `nonConsumable("remove_ads", entitlements = setOf("no_ads"))`, with
  `adFreeEntitlements = setOf("premium", "no_ads")`.

## 5. Feature gate

**When:** a screen, result or action is premium-only (PhotoRecovery's scan results paywall, I1).
```kotlin
fun openResults() {
    if (AdroitBilling.entitlements.value.has(PREMIUM)) navigate(Results) else navigate(ResultsPaywallRoute)
}
// The paywall screen: Purchased → replace itself with Results; Closed / back → go back (NOT to the results).
```
- Closing a gate must never open the gated content. Only premium does.
- A gate the user hits on purpose (they tapped "View results") doesn't need `shouldShow`. A gate that pops up by
  itself does.
- Keep `PriceText.disclosure` under the button in custom UIs.

## 6. "Watch an ad or subscribe" unlock sheet (A/B)

**When:** a non-premium user may unlock one use by watching a rewarded ad, or subscribe. Firebase picks the variant.
This needs the kotlinx-serialization plugin (`org.jetbrains.kotlin.plugin.serialization`) and
`kotlinx-serialization-json`, from the version catalog.

```kotlin
@Serializable enum class UnlockFlow {
    @SerialName("rewarded") REWARDED,     // watch an ad, or subscribe
    @SerialName("subscribe") SUBSCRIBE,   // subscribe only
}
@Serializable enum class SubscribeOpens {
    @SerialName("paywall") PAYWALL,       // open a paywall page
    @SerialName("purchase") PURCHASE,     // Play's sheet for the paywall's chosen plan, no page between
}
@Serializable data class UnlockConfig(
    val flow: UnlockFlow = UnlockFlow.REWARDED,
    val subscribe: SubscribeOpens = SubscribeOpens.PAYWALL,
)
/** What the sheet offers; null = nothing to offer, open the content. */
data class UnlockOffer(val ad: Boolean, val subscribe: Boolean, val purchase: Boolean)

object Unlock {
    // @SerialName makes the console values lowercase; coerceInputValues turns an unknown value into the default.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val remote = RemoteValue.custom("ExportUnlock", UnlockConfig()) { raw ->
        runCatching { json.decodeFromString<UnlockConfig>(raw) }.getOrDefault(UnlockConfig())
    }
    fun offer(selling: Boolean, adOn: Boolean): UnlockOffer? {
        val config = remote.value
        val ad = config.flow == UnlockFlow.REWARDED && adOn       // adOn: rewarded availability != AdAvailability.Disabled
        if (!ad && !selling) return null
        return UnlockOffer(ad = ad, subscribe = selling, purchase = config.subscribe == SubscribeOpens.PURCHASE)
    }
}
// selling = PremiumEnabled.value (§9), or true when the app always sells
```
- **Console values:** `{ "flow": "subscribe" }`, or `{ "flow": "rewarded", "subscribe": "purchase" }`.
- **Without `@SerialName`**, the console values would have to be `"REWARDED"`. A lowercase value would silently fall
  back to the default.
- **Premium users never see the sheet.** A `null` offer opens the content directly.
- **The "Watch ad" button:** the rewarded ad (see the adroit-admob skill, `formats.md` §7). `EARNED` unlocks this one
  use until the next.
- **The "Subscribe" button:**
  - `paywall`: navigate to the paywall.
  - `purchase`: create the paywall's controller and call `controller.purchase(activity)` directly. This opens Play's
    sheet for the chosen plan, with no page in between. Show `PriceText.disclosure` under the button.
- **Disable both buttons offline.** Neither an ad nor a purchase works without a network. Adroit has no public
  "online" API for apps, so observe connectivity with `ConnectivityManager.registerDefaultNetworkCallback`, or with
  the app's own network monitor.
- **Preload** the rewarded ad when the content is ready (`AdSlots.load(...)`), before the sheet appears.

## 7. Pro screen and Get Pro entry points

**When:** a user-opened, multi-plan premium page (weekly / monthly / yearly, with yearly preselected).
```kotlin
PremiumPaywall(PaywallSpec(
    plans = listOf(proPlan("weekly"), proPlan("monthly", savingsVs = "weekly"), proPlan("yearly", badge = "BEST_VALUE", savingsVs = "weekly")),
    selected = 2,
    close = CloseBehavior(delaySeconds = 0, back = BackBehavior.ALWAYS),   // user-opened: never hold them
    showRestore = false,
)),
```
- **Entry points:** a Get Pro button in the headers and a Join Pro banner in Settings. Draw them only while
  `sell = PremiumEnabled.value && !entitlements.has(PREMIUM)`, collected as state, so they disappear the moment the
  purchase lands.
- **Route argument:** pass the placement (`"pro_button"`, `"settings_banner"`), so analytics shows which entry point
  sells.
- **Open it directly**, with no `shouldShow`, because the user asked.

## 8. Settings: restore, manage, fix payment, Play messages

```kotlin
class PremiumViewModel(private val billing: BillingClientApi = AdroitBilling) : ViewModel() {
    private val results = Channel<RestoreResult>(Channel.BUFFERED)
    val entitlements = billing.entitlements
    val restoreResults = results.receiveAsFlow()                          // one result per tap, survives rotation
    fun restore() { viewModelScope.launch { results.send(billing.restore()) } }
    fun manage(activity: Activity) = billing.openManageSubscriptions(activity, AppProducts.PremiumSub)
}
```
- **Restore result messages:**
  - `Restored`: if `newlyActive` is empty, "Your purchases are up to date"; otherwise "Restored".
  - `NothingToRestore`: "Nothing to restore".
  - `Failed`: "Couldn't reach Google Play".
- **Manage subscription:** show it only to subscribers.
- **Fix payment:** show it while `entitlements.needsPaymentFix`; it opens manage.
- **Play's own messages** ("payment declined") come from the main screen:
```kotlin
LifecycleResumeEffect(activity) { activity?.let(AdroitBilling::showInAppMessages); onPauseOrDispose {} }
```

## 9. `PremiumEnabled` kill switch and phased rollout

**When:** the app ships before the products are live in Play, or selling must be switchable remotely.
```kotlin
val PremiumEnabled: RemoteValue<Boolean> = RemoteValue.boolean("PremiumEnabled", default = false)   // adroit-firebase
```
- **When it is off**, hide every sale surface: Get Pro, Join Pro, paywalls, the remove-ads offer, and Subscribe on the
  unlock sheet. **Users who already bought stay premium and ad-free**: entitlements don't depend on the switch.
- **IAP-only apps (no `adroit-firebase`):** read the flag through Firebase Remote Config directly
  (`FirebaseRemoteConfig.getInstance().getBoolean("PremiumEnabled")`, after the session fetch). Or use the global
  `AdroitBilling` parameter `{ "enabled": false }`, which turns every paywall off: `shouldShow` returns
  `GLOBAL_DISABLED`.
- **The phase files**, as PhotoRecovery does it:
  1. Ads only, `PremiumEnabled=false`.
  2. Premium for internal testers, through a Firebase condition on `app.firebaseInstallationId`.
  3. Premium on for everyone.

  Generate the files from code (adroit-admob skill, `remote-config.md` §6).

## 10. More patterns

- **Exit discount:**
  - Set `exitPaywall = "ExitDiscount"` on the main paywall.
  - `ExitDiscount` sells an offer tagged `discount`, with `template = DIALOG` and `show = ShowRules(maxPerDay = 1)`.
  - Wire it with `onExitPaywall` (`paywalls.md` §7).
- **Seasonal sale:**
  - Set `campaign = Campaign(id = "blackfriday", startMillis = …, endMillis = …)` on a paywall, or in Firebase with
    ISO-8601 dates that include the zone.
  - Outside the window, `shouldShow` returns `OUTSIDE_CAMPAIGN`.
  - Point the plans at an offer tagged `sale`.
- **Tiers:** use different entitlements (`premium`, `pro`), with a paywall per tier. Upgrades go through
  `SubscriptionReplacement` (`products-purchases.md` §7). Gate features with `has("pro")`.
- **Lifetime alongside subscriptions:** add `PlanSpec(AppProducts.PremiumLifetime)` to a paywall. It grants the same
  `premium` entitlement, so nothing else changes.
- **Trials:**
  1. Create the offer in Play, tagged `trial`.
  2. Point the plan at it: `PlanSpec(PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial")))`.
  3. `OfferFallback.BASE_PLAN` shows the base price to users who aren't eligible.
  4. `PriceText.cta` reads "Start free trial" only when the trial applies.

  Never print "3 days free" yourself.
