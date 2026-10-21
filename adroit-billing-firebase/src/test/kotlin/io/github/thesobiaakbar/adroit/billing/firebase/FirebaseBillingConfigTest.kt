package io.github.thesobiaakbar.adroit.billing.firebase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The fetch itself is the shared RemoteConfigSession's, tested in adroit-remote-config. */
@RunWith(RobolectricTestRunner::class)
class FirebaseBillingConfigTest {
    @Test
    fun `raw reads the session's active values, and updates follow its activation`() = runTest {
        val fetched = MutableStateFlow(false)
        val config = SessionBillingConfig(mapOf("AdroitBilling" to """{"enabled":false}""")::get, fetched.filter { it }.map { })

        assertEquals("""{"enabled":false}""", config.raw("AdroitBilling"))
        assertNull(config.raw("Onboarding"))

        fetched.value = true
        config.updates.first() // emits once activated, also to a late collector
    }

    @Test
    fun `without Firebase initialized the source is none and never throws`() {
        val config = FirebaseBillingConfig.source()

        assertNull(config.raw("AdroitBilling"))
        assertTrue(config === FirebaseBillingConfig.source())
    }
}
