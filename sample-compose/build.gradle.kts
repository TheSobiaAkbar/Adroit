import java.util.Properties

plugins {
    alias(libs.plugins.companyadmob.android.sample.compose)
}

/**
 * Test device IDs for the STAGING profile, from `local.properties` (never committed):
 * `companyAdmob.sample.testDeviceIds=33BE2250B43518CCDA7DE426D04EE231,…`. Empty by default. Logcat prints a device's
 * ID the first time it requests an ad.
 */
val testDeviceIds: List<String> = rootProject.file("local.properties").takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.getProperty("companyAdmob.sample.testDeviceIds")
    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
    .orEmpty()

android {
    namespace = "com.company.admob.sample.compose"
    defaultConfig {
        applicationId = "com.company.admob.sample.compose"
        buildConfigField(
            "String[]",
            "TEST_DEVICE_IDS",
            testDeviceIds.joinToString(prefix = "{", postfix = "}") { "\"$it\"" },
        )
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":admob-compose"))

    // Sample-only UI libraries. The CompanyAdMob libraries themselves never depend on these.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    debugImplementation(libs.leakcanary.android)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
}
