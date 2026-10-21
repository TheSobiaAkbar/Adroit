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
    // The billing guides' snippets (docs/BILLING*.md), apart from the ads ones.
    sourceSets.getByName("main") {
        kotlin.directories.add("src/billing/kotlin")
        res.directories.add("src/billing/res")
    }
}

dependencies {
    implementation(project(":adroit-core"))
    implementation(project(":adroit-views"))
    implementation(project(":adroit-compose"))
    implementation(project(":adroit-firebase"))
    implementation(project(":adroit-billing"))
    implementation(project(":adroit-billing-views"))
    implementation(project(":adroit-billing-compose"))
    implementation(project(":adroit-billing-firebase"))
    implementation(project(":adroit-update"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.material) // the update Snackbar of the XML guides

    testImplementation(project(":adroit-testing"))
    testImplementation(project(":adroit-billing-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
