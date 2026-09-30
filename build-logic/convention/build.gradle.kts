plugins {
    `kotlin-dsl`
}

group = "io.github.thesobiaakbar.adroit.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // implementation (not compileOnly): the versions declared here are the plugin versions used by every module,
    // which is how the Kotlin Gradle plugin version is pinned on top of AGP's built-in Kotlin.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    // For sample-xml's real Firebase project: loaded here so it sees AGP's classes.
    implementation(libs.googleServices.gradlePlugin)
    // Compile-time only: the dump engine runs in an isolated worker with its own classpath (ApiValidation.kt).
    compileOnly(libs.binaryCompatibilityValidator)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "adroit.android.library"
            implementationClass = "io.github.thesobiaakbar.adroit.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "adroit.android.library.compose"
            implementationClass = "io.github.thesobiaakbar.adroit.buildlogic.ComposeLibraryConventionPlugin"
        }
        register("androidSample") {
            id = "adroit.android.sample"
            implementationClass = "io.github.thesobiaakbar.adroit.buildlogic.SampleAppConventionPlugin"
        }
        register("androidSampleCompose") {
            id = "adroit.android.sample.compose"
            implementationClass = "io.github.thesobiaakbar.adroit.buildlogic.ComposeSampleAppConventionPlugin"
        }
        register("androidFirebase") {
            id = "adroit.android.firebase"
            implementationClass = "io.github.thesobiaakbar.adroit.buildlogic.FirebaseAppConventionPlugin"
        }
        register("publishing") {
            id = "adroit.publishing"
            implementationClass = "io.github.thesobiaakbar.adroit.buildlogic.PublishingConventionPlugin"
        }
    }
}
