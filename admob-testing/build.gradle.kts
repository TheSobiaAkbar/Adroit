plugins {
    alias(libs.plugins.companyadmob.android.library)
    alias(libs.plugins.companyadmob.publishing)
}

android {
    namespace = "com.company.admob.testing"
}

dependencies {
    api(project(":admob-core"))
    api(libs.kotlinx.coroutines.core)
    // CompanyAdMobTestRule. compileOnly: the host's test classpath already has JUnit; we don't force a version.
    compileOnly(libs.junit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.lifecycle.runtime)
}
