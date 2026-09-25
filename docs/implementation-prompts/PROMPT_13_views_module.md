# PROMPT 13: `admob-views` module (XML / View / Fragment integration)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §7 (the views row), §16, §17, §19, and `03_PUBLIC_API.md` §3.9.

## 1. Objective
Give View-based apps (Activities, Fragments, ViewBinding) one-line, lifecycle-safe banner and native integration, plus reusable, brand-neutral native templates, **without** adding Fragment, AppCompat, or Material dependencies.

## 2. Context
- The core already has `BannerApi.create`, `BannerAdHandle.bindTo`, `NativeApi.load/render`, and `NativeAdHandle`.
- Fragment support comes through `LifecycleOwner` (`viewLifecycleOwner`) and `findViewTreeLifecycleOwner()`.
- All resources use the `company_admob_` prefix, enforced by `resourcePrefix`.

## 3. Files to create (`admob-views/src/main/`)
- `kotlin/com/company/admob/views/AdBannerView.kt`: a `FrameLayout`. Its XML attrs: `company_admob_placement` (string key), `company_admob_bannerSize` (enum: anchoredAdaptive | inlineAdaptive | mediumRectangle | standard), `company_admob_inlineMaxHeight` (dimension), and `company_admob_collapseWhenEmpty` (boolean, default true: `GONE` when Hidden or Failed). It resolves the placement by key through an internal core lookup (add `internal`-to-library access: create a small **public-but-`@RestrictTo(LIBRARY_GROUP)`** hook in core, `CompanyAdMobInternal.placementByKey(key)`, and justify it; or use `@InternalCompanyAdMobApi` opt-in. Choose one, and document it as ADR-012). In `onAttachedToWindow`: `findViewTreeLifecycleOwner()` → create the handle → `bindTo(owner)`. Unknown key or no owner → log an error and stay `GONE`. `setPlacement(placement)` for programmatic use.
- `kotlin/com/company/admob/views/BannerExtensions.kt`: `ViewGroup.attachBanner(owner, placement, options): BannerAdHandle`. It resolves the Activity from the container context (unwrapping `ContextWrapper`), creates the handle, adds `handle.view`, binds to `owner`, and applies collapse-when-empty through the state collection (`owner.lifecycleScope`, `repeatOnLifecycle(STARTED)`).
- `kotlin/com/company/admob/views/AdNativeView.kt`: a `FrameLayout` with attrs `company_admob_placement`, `company_admob_template` (small | medium | large), and `company_admob_layout` (a reference to a custom layout; custom layouts **must use the standard template ids**, documented).
- `kotlin/com/company/admob/views/NativeExtensions.kt`: `ViewGroup.attachNativeAd(owner, placement, layout, renderer, onResult)`. It loads, renders, destroys the handle on `ON_DESTROY`, and hides on failure.
- `kotlin/com/company/admob/views/NativeAdHolder.kt`: for ViewModels. It keeps one handle across configuration changes. `bindTo(container, owner, layout)` renders the existing handle, or loads if it has none. `clear()` destroys it. It holds **no View or Context references** between binds (it clears on owner destroy).
- `kotlin/com/company/admob/views/NativeTemplates.kt`: `Small`, `Medium`, and `Large` `NativeAdLayout`s referencing the resources below.
- `kotlin/com/company/admob/views/internal/ContextExt.kt`: `Context.findActivity()`
- `res/layout/company_admob_native_small.xml`, `company_admob_native_medium.xml`, `company_admob_native_large.xml`: framework widgets only (`LinearLayout`/`FrameLayout`, `TextView`, `ImageView`, `Button`, `RatingBar`). Include an "Ad" attribution `TextView` (`@+id/company_admob_native_attribution`). Leave space for AdChoices at top-end. The media container is a `FrameLayout` with a 16:9 min-height (medium/large). IDs: `company_admob_native_headline/body/cta/icon/media/advertiser/stars/price/store/attribution`.
- `res/values/company_admob_attrs.xml` (declare-styleable for both views + theme attrs for template styling: `company_admob_nativeBackground`, `company_admob_nativeCtaBackground`, `company_admob_nativeTextAppearanceHeadline`, …), `res/values/company_admob_styles.xml`, `res/values/company_admob_colors.xml`, `res/values/company_admob_dimens.xml`, and `res/values/company_admob_strings.xml` (`company_admob_ad_attribution` = "Ad"; translatable, so add at least `values-es` and `values-de` placeholders only if the company has translation support, otherwise mark it `translatable="true"` and note it in docs).

