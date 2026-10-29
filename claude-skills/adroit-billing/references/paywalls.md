# Paywalls

## Contents
1. Defining paywalls: `PaywallSpec` and every field
2. When a paywall shows: `shouldShow` and `SkipReason`
3. Compose: built-in, custom blocks, fully custom
4. XML / Views
5. `PaywallController`: the engine under every UI
6. `PriceText`: every string about money
7. Exit paywalls

## 1. Defining paywalls

```kotlin
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition { … }   // name = Firebase parameter

PaywallSpec(
    plans: List<PlanSpec>,                            // required, at least one
    enabled = true,
    template = PaywallTemplate.PLANS,                 // PLANS | SINGLE (full screen) | BOTTOM_SHEET | DIALOG (own window)
    selected = 0,                                     // index of the pre-selected plan
    onOfferUnavailable = OfferFallback.BASE_PLAN,     // BASE_PLAN | HIDE_PLAN | HIDE_PAYWALL, when the named offer isn't offered to this user
    close = CloseBehavior(),
    show = ShowRules(),
    campaign: Campaign? = null,
    exitPaywall: String? = null,                      // another AppPaywalls entry's name, offered when this one closes
    texts = PaywallTexts(),
    style = PaywallStyle(),
    showRestore = true, showManage = false, showTerms = true,
    successAction = SuccessAction.CLOSE,              // or STAY_WITH_MESSAGE (consumables)
)
PlanSpec(product: ProductDefinition, basePlanId: String? = null, offer = OfferRule(), badge: String? = null, savingsVs: String? = null)
OfferRule(ids = emptyList(), tags = emptyList(), trialDays: Int? = null, policy = OfferPolicy.PREFER_FREE_TRIAL)
    // policy: PREFER_FREE_TRIAL | PREFER_INTRO_PRICE | LOWEST_FIRST_PAYMENT | BASE_ONLY
CloseBehavior(delaySeconds = 0, showCountdown = false, position = CloseButtonPosition.TOP_END, back = BackBehavior.ALWAYS)
    // delaySeconds: -1 = no close button (hard paywall), capped at 60. back: ALWAYS | AFTER_DELAY | NEVER
ShowRules(counter = 0, timerSeconds = 0, maxPerSession = 0, maxPerDay = 0, firstAfterLaunches = 0, hideIfEntitled: Set<String>? = null)
Campaign(id, startMillis: Long? = null, endMillis: Long? = null, analyticsId: String? = null)
PaywallTexts(title, subtitle, features: List<String>, cta, ctaTrial, footer)   // plain text, ≤300 chars each
PaywallStyle(accent: Int?, background: Int?, headerImage: String? /* https */, dark: Boolean?)
```

**`PlanSpec` fields:**
- `savingsVs = "monthly"` computes "Save 40%" from Play's own prices against that base plan.
- `badge` is a label such as `"BEST_VALUE"`, which the built-in UI shows.

**`OfferRule`, how the offer is chosen:**
- `tags`: pick Play offers by their tags (`trial`, `intro`). Prefer tags to IDs, so marketing can swap offers in Play.
- `ids`: pick specific offer IDs.
- `trialDays`: require a trial of that length.
- `policy = BASE_ONLY`: never an offer. Use it when the design shows one price per period and has no room for trial
  terms. PhotoRecovery does this.

**`ShowRules`:**
- Applies only through `shouldShow`.
- `firstAfterLaunches = 2` skips the first two launches.
- `hideIfEntitled` defaults to the entitlements of the paywall's own products. A user who already has what the
  paywall sells never sees it. A consumables paywall grants no entitlement, so it always shows.
- `maxPerSession` counts in memory, per process; `maxPerDay` and the launch counts are saved.

**`texts`:**
- Use them for Firebase-tunable copy in built-in UIs.
- A custom UI in a multi-language app should use its own string resources instead. `PaywallTexts` are single-language.

**Templates:**
- `PLANS` is a list of plans; `SINGLE` is one plan with one button.
- `BOTTOM_SHEET` and `DIALOG` open their own window over the current screen.

**Printing the JSON:** `AppPaywalls.X.toJson()` prints the full default value for Firebase.

## 2. When a paywall shows

```kotlin
when (val d = AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "launch")) {
    ShowDecision.Show -> showIt()
    is ShowDecision.Skip -> goOn()          // d.reason: SkipReason
}
```

**`SkipReason`** values:
- **Not set up:** `NOT_INITIALIZED`, `NOT_REGISTERED`.
- **Turned off:** `GLOBAL_DISABLED`, `PAYWALL_DISABLED`, `OUTSIDE_CAMPAIGN`.
- **The user already has it:** `ENTITLED`.
- **The show rules said no:** `LAUNCHES_NOT_REACHED`, `SESSION_CAP`, `DAILY_CAP`, `COUNTER_NOT_REACHED`, `TIMER`.
- **Nothing to sell:** `NO_PLANS`, `OFFER_UNAVAILABLE`.

