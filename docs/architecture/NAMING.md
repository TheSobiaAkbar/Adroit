# Naming placeholders and rename checklist

`CompanyAdMob` is a working name. Every placeholder token is listed here, so the final brand can be applied in one pass.

| Token | Where it lives | Example |
|---|---|---|
| `com.company` | `gradle.properties` → `companyAdmob.group` (Maven groupId) | `com.company:admob-core` |
| `admob-core`, `admob-views`, `admob-compose`, `admob-testing` | module directory names, `settings.gradle.kts`, artifactIds | |
| `com.company.admob` | Kotlin package roots and `android.namespace` in each library `build.gradle.kts` | `com.company.admob.views` |
| `com.company.admob.sample.*` | sample `namespace`/`applicationId` | |
| `CompanyAdMob` | public facade object, `CompanyAdMobVersion`, `CompanyAdMobTestRule`, the log tag, `rootProject.name` | `CompanyAdMob.initialize(...)` |
| `company_admob_` | `gradle.properties` → `companyAdmob.resourcePrefix` (enforced by `resourcePrefix`), every library resource name, XML attrs, SharedPreferences file `company_admob_state` | `@layout/company_admob_native_medium` |
| `companyAdmob.*` | Gradle property names | `companyAdmob.version` |
| `companyadmob.*` | convention plugin IDs in `build-logic` and `gradle/libs.versions.toml` | `companyadmob.android.library` |
| `com.company.admob.buildlogic` | build-logic Kotlin package | |

## Rename checklist
1. Decide the new tokens (for example `com.acme`, `AcmeAds`, `acme_ads_`).
2. Change `gradle.properties` (`companyAdmob.group`, `companyAdmob.resourcePrefix`).
3. `git mv` the package directories in every module (`src/main/kotlin/com/company/admob/...`), then update the `package` and `import` lines.
4. Update `namespace` in each `build.gradle.kts` and the `apiValidation.ignoredClasses` entries in the root `build.gradle.kts`.
5. Rename the resources and XML attributes that carry the prefix (the lint `resourcePrefix` check lists any you miss).
6. Rename the public types (`CompanyAdMob*`). **Before 1.0.0 this is free. After 1.0.0 it's a MAJOR version change.**
7. Find-and-replace the tokens across `docs/`.
8. Run `./gradlew apiDump` and review the diff. Then run `./gradlew build` and `git grep -i "company"` to catch leftovers.
