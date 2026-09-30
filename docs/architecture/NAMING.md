# Names

Every name the library uses, in one place.

| Name | Where it lives | Example |
|---|---|---|
| `io.github.thesobiaakbar` | `gradle.properties` → `adroit.group` (Maven groupId) | `io.github.thesobiaakbar:adroit-core` |
| `adroit-core`, `adroit-views`, `adroit-compose`, `adroit-testing`, `adroit-firebase` | module directory names, `settings.gradle.kts`, artifactIds | |
| `io.github.thesobiaakbar.adroit` | Kotlin package roots and `android.namespace` in each library `build.gradle.kts` | `io.github.thesobiaakbar.adroit.views` |
| `io.github.thesobiaakbar.adroit.sample.*` | sample `namespace`/`applicationId` | |
| `Adroit` | public facade object, `AdroitVersion`, `AdroitTestRule`, the log tag, `rootProject.name` | `Adroit.initialize(...)` |
| `adroit_` | `gradle.properties` → `adroit.resourcePrefix` (enforced by `resourcePrefix`), every library resource name, XML attrs, SharedPreferences file `adroit_state` | `@layout/adroit_native_medium` |
| `adroit.*` | Gradle property names | `adroit.version` |
| `adroit.*` | convention plugin IDs in `build-logic` and `gradle/libs.versions.toml` | `adroit.android.library` |
| `io.github.thesobiaakbar.adroit.buildlogic` | build-logic Kotlin package | |

Renaming a public type (`Adroit*`) after 1.0.0 is a MAJOR version change.
