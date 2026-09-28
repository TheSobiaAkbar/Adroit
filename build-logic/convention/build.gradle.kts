plugins {
    `kotlin-dsl`
}

group = "com.company.admob.buildlogic"

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
            id = "companyadmob.android.library"
            implementationClass = "com.company.admob.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "companyadmob.android.library.compose"
            implementationClass = "com.company.admob.buildlogic.ComposeLibraryConventionPlugin"
        }
        register("androidSample") {
            id = "companyadmob.android.sample"
            implementationClass = "com.company.admob.buildlogic.SampleAppConventionPlugin"
        }
        register("androidSampleCompose") {
            id = "companyadmob.android.sample.compose"
            implementationClass = "com.company.admob.buildlogic.ComposeSampleAppConventionPlugin"
        }
        register("androidFirebase") {
            id = "companyadmob.android.firebase"
            implementationClass = "com.company.admob.buildlogic.FirebaseAppConventionPlugin"
        }
        register("publishing") {
            id = "companyadmob.publishing"
            implementationClass = "com.company.admob.buildlogic.PublishingConventionPlugin"
        }
    }
}
