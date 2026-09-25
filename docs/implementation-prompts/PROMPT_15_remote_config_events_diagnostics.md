# PROMPT 15: Remote config overrides, event sinks, and diagnostics

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §21, §22, §23, §20.6, and `03_PUBLIC_API.md` §3.6–3.7.

## 1. Objective
Apply `AdConfigProvider` overrides at runtime, deliver events to host `AdEventListener`s reliably, and provide `DiagnosticsApi` (snapshot report, recent events, Ad Inspector).

## 2. Context
- `AdOverrides`, `AdConfigProvider`, and `StaticConfigProvider` already exist as types (Prompt 03). The `EventBus` exists (Prompt 02).
- Override precedence: `AD_FREE_USER` > remote `adsEnabled=false` > remote placement override > local config.
- Next-Gen Ad Inspector: `MobileAds.openAdInspector(...)`. **Verify the exact signature** (the reference shows `openAdInspector(listener)`; the guide shows `(context) { error -> }`). It only works on test devices, and it's always disabled in production builds by Google.

## 3. Files to create
- `admob-core/src/main/kotlin/com/company/admob/internal/config/OverridesApplier.kt`: collects `provider.overrides()` in the library scope (catching and logging provider exceptions; a provider failure keeps the last good overrides), validates them with `ConfigValidator` (invalid entries are dropped and logged, never partially applied per placement), and publishes `StateFlow<EffectiveConfig>`.
- `admob-core/src/main/kotlin/com/company/admob/internal/diagnostics/DiagnosticsApiImpl.kt` + `DiagnosticsReportBuilder.kt`
- Tests

## 4. Files to modify
- `EffectiveConfig.kt`: supports being recomputed with overrides. The engines (`FullScreenAdEngine`, `AppOpenController`, banner, and native) must read **the current** effective config at each decision point: collect it or read `StateFlow.value`. Audit each engine and fix any place that captured the config at construction.
- `InitCoordinator`/`Readiness`: remote `adsEnabled=false` → `Unavailable(DISABLED_BY_REMOTE)`, and back to Ready when it's re-enabled.
- `FrequencyGate`: accepts the overridden intervals and caps.
- `AdsClientImpl.kt`: wire `diagnostics`.

## 5. Implementation requirements
1. The ad unit ID override is allowed but validated (production must never accept demo IDs, and development still substitutes demo IDs). A changed ad unit for a placement → the old slot's cache is released, and the new slot loads per strategy.
2. A disabled format or placement → the cached ads for it are released, availability is Disabled, and banners and native handles for it go `Hidden(DISABLED_BY_REMOTE)`.
3. Events:
   - Listeners are called on main in registration order. Each is wrapped in try/catch with an error log naming the listener class.
   - `events` SharedFlow semantics are unchanged.
   - Add `AdEvent.Paid` completeness: currency, precision, and the source mapped from `AdSourceInfo`.
4. Diagnostics:
   - `snapshot()` covers the library version, Google SDK version (`MobileAds.getVersion()` through the adapter, which is callable before init per the reference), environment, `AdsState`, `ConsentState`, test mode info (demo IDs substituted? test device count), per-placement info (format, effective ad unit ID redacted in PRODUCTION, availability, cached `loadedAt` age, retry attempt, next retry in, frequency counters), the active full-screen owner, the current Activity **simple class name**, the last error, and the active overrides summary.
   - `toString()` is a human-readable multi-line report with **no** consent strings, no advertising ID, and no full ad unit IDs in PRODUCTION.
   - `recentEvents`: the last 100 from `EventBus` in DEVELOPMENT/STAGING, and an empty list in PRODUCTION (to avoid holding data).
   - `openAdInspector(activity, onClosed)`: delegates to the adapter and maps the error. In PRODUCTION it still calls the SDK (Google no-ops it for non-test devices), but log at INFO.
5. No new public types beyond 03 §3.7, except whatever 03 lacks for overrides (update 03 in the same change if so).

## 6. Public API requirements
`DiagnosticsApi`, `DiagnosticsReport`, `PlacementDiagnostics`, `TestModeInfo` (03 §3.7), and the `remoteConfig` config property (if Prompt 03 didn't add it).

## 7. Constraints
No Firebase. No networking. The provider is host-supplied.

## 8. Dependencies
None new.

## 9. Tests
- `OverridesApplierTest`: apply, then remove (emitting `AdOverrides()`, which restores the local values). Invalid override dropped. Provider throws → last good kept. A demo-ID override in PRODUCTION rejected. An ad unit change → the slot switches.
- Engine integration: remote disable → state Unavailable → re-enable → Ready, with the caches released and reloaded (fakes). A placement disabled → show returns `Disabled`.
- Frequency override applied to the next check.
- `EventListenerTest`: order, exception isolation, and the main thread.
- `DiagnosticsReportTest`: all the fields, and PRODUCTION redaction (asserting the full ad unit ID is absent from `toString()`).
- `openAdInspector` delegates to the fake and maps errors.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest :admob-core:lint apiDump build
```

## 11. Acceptance criteria
- [ ] Every engine reads the live effective config (the audit list is included in the report).
- [ ] Overrides can't bypass test-ID safety (tested).
- [ ] The diagnostics report is redacted in PRODUCTION (tested).
- [ ] Build, lint, tests, and apiCheck are green.

## 12. Completion report
Per 00_WORKING_RULES §D, plus the engine config-read audit table.
