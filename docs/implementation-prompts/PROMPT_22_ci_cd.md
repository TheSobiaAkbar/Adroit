# PROMPT 22: CI/CD (GitHub Actions)

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §28 and §31.

## 1. Objective
Automate build, lint, unit tests, API compatibility, dependency guards, minified sample builds, publishing verification, nightly instrumentation, and tag-driven releases.

## 2. Context
- The remote is GitHub (`TheSobiaAkbar/AdsPlugin`). Use GitHub Actions.
- JDK 17 on the runners (AGP requirement). The bytecode target stays 11.

## 3. Files to create
- `.github/workflows/ci.yml`: on `pull_request` and `push` to `master`/`main`:
  - `actions/checkout`, `actions/setup-java` (temurin 17), `gradle/actions/setup-gradle` (with the cache). Pin all actions to a major version, and **also pin third-party actions by commit SHA**.
  - `./gradlew build lint apiCheck checkModuleDependencies testDebugUnitTest verifyPublication :sample-xml:assembleRelease :sample-compose:assembleRelease :sample-mixed:assembleRelease :sample-mixed:assembleRelease -PwithMediation=true --stacktrace`
  - A "forbidden patterns" step: fail on `println(` in library sources, `android.util.Log` outside `AndroidAdLogger`, Google imports outside `internal/google`, and `2838681778929171` (the old real app ID) anywhere.
  - Upload lint HTML/SARIF reports and test reports as artifacts.
  - `concurrency` to cancel superseded runs.
  - `permissions: contents: read`
- `.github/workflows/instrumentation.yml`: `schedule` (nightly) + `workflow_dispatch`. Matrix API levels [24, 36]. Uses `reactivecircus/android-emulator-runner` (pinned SHA) with KVM enabled and an AVD cache. Runs `:sample-xml:connectedDebugAndroidTest :sample-compose:connectedDebugAndroidTest :sample-xml:connectedMinifiedDebugAndroidTest`. Tolerates the `@RequiresNetwork` flakiness with at most 1 retry.
- `.github/workflows/release.yml`: on `push` tags `v*`:
  - verify that the tag equals `v${companyAdmob.version}` and that the version isn't a SNAPSHOT
  - verify that `CHANGELOG.md` contains a `## [x.y.z]` section
  - run the full CI command set
  - `./gradlew publishAllPublicationsToGitHubPackagesRepository` (env `GITHUB_TOKEN` with `packages: write`)
  - optionally publish to the company repository when `COMPANY_MAVEN_URL` exists
  - create a GitHub Release with `softprops/action-gh-release` (pinned SHA), using the CHANGELOG section as the body
- `.github/dependabot.yml`: gradle + github-actions weekly. **Ignore** `com.google.android.libraries.ads.mobile.sdk:*` and `com.google.android.ump:*` (manual upgrade checklist).
- `.github/pull_request_template.md`: checklist (API dump reviewed, CHANGELOG updated, docs updated, samples verified).

## 4. Files to modify
None besides the above, plus `README.md` badges (only if README exists; otherwise Prompt 23 adds them).

## 5. Implementation requirements
1. Each workflow runs with least-privilege permissions.
2. Secrets are used only through `secrets.*`. Nothing is echoed.
3. Gradle configuration cache is enabled in CI if the build supports it.
4. The workflows must be valid YAML. Validate them locally with `actionlint` if it's available, or run them through a PR if possible, and report the result.

## 6. Public API requirements
None.

## 7. Constraints
Don't add paid services. Don't publish on PRs.

## 8. Dependencies
GitHub Actions only.

## 9. Tests
Open a draft PR (only if the user approves pushing; otherwise validate with `actionlint` plus `act` if available, and report that it wasn't run remotely).

## 10. Validation commands
```
actionlint .github/workflows/*.yml   # if installed
./gradlew build lint apiCheck checkModuleDependencies testDebugUnitTest verifyPublication
```

## 11. Acceptance criteria
- [ ] The three workflows plus dependabot and the PR template exist.
- [ ] The CI command set passes locally.
- [ ] The release workflow has a tag/version/CHANGELOG guard.
- [ ] Third-party actions are pinned by SHA.
- [ ] The remote runs were verified, or reported as not verified with the reason.

## 12. Completion report
Per 00_WORKING_RULES §D.
