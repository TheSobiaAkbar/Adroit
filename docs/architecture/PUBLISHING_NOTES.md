# Publishing notes

The libraries (`admob-core`, `admob-views`, `admob-compose`, `admob-testing`) publish with Gradle's built-in
`maven-publish`, configured once in `build-logic/.../PublishingConventionPlugin.kt` (plugin `companyadmob.publishing`).
There's no third-party publishing plugin, and no Dokka plugin (see "Javadoc" below).

## What gets published

For each module: `com.company:<artifactId>:<companyAdmob.version>` with the release AAR, the POM, Gradle module
metadata (`.module`), a sources jar and a javadoc jar.

- **POM metadata:** comes from `companyAdmob.pom.*` in `gradle.properties`.
- **Dependency scopes** follow the Gradle declarations. `api` becomes `compile`: coroutines-core, lifecycle-common, and
  admob-core for the other modules. `implementation` becomes `runtime`: `ads-mobile-sdk`, UMP and the rest. So hosts
  never compile against Google types.
- **Versions:** the POM lists resolved versions. Compose artifacts whose version comes from the Compose BOM appear
  without one, next to a `dependencyManagement` import of that BOM. Gradle consumers read the module metadata instead.

## Repositories and credentials

**No credentials are ever committed.** Each repository is added only when what it needs is present:

| Repository | URL | Credentials |
|---|---|---|
| `mavenLocal()` | always | none |
| GitHub Packages (`GitHubPackages`) | `https://maven.pkg.github.com/<companyAdmob.github.repository>` | Gradle properties `gpr.user`/`gpr.key`, or env `GITHUB_ACTOR`/`GITHUB_TOKEN` (a token with `write:packages`) |
| Company Artifactory/Nexus (`Company`) | Gradle property `companyAdmob.repo.url`, or env `COMPANY_MAVEN_URL` | env `COMPANY_MAVEN_USER`/`COMPANY_MAVEN_PASSWORD` (omitted if unset, for anonymous or `file://` repositories) |
| `VerificationBuildDir` | `<module>/build/verification-repo` | none; used by `verifyPublication` |

Put personal values in `~/.gradle/gradle.properties`, never in the repository's `gradle.properties`:

```properties
gpr.user=<github-user>
gpr.key=<personal-access-token>
companyAdmob.repo.url=https://artifactory.example.com/artifactory/libs-release-local
```

In CI, use the platform's secrets as environment variables. GitHub Actions provides `GITHUB_ACTOR`, and
`GITHUB_TOKEN` needs `packages: write`.

Commands:

```bash
./gradlew publishToMavenLocal
./gradlew publishAllPublicationsToGitHubPackagesRepository
./gradlew publishAllPublicationsToCompanyRepository
```

## Snapshots

`-SNAPSHOT` versions can go to `mavenLocal()` and GitHub Packages only. Every publish task to the `Company` repository
depends on `checkReleaseVersion`, which fails for a snapshot. Set a release version in `companyAdmob.version` (or
pass `-PcompanyAdmob.version=…`) to publish there.

## Signing

Signing is optional. When the environment has `SIGNING_KEY` (an ASCII-armored secret key) and `SIGNING_PASSWORD`,
the `signing` plugin is applied with an in-memory key and signs the `release` publication (`.asc` files). Without
them, nothing is signed and no signing tasks exist.

## Javadoc

AGP 9's `withJavadocJar()` builds the javadoc jar itself (task `javaDocReleaseGeneration`), with a Dokka engine
bundled inside AGP. It documents Kotlin sources: the admob-core jar has 192 HTML pages for the public API, and
internal declarations are left out. So the project doesn't apply the `org.jetbrains.dokka` plugin.

## Verification

- `./gradlew verifyPublication` publishes each module to `build/verification-repo` and fails unless:
  - the AAR, POM, `.module`, sources jar (with `.kt` files) and javadoc jar (with HTML) are all there;
  - every `api` dependency has POM scope `compile`, and every `implementation` dependency has scope `runtime`.
- `consumer-smoke/` is a separate host build that depends only on `com.company:admob-compose` from `mavenLocal()`
  (see its README). It compiles with AGP 9's built-in Kotlin 2.2.10, older than the 2.4.20 the libraries are built
  with, and checks that:
  - CompanyAdMob and the Google SDKs resolve transitively at runtime;
  - no Google Mobile Ads or UMP artifact is on its compile classpath.
