# PROMPT 05: Consent coordinator (UMP) and PrivacyApi

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §11 (the sequence diagram), §30, and `01_SDK_RESEARCH.md` §1.6.

## 1. Objective
Implement `ConsentCoordinator` (internal) and `PrivacyApi` (public) exactly as Google documents the UMP flow, so that the SDK init coordinator (Prompt 06) is triggered **only** when `canRequestAds()` is true, and never twice.

## 2. Context
Official flow (Google, UMP 4.0.0):
1. `requestConsentInfoUpdate` on **every** launch.
2. After it succeeds, `canRequestAds()` may already be true from a previous session. Start SDK init **in parallel** at that point.
3. `loadAndShowConsentFormIfRequired(activity)`. The callback fires immediately if no form is required.
4. Check `canRequestAds()` again.
5. On an update error, **still** check `canRequestAds()` (it falls back to the previous session's consent).
6. Prevent double init with an atomic flag.
7. `canRequestAds()` is always false before the update call.
8. Never cache consent yourself.
9. The privacy options entry is required when `privacyOptionsRequirementStatus == REQUIRED`.

## 3. Files to create
- `admob-core/src/main/kotlin/com/company/admob/internal/consent/ConsentCoordinator.kt`
- `admob-core/src/main/kotlin/com/company/admob/internal/consent/PrivacyApiImpl.kt`
- `admob-core/src/main/kotlin/com/company/admob/internal/consent/RdpWriter.kt`: writes or removes `gad_rdp` in `PreferenceManager.getDefaultSharedPreferences` equivalent. **Don't add `androidx.preference`.** Use `context.getSharedPreferences(context.packageName + "_preferences", MODE_PRIVATE)` and verify that this matches the default-preferences file name used by `PreferenceManager.getDefaultSharedPreferences` (the Next-Gen SDK reads from the default preferences). Document the verification in KDoc.
- Tests under `admob-core/src/test/kotlin/com/company/admob/internal/consent/`

## 4. Files to modify
- None of the public API files except where `PrivacyApi` needs refinement. If a signature changes from 03_PUBLIC_API, update that doc in the same change and report it.

## 5. Implementation requirements
1. `ConsentCoordinator(consentSdk, config: EffectiveConfig, log, events, onCanRequestAds: () -> Unit)`:
   - `gather(activity, onFinished: (ConsentState) -> Unit)`. If a gather is already in flight, **attach** the new callback to it instead of starting a second one. This handles a configuration change during the form.
   - Sequence: `requestUpdate` → on success, if `canRequestAds` then `onCanRequestAds()` (early init) → `loadAndShowFormIfRequired` → re-check → `onCanRequestAds()` if true → publish `ConsentState` and call `onFinished`. On update failure: record `lastError`, check `canRequestAds` (previous-session consent), and finish without showing a form.
   - `onCanRequestAds` may be called up to twice; Prompt 06's `InitCoordinator` guards it with an `AtomicBoolean`. The coordinator still dedupes its own calls (calls it at most once per gather).
   - Doesn't hold the `Activity` after the SDK callbacks return. Assert this in a test with a `WeakReference` + GC hint, or by checking field nulling.
2. `ConsentParams` derived from config:
   - `debugGeography` and `testDeviceHashedIds` only when `environment != PRODUCTION`. `ConfigValidator` already rejects them in PRODUCTION; still guard here.
   - `tagUnderAgeOfConsent = (ageTreatment == CHILD)`. **Verify that UMP 4.0.0 `ConsentRequestParameters.Builder` still exposes an under-age-of-consent setter, and report its exact name.** If it's gone, drop the param and document it.
   - `consentSyncId` if configured. Add `privacy { consentSyncId = … }` to the config if it isn't there yet.
3. `RdpWriter.apply(restrictedDataProcessing)` runs **before** SDK init (Prompt 06 calls it), writing `gad_rdp = 1` or removing the key.
4. `PrivacyApiImpl`:
   - `consentState: StateFlow<ConsentState>`, updated after every step
   - `isPrivacyOptionsRequired: StateFlow<Boolean>`
   - `showPrivacyOptions(activity, onDismissed)`: if not required, call back with `null` and log at info. Otherwise show the form, then re-evaluate `canRequestAds`. If it becomes true → `onCanRequestAds()`. If it becomes false → invoke `onConsentRevoked()` (Prompt 06 moves the state to `Unavailable(CONSENT_NOT_OBTAINED)`, destroys caches, and hides views). Emit `AdEvent.ConsentUpdated`.
   - `resetConsentForTesting()`: allowed only when `environment != PRODUCTION`. Otherwise log an error and do nothing.
5. Every callback runs on main. All state lives on main.
6. Log every step at DEBUG, with no consent strings. Never read or log TC/GPP strings.

## 6. Public API requirements
`PrivacyApi`, `ConsentState`, and `ConsentStatus` as in 03 §3.5. Any `privacy { consentSyncId }` config addition goes in the dump.

## 7. Constraints
- No SDK init here; only the callback hook.
- No consent caching in SharedPreferences, except `gad_rdp`, which is an SDK input and not a cache.

## 8. Dependencies
None new.

## 9. Tests (with `FakeConsentSdk`)
- A first launch in the EEA: consent required, form shown, obtained → `onCanRequestAds` called once.
- A first launch where consent isn't required → the form callback fires immediately → init.
- A returning user with prior consent: `canRequestAds` is true right after the update → init starts **before** the form step, and it isn't called again after the form.
- The user declines, or closes with no consent → `canRequestAds` false → no init, status REQUIRED/UNKNOWN, `Unavailable` signalled.
- An update error with prior consent → init. An update error without prior consent → no init, and `lastError` is set.
- A form error → `canRequestAds` re-check.
- Rotation: a second `gather` during the in-flight form attaches, both callbacks fire once, and the SDK is called once.
- Privacy options: not required → no-op. Required → shown → revoked → `onConsentRevoked`. Granted later → `onCanRequestAds`.
- PRODUCTION ignores debug geography and test hashed IDs.
- `resetConsentForTesting` is a no-op in PRODUCTION.
- `RdpWriterTest` (Robolectric): writes and removes the key in the default preferences file.

## 10. Validation commands
```
./gradlew :admob-core:testDebugUnitTest --tests "com.company.admob.internal.consent.*"
./gradlew :admob-core:lint apiCheck build
```

## 11. Acceptance criteria
- [ ] Every scenario in §9 has a passing test.
- [ ] It's impossible for `onCanRequestAds` to fire while `canRequestAds()` is false (asserted in every test via the fake).
- [ ] The Activity isn't retained after completion.
- [ ] The UMP under-age parameter status has been verified and reported.

## 12. Completion report
Per 00_WORKING_RULES §D, plus a scenario → test mapping table.
