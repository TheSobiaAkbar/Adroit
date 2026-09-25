plugins {
    alias(libs.plugins.companyadmob.android.library.compose)
    alias(libs.plugins.companyadmob.publishing)
}

android {
    namespace = "com.company.admob.compose"
}

dependencies {
    api(project(":admob-core"))
    api(project(":admob-views"))

    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
}
