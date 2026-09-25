plugins {
    alias(libs.plugins.companyadmob.android.library)
    alias(libs.plugins.companyadmob.publishing)
}

android {
    namespace = "com.company.admob"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "LIBRARY_VERSION", "\"${project.version}\"")
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
