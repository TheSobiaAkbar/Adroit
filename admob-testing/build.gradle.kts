plugins {
    alias(libs.plugins.companyadmob.android.library)
    alias(libs.plugins.companyadmob.publishing)
}

android {
    namespace = "com.company.admob.testing"
}

dependencies {
    api(project(":admob-core"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
