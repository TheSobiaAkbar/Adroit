// A separate Gradle build (not included in the main settings): a host app that consumes the published Adroit
// artifacts from mavenLocal() exactly as an app would. Run `./gradlew publishToMavenLocal` in the repository
// root first, then `./gradlew -p consumer-smoke assembleDebug verifyConsumerClasspath`. See README.md.
pluginManagement {
    // The compatibility matrix (README.md): -Psmoke.agp picks the Android Gradle plugin, -Psmoke.kotlin the Kotlin
    // Gradle plugin (applied on AGP 8; AGP 9 uses it as its built-in Kotlin) and the Compose compiler plugin.
    val agpVersion = providers.gradleProperty("smoke.agp").getOrElse("9.1.1")
    val kotlinVersion = providers.gradleProperty("smoke.kotlin").getOrElse("2.2.10")
    resolutionStrategy {
        eachPlugin {
            when {
                requested.id.id.startsWith("com.android.") -> useVersion(agpVersion)
                requested.id.id.startsWith("org.jetbrains.kotlin.") -> useVersion(kotlinVersion)
            }
        }
    }
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
            content { includeGroup("io.github.thesobiaakbar") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "consumer-smoke"
include(":app")
