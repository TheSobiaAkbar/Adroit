plugins {
    alias(libs.plugins.adroit.android.library.compose)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-billing-compose"
    description = "Adroit Billing Jetpack Compose integration: paywall screens, dialogs and building blocks."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.billing.compose"
}

dependencies {
    api(project(":adroit-billing"))

    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    // Registers the ComponentActivity that the Compose test rule launches. Debug only: never in a release classpath.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
