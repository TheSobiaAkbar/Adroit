package io.github.thesobiaakbar.adroit.sample.compose.ads

import android.app.Application
import io.github.thesobiaakbar.adroit.config.AdDefinitions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Both samples read the same Remote Config parameters (Firebase project `sample-ads-plugin`), so [SampleAds]' Firebase
 * template must equal sample-xml's committed one (`sample-xml/firebase/remote_config_template.json`).
 */
// Robolectric: org.json is part of Android. A plain Application: this test needs neither ads nor Firebase.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirebaseTemplateTest {
    @Test
    fun `SampleAds has the same Firebase parameters as sample-xml`() {
        val xmlTemplate = File("../sample-xml/firebase/remote_config_template.json").readText().replace("\r\n", "\n")
        assertEquals(xmlTemplate, AdDefinitions.firebaseTemplate(SampleAds.entries, SampleAds.settings) + "\n")
    }
}
