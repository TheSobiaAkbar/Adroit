# PROMPT 11: Banner core

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §16 (all), §12, §25, and `03_PUBLIC_API.md` §3.4 (`BannerApi`).

## 1. Objective
Implement `BannerApi.create(activity, placement, options): BannerAdHandle` in `admob-core`: a lifecycle-bindable handle that owns a Google `AdView` inside a library container, sizes it with the **current, non-deprecated** adaptive API, and loads it only when `Ready`.

## 2. Context (Next-Gen banner, verified 2026-09-25; re-verify)
- `AdView(activity)`, `adView.loadAd(BannerAdRequest.Builder(adUnitId, adSize).build(), AdLoadCallback<BannerAd>)`
- Size: `AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)`. `getCurrentOrientationAnchoredAdaptiveBannerAdSize` and the portrait/landscape variants are **deprecated**. Inline adaptive: `getInlineAdaptiveBannerAdSize(widthDp, maxHeightDp)` or `getCurrentOrientationInlineAdaptiveBannerAdSize(ctx, widthDp)`. Fixed: `AdSize.BANNER` and `AdSize.MEDIUM_RECTANGLE` (verify the constant names).
- `BannerAdEventCallback` (impression, click, paid, full-screen open/close) and `BannerAdRefreshCallback` (refreshed, failedToRefresh). Set the refresh callback before adding to the hierarchy.
- Release: remove from the parent → `adView.destroy()`.
- Refresh is controlled in the AdMob UI and only happens while the banner is visible. Since 1.3.0, `loadAd` pauses SDK auto-retries when refresh is off.

## 3. Files to create
- `admob-core/src/main/kotlin/com/company/admob/internal/banner/BannerApiImpl.kt`
- `admob-core/src/main/kotlin/com/company/admob/internal/banner/BannerAdHandleImpl.kt`
- `admob-core/src/main/kotlin/com/company/admob/internal/banner/BannerSizeResolver.kt`: a pure function from (BannerSize, containerWidthPx, density, windowWidthPx) to (adSize request spec, widthDp), plus an 8 dp threshold for width-change reloads
- Adapter additions in `internal/google/NextGenBannerView.kt` implementing `SdkBannerView` (if not already created in Prompt 04)

## 4. Files to modify
- `AdsClientImpl.kt` (wire `banner`)
- `internal/port/AdsSdk.kt` / `SdkBannerView`, if the shape needs adjustment (and the fakes to match)

## 5. Implementation requirements
1. `create()` must be called on main (assert, and log an error when called elsewhere). It creates a library `FrameLayout` container (`handle.view`), `layoutParams` `MATCH_PARENT` × `WRAP_CONTENT`, and adds the SDK `AdView` into it **lazily, at first load**.
2. Width: use the container's measured width after `doOnLayout`. If it's 0, fall back to the Activity window width (`WindowMetrics` on API 30+, `DisplayMetrics` below). Convert to dp.
3. `reserveSpace = true` → set the container's `minimumHeight` to the expected height (via `SdkBannerView.expectedHeightPx`) **before** the load, so there's no layout shift. `expectedHeightPx` is exposed on the handle.
4. Load only when `readiness.canLoad()`. Otherwise observe the readiness state and load when it becomes Ready (a single collector per handle, cancelled on destroy).
5. Failures: state `Failed(error)`, and retry with the banner policy **only while the view is attached to a window and `isShown`**. Pause retries on detach and resume on attach. Don't retry when the SDK refresh handles it (the refresh-failure callback). Log it and let the SDK continue.
6. A width change of ≥ 8 dp on `onSizeChanged` or a configuration change (the same handle surviving because the host handles config changes) → destroy the old AdView and load a new one.
7. `bindTo(owner)`: observe `ON_DESTROY` → `destroy()`. Multiple `bindTo` calls: the last owner wins, and the previous observer is removed.
8. `destroy()`: idempotent. It cancels the collectors and retries, removes the AdView from its parent, calls `adView.destroy()`, clears the listener references, and sets the state to `Hidden(null)`. After destroy, every method is a no-op.
9. Blocked (ad-free, consent revoked, remote disabled) → the AdView is destroyed and the state is `Hidden(reason)`. When it's unblocked, the handle recreates the view and loads again if it's not destroyed.
10. Events: `LoadRequested`, `Loaded`, `LoadFailed`, `Impression`, `Clicked`, and `Paid` with the banner placement.
11. The environment ID substitution was already applied by `EffectiveConfig`. Banner `Standard`/`MediumRectangle` sizes use the fixed demo ID in DEVELOPMENT.
12. Hardware acceleration: if the Activity window has hardware acceleration disabled (`window.attributes.flags and FLAG_HARDWARE_ACCELERATED == 0`), log a warning once (video ads won't play).

## 6. Public API requirements
`BannerApi`, `BannerOptions`, `BannerSize`, `BannerAdHandle`, and `BannerState` as in 03 §3.4. No Google types.

## 7. Constraints
- No XML attrs or resources (that's the `admob-views` job).
- No Compose.
- Don't implement a library refresh timer.

## 8. Dependencies
None new.

## 9. Tests
- `BannerSizeResolverTest`: widths, density, fallbacks, and the 8 dp threshold.
- `BannerAdHandleTest` (Robolectric, `FakeAdsSdk` with a `FakeSdkBannerView`):
  - create before Ready → no load → Ready → one load
  - reserved height applied before the load
  - `destroy` is idempotent, the fake view's `destroy()` is called once, and the view is removed from its parent
  - `bindTo(TestLifecycleOwner)` → `ON_DESTROY` destroys it
  - a failure → retry only when attached and shown (detach → no retry, attach → retry)
  - ad-free → hidden and destroyed → back → reload
  - a width change → reload, and a small change (< 8 dp) → no reload
  - no Activity retained after destroy (WeakReference + GC)
- `BannerEventsTest`: the event sequence.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.internal.banner.*" :admob-core:lint apiDump build
```
Manual: in the sample, `val handle = CompanyAdMob.banner.create(this, Placements.HomeBanner); container.addView(handle.view); handle.bindTo(this)`. A test banner appears. Rotate: no crash, and no leak (LeakCanary is added in Prompt 17; for now check the logs).

## 11. Acceptance criteria
- [ ] Only non-deprecated `AdSize` APIs are used (the `-Werror` deprecation warnings prove it).
- [ ] Every test in §9 passes.
- [ ] The handle survives misuse (destroy twice, bind after destroy, create off main) without crashing.

## 12. Completion report
Per 00_WORKING_RULES §D.
