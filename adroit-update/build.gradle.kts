plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-update"
    description = "Adroit Update: Google Play in-app updates (flexible, forced or off) controlled from Firebase " +
        "Remote Config. Works in ads-only, billing-only and ads + billing apps."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.update"
}

dependencies {
    // No ads or billing module (S00 rule 4): the Firebase AppUpdate key comes through the shared session.
    api(project(":adroit-remote-config"))
    // StateFlow is in the public API.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.play.app.update)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
