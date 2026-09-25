plugins {
    alias(libs.plugins.companyadmob.android.library)
    alias(libs.plugins.companyadmob.publishing)
}

android {
    namespace = "com.company.admob.views"
}

dependencies {
    api(project(":admob-core"))

    testImplementation(libs.junit)
}
