# S05 — Publishing, R8 and consumer-smoke for the new modules

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it.

## Objective
Make `adroit-remote-config` and `adroit-update` release-ready, exactly like the existing modules.

## Files
1. **Publishing:** add both modules to the publishing setup the billing modules use, as artifacts
   `io.github.thesobiaakbar:adroit-remote-config` and `:adroit-update`. Follow `docs/RELEASE.md` and the B24 changes
   (POM, version, signing, any BOM or version-alignment file).
2. **R8:** add `consumer-rules.pro` only if a rule is needed. Firebase and Play ship their own rules. Don't copy
   them.
3. **consumer-smoke** (its own build, with its own `settings.gradle.kts`):
   - make sure it still builds now that the Firebase modules depend on `adroit-remote-config`;
   - add `adroit-update` with one call to `AdroitUpdate.check`, so the published artifact is resolved and shrunk in
     a release build.
4. **CI workflow:** the new modules are included in the build/test/apiCheck tasks. Edit the workflow only if it lists
   modules by name.
5. `docs/RELEASE.md`: list the two new artifacts.

## Verify
```
./gradlew publishToMavenLocal
./gradlew :sample-xml:assembleRelease :sample-compose:assembleRelease
```
Then the consumer-smoke build per `docs/RELEASE.md` (release variant).

## Acceptance criteria
- [ ] Both new artifacts publish to mavenLocal with correct POM dependencies (`adroit-firebase` →
      `adroit-remote-config` is `compile` scope, since it's `api`).
- [ ] Both samples' release builds succeed with R8 on.
- [ ] consumer-smoke release builds against the mavenLocal artifacts.

## Report
S00 §E.
