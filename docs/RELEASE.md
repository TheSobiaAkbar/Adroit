# Releasing CompanyAdMob

For library maintainers. Publishing mechanics (repositories, credentials, signing) are in
[architecture/PUBLISHING_NOTES.md](architecture/PUBLISHING_NOTES.md).

## Versioning

Semantic Versioning, with these rules ([architecture §31](architecture/02_ARCHITECTURE.md)):

- **MAJOR:** a public API break, or a raised consumer requirement (minSdk, the Kotlin version consumers need, the Java
  bytecode level).
- **MINOR:** new API, or a Google SDK upgrade with no consumer-visible change.
- **PATCH:** fixes only.
- `0.x` while the API stabilizes inside the company; `1.0.0` after the final production audit.

All modules are released together with one version, `companyAdmob.version` in `gradle.properties`.

## Release checklist

1. `master` is green in CI, and last night's instrumentation run passed.
2. The API dumps are reviewed: `./gradlew apiCheck` passes, and any `.api` change since the last release matches the
   version bump.
3. `CHANGELOG.md`: move `[Unreleased]` into `## [x.y.z] - YYYY-MM-DD`, and update the compare links.
4. Set `companyAdmob.version=x.y.z` (no `-SNAPSHOT`) in `gradle.properties`.
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
3. Bump the versions in `gradle/libs.versions.toml`, and fix anything that moved inside `admob-core`'s
   `internal/google` package (the only code that touches Google's APIs). Verify changed signatures with `javap`.
4. Run the full CI command set, and the instrumentation workflow (manually, `workflow_dispatch`) on API 24 and 36.
5. The API dumps must be unchanged: a Google upgrade must never change CompanyAdMob's public API.
6. Check `docs/architecture/R8_REPORT.md`'s findings still hold (minified samples launch).
7. Add a "Google SDK" section to the CHANGELOG entry, and update the "Last verified against" lines and the README table.

## Tag and publish

```bash
git tag v0.1.0
git push origin v0.1.0
```

The `release.yml` workflow then:
1. checks that the tag is exactly `v` + `companyAdmob.version`, that the version isn't a snapshot, and that
   `CHANGELOG.md` has a non-empty `## [x.y.z]` section;
2. runs the full CI checks;
3. publishes to GitHub Packages, and to the company repository when its secrets are configured;
4. creates the GitHub Release, with the CHANGELOG section as its notes.

If the workflow fails before publishing, fix the problem, delete the tag (`git push --delete origin vX.Y.Z`), and tag
again. Once published, never republish a version: release a new patch.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
