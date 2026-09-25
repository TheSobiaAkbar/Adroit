# PROMPT 21: Maven publishing

Read `docs/implementation-prompts/00_WORKING_RULES.md` and follow it. Then read `02_ARCHITECTURE.md` §27 and §31.

## 1. Objective
Make every library module publishable as an AAR with sources, javadoc, and complete POM metadata to Maven Local, GitHub Packages, and a company Artifactory/Nexus, using only the built-in `maven-publish` plugin through the `companyadmob.publishing` convention plugin.

## 2. Context
- Coordinates: `com.company:admob-core|admob-views|admob-compose|admob-testing` (`admob-firebase` comes later, if created). Version: `companyAdmob.version` in `gradle.properties`.
- Repository URL: `https://github.com/TheSobiaAkbar/AdsPlugin` (check `git remote -v`; the SCM field comes from a property, not hard-coded).

## 3. Files to create / modify
- `build-logic/convention/.../PublishingConventionPlugin.kt`: fill in the Prompt 01 stub.
- The library modules' `build.gradle.kts`: apply `companyadmob.publishing` and set `artifactId` + a description property.
- `gradle.properties`: `companyAdmob.pom.url`, `companyAdmob.pom.scm.connection`, `companyAdmob.pom.license.name` (placeholder "Proprietary – Company internal use"), and `companyAdmob.pom.developer.id/name`.
- `consumer-smoke/`: a **separate** Gradle build (not included in the main settings) with an app that depends on `com.company:admob-compose:<version>` from `mavenLocal()` and calls `CompanyAdMob.initialize` + `AdBanner`. It verifies that the published artifacts resolve transitively (core, views, `ads-mobile-sdk`, UMP) and that Google types aren't on its compile classpath.
- `docs/architecture/PUBLISHING_NOTES.md`: how credentials are supplied.

## 4. Implementation requirements
1. AGP: `android { publishing { singleVariant("release") { withSourcesJar(); withJavadocJar() } } }`. Report how AGP 9 generates the javadoc jar for Kotlin sources (Dokka integration or not). If the jar is empty for Kotlin, add Dokka (`org.jetbrains.dokka`, latest stable 2.x; verify) **only** for the javadoc jar, and report it.
2. `publishing { publications { create<MavenPublication>("release") { from(components["release"]) ; groupId/artifactId/version; pom { name, description, url, licenses, developers, scm } } } }`, registered in `afterEvaluate` only if AGP requires it (check the AGP 9 docs).
3. Repositories, each added only when its properties or env are present:
   - `mavenLocal()`: always
   - GitHub Packages: `https://maven.pkg.github.com/${owner}/${repo}`, with credentials from env `GITHUB_ACTOR`/`GITHUB_TOKEN` or the Gradle properties `gpr.user`/`gpr.key`
   - Company: URL from `companyAdmob.repo.url` / env `COMPANY_MAVEN_URL`, and credentials from `COMPANY_MAVEN_USER`/`COMPANY_MAVEN_PASSWORD`
   - **No credentials in any committed file.**
4. Signing: apply the `signing` plugin only when `SIGNING_KEY`/`SIGNING_PASSWORD` env vars exist (in-memory keys). Otherwise skip it.
5. The POM must declare dependencies with correct scopes: `ads-mobile-sdk` → `runtime` (from `implementation`), coroutines-core + lifecycle-common → `compile` (from `api`). Verify this in the generated POM.
6. Add a `verifyPublication` task that publishes to a build-dir repo and asserts the expected files exist (aar, pom, module, sources, javadoc) for each module.
7. Snapshot versions (`-SNAPSHOT`) can only be published to mavenLocal and GitHub Packages. The company repo release path requires a non-snapshot version (a guard task).

## 5. Public API requirements
None.

## 6. Constraints
No third-party publish plugins, unless the javadoc requirement forces Dokka.

## 7. Dependencies
Possibly Dokka (javadoc only).

## 8. Tests
- `./gradlew verifyPublication`
- The `consumer-smoke` build against mavenLocal: `./gradlew -p consumer-smoke assembleDebug`, plus a compile-probe that referencing `com.google.android.libraries.ads.mobile.sdk.MobileAds` fails (then remove the probe).

## 9. Validation commands
```
./gradlew publishToMavenLocal verifyPublication
./gradlew -p consumer-smoke assembleDebug
cat ~/.m2/repository/com/company/admob-core/<version>/admob-core-<version>.pom
```

## 10. Acceptance criteria
- [ ] Every library module publishes the aar, pom, Gradle module metadata, sources jar, and a non-empty javadoc jar.
- [ ] The POMs have correct metadata and dependency scopes.
- [ ] The consumer smoke build resolves and compiles from mavenLocal.
- [ ] No secrets are committed (`git grep -i "password\|token"` reviewed).

## 11. Completion report
Per 00_WORKING_RULES §D, with a POM excerpt for admob-core.
