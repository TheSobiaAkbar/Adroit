# PROMPT 01: Repository, Gradle, and build-logic foundation

You are working in the CompanyAdMob repository (an Android AdMob library). First read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it exactly. Then read `docs/architecture/02_ARCHITECTURE.md` §5–§8, §20.4, §27, §31 and `docs/architecture/01_SDK_RESEARCH.md` §1.2, §1.8.

## 1. Objective
Turn the single-module Android Studio template into the multi-module library workspace defined in the architecture. The result is empty but compiling library modules, a convention-plugin build, and API/dependency guards. **Write no ad logic.**

## 2. Context
- The repo today: `:app` (package `com.shark.adplugin`, AppCompat + XML template), `gradle/libs.versions.toml` (AGP 9.1.1, legacy `play-services-ads` 25.5.0), Gradle 9.3.1, and AGP built-in Kotlin (there is no `kotlin-android` plugin, but Kotlin compiles). `app/src/main/AndroidManifest.xml` contains the company's **real** AdMob app ID.
- ADR-001: the library uses the **GMA Next-Gen SDK** (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk`). The legacy `play-services-ads` must be removed from the project entirely.
- Modules (ADR-003): `admob-core`, `admob-views`, `admob-compose`, `admob-testing`, `admob-firebase` (created later in Prompt 24, **not now**), `sample-xml` (the renamed `:app`), `sample-compose`, `sample-mixed`.
- Library settings: minSdk 24, compileSdk 36, JVM target 11, Kotlin `languageVersion`/`apiVersion` 2.1 (ADR-010), explicit API mode, `allWarningsAsErrors`, and `resourcePrefix = "company_admob_"`.

## 3. Files to create
- `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`
- `build-logic/convention/src/main/kotlin/companyadmob.android.library.gradle.kts`: or class-based plugins `AndroidLibraryConventionPlugin.kt`, `ComposeLibraryConventionPlugin.kt`, `PublishingConventionPlugin.kt` (a stub for now; filled in Prompt 21), `SampleAppConventionPlugin.kt`. Choose class-based plugins registered in `build-logic/convention/build.gradle.kts` with ids `companyadmob.android.library`, `companyadmob.android.library.compose`, `companyadmob.publishing`, `companyadmob.android.sample`.
- `admob-core/build.gradle.kts`, `admob-core/src/main/AndroidManifest.xml` (empty manifest), `admob-core/consumer-rules.pro` (a comment only for now), `admob-core/src/main/kotlin/com/company/admob/CompanyAdMobVersion.kt` (`public object CompanyAdMobVersion { public const val NAME: String = "<from gradle.properties via BuildConfig or generated source>" }`)
- The same skeleton for `admob-views` (package `com.company.admob.views`), `admob-compose` (`com.company.admob.compose`), and `admob-testing` (`com.company.admob.testing`), each with one placeholder `internal` file so the module compiles.
- `sample-compose/` and `sample-mixed/` app modules with a minimal launcher Activity each (Compose `ComponentActivity` for sample-compose, AppCompat XML Activity hosting a `ComposeView` for sample-mixed).
- `gradle/dependency-rules.gradle.kts`, or a task in the convention plugin: a `checkModuleDependencies` task that fails the build if any forbidden edge exists (see Implementation §5.6).
- `.editorconfig` (Kotlin official style, 4-space indent, max line 120).
- `docs/architecture/NAMING.md`: the single table of placeholder tokens (`CompanyAdMob`, `com.company.admob`, `com.company`, `company_admob_`, `CompanyAdMob` log tag) and the rename checklist.

## 4. Files to modify
- `settings.gradle.kts`: `includeBuild("build-logic")` inside `pluginManagement`, include all modules, rename `:app` to `:sample-xml` with `git mv app sample-xml`, and set `rootProject.name = "CompanyAdMob"`.
- `build.gradle.kts` (root): declare plugins `apply false` (android application/library, compose compiler, BCV). Apply BCV at the root with `ignoredProjects` = the sample modules.
- `gradle/libs.versions.toml`: **remove `play-services-ads`**. Add `ads-mobile-sdk = "1.4.0"`, `ump = "4.0.0"`, `androidx-lifecycle` (latest stable, verify; design-time 2.11.0), `kotlinx-coroutines` (latest stable; design-time 1.11.0), `androidx-annotation`, `compose-bom` (verify; design-time 2026.09.00), `kotlin` (only if AGP's built-in Kotlin needs an explicit KGP version for the compose compiler plugin; verify), `binary-compatibility-validator` (0.18.x, verify), `junit`, `kotlinx-coroutines-test`, `turbine`, `robolectric`, `androidx-test-core`, and `leakcanary` (2.x). Add plugin aliases for `android-library`, `kotlin-compose` (compose compiler), and `kotlinx-binary-compatibility-validator`.
- `gradle.properties`: add `companyAdmob.group=com.company`, `companyAdmob.version=0.1.0-SNAPSHOT`, `companyAdmob.resourcePrefix=company_admob_`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true` (keep only if the build stays compatible; otherwise report why not), `android.nonTransitiveRClass=true`.
- `sample-xml/build.gradle.kts` (formerly `app`): apply `companyadmob.android.sample`, set `namespace`/`applicationId` to `com.company.admob.sample.xml`, drop `play-services-ads`, and depend on `project(":admob-core")` and `project(":admob-views")`. Move the Kotlin sources to the new package with `git mv` and update the package declarations and manifest.
- `sample-xml/src/main/AndroidManifest.xml`: replace the hard-coded real app ID with `${admobAppId}`, set through `manifestPlaceholders`. The default is Google's **sample app ID `ca-app-pub-3940256099942544~3347511713`**, overridable from `local.properties`/Gradle property `companyAdmob.sample.appId`. **Don't commit the real ID anywhere.** Mention in the report that the real ID was removed from the manifest.
- `.gitignore`: add `/build-logic/.gradle`, `/build-logic/**/build`, and `**/build`.

## 5. Implementation requirements
1. **Convention plugin `companyadmob.android.library`** applies `com.android.library`, uses AGP built-in Kotlin (verify how AGP 9 exposes the Kotlin compiler options; use the `kotlin { compilerOptions { … } }` extension that AGP 9 built-in Kotlin provides and report which API you used), and sets:
   - `compileSdk = 36` (read from the catalog `versions.compileSdk`), `minSdk = 24`, `testOptions.unitTests.isIncludeAndroidResources = true`, `resourcePrefix = companyAdmob.resourcePrefix`
   - `compileOptions` Java 11, Kotlin `jvmTarget = JVM_11`, `explicitApi()`, `allWarningsAsErrors = true`, `languageVersion = apiVersion = KOTLIN_2_1` (verify that the bundled Kotlin supports this; if not, use the lowest supported and record it), `freeCompilerArgs += "-Xjvm-default=all"` only if needed
   - `buildFeatures { buildConfig = false }` except in core, where `buildConfig = true` is used only for `LIBRARY_VERSION`
   - `lint { abortOnError = true; warningsAsErrors = false; checkDependencies = false; baseline = file("lint-baseline.xml") }` (create an empty baseline)
   - `defaultConfig.consumerProguardFiles("consumer-rules.pro")`
   - group and version from `gradle.properties`
2. **`companyadmob.android.library.compose`** = library + the compose compiler plugin + `buildFeatures.compose = true` + a `platform(compose-bom)` dependency.
3. **`companyadmob.android.sample`** applies `com.android.application`, `compileSdk 36`, `targetSdk 36`, `minSdk 24`, Java 11, release `isMinifyEnabled = true` + `isShrinkResources = true` with `proguard-android-optimize.txt`, and debug-signs the release for local testing only (document that in a comment).
4. `admob-core` dependencies: `implementation(libs.ads.mobile.sdk)`, `implementation(libs.ump)`, `implementation(libs.androidx.lifecycle.process)`, `implementation(libs.androidx.lifecycle.runtime)`, `implementation(libs.kotlinx.coroutines.android)`, `api(libs.kotlinx.coroutines.core)` (needed because the public API exposes `StateFlow`/`Flow`; verify there's no version conflict with the version Next-Gen pulls in), and `api(libs.androidx.lifecycle.common)` (the public API exposes `LifecycleOwner`). Test: junit, coroutines-test, turbine, robolectric, androidx-test-core.
5. `admob-views`: `api(project(":admob-core"))`. `admob-compose`: `api(project(":admob-core"))`, `api(project(":admob-views"))`, compose `runtime`/`ui`/`foundation`, `lifecycle-runtime-compose`, and `lifecycle-viewmodel-compose`. `admob-testing`: `api(project(":admob-core"))` plus coroutines.
6. **Dependency guard** `checkModuleDependencies` (wired into `check`): resolve each library module's `releaseRuntimeClasspath` and fail if:
   - `admob-core` depends on any of `androidx.compose.*`, `androidx.appcompat`, `com.google.android.material`, `androidx.fragment`, `com.google.dagger`, `com.google.firebase`, or project `:admob-views|:admob-compose|:admob-firebase`
   - `admob-views` depends on `androidx.compose.*`
   - **any** module (library or sample) resolves `com.google.android.gms:play-services-ads` or `play-services-ads-lite`
7. **BCV**: apply it at the root, configure `ignoredProjects` for samples, and use `nonPublicMarkers` for `com.company.admob.GoogleSdkEscapeHatch` **only if** the team decides to hide escape hatches from dumps (default: keep them visible; they're part of the API). Run `./gradlew apiDump` and commit the `.api` files.
8. Remove the `ExampleInstrumentedTest` template or move it to the new package. Replace the Espresso template dependencies only as needed.
9. Keep `local.properties` untracked.

## 6. Public API requirements
Only `CompanyAdMobVersion.NAME` (in core). Nothing else is public yet.

## 7. Constraints
- Don't add Hilt, Firebase, Material (to libraries), Detekt, or any publish plugin.
- Don't implement any ad logic.
- Don't change the architecture's module list. If a module seems unnecessary, report it rather than deleting it.
- The samples may use AppCompat and Material; the libraries may not.

## 8. Dependencies allowed
Only those listed in §5.4–5.5 plus test libraries. Verify each version's latest stable release and record it in the report.

## 9. Tests
- `admob-core/src/test/.../CompanyAdMobVersionTest.kt`: asserts `NAME` is non-blank and matches the `\d+\.\d+\.\d+(-SNAPSHOT)?` format.
- A Gradle test of the guard: temporarily add `implementation("androidx.appcompat:appcompat:…")` to core, run `checkModuleDependencies`, confirm it fails, then revert. Report the output. Don't commit the temporary change.

## 10. Validation commands
```
./gradlew --version
./gradlew projects
./gradlew build
./gradlew checkModuleDependencies
./gradlew apiDump apiCheck
./gradlew :sample-xml:assembleRelease :sample-compose:assembleDebug :sample-mixed:assembleDebug
./gradlew :admob-core:dependencies --configuration releaseRuntimeClasspath   # confirm no play-services-ads
```

## 11. Acceptance criteria
- [ ] `./gradlew build` passes from a clean checkout (`./gradlew clean build`).
- [ ] Modules exist: admob-core, admob-views, admob-compose, admob-testing, sample-xml, sample-compose, sample-mixed. `:app` no longer exists, and git history is preserved through `git mv`.
- [ ] `play-services-ads` appears nowhere in the catalog, the build files, or any resolved classpath.
- [ ] `ads-mobile-sdk` 1.4.0 and UMP 4.0.0 are on the core `implementation` classpath and **not** on the compile classpath of the samples, confirmed by trying to reference `com.google.android.libraries.ads.mobile.sdk.MobileAds` from sample code: compilation must fail. Report this, then remove the probe.
- [ ] Explicit API mode, `allWarningsAsErrors`, JVM 11, and `resourcePrefix` are active in every library module.
- [ ] `checkModuleDependencies` runs in `check` and fails on a forbidden edge (demonstrated).
- [ ] The `.api` dumps are committed, and `apiCheck` passes.
- [ ] No real AdMob app ID remains in the repo (`git grep -n "$(git show 2f76e72:app/src/main/AndroidManifest.xml | grep -o 'ca-app-pub-[0-9]*')"` finds nothing).
- [ ] The minified `:sample-xml:assembleRelease` builds.
- [ ] `docs/architecture/NAMING.md` exists.

## 12. Completion report
Follow 00_WORKING_RULES §D. Also report: the exact AGP built-in Kotlin configuration API used, the Kotlin compiler version in effect, whether `languageVersion 2.1` was accepted, whether configuration cache is enabled, and the resolved versions of every added dependency.
