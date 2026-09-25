# PROMPT 14: `admob-compose` module

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §18 (all), §7, and `03_PUBLIC_API.md` §3.10.

## 1. Objective
Give Compose apps idiomatic, recomposition-safe composables and state holders for banners, native ads, ad availability, and full-screen launches, with no Material dependency and no ad reloads on recomposition or configuration change (native).

## 2. Context
- Compose is optional. Core never depends on it.
- The views module provides the native templates and render path. Compose wraps them through `AndroidView`.
- The official Next-Gen banner Compose sample uses `AndroidView` + `DisposableEffect { destroy }`.

## 3. Files to create (`admob-compose/src/main/kotlin/com/company/admob/compose/`)
- `AdBanner.kt`: `@Composable fun AdBanner(placement, modifier, options, placeholder, onStateChange)`
- `AdNative.kt`: `@Composable fun AdNative(placement, modifier, layout, slotKey, renderer, placeholder)`
- `RememberNativeAd.kt`: `rememberNativeAd(placement, slotKey): State<NativeAdResult?>`, backed by `NativeAdRetainer`
- `internal/NativeAdRetainer.kt`: `ViewModel` keyed per (placement, slotKey). Holds `NativeAdHandle`s and destroys them in `onCleared()`. Bounded to `maxRetainedPerScreen` (default 3) with LRU destruction.
- `AdAvailability.kt`: `rememberAdAvailability(placement): State<AdAvailability>`, using `collectAsStateWithLifecycle`
- `FullScreenAdLauncher.kt`: `rememberFullScreenAdLauncher(): FullScreenAdLauncher`. It resolves the Activity at call time from `LocalContext`/`LocalActivity` (check which is available in the resolved `activity-compose` version, or use `findActivity()`) and never stores it.
- `internal/FindActivity.kt`

## 4. Files to modify
- `admob-compose/build.gradle.kts`: compose `runtime`, `ui`, `foundation`; `lifecycle-runtime-compose`; `lifecycle-viewmodel-compose`. Test: `ui-test-junit4`, `ui-test-manifest` (debug), robolectric, and turbine.

## 5. Implementation requirements
1. `AdBanner`:
   - `val activity = LocalContext.current.findActivity()`. If it's null → render nothing and log an error.
   - `val handle = remember(placement, activity, options) { CompanyAdMob.banner.create(activity, placement, options) }`
   - `DisposableEffect(handle) { onDispose { handle.destroy() } }`
   - `val state by handle.state.collectAsStateWithLifecycle()`, plus `LaunchedEffect(state) { onStateChange(state) }`
   - Layout: a `Box(modifier.fillMaxWidth().heightIn(min = reservedHeightDp))` containing the `AndroidView(factory = { handle.view.also { (it.parent as? ViewGroup)?.removeView(it) } }, update = {})` + the placeholder while `Loading`. Collapse to 0 height on Hidden/Failed if `options.reserveSpace` is false.
   - **The factory must never create a new AdView.** It returns `handle.view`.
2. `AdNative`:
   - `val result by rememberNativeAd(placement, slotKey)`
   - `Loaded` → an `AndroidView(factory = { ctx -> FrameLayout(ctx) }, update = { container -> if (container.tag !== handle) { CompanyAdMob.native.render(handle, container, layout, renderer); container.tag = handle } })`. This re-renders only when the handle changes, which a rotation causes because it's a new container but the same handle.
   - `Failed` → nothing, or the placeholder. Loading → the placeholder.
   - No `destroy` in the composable. The ViewModel owns it.
3. `rememberNativeAd`: `val retainer: NativeAdRetainer = viewModel(key = "company_admob_native")`. Then `retainer.get(placement, slotKey)` returns a `StateFlow<NativeAdResult?>`, loading on the first request only.
4. Recomposition must not reload, create duplicate views, duplicate listeners, or leak. Stability: mark the public parameter types `@Stable`/`@Immutable` where it's correct (`NativeAdLayout`, `BannerOptions` data classes are immutable; add the `compose-runtime-annotation` artifact dependency only if needed, and prefer a stability config file `compose_stability.conf` listing the core types, which requires no core dependency on Compose).
5. Every composable follows the Compose API guidelines: a `modifier` parameter first among the optionals, no side effects in composition, and state hoisting.

## 6. Public API requirements
03 §3.10 exactly. No Material types. No `View` types in public composable signatures.

## 7. Constraints
No Material/Material3. No Navigation dependency. No Hilt. `admob-core` gets no Compose annotations (use the stability config file instead).

## 8. Dependencies
Those listed in §4. Verify the latest stable versions through the BOM.

## 9. Tests (Compose UI tests on Robolectric, with `FakeAdsSdk` via a test runtime installed in `CompanyAdMob` using the `@VisibleForTesting` hook)
- `AdBannerTest`: recomposing 10× with changing unrelated state → `create` is called once (the fake counter). Removing it from composition → `destroy` is called once. A changed placement → the old one is destroyed and a new one created.
- `AdNativeTest`: first composition → one load. Recomposition ×10 → no new load. `StateRestorationTester`, or `ActivityScenario.recreate()` with `createAndroidComposeRule` → **no new load** and the same handle is re-rendered. Leaving the screen (removing it from the ViewModelStore) → destroyed.
- LazyColumn with 20 items and native every 5 with distinct `slotKey`s → retained ≤ 3, and the older ones are destroyed.
- `FullScreenAdLauncherTest`: calls resolve the Activity at call time, and no Activity is held after the composition is disposed (WeakReference).
- `rememberAdAvailability` reflects the fake state changes.

## 10. Validation commands
```
./gradlew :admob-compose:testDebugUnitTest :admob-compose:lint :admob-compose:apiDump build checkModuleDependencies
```

## 11. Acceptance criteria
- [ ] Tests prove no reloads on recomposition and no native reload on configuration change.
- [ ] Each AdView/NativeAd is created or loaded once per logical slot and destroyed once.
- [ ] No Material dependency, and `checkModuleDependencies` passes.
- [ ] The public API matches 03 §3.10.

## 12. Completion report
Per 00_WORKING_RULES §D, including how the Activity is resolved (`LocalActivity` vs `findActivity`) and the stability configuration used.
