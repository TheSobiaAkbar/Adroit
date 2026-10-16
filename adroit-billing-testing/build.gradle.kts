plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-billing-testing"
    description = "Adroit Billing test doubles: FakeBilling and product builders for host app unit tests, " +
        "with no Play Billing calls."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.billing.testing"
}

dependencies {
    api(project(":adroit-billing"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
