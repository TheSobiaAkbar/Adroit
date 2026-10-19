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
    // ComponentActivity, OnBackPressedDispatcher, edge-to-edge and ActivityResultContract. Not AppCompat (ADR-014).
    implementation(libs.androidx.activity)
    // lifecycleScope and repeatOnLifecycle, and the ViewModels that keep the controllers across rotation.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    testImplementation(project(":adroit-billing-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
