# B24 — Publishing and release

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Publish the five billing artifacts with the existing Adroit release pipeline (Maven Central, `adroit.publishing`,
`centralBundle`), versioned together with Adroit. Verify a clean consumer can resolve and use them.

## Context
- `docs/RELEASE.md`;
- `docs/architecture/PUBLISHING_NOTES.md`;
- `.github/workflows`;
- the last release commits (`a3a2f75`, `dd47851`: Maven Central, configuration cache);
- `consumer-smoke/`.

## Prerequisites
B23.

## Work items
1. POM metadata for each billing module: name, description, Apache-2.0, and the same developer/SCM as the ads.
   Add the five billing modules to `publishedLibraries` in the root `build.gradle.kts`. B01 left them out on purpose,
   so a release before B24 doesn't ship empty billing artifacts.
2. `consumer-smoke`: add a billing consumer in an XML variant and a Compose variant that resolves from
   `mavenLocal()`, initializes, and compiles a paywall call.
3. Verify AAR metadata: `minCompileSdk` (the `consumerMinCompileSdk` policy), `minSdk 24`, no unexpected transitive
   dependencies (`./gradlew :adroit-billing:dependencies --configuration releaseRuntimeClasspath`, and confirm no
   Compose or Firebase).
4. Update the CI workflow so it builds and tests the billing modules and runs `apiCheck`.
5. Bump the version per `docs/RELEASE.md`, and move the CHANGELOG entry from Unreleased.
6. **Don't publish or push tags without the user's explicit go-ahead.** Prepare everything, then ask.

## Verify
```
./gradlew publishToMavenLocal
./gradlew -p consumer-smoke build
./gradlew centralBundle --configuration-cache
```

## Acceptance criteria
- [ ] Local publish yields correct POMs, sources and javadoc for five artifacts.
- [ ] The consumer smoke builds for XML and Compose.
- [ ] The CI config covers billing.
- [ ] The release is ready and awaiting the user's approval.

## Report
B00 §D.
