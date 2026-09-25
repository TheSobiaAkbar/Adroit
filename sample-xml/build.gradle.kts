plugins {
    alias(libs.plugins.companyadmob.android.sample)
}

android {
    namespace = "com.company.admob.sample.xml"
    defaultConfig {
        applicationId = "com.company.admob.sample.xml"
    }
}

dependencies {
    implementation(project(":admob-core"))
    implementation(project(":admob-views"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
}
