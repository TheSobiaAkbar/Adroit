plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-testing"
    description = "Adroit test doubles: FakeAdsClient and AdroitTestRule for host app unit " +
        "tests, with no Google SDK calls."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.testing"
}

dependencies {
    api(project(":adroit-core"))
    api(libs.kotlinx.coroutines.core)
    // AdroitTestRule. compileOnly: the host's test classpath already has JUnit; we don't force a version.
    compileOnly(libs.junit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.lifecycle.runtime)
}
