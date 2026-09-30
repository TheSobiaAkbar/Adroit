plugins {
    alias(libs.plugins.adroit.android.library.compose)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-compose"
    description = "Adroit Jetpack Compose integration: banner, native, availability and " +
        "full-screen ad composables."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.compose"
}

composeCompiler {
    // adroit-core has no Compose dependency, so its immutable parameter types are declared stable here (ADR-015).
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    api(project(":adroit-core"))
    api(project(":adroit-views"))

    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.turbine)
    testImplementation(libs.compose.ui.test.junit4)
    // ComponentActivity.setContent for the recreation tests (the test rule only brings it at runtime).
    testImplementation(libs.androidx.activity.compose)
    // Registers the ComponentActivity that the Compose test rule launches. Debug only: never in a release classpath.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