Rules for calling it:
- **Automatic placements** (launch, ad close, feature gate, an exit offer) call `shouldShow`.
- **User-initiated ones** (an Upgrade button, Pro) open directly and skip the rules.
- `Paywall(...)` and `controller(...)` never check the rules themselves.
- The paywall records its own shows and closes (`onShown`), which feeds the counters.
- Other members of `Paywalls`: `definition(name)`, `model(paywall): StateFlow<PaywallModelState>`,
  `controller(paywall, placement, scope)`, `recordShown(paywall, placement)` and
  `recordClosed(paywall, placement, reason)`. Call the last two only when you drive a fully custom UI without a
  controller.

## 3. Compose

**Built-in** (the template decides the layout and the window):
```kotlin
@Composable
fun SettingsUpgrade() {
    var open by rememberSaveable { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text(stringResource(R.string.upgrade)) }
    if (open) Paywall(AppPaywalls.Settings, placement = "settings", onClose = { reason -> open = false })
}
```
The full signatures:
```kotlin
Paywall(definition: PaywallDefinition, placement: String, onClose: (PaywallCloseReason) -> Unit,
        modifier: Modifier = Modifier, imageLoader: (@Composable (url: String) -> Unit)? = null,
        onExitPaywall: ((PaywallDefinition) -> Unit)? = null, content: (@Composable PaywallScope.() -> Unit)? = null)
Paywall(controller: PaywallController, onClose, modifier, imageLoader, onExitPaywall, content)
rememberPaywallController(definition, placement): PaywallController      // ViewModel + SavedStateHandle: survives rotation
PaywallTheme(style: PaywallStyle) { … }                                  // PaywallDefaults: CloseButtonSize 48.dp, MaxWidth 560.dp
```
- `PaywallCloseReason` is one of `CLOSE_BUTTON`, `BACK`, `PURCHASED` or `ERROR`.
- `Paywall` calls `controller.onShown()` and renders the state itself. Only a fully custom UI calls `onShown()`.
- With `template = DIALOG` or `BOTTOM_SHEET`, `Paywall` opens its own window over the screen. Draw it conditionally
  (`if (open) Paywall(...)`), exactly like the full-screen templates.
- The copy in force (Firebase-merged) is `controller.spec.texts`. All its fields are nullable.
- `imageLoader` is required only to show `style.headerImage`. Pass a Coil composable, for example.

**Your layout, the library's blocks.** A `content` lambda replaces the whole layout, close button included:
```kotlin
Paywall(AppPaywalls.Settings, placement = "settings", onClose = { onDone() }) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Title(Modifier.weight(1f)); CloseButton() }
        Features(); PlanList(); Message(); CtaButton()
        Disclosure()                                            // keep: Play requires the renewal terms by the button
        RestoreButton(Modifier.align(Alignment.CenterHorizontally))
    }
}
```
The available blocks are `HeaderImage`, `Title`, `Features`, `PlanList`, `PlanCard(plan)`, `CtaButton`, `Disclosure`,
`Message`, `CloseButton`, `RestoreButton` and `ManageButton`, plus the `controller` and `state` properties.

**Fully custom UI**, matching a design system or a wireframe. Drive your own composables from a controller:
```kotlin
@Composable
fun CustomPaywall(onDone: (premium: Boolean) -> Unit) {
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val controller = rememberPaywallController(AppPaywalls.Onboarding, "launch")
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller) { controller.onShown() }                 // starts the close delay; counts the show
    BackHandler { controller.onBackPressed() }                          // honours back = AFTER_DELAY / NEVER
    when (val s = state) {
        PaywallUiState.Loading -> Loading()
        is PaywallUiState.Content -> Column {
            s.model.plans.forEachIndexed { i, plan ->
                PlanRow(price = PriceText.planPrice(resources, plan).orEmpty(), selected = i == s.selectedIndex,
                        onClick = { controller.select(i) })
            }
            Button(onClick = { activity?.let(controller::purchase) }, enabled = s.busy == null) {
                Text(PriceText.cta(resources, s.selectedPlan))
            }
            PriceText.disclosure(resources, s.selectedPlan)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (s.closeVisible) IconButton(onClick = controller::onCloseClicked) { CloseIcon() }
            s.message?.let { Text(stringResource(it.text)) }             // UiMessage(@StringRes text, retry)
        }
        is PaywallUiState.Purchased -> LaunchedEffect(Unit) { onDone(true) }
        is PaywallUiState.Unavailable -> Unavailable(onClose = { onDone(s.reason == SkipReason.ENTITLED) })
        is PaywallUiState.Closed -> LaunchedEffect(Unit) { onDone(false) }
    }
}
```
The `Content` state carries `model`, `selectedIndex`, `selectedPlan`, `closeVisible`, `closeCountdownSeconds`,
`backAllowed`, `busy` (`PURCHASING` or `RESTORING`) and `message`.

