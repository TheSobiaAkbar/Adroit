# S02 — One premium source: `Adroit.isAdFree` drives the splash

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it.

## Objective
Today, an app with both ads and IAP states premium twice:
- the bridge `AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) }` in `Application.onCreate`;
- `SplashFlow.create(app, SplashAd, isPremium = AdroitBilling.entitlements.value.adFree)` in the splash ViewModel.

Make `Adroit.setAdFree` the only source, so `SplashFlow` needs no premium argument.

## Files
1. `adroit-core/.../Adroit.kt`:
   - add `public val isAdFree: Boolean`, the last value passed to `setAdFree` (`requestedAdFree == true`), with
     KDoc;
   - add it to `AdsClient` too if the facade pattern requires it, with the `adroit-testing` fake updated.
2. `adroit-core/.../splash/SplashFlow.kt`, `create(...)`:
   - change the default to `isPremium: Boolean = Adroit.isAdFree`;
   - update the KDoc: "defaults to `Adroit.isAdFree`; apps with the billing bridge pass nothing".
3. Both samples' `SplashViewModel.kt`: drop the explicit `isPremium` argument and the billing import, if unused.
4. `docs/SPLASH.md`: one sentence on the new default.
5. `CHANGELOG.md`: one line.

## Edge cases
- `setAdFree` is never called (ads-only app with no premium): `isAdFree` is false and the behaviour is unchanged.
- The bridge emits a restored entitlement before the splash ViewModel is created: the splash sees it. The samples'
  `appScope` uses `Main.immediate`, so cached entitlements arrive in `Application.onCreate`. Verify this and report
  it.

## Tests
- `isAdFree` reflects `setAdFree` before and after `initialize`, and after `reset` if the tests have one.
- `SplashFlow` created without an argument after `setAdFree(true)` takes the premium path: no consent, no ad, the
  premium timer.

## Verify
```
./gradlew :adroit-core:testDebugUnitTest :adroit-core:apiDump :adroit-core:apiCheck
./gradlew :sample-xml:assembleDebug :sample-compose:assembleDebug
```

## Acceptance criteria
- [ ] Neither sample passes `isPremium` anywhere.
- [ ] Ads-only behaviour is unchanged (existing SplashFlow tests pass untouched).
- [ ] The `.api` diff is additions only.

## Report
S00 §E.
