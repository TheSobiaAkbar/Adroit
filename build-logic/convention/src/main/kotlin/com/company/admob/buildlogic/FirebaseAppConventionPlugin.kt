package com.company.admob.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `companyadmob.android.firebase`: an app with a real Firebase project (its `google-services.json` next to the build
 * file). Applies Google's `com.google.gms.google-services` plugin, which has to load from build-logic's classpath to
 * see AGP's classes; apply it after `companyadmob.android.sample`.
 */
class FirebaseAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.google.gms.google-services")
    }
}
