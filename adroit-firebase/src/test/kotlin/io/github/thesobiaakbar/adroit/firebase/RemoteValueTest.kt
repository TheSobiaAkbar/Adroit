package io.github.thesobiaakbar.adroit.firebase

import io.github.thesobiaakbar.adroit.firebase.internal.ActivationSignal
import io.github.thesobiaakbar.adroit.firebase.internal.RemoteConfigSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteValueTest {
    private val values = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        RemoteValue.sourceOverride = object : RemoteConfigSource {
            override fun string(key: String): String? = values[key]
            override fun addUpdateListener(onUpdate: () -> Unit): () -> Unit = {}
            override fun activate(onDone: () -> Unit) = onDone()
            override fun fetchAndActivate(onDone: (Boolean) -> Unit) = onDone(true)
        }
    }

    @After
    fun tearDown() {
        RemoteValue.sourceOverride = null
    }

    @Test
    fun `a parameter missing from Firebase reads as its default`() {
        assertTrue(RemoteValue.boolean("show_rate", default = true).value)
        assertEquals(5L, RemoteValue.long("free_levels", default = 5).value)
        assertEquals("Hi", RemoteValue.string("welcome", default = "Hi").value)
        assertEquals(0, RemoteValue.json("features").value.length())
    }

    @Test
    fun `Firebase values are read by type`() {
        values += mapOf(
            "show_rate" to "true",
            "dark" to "0",
            "free_levels" to " 12 ",
            "price" to "1.5",
            "welcome" to "",
            "features" to """{"chat": true, "limit": 3}""",
        )
        assertTrue(RemoteValue.boolean("show_rate", default = false).value)
        assertFalse(RemoteValue.boolean("dark", default = true).value)
        assertEquals(12L, RemoteValue.long("free_levels", default = 5).value)
        assertEquals(1.5, RemoteValue.double("price", default = 0.0).value, 0.0)
        assertEquals("an empty string is a value", "", RemoteValue.string("welcome", default = "Hi").value)
        val features = RemoteValue.json("features").value
        assertTrue(features.getBoolean("chat"))
        assertEquals(3, features.getInt("limit"))
    }

    @Test
    fun `an invalid value reads as the default`() {
        values += mapOf("free_levels" to "many", "show_rate" to "maybe", "features" to "[1, 2]", "price" to "NaN")
        assertEquals(5L, RemoteValue.long("free_levels", default = 5).value)
        assertTrue(RemoteValue.boolean("show_rate", default = true).value)
        assertEquals("fallback", RemoteValue.json("features", JSONObject().put("a", "fallback")).value.getString("a"))
        assertEquals(2.0, RemoteValue.double("price", default = 2.0).value, 0.0)
    }

    @Test
    fun `custom parses the app's own type, and a failed parse reads as the default`() {
        data class Offer(val percent: Int)
        val offer = RemoteValue.custom("offer", Offer(0)) { Offer(JSONObject(it).getInt("percent")) }
        assertEquals(Offer(0), offer.value)
        values["offer"] = """{"percent": 30}"""
        assertEquals(Offer(30), offer.value)
        values["offer"] = "{}"
        assertEquals("a throwing parse", Offer(0), offer.value)
    }

    @Test
    fun `a JSON read is a copy`() {
        val json = RemoteValue.json("features", JSONObject().put("chat", true))
        json.value.put("chat", false)
        assertTrue(json.value.getBoolean("chat"))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `updates emits the value now and each changed value after an activation`() = runTest(UnconfinedTestDispatcher()) {
        val levels = RemoteValue.long("free_levels", default = 5)
        val seen = mutableListOf<Long>()
        val job = launch { levels.updates().take(2).toList(seen) }
        ActivationSignal.notifyActivated() // nothing changed: no emission
        values["free_levels"] = "9"
        ActivationSignal.notifyActivated()
        job.join()
        assertEquals(listOf(5L, 9L), seen)
    }
}
