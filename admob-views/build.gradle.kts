plugins {
    alias(libs.plugins.companyadmob.android.library)
    alias(libs.plugins.companyadmob.publishing)
}

android {
    namespace = "com.company.admob.views"
}

dependencies {
    api(project(":admob-core"))
    // lifecycleScope and repeatOnLifecycle. Core keeps lifecycle-runtime as implementation, so it isn't visible here.
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // A real Fragment for the view-lifecycle tests. Test only: never a runtime dependency (ADR-014).
    testImplementation(libs.androidx.fragment.testing)
}
