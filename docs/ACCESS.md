# Getting access

Adroit is private. Its code and its packages live in a private GitHub repository, and only people the author invites
can download them. This page covers what you do once, on each computer that builds your app.

## 1. Get an invite

Ask [Sobia Akbar](https://github.com/TheSobiaAkbar) for access, with your GitHub user name. You'll get an email
from GitHub: accept the invitation. You can then open https://github.com/TheSobiaAkbar/Adroit.

## 2. Create a token

Gradle downloads Adroit from GitHub Packages, which asks for a token instead of your password.

1. On GitHub: your picture → **Settings** → **Developer settings** → **Personal access tokens** → **Tokens (classic)**
   → **Generate new token (classic)**.
2. Name it, for example "Adroit packages", and choose an expiration.
3. Tick only **`read:packages`**.
4. **Generate token**, and copy it: GitHub shows it once.

## 3. Save it on your computer

Put your user name and the token in `~/.gradle/gradle.properties`: `C:\Users\<you>\.gradle\gradle.properties` on
Windows, `/Users/<you>/.gradle/gradle.properties` on macOS. Create the file if it doesn't exist.

```properties
gpr.user=<your GitHub user name>
gpr.key=<the token>
```

Never put these lines in your project's own `gradle.properties`: that file is committed, and the token would leak.

## 4. Add the repository to your app

In your app's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/TheSobiaAkbar/Adroit") {
            credentials {
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
        }
    }
}
```

Then add the module your app needs, and continue with your guide:
[Kotlin + XML app](NEW_APP_XML.md) or [Jetpack Compose app](NEW_APP_COMPOSE.md).

```kotlin
dependencies {
    implementation("io.github.thesobiaakbar:adroit-views:1.0.0-rc3")   // XML apps
    // implementation("io.github.thesobiaakbar:adroit-compose:1.0.0-rc3") // Compose apps
}
```

## Building on CI

On GitHub Actions, or any CI, pass the same two values as environment variables, from the CI's secrets, and read them
in `settings.gradle.kts`:

```kotlin
username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GPR_USER")).get()
password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GPR_KEY")).get()
```

The token must belong to an account with access to Adroit.

## If the build can't find Adroit

| Gradle says | Fix |
|---|---|
| `Could not find io.github.thesobiaakbar:adroit-…` | The repository block is missing from `settings.gradle.kts`, or the version doesn't exist: check the [releases](https://github.com/TheSobiaAkbar/Adroit/releases). |
| `Received status code 401` | The token is wrong or expired, or it lacks `read:packages`: create a new one (step 2). |
| `Received status code 403` or `404` | Your account has no access yet: accept the invitation, or ask for one (step 1). |
| `Cannot query the value of this provider because it has no value available` | `gpr.user` or `gpr.key` is missing from `~/.gradle/gradle.properties` (step 3). |

## For the owner: giving and removing access

- **Give access:** in the repository, **Settings** → **Collaborators** → **Add people**, with the **Read** role. For
  a company, invite each developer, or the account its CI uses.
- **Remove access:** **Settings** → **Collaborators** → **Remove**. Their token stops working for new downloads at
  once; builds that already downloaded Adroit keep the files in their Gradle cache.
- **Publish a release:** see [Releasing](RELEASE.md). Invited developers see the new version right away.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-30
