plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-core"
    description = "Adroit core: configuration, consent, initialization and every ad format on the " +
        "Google Mobile Ads Next-Gen SDK, without exposing Google types."
}

android {
    namespace = "io.github.thesobiaakbar.adroit"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "LIBRARY_VERSION", "\"${project.version}\"")
    }
    buildTypes {
        debug {
            // Opt-in JaCoCo for unit tests, off by default so regular builds aren't instrumented:
            // ./gradlew :adroit-core:createDebugUnitTestCoverageReport -Padroit.coverage
            enableUnitTestCoverage = providers.gradleProperty("adroit.coverage").isPresent
        }
    }
}

dependencies {
    // Public API exposes StateFlow/Flow and LifecycleOwner.
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.lifecycle.common)

    // Google SDKs stay off consumers' compile classpath (ADR-001, ADR-003).
    implementation(libs.ads.mobile.sdk)
    implementation(libs.ump)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
