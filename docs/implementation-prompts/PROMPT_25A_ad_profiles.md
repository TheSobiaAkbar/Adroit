# PROMPT 25A: Ad profiles (one JSON per ad, controlled from Firebase)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `docs/architecture/05_AD_PROFILES.md`
(the design; it wins where this prompt is shorter), and `02_ARCHITECTURE.md` §15, §16, §17, §20, §21 and ADR-017.

## 1. Objective
Let an app define every ad in one JSON file, `res/raw/ads_config.json`. Each ad's settings can then be changed from
a Firebase Remote Config parameter named after it:
- on or off;
- ad unit IDs rotated one after another;
- a counter, a timer, and a per-session limit;
- the banner and native style.

Add an overall interstitial limit per session that skips the splash interstitial, and a splash interstitial show.
Debug builds keep using Google's test ads through `AdEnvironment.DEVELOPMENT`.

## 2. Context
- `admob-firebase` hasn't been released (it's under `[Unreleased]`), so its `company_admob_*` keys are replaced,
  not kept.
- `org.json` is part of Android: the parser adds no dependency. Parse off the main thread only if it's measurably
  slow. It's one small file, parsed once at `initialize` (PROMPT 26 measures it).
- Collapsible banners: `BannerAdRequest.Builder.setGoogleExtrasBundle(Bundle().apply { putString("collapsible",
  "bottom" or "top") })`. Verify it against the Next-Gen reference before use.

## 3. Work items
1. **Config model (core):**
   - `PlacementConfig.adUnitIds: List<AdUnitId>`, with `adUnitId` kept as the first entry.
   - The placements DSL accepts one or more IDs.
   - `FrequencyRule` gains `counter`.
   - Settings gain `maxInterstitialsPerSession` (0 = unlimited).
   - Interstitial placements gain `splash` and `splashTimeout`.
   - Banner placements take a style (`BannerSize.Collapsible(position)` added); native placements get a style
     (`NativeStyle.SMALL`/`MEDIUM`/`LARGE`).
   - `PlacementOverride` and `AdOverrides` carry the same fields.
   - Placement keys are widened to `[A-Za-z0-9_]{1,40}`.
   - `ConfigValidator` covers every new field, including a list with no valid ID and a demo ID in PRODUCTION.
2. **Profile parser (core, internal):**
   - Parses the file (settings plus ads) and one Firebase value per key, with the rules in design §3.3: unknown
     fields ignored, an invalid field dropped with a warning, a format mismatch ignoring the whole value.
   - `adMobConfig { profiles(@RawRes id) }` builds placements and settings from the file at `initialize`. Reading or
     parsing fails → `InvalidConfiguration` (fail fast outside PRODUCTION, as today).
   - The Firebase module reaches the parser through a `CompanyAdMobInternal` hook (ADR-012 pattern).
3. **Rotation:**
   - The full-screen slot and the native pool are keyed by (format, ID list), and each load uses the next ID
     (design §4.1).
   - The banner handle takes the next ID on each new load.
   - Positions are in memory only.
4. **Counter and overall limit:**
   - In `FrequencyGate`, with the check order in design §4.3.
   - New `AdError.CounterNotReached(remaining: Int)`.
   - Only an actual showing counts or resets.
   - Splash placements skip the overall limit and the counter.
5. **`showOnSplash`** on `InterstitialApi` (design §4.4), modelled on `AppOpenApi.showOnColdStart`: wait for
   Ready and a loaded ad up to the timeout, show or complete with `NotShown`, exactly one result.
6. **Styles:**
   - The banner size and native template come from the placement's effective style when the call site passes none,
     in `AdBanner`, `AdBannerView` and `attachBanner`, and in `AdNative`, `AdNativeView` and `NativeAdHolder`.
   - The collapsible extra is sent on the first load of each banner view only.
   - Fix the recorded gap: the placement-level banner size is currently ignored.
7. **admob-firebase:**
   - `FirebaseRemoteConfigProvider` reads a parameter for each ad key in the effective configuration, plus the
     settings parameter (`AdSettings` by default, configurable).
   - It emits on activation and real-time updates, as now.
   - Remove the old keys, and update ADR-017 and the CHANGELOG.
8. **Samples:** sample-xml moves to `res/raw/ads_config.json` (local only, no Firebase), including a rotation list,
   a counter ad, a splash interstitial, a collapsible banner and a native style. The docs snippets show the Firebase
   provider.
9. **Docs:**
   - New `docs/AD_PROFILES.md`: every field, the check order, examples, and how to set up the Firebase parameters.
   - Update CONFIGURATION, REMOTE_CONFIG, INTERSTITIAL, BANNER, NATIVE and MIGRATION.
   - The design becomes ADR-018 (Accepted).
   - Every new doc passes `check-docs.mjs`.

## 4. Tests (at least)
- **Parser:** every field, the defaults, unknown fields, wrong types, a format mismatch, empty or invalid ID lists,
  and a Firebase value merging over the file.
- **Rotation:** loads cycle through the list, a retry moves to the next ID, a changed list restarts at its first ID,
  and two placements with the same list share a position.
- **Counter:** not due, due but not ready (stays due), reset after a show, and the timer ignored when a counter is
  set.
- **Overall limit:** caps non-splash interstitials, wins over a higher per-ad limit, ignores splash ads, and 0
  means unlimited.
- **`showOnSplash`:** ready in time, timeout, consent not given, and exactly one result.
- **Styles:** the banner size and native template come from the style; a call-site value wins; the collapsible
  extra is sent on the first load only (checked with the fake SDK's recorded requests).
- **Firebase:** provider tests with the fake source, one parameter per key.
- **Device:** sample-xml's instrumented tests still pass, plus a new one: rotation and a collapsible banner on the
  Pixel with test ads.

## 5. Validation commands
The full CI command set (ci.yml), `apiDump` with the `.api` diff reviewed line by line, and the device run above.

## 6. Acceptance criteria
- [ ] One `ads_config.json` plus the one-call setup configures every ad; debug builds show only test ads.
- [ ] Each ad's Firebase parameter can turn it off, and change its IDs, counter, timer, per-session limit and style,
      without a new release.
- [ ] ID rotation, the counter (with priority over the timer), the overall interstitial limit (splash excluded, and
      winning over per-ad limits) and `showOnSplash` behave as in design §4, with tests.
- [ ] Collapsible, rectangle, standard and adaptive banners, and small, medium and large native ads, are selectable
      by style.
- [ ] The `.api` diff contains only the additions listed in design §5.
- [ ] The CI command set is green, and the device tests pass.

## 7. Completion report
Per 00_WORKING_RULES §D.