## 4. Files to modify
- `admob-core`: the internal placement lookup hook (ADR-012), and nothing else.
- `admob-views/build.gradle.kts`: `api(project(":admob-core"))`, `implementation(libs.androidx.lifecycle.runtime.ktx)` (for `lifecycleScope`/`repeatOnLifecycle`). Verify that it's not already transitive.

## 5. Implementation requirements
1. Everything is lifecycle-safe: views created in `Fragment.onViewCreated` bound to `viewLifecycleOwner` are destroyed in `onDestroyView`, not `onDestroy`. Test this with a real Fragment in the test source set only (add `androidx.fragment:fragment-testing` as **testImplementation**; that's allowed).
2. Recreating a Fragment view (back stack pop/push) → the old handles are destroyed and new ones created. No duplicates.
3. `AdBannerView` in a `RecyclerView` is **not** supported for banners (it's documented; banners belong in fixed containers). Native in RecyclerView: document the pattern of using a `NativeAdHolder` per ad position in the adapter's owner ViewModel. Provide a sample in Prompt 17.
4. Templates work in light and dark themes (use `?android:attr/textColorPrimary` and friends) and in RTL (`start`/`end`).
5. Accessibility: the CTA is a `Button`, with content descriptions for the icon (`null`, decorative) and media.
6. No `androidx.fragment` in `implementation`/`api`. Test only.

## 6. Public API requirements
Exactly 03 §3.9, plus any hook from ADR-012 marked restricted or internal-opt-in.

## 7. Constraints
No Compose. No AppCompat/Material. No `findViewById` outside the binder/template code.

## 8. Dependencies
`lifecycle-runtime-ktx` (implementation), and `fragment-testing` + `robolectric` + `androidx-test-core` (test).

## 9. Tests (Robolectric)
- `AdBannerViewInflationTest`: inflate from XML with attrs, attach to an Activity → the handle is created and bound. Unknown key → GONE with no crash. Detach/destroy → destroyed.
- `AttachBannerFragmentTest`: a Fragment with `viewLifecycleOwner` → `onDestroyView` destroys it. Replacing the fragment and popping back → one live handle.
- `AttachNativeAdTest`: load success renders the template (headline text set). Failure → GONE. Destroy on owner destroy.
- `NativeAdHolderTest`: rotation (`ActivityScenario.recreate()`) → the same handle is re-rendered with no new load (fake load counter) → `clear()` destroys it.
- `NativeTemplatesTest`: each template inflates and contains every id declared in its `NativeAdLayout` (reflection-free: `findViewById` for each id).
- `ResourcePrefixTest`: none needed. Lint enforces it. Make sure `lint` passes.

## 10. Validation commands
```
./gradlew :admob-views:testDebugUnitTest :admob-views:lint :admob-views:apiDump build checkModuleDependencies
```

## 11. Acceptance criteria
- [ ] Fragment view lifecycle correctness is proven by tests.
- [ ] Native survives rotation via `NativeAdHolder` without reloading (proven).
- [ ] Templates are brand-neutral, themeable, RTL-safe, and include attribution.
- [ ] No forbidden dependency (`checkModuleDependencies` passes).
- [ ] ADR-012 is recorded.

## 12. Completion report
Per 00_WORKING_RULES §D, plus screenshots (Robolectric `captureRoboImage` if available, otherwise skip and say so) of the three templates.
