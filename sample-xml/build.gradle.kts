import java.util.Properties

plugins {
    alias(libs.plugins.companyadmob.android.sample)
}

/**
 * Test device IDs for the `staging` build, from `local.properties` (never committed):
 * `companyAdmob.sample.testDeviceIds=33BE2250B43518CCDA7DE426D04EE231,…`. Empty by default. Logcat prints a device's
 * ID the first time it requests an ad.
 */
val testDeviceIds: List<String> = rootProject.file("local.properties").takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.getProperty("companyAdmob.sample.testDeviceIds")
    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
    .orEmpty()

android {
    namespace = "com.company.admob.sample.xml"
    defaultConfig {
        applicationId = "com.company.admob.sample.xml"
        buildConfigField(
            "String[]",
            "TEST_DEVICE_IDS",
            testDeviceIds.joinToString(prefix = "{", postfix = "}") { "\"$it\"" },
        )
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    buildTypes {
        // Like debug (debuggable, not minified), but configured as a tester's build: STAGING (see SampleAdsConfig).
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            matchingFallbacks += "debug"
        }
    }
}

dependencies {
    implementation(project(":admob-core"))
    implementation(project(":admob-views"))

    // Sample-only UI libraries. The CompanyAdMob libraries themselves never depend on these.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    debugImplementation(libs.leakcanary.android)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
}
