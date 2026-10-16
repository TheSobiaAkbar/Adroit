# B17 — adroit-billing-compose: paywall, discount dialog and building blocks

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Optional Compose UI that renders `PaywallController` for every template (`plans`, `single`, `bottom_sheet`,
`dialog`), styled from Firebase, with the configurable close button. Also provide building blocks so apps can design
their own paywall and still use the controller.

## Context
06 §5.2, B13. Look at `adroit-compose` for this repo's Compose conventions: retainers in a ViewModel, no Activity
leaks, the BOM version, and `adroit.android.library.compose`.

## Prerequisites
B13.

## Files (`adroit-billing-compose/.../billing/compose/`)
- `Paywall.kt`: `@Composable fun Paywall(definition, placement, onClose: (PaywallCloseReason) -> Unit, modifier,
  content: (@Composable PaywallScope.() -> Unit)? = null)`. A null `content` means the built-in template from the
  config. It gets its controller from `viewModel()` keyed by `definition.name + placement`.
- `PaywallDialog.kt` (`dialog` template, `Dialog`), `PaywallBottomSheet.kt` (`ModalBottomSheet`), `PaywallScreen.kt`
  (full-screen `plans`/`single`).
- `PaywallScope` building blocks:
  - `PlanList()`;
  - `PlanCard(plan)`;
  - `CtaButton()`;
  - `CloseButton()`, which renders the countdown ring when `showCountdown`;
  - `RestoreButton()`;
  - `ManageButton()`;
  - `Disclosure()`, which is always rendered by templates;
  - `Features()`;
  - `HeaderImage()`.
- `rememberPaywallController(definition, placement)` for fully custom UIs.
- `PaywallTheme`: colors from `style` (accent, background, dark) over `MaterialTheme`.
- Tests: `src/test` (Robolectric Compose) or `src/androidTest`, following `adroit-compose`.

## Requirements
1. `BackHandler(enabled = !state.backAllowed)` consumes back while the config forbids it. Predictive back works when
   it's allowed.
2. `onShown()` is called from a `LaunchedEffect` after the first composition that is actually displayed, and
   `dispose()` from the ViewModel's `onCleared`.
3. `purchase` needs an Activity: get it with `LocalActivity.current` (activity-compose) or by unwrapping
   `LocalContext`. Don't store it.
4. Header image: no image-loading library dependency. Allow `headerImage` only through an optional
   `imageLoader: (@Composable (url: String) -> Unit)?` parameter. Without it, the header image is skipped. Document
   that Coil can be plugged in.
5. Accessibility:
   - the close button has `contentDescription` "Close" and a 48dp touch target;
   - the countdown is announced politely only at reveal;
   - plan cards use `selectable` with `Role.RadioButton`;
   - prices are read as Play's string;
   - TalkBack order: title, plans, CTA, disclosure.
6. Disclosure text: always visible under the CTA. For a trial: "{trial} free, then {renewalPrice}/{period}. Cancel
   anytime in Google Play." It's localizable through resources.
7. A success per `successAction` calls `onClose(PURCHASED)` or shows a "You're premium" state.
8. Insets: the paywall handles `WindowInsets.safeDrawing` itself, so it works edge-to-edge.

## Edge cases
- Rotation mid-countdown: the controller in the ViewModel keeps the remaining time.
- The catalog is Loading: a shimmer or progress, with close following the delay rule.
- `Unavailable`: a message plus close is always enabled.
- Very long remote texts: the layout scrolls, and nothing is clipped off-screen.

## Tests
Compose UI tests:
- delay 0 shows X at once;
- delay 3 shows X after an advanced clock;
- −1 never shows X;
- back is blocked or allowed per the policy;
- the selected plan changes the CTA text;
- trial copy only with a trial;
- semantics (roles, descriptions);
- rotation keeps the countdown.

## Verify
```
./gradlew :adroit-billing-compose:testDebugUnitTest :adroit-billing-compose:apiCheck :adroit-billing-compose:lint
```

Also run `connectedDebugAndroidTest` if instrumented tests were added and a device is attached.

## Acceptance criteria
- [ ] Every template renders from JSON alone (the fixtures from B11).
- [ ] A custom `content` can reuse the blocks and the controller.
- [ ] No Activity leak (LeakCanary in the sample, B21).

## Report
B00 §D.
