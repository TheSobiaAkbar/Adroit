# B18 — adroit-billing-views: paywall Activity, PaywallView and dialog for XML apps

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Give Kotlin + XML/ViewBinding apps the same paywalls as Compose, without forcing Compose or Fragment on them:
- a ready `AdroitPaywallActivity` (full-screen templates);
- a `PaywallView` to embed in any layout;
- a dialog/bottom-sheet presenter.

All of them render `PaywallController`.

## Context
`adroit-views` must never depend on androidx.fragment at runtime (ADR-014). Billing views follow the same rule. Read
`adroit-views` for its conventions (retainers in a `ViewModelStoreOwner`, attrs prefix, sdp/ssp usage).

## Prerequisites
B13.

## Files (`adroit-billing-views/.../billing/views/`)
- `AdroitPaywall.kt` (public object):
  - `show(activity, definition, placement)`, which launches `AdroitPaywallActivity`;
  - `contract()`: an `ActivityResultContract<PaywallLaunch, PaywallCloseReason>` for apps that want the result.
- `AdroitPaywallActivity.kt`: a `ComponentActivity` (not AppCompat), with a theme
  `Theme.AdroitBilling.Paywall` (no-ActionBar, edge-to-edge). The controller lives in a ViewModel.
- `PaywallView.kt` (custom `FrameLayout`): binds to a controller from the nearest `ViewModelStoreOwner`. It has XML
  attributes `app:adroit_billing_paywall="Onboarding"` (the enum name, resolved through `AdroitBilling` settings) and
  `app:adroit_billing_placement`.
- `PaywallDialog.kt`: an `android.app.Dialog` / `BottomSheetDialog`?
  - Material isn't allowed in the library. Use a plain `Dialog` with a bottom-gravity window for `bottom_sheet`.
  - The dialog is retained across rotation by re-showing from the controller state in the owner's ViewModel.
- Layouts `adroit_billing_paywall_plans.xml`, `adroit_billing_paywall_single.xml`, `adroit_billing_plan_item.xml`,
  `adroit_billing_close_button.xml` (with a `ProgressBar`-style countdown ring), and strings/dimens (prefix
  `adroit_billing_`).
- Tests (Robolectric), following `adroit-views`.

## Requirements
1. Same behaviour as B17:
   - close delay, countdown and back policy (`OnBackPressedDispatcher` callback enabled while blocked);
   - disclosure always shown;
   - accessibility (content descriptions, 48dp targets, `isSelected` + RadioButton semantics on plans);
   - success handling.
2. Activity references: `purchase` and `manage` use the hosting Activity found via the view's context, only during
   the click.
3. Styling from config: colors applied programmatically. The app can override the theme `Theme.AdroitBilling.Paywall`
   and the layouts by name (the same resource-override approach as the native ad layouts).
4. `AdroitPaywall.show` first checks `shouldShow`. If it's Skip, it doesn't launch and returns the `ShowDecision`.
   Add a `force = true` parameter for buttons ("Upgrade" in Settings always opens it).
5. Exit paywall: when the user closes, if `recordClosed` returns an exit paywall, show it right after (same Activity,
   swapped content).

## Edge cases
- Rotation and process death in `AdroitPaywallActivity`: the state comes back from `SavedStateHandle` (B13
  `saveState`).
- `PaywallView` in a RecyclerView: no. Document that it's for screens and sheets, not list items.
- An app using a Material3 theme: the paywall Activity has its own theme, so there's no clash.

## Tests
Robolectric:
- delay 0/N/−1 with a paused looper and an advanced clock;
- back blocked or allowed;
- rotation keeps the state;
- the contract returns `PURCHASED`/`CLOSE_BUTTON`/`BACK`;
- `show` honours `shouldShow` and `force`;
- no Fragment class on the runtime classpath (the dependency guard).

## Verify
```
./gradlew :adroit-billing-views:testDebugUnitTest :adroit-billing-views:apiCheck :adroit-billing-views:lint checkModuleDependencies
```

## Acceptance criteria
- [ ] An XML app can show a fully Firebase-styled paywall with one line: `AdroitPaywall.show(this,
      AppPaywalls.Onboarding, "onboarding")`.
- [ ] No Compose, AppCompat, Material or Fragment in the runtime dependencies of `adroit-billing-views`.

## Report
B00 §D.