`PaywallUiState.Unavailable(reason, error)` can always be closed:
- `reason == null`: no answer from Play (offline). Show no price, and allow closing at once.
- `ENTITLED`: the user is already premium. Treat it as done.
- Anything else (product missing, disabled): continue the flow.

## 4. XML / Views

```kotlin
// User asked: no show rules
AdroitPaywall.show(activity, AppPaywalls.Settings, "settings", force = true)
// Automatic: show rules apply; the returned decision says whether it opened
if (AdroitPaywall.show(activity, AppPaywalls.Onboarding, "launch") is ShowDecision.Skip) goOn()

// Need the outcome: the Activity Result contract (register in every onCreate, so it survives rotation)
private val upgrade = registerForActivityResult(AdroitPaywall.contract()) { reason: PaywallCloseReason ->
    if (reason == PaywallCloseReason.PURCHASED) onPremium()
}
upgrade.launch(PaywallLaunch(AppPaywalls.Settings, "settings_button"))   // contract() does NOT check show rules
```
- `AdroitPaywallActivity` handles every template. It isn't exported, and its theme is
  `Adroit.Billing.Theme.Paywall`, which you can override.
- To use the header image, set `AdroitPaywall.imageLoader = { imageView, url -> … }`.
- **Embedded in a layout:**
  `<io.github.thesobiaakbar.adroit.billing.views.PaywallView android:id="@+id/paywall" app:adroit_billing_paywall="Onboarding" app:adroit_billing_placement="launch"/>`.
  Then set `paywallView.onClose = { reason -> … }`, or call `bind(definition, placement)` from code. Give it an id so
  it keeps its state across rotation. It supports the full-screen templates only.
- **Fully custom Views UI:** `AdroitBilling.paywalls.controller(def, placement, viewModelScope)` in a ViewModel.
  Collect its `state`, and call `dispose()` in `onCleared`.

## 5. `PaywallController`

`AdroitBilling.paywalls.controller(paywall, placement, scope)` creates one, or `rememberPaywallController` in Compose.

**Members:**
- Properties: `paywall`, `placement`, `spec`, and `state: StateFlow<PaywallUiState>`.
- All `@MainThread`:
  - `onShown()`: call once, when it is on screen.
  - `select(index)`
  - `purchase(activity)`: buys the selected plan with its chosen offer.
  - `restore()`
  - `manage(activity)`
  - `onCloseClicked()`
  - `onBackPressed(): Boolean`: false while back isn't allowed yet.
  - `dispose()`: call it when you created the controller yourself.
  - `saveState()` / `restore(map)`: for process death in a custom host.

**Lifecycle:**
- A controller ends at `Purchased` or `Closed`. To buy again (consumables), create a new one.
- `PaywallController.forModel(...)` builds one from a fixed model, for tests and previews (`testing.md`).

## 6. `PriceText` (all `@JvmStatic`, takes `Resources`)

| Call | Gives |
|---|---|
| `planPrice(res, plan)` | "€4.99 / month" |
| `cta(res, plan)` | `texts.cta`, else "Start free trial" / "Continue" |
| `disclosure(res, plan)` | The renewal terms Play requires before purchase |
| `duration(res, period)` / `every(res, period)` | "7 days" / "month" |
| `fill(res, template, offer, savingsVs)` | Fills `{trial} {price} {period} {renewalPrice} {renewalPeriod} {perWeek} {perMonth} {savings}`; `null` when a placeholder can't be filled |

A plan's raw data is `plan.offer` (an `OfferInfo` with its phases), `plan.savingsPercent`, `plan.basePlanId` and
`plan.product`.

A design that needs its own wording ("Weekly · $1.99"), built from `BillingPeriod`:
1. Map the common periods (7 days or 1 week, 1 month, 1 year) to your own string resources, with the formatted price
   as the argument.
2. Fall back to `PriceText` for anything else.

PhotoRecovery does this in `PremiumPlans.kt`.

## 7. Exit paywalls

```kotlin
Onboarding(PaywallSpec(plans = …, exitPaywall = "ExitDiscount")),
ExitDiscount(PaywallSpec(plans = listOf(PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", offer = OfferRule(tags = listOf("discount")))),
    template = PaywallTemplate.DIALOG, show = ShowRules(maxPerDay = 1))),
```
- **Compose:** `onExitPaywall = { exit -> current = exit }` runs right after `onClose`, in the same step, so the exit
  paywall replaces the first one before navigation reacts. Key the `Paywall` on the definition (`key(route.definition)`).
- **Views:** the contract returns the close reason. `recordClosed(...)` returns the exit paywall to show next, if any.
