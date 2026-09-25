plugins {
    alias(libs.plugins.companyadmob.android.sample.compose)
}

android {
    namespace = "com.company.admob.sample.compose"
    defaultConfig {
        applicationId = "com.company.admob.sample.compose"
    }
}

dependencies {
    implementation(project(":admob-compose"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
}
