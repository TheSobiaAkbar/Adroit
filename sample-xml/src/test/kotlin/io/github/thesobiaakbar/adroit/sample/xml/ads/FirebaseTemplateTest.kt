package io.github.thesobiaakbar.adroit.sample.xml.ads

import io.github.thesobiaakbar.adroit.config.AdDefinitions
import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Keeps `firebase/remote_config_template.json` (import it in the Firebase console: Remote Config, ⋮, "Publish from a
 * file") equal to [SampleAds]' defaults plus the `AppUpdate` parameter. After changing an entry, regenerate it:
 * `./gradlew :sample-xml:testDebugUnitTest -PupdateFirebaseTemplate`. The billing keys are in
 * docs/firebase/sample_billing_remote_config.json, in the same Firebase project.
 */
// Robolectric: org.json is part of Android. A plain Application: this test needs neither ads nor Firebase.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirebaseTemplateTest {
    @Test
    fun `the committed Firebase template matches SampleAds`() {
        val ads = JSONObject(AdDefinitions.firebaseTemplate(SampleAds.entries, SampleAds.settings))
        ads.getJSONObject("parameters").put(
            "AppUpdate",
            JSONObject()
                .put("defaultValue", JSONObject().put("value", APP_UPDATE))
                .put("valueType", "JSON")
                .put("description", "In-app update (docs/APP_UPDATE.md). Same Firebase project as the ads and billing keys"),
        )
        val expected = ads.toString(2) + "\n"
        val file = File("firebase/remote_config_template.json")
        if (System.getProperty("updateFirebaseTemplate") == "true") file.writeText(expected)
        assertEquals(
            "Out of date: run ./gradlew :sample-xml:testDebugUnitTest -PupdateFirebaseTemplate",
            expected,
            file.takeIf { it.isFile }?.readText()?.replace("\r\n", "\n"),
        )
    }

    private companion object {
        const val APP_UPDATE = """{"type":"flexible","forceBelowVersionCode":0}"""
    }
}
