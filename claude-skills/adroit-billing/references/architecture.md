# Multi-module, Hilt, MVI apps

From PhotoRecoveryNewApp (`:app`, `:core:*`, `:feature:*`). For a single-module app, skip this file.

## Module rules

- **Feature modules may depend on `adroit-billing`** (the core: controllers, `PriceText`, `BillingClientApi`), for
  example when a feature owns the paywall screens. Mark it in `build.gradle.kts`:
  `implementation(libs.adroit.billing) // Billing only: never the ads`. Tests get
  `testImplementation(libs.adroit.billing.testing)`.
- **Features never depend on the ads modules**, and never know `AppProducts` / `AppPaywalls`. Those enums live in
  `:app` (`billing/AppBilling.kt`), next to `AppAds`.
- `:app` calls `AdroitBilling.initialize`, runs the ad-free bridge, decides where each paywall opens, and binds the
  feature interfaces below.

## A feature-owned `fun interface` per paywall

The feature declares what it needs, which is a controller for "its" paywall. `:app` binds that to a concrete
`AppPaywalls` entry and placement:

```kotlin
// :feature:launch: no AppPaywalls here
fun interface RemoveAdsPaywall { fun open(scope: CoroutineScope): PaywallController }
fun interface PremiumPaywall { fun open(placement: String, scope: CoroutineScope): PaywallController }
fun interface PaywallControllers { fun open(step: PaywallStep, scope: CoroutineScope): PaywallController }  // multi-step

@HiltViewModel
class RemoveAdsViewModel @Inject constructor(paywall: RemoveAdsPaywall) : MviViewModel<…>() {
    private val sale = PaywallSale(paywall.open(viewModelScope), viewModelScope, onUpdate = ::render, onEnd = ::finish)
}

// :app: di/BillingModule.kt
@Module @InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides fun billing(): BillingClientApi = AdroitBilling
    @Provides fun removeAdsPaywall(): RemoveAdsPaywall = RemoveAdsPaywall { scope ->
        AdroitBilling.paywalls.controller(AppPaywalls.RemoveAdsPaywall, AppPaywalls.AD_CLOSE_PLACEMENT, scope)
    }
    @Provides fun premiumPaywall(): PremiumPaywall = PremiumPaywall { placement, scope ->
        AdroitBilling.paywalls.controller(AppPaywalls.PremiumPaywall, placement, scope)
    }
    @Provides fun paywallControllers(): PaywallControllers = PaywallControllers { step, scope ->
        AdroitBilling.paywalls.controller(AppPaywalls.of(step), AppPaywalls.LAUNCH_PLACEMENT, scope)
    }
}
```
Unit tests pass `RemoveAdsPaywall { scope -> PaywallController.forModel(...) }` (`testing.md`).

## `PaywallSale`: one wrapper for every custom paywall screen

Several screens (remove-ads, the feature gate, Pro, the unlock sheet's direct purchase) need the same mapping from
`PaywallUiState` to screen state, plus "end once". Write it once in the feature (or in `:core:ui` if two features need
it), and don't repeat it in each ViewModel:

```kotlin
data class SaleState(
    val plan: PaywallPlan? = null,            // selected plan; null while loading or unavailable
    val plans: List<PaywallPlan> = emptyList(),
    val unavailable: Boolean = false,         // offline: no price, closable
    val closeVisible: Boolean = false,
    val purchasing: Boolean = false,
    @param:StringRes val message: Int? = null,
)
sealed interface SaleIntent { data object Shown : SaleIntent; data class Select(val index: Int) : SaleIntent
    data class Buy(val activity: Activity) : SaleIntent; data object Close : SaleIntent; data object Back : SaleIntent }

internal class PaywallSale(
    private val controller: PaywallController, scope: CoroutineScope,
    private val onUpdate: (SaleState) -> Unit, private val onEnd: (premium: Boolean) -> Unit,
) {
    private var sale = SaleState(); private var ended = false; private var watch: Job? = null
    init { watch = scope.launch { controller.state.collect(::show) }.also { if (ended) it.cancel() } } // may end before launch returns

    fun onIntent(intent: SaleIntent) {
        if (ended) return
        when (intent) {
            SaleIntent.Shown -> controller.onShown()
            is SaleIntent.Select -> controller.select(intent.index)
            is SaleIntent.Buy -> controller.purchase(intent.activity)
            SaleIntent.Close -> controller.onCloseClicked()
            SaleIntent.Back -> controller.onBackPressed()
        }
    }
    fun end(premium: Boolean = false) { if (ended) return; ended = true; watch?.cancel(); controller.dispose(); onEnd(premium) }

    private fun show(state: PaywallUiState) {
        if (ended) return
        when (state) {
            PaywallUiState.Loading -> update(sale.copy(plan = null, closeVisible = false, purchasing = false))
            is PaywallUiState.Content -> update(sale.copy(plan = state.selectedPlan, plans = state.model.plans,
                closeVisible = state.closeVisible, purchasing = state.busy == Busy.PURCHASING, message = state.message?.text))
            is PaywallUiState.Unavailable -> when (state.reason) {
                null -> update(sale.copy(plan = null, plans = emptyList(), unavailable = true, closeVisible = true, purchasing = false))
                SkipReason.ENTITLED -> end(premium = true)
                else -> end()
            }
            is PaywallUiState.Purchased -> end(premium = true)
            is PaywallUiState.Closed -> end()
        }
    }
    private fun update(next: SaleState) { sale = next; onUpdate(next) }
}
```
- The ViewModel turns `onEnd(premium)` into its "leave" effect: navigate back, or replace itself with the gated
  content when the user is premium.
- **Always call `dispose()`** on a controller you opened yourself. `PaywallSale.end` does it.
- **Rotation:** the controller lives in the ViewModel's scope, so it survives rotation, and the Activity is passed
  only into `Buy`.

## Screens stay stateless

The paywall screen is a stateless `XScreen(state, onIntent)` drawn with the app's design system. It calls
`PriceText.planPrice/cta/disclosure` through `LocalResources.current`, or receives pre-formatted strings from the
ViewModel. Its preview builds a `PaywallPlan` from `TestPaywalls.model(...)` (in `debug` / test fixtures), or a plain
UI model.

## Premium gating at the app level

```kotlin
// :app: AppNavHost
val entitlements by AdroitBilling.entitlements.collectAsStateWithLifecycle()
val sell = PremiumEnabled.value && !entitlements.has(PREMIUM)
homeScreen(proButton = { if (sell) GetProButton(onClick = { navController.navigate(PremiumRoute(PRO_BUTTON_PLACEMENT)) }) })
```
Features receive slots or booleans, and never read entitlements for app-wide decisions. A feature that needs to react
to a purchase (ending onboarding) takes `BillingClientApi` and waits on `entitlements`.

## Instrumented tests

Replace `BillingModule` with `@TestInstallIn(replaces = [BillingModule::class])`, and provide a `FakeBilling` and
`forModel` controllers. Alternatively, keep real `AdroitBilling` with `DemoBilling(true)` in the debug test build.
