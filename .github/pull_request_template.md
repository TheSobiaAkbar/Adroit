## What and why

<!-- What changes, and why. Link the issue if there is one. -->

## Checklist

- [ ] **API dump reviewed:** `./gradlew apiCheck` passes, and any `.api` change (from `./gradlew apiDump`) is intended and
      explained above. Breaking changes need a MAJOR version (docs/architecture/02_ARCHITECTURE.md §31).
- [ ] **CHANGELOG updated** under `## [Unreleased]`, including a "Google SDK" note for Google SDK upgrades.
- [ ] **Docs updated:** KDoc for public API, and `docs/` when behaviour or setup changes.
- [ ] **Samples verified:** the affected sample screens still work; instrumented tests run on a device or emulator
      when ads, lifecycle or navigation code changed.
- [ ] No real ad unit or app IDs, credentials or secrets in the change.
