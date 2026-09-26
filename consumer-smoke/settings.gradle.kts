// A separate Gradle build (not included in the main settings): a host app that consumes the published CompanyAdMob
// artifacts from mavenLocal() exactly as a company app would. Run `./gradlew publishToMavenLocal` in the repository
// root first, then `./gradlew -p consumer-smoke assembleDebug verifyConsumerClasspath`. See README.md.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal {
            content { includeGroup("com.company") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "consumer-smoke"
include(":app")
