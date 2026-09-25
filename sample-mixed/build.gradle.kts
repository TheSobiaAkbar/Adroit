plugins {
    alias(libs.plugins.companyadmob.android.sample.compose)
}

android {
    namespace = "com.company.admob.sample.mixed"
    defaultConfig {
        applicationId = "com.company.admob.sample.mixed"
    }
}

dependencies {
    implementation(project(":admob-views"))
    implementation(project(":admob-compose"))

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
}
