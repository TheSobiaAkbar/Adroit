plugins {
    alias(libs.plugins.adroit.android.sample.compose)
}

// The code shown in docs/*.md, compiled so the docs can't drift from the API. Never published or shipped.
// Each snippet is a `// region name` … `// endregion` block; .github/scripts/check-docs.mjs checks that the docs
// quote them exactly.
android {
    namespace = "io.github.thesobiaakbar.adroit.docs"
    defaultConfig {
        applicationId = "io.github.thesobiaakbar.adroit.docs"
    }
    // For `BuildConfig.DEBUG`, as apps write it in the ad profiles setup.
    buildFeatures { buildConfig = true }
    // AdsFileExampleTest reads res/raw/ads_config.json.
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":adroit-core"))
    implementation(project(":adroit-views"))
    implementation(project(":adroit-compose"))
    implementation(project(":adroit-firebase"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    testImplementation(project(":adroit-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
