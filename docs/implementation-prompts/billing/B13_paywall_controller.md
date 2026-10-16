# B13 — PaywallController: UI-agnostic paywall state (close-button countdown, purchase, success, errors)

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
One state holder that both UI modules render, so the Compose and XML paywalls behave identically. It covers:
- loading;
- plans;
- selection;
- **close-button countdown from Firebase**;
- back-press policy;
- purchasing;
- pending;
- success;
- error;
- restore;
- manage.

No Android UI dependency.

## Context
06 §5.2 `close`, `successAction`, `showRestore`, `showManage`. B12 provides `PaywallModel`.

## Prerequisites
B12, B09 (manage), B08 (restore).

## Files
- `PaywallController.kt` (public). Created by `AdroitBilling.paywalls.controller(def, placement, scope)`.
- `PaywallUiState.kt` (public, immutable):
  - `Loading`;
  - `Content(model, selectedIndex, closeVisible, closeCountdownSeconds, backAllowed, busy: Busy?, message:
    UiMessage?)`;
  - `Purchased(result)`;
  - `Unavailable(reason)`.
- Tests

## Requirements
1. `state: StateFlow<PaywallUiState>`. Actions:
   - `select(index)`;
   - `purchase(activity)`;
   - `restore()`;
   - `manage(activity)`;
   - `onCloseClicked()`;
   - `onBackPressed(): Boolean` (true = consumed, the paywall stays);
   - `onShown()`;
   - `dispose()`.
2. Close button:
   - `delaySeconds = 0`: `closeVisible = true` immediately;
   - `delaySeconds = N > 0`: `closeVisible = false`, `closeCountdownSeconds` ticks N…1 (if `showCountdown`), then
     true;
   - `delaySeconds = -1`: never visible.

   The countdown starts at `onShown()` (the first frame actually visible), not at construction. It **pauses while
   the app is in the background** (`ProcessLifecycleOwner`), so the delay counts visible seconds. Emit
   `PaywallCloseButtonShown(afterSeconds)`.
3. Back:
   - `ALWAYS`: allowed;
   - `AFTER_DELAY`: allowed once `closeVisible` (or, with `delaySeconds = -1`, never);
   - `NEVER`: never.

   While a purchase is in flight, back is consumed (the Play sheet is on top anyway).
4. Purchase: calls `AdroitBilling.purchase` with `placement`, `paywallId = def.name`, `campaignId`, `basePlanId` and
   `offer` = the resolved offer ID.
   - `Cancelled`: back to `Content`, no message;
   - `Pending`: message "Payment pending…" (a string resource ID, not text), and the paywall stays;
   - `Failed`: a message keyed by error type, with retry allowed when `isRetryable`;
   - `Purchased`: `Purchased`, then the UI closes per `successAction`.
5. Survives configuration changes: the controller is meant to live in a ViewModel (the UI modules do that). The state
   is fully restorable from `SavedStateHandle`-like `saveState(): Map<String, Any>` / `restore(map)`, so the
   countdown remaining and the selection survive process death.
6. An `Unavailable` state appears if `shouldShow` says Skip at controller creation (the app showed it anyway) or the
   catalog `Failed`. The UI then shows a friendly error with close **always** allowed. A broken paywall must never
   trap the user, whatever the config says.
7. Events: `PaywallShown` (once per controller), `PaywallClosed(reason, secondsVisible)`, `OfferShown` for the
   selected plan.

## Tests
Virtual time:
- delays 0, 3 and −1;
- the countdown ticks;
- pause in the background;
- each back policy;
- back during a purchase;
- each purchase result;
- save and restore mid-countdown;
- the Unavailable escape hatch (close allowed even when `back = NEVER`, `delay = -1`).

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] The close-button delay behaves exactly as configured (0, N, −1), counting only visible time.
- [ ] A failed or empty paywall can always be closed.
- [ ] No Android UI imports in the controller.

## Report
B00 §D.
