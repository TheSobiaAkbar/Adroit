plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-billing-firebase"
    description = "Adroit Billing Firebase integration: paywall and billing settings from Firebase Remote Config " +
        "and billing events to Google Analytics for Firebase. Optional; the host app initializes Firebase."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.billing.firebase"
}

dependencies {
    api(project(":adroit-billing"))
    // The one Remote Config fetch, shared with adroit-firebase and adroit-update. It has no ads code.
    api(project(":adroit-remote-config"))

    // Not adroit-firebase: it brings adroit-core and the Mobile Ads SDK, which a billing-only app must not get
    // (06_BILLING_ARCHITECTURE §2). Firebase types appear in this module's public API, so both SDKs are `api`.
    api(platform(libs.firebase.bom))
    api(libs.firebase.config)
    api(libs.firebase.analytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
