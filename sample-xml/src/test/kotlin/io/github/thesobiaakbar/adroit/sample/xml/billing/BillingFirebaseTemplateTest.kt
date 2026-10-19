package io.github.thesobiaakbar.adroit.sample.xml.billing

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Keeps `docs/firebase/sample_billing_remote_config.json` (Firebase console: Remote Config, ⋮, "Publish from a file")
 * equal to [SamplePaywalls]' defaults plus an `AdroitBilling` example. After changing an entry, regenerate it:
 * `./gradlew :sample-xml:testDebugUnitTest -PupdateFirebaseTemplate`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BillingFirebaseTemplateTest {
    @Test
    fun `the committed billing Firebase template matches SamplePaywalls`() {
        val parameters = JSONObject().put("AdroitBilling", parameter(GLOBAL, "Billing-wide settings (adroit-billing)"))
        SamplePaywalls.entries.forEach { parameters.put(it.name, parameter(JSONObject(it.toJson()), "The ${it.name} paywall")) }
        val expected = JSONObject().put("parameters", parameters).toString(2) + "\n"

        val file = File("../docs/firebase/sample_billing_remote_config.json")
        if (System.getProperty("updateFirebaseTemplate") == "true") file.writeText(expected)
        assertEquals(
            "Out of date: run ./gradlew :sample-xml:testDebugUnitTest -PupdateFirebaseTemplate",
            expected,
            file.takeIf { it.isFile }?.readText()?.replace("\r\n", "\n"),
        )
    }

    private fun parameter(value: JSONObject, description: String) = JSONObject()
        .put("defaultValue", JSONObject().put("value", value.toString()))
        .put("valueType", "JSON")
        .put("description", description)

    private companion object {
        val GLOBAL: JSONObject = JSONObject()
            .put("enabled", true)
            .put("adFreeEntitlements", org.json.JSONArray(listOf("premium")))
            .put("offlineGraceHours", 72)
            .put("products", JSONObject().put("PremiumSub", JSONObject().put("entitlements", org.json.JSONArray(listOf("premium")))))
            .put("configVersion", "sample-1")
    }
}
