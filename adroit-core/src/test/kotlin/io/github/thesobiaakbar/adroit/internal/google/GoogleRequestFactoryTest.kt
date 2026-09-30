package io.github.thesobiaakbar.adroit.internal.google

import io.github.thesobiaakbar.adroit.GoogleRequestCustomizer
import io.github.thesobiaakbar.adroit.GoogleSdkEscapeHatch
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.RequestExtras
import io.github.thesobiaakbar.adroit.logging.AdLogger
import io.github.thesobiaakbar.adroit.logging.LogLevel
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(GoogleSdkEscapeHatch::class)
@RunWith(RobolectricTestRunner::class)
class GoogleRequestFactoryTest {
    private val errors = mutableListOf<Pair<String, Throwable?>>()
    private val factory = GoogleRequestFactory(
        AdLog(AdLogger { level, _, message, t -> if (level == LogLevel.ERROR) errors += message to t }, LogLevel.VERBOSE),
    )
    private val placement = InterstitialPlacement("level")
    private val unit = "ca-app-pub-3940256099942544/1033173712"

    private fun requestWith(customizer: GoogleRequestCustomizer): AdRequest =
        factory.adRequest(unit, RequestExtras(placement, customizer))

    @Test
    fun `the customizer gets the format's Google builder`() {
        var received: Any? = null
        requestWith { _, builder -> received = builder }
        assertTrue(received is AdRequest.Builder)
        assertEquals(emptyList<Pair<String, Throwable?>>(), errors)
    }

    @Test
    fun `a throwing customizer is logged and the request is built without it`() {
        val request = requestWith { _, _ -> throw IllegalStateException("host bug") }
        assertEquals(unit, request.adUnitId)
        assertTrue(errors.single().second is IllegalStateException)
    }

    @Test
    fun `a linkage error from a customizer built against another SDK version is contained too`() {
        val request = requestWith { _, _ -> throw NoSuchMethodError("AdRequest.Builder.removedMethod") }
        assertEquals(unit, request.adUnitId)
        assertTrue(errors.single().second is NoSuchMethodError)
    }
}
