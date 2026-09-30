# Publishing notes

The libraries (`adroit-core`, `adroit-views`, `adroit-compose`, `adroit-testing`) publish with Gradle's built-in
`maven-publish`, configured once in `build-logic/.../PublishingConventionPlugin.kt` (plugin `adroit.publishing`).
There's no third-party publishing plugin, and no Dokka plugin (see "Javadoc" below).

## What gets published

For each module: `io.github.thesobiaakbar:<artifactId>:<adroit.version>` with the release AAR, the POM, Gradle module
metadata (`.module`), a sources jar and a javadoc jar.

- **POM metadata:** comes from `adroit.pom.*` in `gradle.properties`.
- **Dependency scopes** follow the Gradle declarations. `api` becomes `compile`: coroutines-core, lifecycle-common, and
  adroit-core for the other modules. `implementation` becomes `runtime`: `ads-mobile-sdk`, UMP and the rest. So hosts
  never compile against Google types.
- **Versions:** the POM lists resolved versions. Compose artifacts whose version comes from the Compose BOM appear
  without one, next to a `dependencyManagement` import of that BOM. Gradle consumers read the module metadata instead.

## Repositories and credentials

**No credentials are ever committed.** Each repository is added only when what it needs is present:

| Repository | URL | Credentials |
|---|---|---|
| `mavenLocal()` | always | none |
| GitHub Packages (`GitHubPackages`) | `https://maven.pkg.github.com/<adroit.github.repository>` | Gradle properties `gpr.user`/`gpr.key`, or env `GITHUB_ACTOR`/`GITHUB_TOKEN` (a token with `write:packages`) |
| `VerificationBuildDir` | `<module>/build/verification-repo` | none; used by `verifyPublication` |

Put personal values in `~/.gradle/gradle.properties`, never in the repository's `gradle.properties`:

```properties
gpr.user=<github-user>
gpr.key=<personal-access-token>
```

In CI, use the platform's secrets as environment variables. GitHub Actions provides `GITHUB_ACTOR`, and
`GITHUB_TOKEN` needs `packages: write`.

Commands:

```bash
./gradlew publishToMavenLocal
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

## Snapshots

`-SNAPSHOT` versions can go to `mavenLocal()` and GitHub Packages. The release workflow refuses them: tag only release
versions.

## Signing

Signing is optional. When the environment has `SIGNING_KEY` (an ASCII-armored secret key) and `SIGNING_PASSWORD`,
the `signing` plugin is applied with an in-memory key and signs the `release` publication (`.asc` files). Without
them, nothing is signed and no signing tasks exist.

## Javadoc

AGP 9's `withJavadocJar()` builds the javadoc jar itself (task `javaDocReleaseGeneration`), with a Dokka engine
bundled inside AGP. It documents Kotlin sources: the adroit-core jar has 192 HTML pages for the public API, and
internal declarations are left out. So the project doesn't apply the `org.jetbrains.dokka` plugin.

## Verification

- `./gradlew verifyPublication` publishes each module to `build/verification-repo` and fails unless:
  - the AAR, POM, `.module`, sources jar (with `.kt` files) and javadoc jar (with HTML) are all there;
  - every `api` dependency has POM scope `compile`, and every `implementation` dependency has scope `runtime`.
- `consumer-smoke/` is a separate host build that depends only on `io.github.thesobiaakbar:adroit-compose` from `mavenLocal()`
  (see its README). It compiles with AGP 9's built-in Kotlin 2.2.10, older than the 2.4.20 the libraries are built
  with, and checks that:
  - Adroit and the Google SDKs resolve transitively at runtime;
  - no Google Mobile Ads or UMP artifact is on its compile classpath.
