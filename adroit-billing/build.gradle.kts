plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-billing"
    description = "Adroit Billing: Google Play in-app purchases and subscriptions, entitlements, placement " +
        "attribution and remotely configured paywalls, without exposing Play Billing types."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.billing"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "LIBRARY_VERSION", "\"${project.version}\"")
    }
}

dependencies {
    // Public API exposes StateFlow/Flow.
    api(libs.kotlinx.coroutines.core)

    // Play Billing stays off consumers' compile classpath (06_BILLING_ARCHITECTURE §2). It merges the BILLING
    // permission and brings androidx.core 1.15.
    implementation(libs.play.billing)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.annotation)

    testImplementation(project(":adroit-billing-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
