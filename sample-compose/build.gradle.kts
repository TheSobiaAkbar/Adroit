import java.util.Properties

plugins {
    alias(libs.plugins.adroit.android.sample.compose)
    // Applied below only when google-services.json is here: see the comment there.
    alias(libs.plugins.adroit.android.firebase) apply false
}

/**
 * Firebase (project `sample-ads-plugin`, the same Remote Config parameters as sample-xml) once this app is registered
 * there: Firebase console → Project settings → Add app → Android, package `io.github.thesobiaakbar.adroit.sample.compose`, then
 * download `google-services.json` into this folder. Without the file the app builds and runs on SampleAds' defaults,
 * and logs that Firebase isn't set up (SampleApplication).
 */
if (file("google-services.json").isFile) apply(plugin = "adroit.android.firebase")

/**
 * Test device IDs for the `staging` build, from `local.properties` (never committed):
 * `adroit.sample.testDeviceIds=33BE2250B43518CCDA7DE426D04EE231,…`. Empty by default. Logcat prints a device's
 * ID the first time it requests an ad.
 */
val testDeviceIds: List<String> = rootProject.file("local.properties").takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.getProperty("adroit.sample.testDeviceIds")
    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
    .orEmpty()

android {
    namespace = "io.github.thesobiaakbar.adroit.sample.compose"
    defaultConfig {
        applicationId = "io.github.thesobiaakbar.adroit.sample.compose"
        buildConfigField(
            "String[]",
            "TEST_DEVICE_IDS",
            testDeviceIds.joinToString(prefix = "{", postfix = "}") { "\"$it\"" },
        )
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        // Like debug (debuggable, not minified), but configured as a tester's build: STAGING (see SampleAdsConfig).
        create("staging") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
        }
    }
}

dependencies {
    implementation(project(":adroit-compose"))
    // Remote Config and Analytics (Firebase BoM, from adroit-firebase); Firebase itself starts from google-services.json.
    implementation(project(":adroit-firebase"))

    // Sample-only UI libraries. The Adroit libraries themselves never depend on these.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    // Compose ui-test brings Espresso 3.5.0, whose reflection on InputManager.getInstance fails on Android 17.
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
}
