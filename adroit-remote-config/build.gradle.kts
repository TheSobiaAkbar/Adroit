plugins {
    alias(libs.plugins.adroit.android.library)
    alias(libs.plugins.adroit.publishing)
}

adroitPublishing {
    artifactId = "adroit-remote-config"
    description = "Adroit Remote Config session: fetches Firebase Remote Config once per app session, shared by " +
        "adroit-firebase, adroit-billing-firebase and adroit-update. Optional; the host app initializes Firebase."
}

android {
    namespace = "io.github.thesobiaakbar.adroit.remoteconfig"
}

dependencies {
    // No adroit-* module: ads-only and billing-only apps both use this one (S00 rule 5).
    // FirebaseRemoteConfig appears in RemoteConfigSession.start, so it is `api`.
    api(platform(libs.firebase.bom))
    api(libs.firebase.config)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
