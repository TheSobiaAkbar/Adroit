# PROMPT 09: Rewarded and rewarded interstitial

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §14 (all), §13, §22, and `03_PUBLIC_API.md` §3.4 (`RewardedApi`, `RewardedInterstitialApi`).

## 1. Objective
Add the rewarded and rewarded-interstitial formats on top of `FullScreenAdEngine`, with **exactly-once, never-lost** reward semantics, including the suspend API's cancellation safety via `unclaimedResults`.

## 2. Context
- Next-Gen: `RewardedAd.show(activity, OnUserEarnedRewardListener)` with `RewardItem.amount`/`type`. `RewardedInterstitialAd` has the same shape. SSV custom data is set on the ad object before `show`. Verify the exact API names on `/next-gen/rewarded` and `/next-gen/ssv`.
- Rewarded interstitial **requires a host-provided intro screen with an opt-out**. The library doesn't render UI for it, but its KDoc and docs must say so.
- The reward can be earned before or after dismissal. The final `RewardResult` is emitted on dismissal (or on failure/watchdog).

## 3. Files to create (`admob-core/src/main/kotlin/com/company/admob/internal/fullscreen/`)
- `RewardSession.kt`: extends or wraps `ShowSession<RewardResult>`. It holds `earned: Reward?` (the first `onRewardEarned` wins, and later ones are logged and ignored) and `rewardId = UUID.randomUUID().toString()`. On dismiss → `Earned(reward, rewardId)` or `NotEarned`. On failure/watchdog → `Failed(error)`. Emits `AdEvent.RewardEarned` at the moment of earning.
- `UnclaimedRewards.kt`: `Channel<RewardResult.Earned>(capacity = Channel.UNLIMITED)` exposed as `receiveAsFlow()` (single consumer, which is documented).
- `RewardedApiImpl.kt`, `RewardedInterstitialApiImpl.kt`
- Strategy objects `RewardedStrategy` and `RewardedInterstitialStrategy`. Rewarded is **excluded** from the global full-screen interval by default; rewarded interstitial participates.

## 4. Files to modify
- `FullScreenAdEngine.kt`: the minimal extension needed for reward sessions and SSV data (`RewardedShowOptions.serverSideVerificationCustomData` → `LoadedFullScreenAd.setServerSideVerification` before show).
- `AdsClientImpl.kt`: wire `rewarded`, `rewardedInterstitial`, and `showRewarded`. Remove the placeholders.

## 5. Implementation requirements
1. **Callback API:** `show(activity, placement, options, onResult)`. `onResult` runs exactly once on main. The lambda is held by the session until resolution and then cleared.
2. **Suspend API:** `suspend fun showAndAwait(...)`: `suspendCancellableCoroutine`. On cancellation of the caller: the ad is **not** cancelled, the session is marked `callerCancelled = true`, and when the session resolves with `Earned`, it's sent to `UnclaimedRewards` instead of the continuation. `NotEarned`/`Failed` with a cancelled caller are dropped (logged at debug). Resolution must pick exactly one destination atomically. Use a single `AtomicReference<Destination>` or confine the decision to main, and prove it with a race test.
3. `unclaimedResults` is per-format (`rewarded.unclaimedResults`, `rewardedInterstitial.unclaimedResults`).
4. The gate order is the same as interstitial, except rewarded skips the global frequency interval by default (it's configurable).
5. KDoc on `RewardedApi.show` states: grant in a ViewModel or repository, use `rewardId` for idempotency, prefer SSV for valuable rewards, and process death during the ad loses the client-side reward.
6. KDoc on `RewardedInterstitialApi.show` states: "You must show an intro screen announcing the reward with an option to opt out before calling this" (with a link to Google's rewarded-interstitial guide).

## 6. Public API requirements
`RewardedApi`, `RewardedInterstitialApi`, `RewardedShowOptions`, `AdsClient.showRewarded`, as in 03 §3.4.

## 7. Constraints
No persistence of rewards. No UI.

## 8. Dependencies
None new.

## 9. Tests
- Earned-then-dismissed → `Earned` once, with the same `rewardId` in the event and the result.
- Dismissed without a reward → `NotEarned`.
- Two `onRewardEarned` callbacks → one reward.
- The reward callback arriving **after** dismissal, within a 500 ms grace window: implement a small grace delay before resolving on dismiss (configurable, default 300 ms) and test both orders. Document why: some adapters deliver the reward after dismissal.
- Failed to show → `Failed`, and no reward.
- Watchdog → `Failed(Timeout)`.
- Suspend: normal completion. Caller cancelled mid-ad then earned → exactly one item in `unclaimedResults` and the continuation isn't resumed. Caller cancelled then not earned → nothing emitted.
- Race test: the resolution and the caller's cancellation from different threads, 10,000 iterations → never both destinations, never neither (for Earned).
- SSV custom data is passed to the fake ad before `show`.
- Rewarded isn't blocked by the global interval after an interstitial. Rewarded interstitial is.
- Every §9 case from Prompt 08 that applies (gates, expiry, duplicate show) runs once for each of the two formats (parameterized).

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.internal.fullscreen.*" :admob-core:lint apiDump build
```
Manual: in the sample, rewarded "Watch ad for +10 coins". Confirm the coin count increments exactly once per earned test ad, including when you rotate the device during the ad.

## 11. Acceptance criteria
- [ ] The exactly-once reward guarantee is proven by the race test.
- [ ] The rotation case is covered by `unclaimedResults` and demonstrated in the sample (or reported as not verified).
- [ ] The KDoc requirements in §5.5–5.6 are present.
- [ ] Build, lint, tests, and apiCheck are green.

## 12. Completion report
Per 00_WORKING_RULES §D. Explain the grace-window choice and its default.
