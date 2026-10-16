plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-billing-views"
    description = "Adroit Billing Android View integration: a paywall Activity, PaywallView and paywall dialogs " +
        "for XML apps, with no Compose, AppCompat, Material or Fragment."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.billing.views"
}

dependencies {
    api(project(":adroit-billing"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
