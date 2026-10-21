# Releasing Adroit

For library maintainers. Publishing mechanics (repositories, credentials, signing) are in
[architecture/PUBLISHING_NOTES.md](architecture/PUBLISHING_NOTES.md).

## Versioning

Semantic Versioning, with these rules ([architecture §31](architecture/02_ARCHITECTURE.md)):

- **MAJOR:** a public API break, or a raised consumer requirement (minSdk, the Kotlin version consumers need, the Java
  bytecode level).
- **MINOR:** new API, or a Google SDK upgrade with no consumer-visible change.
- **PATCH:** fixes only.
- `0.x` while the API stabilized. `1.0.0-rc1` froze the API (PROMPT 27); `1.0.0` follows the final
  production audit. Pre-releases are `x.y.z-rcN`, and the rules below apply to them already.

All modules are released together with one version, `adroit.version` in `gradle.properties`.

## API stability

These rules hold from `1.0.0-rc1` ([audits/API_STABILITY_AUDIT.md](audits/API_STABILITY_AUDIT.md)). The committed
`api/*.api` dumps are the baseline: `apiCheck` fails on any change to them, so every public change is a reviewed
`apiDump` diff in the same pull request.

- **Deprecation:** an API is deprecated in a minor version N with `@Deprecated(message, ReplaceWith(...))` at level
  WARNING, and a CHANGELOG "Deprecated" entry. It stays at least until the next major version, where it may be
  removed (it may be raised to ERROR in a later minor first). Never remove or change it in a minor or patch version.
- **Default arguments:** never add a parameter to an existing public function or constructor, even with a default;
  that changes its JVM signature. Add an overload (or a property on an options class such as `BannerOptions`) and keep
  the old signature. Public constructors and top-level functions with default arguments carry `@JvmOverloads`;
  interface members can't, so Java passes every argument there.
- **Interfaces the library implements** (`AdsClient`, the format APIs, `BannerAdHandle`, `NativeAdHandle`,
  `FullScreenAdLauncher`): members may be added in minor versions, always with a default implementation. The libraries
  compile with `jvmDefault = NO_COMPATIBILITY`, so these are real Java default methods and an existing implementation
  (a host's hand-written fake) still links.
- **Interfaces hosts implement** (`AdLogger`, `AdEventListener`, `AdConfigProvider`, `NativeAdRenderer`,
  `GoogleRequestCustomizer`) stay `fun interface`s with one abstract member. New members need a default.
- **Sealed types and enums** say in their KDoc whether they're closed until the next major version (an exhaustive
  `when` is safe: `FullScreenResult`, `RewardResult`, `NativeAdResult`, `AdEnvironment`, `LogLevel`,
  `MaxAdContentRating`, `CollapsibleBannerPosition`) or may gain values in a minor version (include an `else`
  branch: `AdError`, `AdEvent`, `AdsState`, `AdAvailability`, `AdPlacement`, `BannerSize`, `BannerState`,
  `NativeAdHandleState`, `AdFormat`, `UnavailableReason`, `ConsentStatus`, `RevenuePrecision`, `AgeTreatment`,
  `DebugGeography`, `PreloadStrategy`, `NativeStyle`, `FirebaseAdEvent`, adroit-testing's `FakeCall`).
- **Data classes** only for value types that won't gain properties (ADR-011). A class that may grow is a regular
  class, and a new property comes with a new constructor overload; the old constructor stays.
- **New surfaces** that aren't settled yet are marked `@ExperimentalAdroitApi` (opt-in, warning) and are exempt
  from these rules until the marker is removed. Google-typed escape hatches are `@GoogleSdkEscapeHatch` and may change
  in any release.
- **Java** (ADR-013): the facade, `start`, every show, banner and native call, privacy, diagnostics and the
  configuration builder (including `profiles(context, resId)`) are callable from Java. `kotlin.time.Duration`
  options are Kotlin-only (Java sets timings in the ads file), except `showOnColdStart(activity, maxWaitMillis, …)`.
  Callbacks are Kotlin function types: a Java lambda returns `Unit.INSTANCE`. `adroit-core`'s
  `JavaFacadeCalls.java` test source compiles this surface.
- **Consumer requirements** (a MAJOR change to raise): minSdk 24, Java 11 bytecode, Kotlin 2.1, compileSdk 34 and
  AGP 8.1.1 (compileSdk 37 and AGP 9.1.0 with `adroit-compose`, set by Compose 1.12), as in the README's "Supported
  versions". `consumerMinCompileSdk` in `gradle/libs.versions.toml` is what the AARs ask for; the consumer-smoke matrix
  checks the rest.

## Release checklist

1. `master` is green in CI, and last night's instrumentation run passed.
2. The API dumps are reviewed: `./gradlew apiCheck` passes, and any `.api` change since the last release matches the
   version bump.
3. `CHANGELOG.md`: move `[Unreleased]` into `## [x.y.z] - YYYY-MM-DD`, and update the compare links.
4. Set `adroit.version=x.y.z` (no `-SNAPSHOT`) in `gradle.properties`.
5. Update the "Supported versions" table in the README if a requirement changed, and the "Last verified" lines if you
   re-verified the docs.
6. Run locally: `./gradlew build lint apiCheck checkModuleDependencies testDebugUnitTest verifyPublication` and the
   consumer smoke build (`./gradlew publishToMavenLocal`, then `./gradlew -p consumer-smoke assembleDebug`).
7. Commit ("chore: release x.y.z"), then tag and push the tag (below).
8. After the release, set the next `-SNAPSHOT` version and add an empty `## [Unreleased]`.

## Google SDK upgrade checklist

Upgrades of `ads-mobile-sdk` and `user-messaging-platform` never come from a bot (Dependabot ignores them):

1. Read the Next-Gen [release notes](https://developers.google.com/admob/android/next-gen/rel-notes) and
   [deprecation schedule](https://developers.google.com/admob/android/next-gen/deprecation), and the UMP
   [release notes](https://developers.google.com/admob/android/privacy/release-notes), for every version skipped.
2. Update `docs/architecture/01_SDK_RESEARCH.md`: new APIs, deprecations, behaviour changes, transitive dependencies.
3. Bump the versions in `gradle/libs.versions.toml`, and fix anything that moved inside `adroit-core`'s
   `internal/google` package (the only code that touches Google's APIs). Verify changed signatures with `javap`.
4. Run the full CI command set, and the instrumentation workflow (manually, `workflow_dispatch`) on API 24 and 36.
5. The API dumps must be unchanged: a Google upgrade must never change Adroit's public API.
6. Check `docs/architecture/R8_REPORT.md`'s findings still hold (minified samples launch).
7. Check the new resolved runtime classpath against the GitHub Advisory Database, and update the dependency findings
   in `docs/audits/SECURITY_PRIVACY_AUDIT.md` (check 15).
8. Add a "Google SDK" section to the CHANGELOG entry, and update the "Last verified against" lines and the README table.

## Tag and publish

```bash
git tag v0.1.0
git push origin v0.1.0
```

The `release.yml` workflow then:
1. checks that the tag is exactly `v` + `adroit.version`, that the version isn't a snapshot, and that
   `CHANGELOG.md` has a non-empty `## [x.y.z]` section;
2. runs the full CI checks;
3. publishes the signed bundle to Maven Central (`./gradlew centralBundle`, then the Publisher API);
4. creates the GitHub Release, with the CHANGELOG section as its notes.

If the workflow fails before publishing, fix the problem, delete the tag (`git push --delete origin vX.Y.Z`), and tag
again. Once published, never republish a version: release a new patch.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
