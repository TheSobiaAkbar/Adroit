plugins {
    alias(libs.plugins.companyadmob.android.sample.compose)
}

// The code shown in docs/*.md, compiled so the docs can't drift from the API. Never published or shipped.
// Each snippet is a `// region name` … `// endregion` block; .github/scripts/check-docs.mjs checks that the docs
// quote them exactly.
android {
    namespace = "com.company.admob.docs"
    defaultConfig {
        applicationId = "com.company.admob.docs"
    }
}

dependencies {
    implementation(project(":admob-core"))
    implementation(project(":admob-views"))
    implementation(project(":admob-compose"))
    implementation(project(":admob-firebase"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    testImplementation(project(":admob-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
