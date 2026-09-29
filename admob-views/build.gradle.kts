plugins {
    alias(libs.plugins.companyadmob.android.library)
    alias(libs.plugins.companyadmob.publishing)
}

companyAdmobPublishing {
    artifactId = "admob-views"
    description = "CompanyAdMob Android View integration: XML banner and native views, lifecycle " +
        "extensions and native ad templates."
}

android {
    namespace = "com.company.admob.views"
}

dependencies {
    api(project(":admob-core"))
    // lifecycleScope and repeatOnLifecycle. Core keeps lifecycle-runtime as implementation, so it isn't visible here.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // The templates' dimens use sdp and ssp (@dimen/_8sdp, @dimen/_12ssp): sizes that scale with the screen width.
    // Resources only, and api: an app can use them in its own layouts and overrides (an app already using them
    // shares the same artifacts).
    api(libs.sdp.android)
    api(libs.ssp.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // A real Fragment for the view-lifecycle tests. Test only: never a runtime dependency (ADR-014).
    testImplementation(libs.androidx.fragment.testing)
}
