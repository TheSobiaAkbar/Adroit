# Publishing notes

The libraries (`adroit-core`, `adroit-views`, `adroit-compose`, `adroit-testing`, `adroit-firebase`, the billing
modules `adroit-billing`, `adroit-billing-views`, `adroit-billing-compose`, `adroit-billing-firebase`,
`adroit-billing-testing`, and the shared `adroit-remote-config` and `adroit-update`) publish with Gradle's built-in
`maven-publish`, configured once in `build-logic/.../PublishingConventionPlugin.kt` (plugin `adroit.publishing`).
There's no third-party publishing plugin, and no Dokka plugin (see "Javadoc" below).

## What gets published

For each module: `io.github.thesobiaakbar:<artifactId>:<adroit.version>` with the release AAR, the POM, Gradle module
metadata (`.module`), a sources jar and a javadoc jar.

- **POM metadata:** comes from `adroit.pom.*` in `gradle.properties`, Apache 2.0 license included.
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

## Maven Central

The public channel: apps add only `mavenCentral()`. Each library also publishes into `CentralBundle`, the folder
`build/central-bundle` at the root; `./gradlew centralBundle` zips this version's files (AAR, POM, `.module`, sources
and javadoc jars, their `.asc` signatures and checksums) into `build/central-bundle.zip`, which the Central Portal's
Publisher API takes in one upload. The release workflow does both. `centralBundle` fails without
`SIGNING_KEY`, or on a `-SNAPSHOT` version, since Central rejects both. A released version can never be removed or
replaced.

**Once, by the owner** (Central accounts can't be made from a build):

1. Sign in at [central.sonatype.com](https://central.sonatype.com) with the GitHub account **TheSobiaAkbar**. That
   verifies the namespace `io.github.thesobiaakbar` by itself; check it under **Namespaces**.
2. A signing key: `gpg --quick-generate-key "Sobia Akbar <email>" rsa4096 sign 0`, then publish the public half,
   `gpg --keyserver keyserver.ubuntu.com --send-keys <key id>` (Central checks signatures against it).
3. **View Account → Generate User Token**, then in the Adroit repository's **Settings → Secrets → Actions**:
   - `SIGNING_KEY`: `gpg --armor --export-secret-keys <key id>`
   - `SIGNING_PASSWORD`: that key's passphrase
   - `CENTRAL_TOKEN`: base64 of `<token name>:<token password>`
4. Tag the version (`git tag v1.0.0-rc6 && git push origin v1.0.0-rc6`). The workflow uploads it; it appears on
   central.sonatype.com at once and on repo1.maven.org after Central's checks, usually within 30 minutes.

By hand, from a machine with the same three values in the environment:

```bash
./gradlew centralBundle
curl --request POST --header "Authorization: Bearer $CENTRAL_TOKEN" --form bundle=@build/central-bundle.zip   "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
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
- `consumer-smoke/` is a separate host build that depends only on `io.github.thesobiaakbar:adroit-compose` and
  `adroit-billing-compose` from `mavenLocal()` (`-Psmoke.compose=false`: the View modules; see its README). It compiles with AGP 9's built-in Kotlin 2.2.10, older than the 2.4.20 the libraries are built
  with, and checks that:
  - Adroit and the Google SDKs resolve transitively at runtime;
  - no Google Mobile Ads or UMP artifact is on its compile classpath.
