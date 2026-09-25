# PROMPT 12: Native core (loader, pool, handle, binder)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §17 (all), §12, §25, and `03_PUBLIC_API.md` §3.4 (`NativeApi`, `NativeAdHandle`, `NativeAdLayout`, `NativeAdRenderer`, `NativeAdAssets`).

## 1. Objective
Implement native ad loading, a small per-placement pool, the public opaque `NativeAdHandle`, and the internal binder that renders a **host-provided plain layout** inside a programmatically created Google `NativeAdView`. Hosts never reference Google classes.

## 2. Context (Next-Gen native, verified 2026-09-25; re-verify)
- `NativeAdLoader.load(NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)).build(), NativeAdLoaderCallback { onNativeAdLoaded, onAdFailedToLoad })`. Multi-ad (`numberOfAds` ≤ 5) **doesn't work with mediation**, so always request 1.
- `NativeAdView` (a `FrameLayout`) must be the ancestor of every asset view. Set `headlineView`, `bodyView`, `callToActionView`, `iconView`, `advertiserView`, `priceView`, `starRatingView`, and `storeView`, then `registerNativeAd(nativeAd, mediaView)`.
- `MediaView` shows video or the main image. `mediaView.imageScaleType`.
- The AdChoices overlay is added by the SDK. **The ad attribution label is required.** No custom click handlers on asset views.
- Destroy native ads when no longer used. When precaching, clear and reload after 1 h. Cache only what's visible.
- Hardware acceleration is required for video.
- `NativeAdOptions` (media aspect ratio, AdChoices placement, video options): check `/next-gen/native/options` for current names. Expose only `adChoicesCorner` and `mediaAspectRatio` preference through `NativePlacementConfig`, if they're simple.

## 3. Files to create (`admob-core/src/main/kotlin/com/company/admob/internal/native/`)
- `NativeAdLoaderEngine.kt`: `load(placement, callback)`, `suspend load`, `preload(placement)`, and a pool per effective ad unit (size from config, 1..3). `load` serves from the pool first (a fresh item), then triggers a refill. Retry uses the native policy while the pool is below target **and** a consumer is waiting or preload is on. TTL 55 min: expired pooled ads are destroyed and refilled.
- `NativeAdHandleImpl.kt`: implements `NativeAdHandle`. It wraps `LoadedNativeAd`. `destroy()` is idempotent: it calls `LoadedNativeAd.destroy()` and clears the view bindings. `isDestroyed`. It also tracks the bound `NativeAdView` so re-rendering into a new view (after rotation) unregisters or detaches from the old one first.
- `NativeAdBinder.kt` (it uses Google types, so it lives in `internal/google/NextGenNativeAdBinder.kt`, with a port `NativeRenderPort` in `internal/port/`). `render(handle, container, layout, renderer): View`:
  1. Create a `NativeAdView(context)` with MATCH_PARENT/WRAP_CONTENT.
  2. Inflate `layout.layoutRes` into it.
  3. For each non-zero id, find the view. **A missing required view (headline) → throw `IllegalArgumentException`** naming the id, since it's a programmer error. Log optional missing views at WARN once.
  4. Populate: `TextView.text` for text assets, `ImageView.setImageDrawable(icon)`, and a `RatingBar` for starRating **if the view is a RatingBar, otherwise text**. Hide (`GONE`) views whose asset is null, except the headline.
  5. If `mediaContainerId != 0`: create a Google `MediaView`, add it to that container with MATCH_PARENT, and set the scale type to CENTER_CROP by default.
  6. Assign the asset views on `NativeAdView`.
  7. `registerNativeAd(nativeAd, mediaView)`.
  8. Call `renderer?.onBind(root, assets)`.
  9. `container.removeAllViews(); container.addView(nativeAdView)` and return `nativeAdView` as `View`.
- `NativeApiImpl.kt`

## 4. Files to modify
- `AdsClientImpl.kt` (wire `native`)
- `internal/port/AdsSdk.kt` if `LoadedNativeAd` needs `bindInternal()` adjustments. Update the fakes, and add a `FakeNativeRenderPort` that records bindings without Google classes.

## 5. Implementation requirements
1. **Ownership rule:** a handle returned from `load` belongs to the caller, who must `destroy()` it. Pooled ads are owned by the engine until handed out. Document this in KDoc on `NativeApi.load`.
2. Blocked (ad-free, consent, remote) → destroy the whole pool, and notify live handles (state becomes destroyed or hidden). The `admob-views`/`compose` layers observe a `StateFlow<NativeAdHandleState>` on the handle. Add `val state: StateFlow<NativeAdHandleState>` (`Active`, `Destroyed`, `Blocked`) to the public handle if it isn't in 03, and update 03 in the same change.
3. The events are `Loaded`, `LoadFailed`, `Impression`, `Clicked`, `Paid`, `Destroyed`, and `Expired`. `Impression` comes from `NativeAdEventCallback.onAdImpression`.
4. The binder runs on main only. The loader callbacks are already posted to main by the adapter.
5. The hardware acceleration warning is shared with the banner (same helper).
6. Policy notes in KDoc on `NativeAdLayout`: an attribution view is required, leave a corner for AdChoices, don't add click listeners, and the media view needs at least 120×120 dp for video (verify the current Google guidance on the native options page and cite it).

## 6. Public API requirements
`NativeApi` (`preload`, `load` ×2, `render`), `NativeAdResult`, `NativeAdHandle` (+ `state`), `NativeAdAssets`, `NativeAdLayout`, and `NativeAdRenderer`.

## 7. Constraints
- **No layout resources in core.** Templates belong in `admob-views` (Prompt 13). Core tests use test-only layouts in `src/test/res` (Robolectric), prefixed `company_admob_test_`.
- No RecyclerView or list helpers in core.

## 8. Dependencies
None new.

## 9. Tests
- The loader, with fakes: load success and failure. The pool fills to its size after Ready. A pool hit is served without an SDK call and triggers a refill. Expiry destroys and refills. Blocked destroys everything. Mediation-safe: the fake asserts `numberOfAds == 1` for every request.
- `NativeAdHandleImplTest`: destroy is idempotent, render after destroy → `IllegalStateException` (documented), and re-render into a new container detaches the old view.
- Binder (Robolectric, using `FakeNativeRenderPort` for the Google parts, plus one test with the real `NextGenNativeAdBinder` if Google's `NativeAdView` can be constructed under Robolectric; if it can't, document it and cover it in instrumentation in Prompt 17): missing required id → exception, null assets hidden, RatingBar vs TextView rating, and `renderer.onBind` called after binding.
- A leak test: the handle is destroyed → no reference to the container or context remains (WeakReference).

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.internal.native.*" :admob-core:lint apiDump build
```

## 11. Acceptance criteria
- [ ] Hosts can render a native ad using only their own layout and ids, with no Google class in the host code or XML.
- [ ] Every loaded Google `NativeAd` is destroyed exactly once in every path (a fake counter test over the load → pool → handout → destroy / expiry / blocked paths).
- [ ] Every request uses `numberOfAds = 1`.
- [ ] Build, lint, tests, and apiCheck are green. 03_PUBLIC_API is updated if the handle state was added.

## 12. Completion report
Per 00_WORKING_RULES §D. State whether `NativeAdView` works under Robolectric.
