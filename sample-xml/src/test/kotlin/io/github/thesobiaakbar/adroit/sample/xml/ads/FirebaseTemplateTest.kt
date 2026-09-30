package io.github.thesobiaakbar.adroit.sample.xml.ads

import io.github.thesobiaakbar.adroit.config.AdDefinitions
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Keeps `firebase/remote_config_template.json` (import it in the Firebase console: Remote Config, ⋮, "Publish from a
 * file") equal to [SampleAds]' defaults. After changing an entry, regenerate it:
 * `./gradlew :sample-xml:testDebugUnitTest -PupdateFirebaseTemplate`.
 */
// Robolectric: org.json is part of Android. A plain Application: this test needs neither ads nor Firebase.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirebaseTemplateTest {
    @Test
    fun `the committed Firebase template matches SampleAds`() {
        val expected = AdDefinitions.firebaseTemplate(SampleAds.entries, SampleAds.settings) + "\n"
        val file = File("firebase/remote_config_template.json")
        if (System.getProperty("updateFirebaseTemplate") == "true") file.writeText(expected)
        assertEquals(
            "Out of date: run ./gradlew :sample-xml:testDebugUnitTest -PupdateFirebaseTemplate",
            expected,
            file.takeIf { it.isFile }?.readText()?.replace("\r\n", "\n"),
        )
    }
}
