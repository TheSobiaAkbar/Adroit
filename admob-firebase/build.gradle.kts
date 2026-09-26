plugins {
    alias(libs.plugins.companyadmob.android.library)
    alias(libs.plugins.companyadmob.publishing)
}

companyAdmobPublishing {
    artifactId = "admob-firebase"
    description = "CompanyAdMob Firebase integration: remote overrides from Firebase Remote Config and ad events " +
        "to Google Analytics for Firebase. Optional; the host app initializes Firebase."
}

android {
    namespace = "com.company.admob.firebase"
}

dependencies {
    api(project(":admob-core"))

    // Firebase types appear in this module's public constructors (the one exception to "no vendor types", ADR-017),
    // so both SDKs are `api`. The host applies the google-services plugin and initializes Firebase itself.
    api(platform(libs.firebase.bom))
    api(libs.firebase.config)
    api(libs.firebase.analytics)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
