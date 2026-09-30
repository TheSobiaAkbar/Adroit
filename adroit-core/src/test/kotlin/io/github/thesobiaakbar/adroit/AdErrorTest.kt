package io.github.thesobiaakbar.adroit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class AdErrorTest {

    /** One instance of every subtype. Add new subtypes here; the count check below enforces it. */
    private val allErrors: List<AdError> = listOf(
        AdError.NotInitialized,
        AdError.ConsentRequired,
        AdError.InvalidConfiguration("reason"),
        AdError.Disabled(UnavailableReason.AD_FREE_USER),
        AdError.NoFill(),
        AdError.NetworkError(),
        AdError.Timeout(),
        AdError.LoadFailed(),
        AdError.NotReady,
        AdError.Expired,
        AdError.ShowFailed(),
        AdError.ActivityUnavailable,
        AdError.AlreadyShowing,
        AdError.FrequencyCapped(5.seconds),
        AdError.CounterNotReached(2),
        AdError.SdkInitFailed(),
        AdError.Internal("detail"),
    )

    @Test
    fun `every subtype is covered`() {
        val declared = AdError::class.java.declaredClasses.count { AdError::class.java.isAssignableFrom(it) }
        assertEquals("Update allErrors when adding an AdError subtype", declared, allErrors.size)
    }

    @Test
    fun `codes are unique and stable`() {
        val expected = listOf(
            "not_initialized", "consent_required", "invalid_configuration", "disabled", "no_fill",
            "network_error", "timeout", "load_failed", "not_ready", "expired", "show_failed",
            "activity_unavailable", "already_showing", "frequency_capped", "counter_not_reached", "sdk_init_failed",
            "internal",
        )
        assertEquals(expected, allErrors.map { it.code })
        assertEquals(allErrors.size, allErrors.map { it.code }.toSet().size)
    }

    @Test
    fun `codes are snake_case and messages are non-blank`() {
        allErrors.forEach {
            assertTrue(it.code, Regex("[a-z_]+").matches(it.code))
            assertTrue(it.code, it.message.isNotBlank())
        }
    }

    @Test
    fun `sdkDetails defaults to null`() {
        allErrors.forEach { assertNull(it.code, it.sdkDetails) }
    }

    @Test
    fun `sdkDetails is carried when provided`() {
        val details = SdkErrorDetails("domain", 3, "NO_FILL", "No ad", "resp-1")
        assertEquals(details, AdError.NoFill(details).sdkDetails)
        assertTrue(AdError.LoadFailed(details).message.contains("No ad"))
    }
}
