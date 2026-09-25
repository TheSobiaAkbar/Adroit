package com.company.admob.internal.google

import com.company.admob.AdError
import com.company.admob.SdkErrorDetails
import com.company.admob.internal.port.SdkLoadResult
import com.google.android.libraries.ads.mobile.sdk.common.AdInspectorError
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.ump.FormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleErrorMapperTest {
    private fun load(name: String) = GoogleErrorMapper.mapLoadErrorCode(name, 7, "msg", "domain", "resp")

    /** Architecture §24: code name → (error type, retryable). Codes the table doesn't name are retryable. */
    private val loadTable: Map<String, Pair<Class<out AdError>, Boolean>> = mapOf(
        "NO_FILL" to (AdError.NoFill::class.java to true),
        "NETWORK_ERROR" to (AdError.NetworkError::class.java to true),
        "TIMEOUT" to (AdError.Timeout::class.java to true),
        "INVALID_REQUEST" to (AdError.LoadFailed::class.java to false),
        "APP_ID_MISSING" to (AdError.LoadFailed::class.java to false),
        "NOT_FOUND" to (AdError.LoadFailed::class.java to false),
        "INTERNAL_ERROR" to (AdError.LoadFailed::class.java to true),
        "CANCELLED" to (AdError.LoadFailed::class.java to true),
        "REQUEST_ID_MISMATCH" to (AdError.LoadFailed::class.java to true),
        "INVALID_AD_RESPONSE" to (AdError.LoadFailed::class.java to true),
        "AD_RESPONSE_ALREADY_USED" to (AdError.LoadFailed::class.java to true),
    )

    @Test
    fun `table covers exactly the pinned SDK's load error codes`() {
        // Fails on an SDK upgrade that adds a code, so the new code gets a deliberate mapping decision.
        assertEquals(LoadAdError.ErrorCode.values().map { it.name }.toSet(), loadTable.keys)
    }

    @Test
    fun `every load error code maps per architecture section 24`() {
        LoadAdError.ErrorCode.values().forEach { code ->
            val (expectedType, expectedRetryable) = loadTable.getValue(code.name)
            val result = load(code.name)
            assertEquals(code.name, expectedType, result.error.javaClass)
            assertEquals(code.name, expectedRetryable, result.retryable)
        }
    }

    @Test
    fun `every Google error constant is matched by identity, not by its possibly obfuscated name`() {
        LoadAdError.ErrorCode.values().forEach { assertEquals(it.name, GoogleErrorMapper.codeName(it)) }
        FullScreenContentError.ErrorCode.values().forEach { assertEquals(it.name, GoogleErrorMapper.codeName(it)) }
        AdInspectorError.ErrorCode.values().forEach { assertEquals(it.name, GoogleErrorMapper.codeName(it)) }
    }

    @Test
    fun `unknown load error code is a retryable LoadFailed`() {
        assertEquals(SdkLoadResult.Failure(AdError.LoadFailed(SdkErrorDetails("domain", 7, "SOMETHING_NEW", "msg", "resp")), true), load("SOMETHING_NEW"))
    }

    @Test
    fun `load error keeps the raw SDK details`() {
        assertEquals(SdkErrorDetails("domain", 7, "NO_FILL", "msg", "resp"), load("NO_FILL").error.sdkDetails)
    }

    @Test
    fun `Google LoadAdError overload uses the enum name and value`() {
        val result = GoogleErrorMapper.map(LoadAdError(LoadAdError.ErrorCode.NOT_FOUND, "not found", null))
        assertEquals(false, result.retryable)
        assertEquals(
            SdkErrorDetails(null, LoadAdError.ErrorCode.NOT_FOUND.value, "NOT_FOUND", "not found", null),
            result.error.sdkDetails,
        )
    }

    @Test
    fun `every full-screen show error code maps per architecture section 24`() {
        val names = FullScreenContentError.ErrorCode.values().map { it.name }.toSet()
        assertEquals(setOf("AD_REUSED", "APP_NOT_FOREGROUND", "H5_SHOW_AD_NOT_LOADED", "INTERNAL_ERROR", "MEDIATION_SHOW_ERROR"), names)
        names.forEach { name ->
            val error = GoogleErrorMapper.mapShowErrorCode(name, 3, "msg", "domain")
            if (name == "APP_NOT_FOREGROUND") {
                assertEquals(AdError.ActivityUnavailable, error)
            } else {
                assertEquals(name, AdError.ShowFailed(SdkErrorDetails("domain", 3, name, "msg", null)), error)
            }
        }
        assertTrue(GoogleErrorMapper.mapShowErrorCode("SOMETHING_NEW", 9, "msg", null) is AdError.ShowFailed)
    }

    @Test
    fun `Google FullScreenContentError overload maps`() {
        val error = FullScreenContentError(FullScreenContentError.ErrorCode.APP_NOT_FOREGROUND, "background", null)
        assertEquals(AdError.ActivityUnavailable, GoogleErrorMapper.map(error))
    }

    @Test
    fun `UMP form errors map to network, timeout or internal`() {
        assertEquals(
            AdError.NetworkError(SdkErrorDetails(GoogleErrorMapper.UMP_DOMAIN, FormError.ErrorCode.INTERNET_ERROR, "INTERNET_ERROR", "offline", null)),
            GoogleErrorMapper.mapFormErrorCode(FormError.ErrorCode.INTERNET_ERROR, "offline"),
        )
        assertTrue(GoogleErrorMapper.mapFormErrorCode(FormError.ErrorCode.TIME_OUT, "slow") is AdError.Timeout)
        assertTrue(GoogleErrorMapper.mapFormErrorCode(FormError.ErrorCode.INTERNAL_ERROR, "x") is AdError.Internal)
        assertTrue(GoogleErrorMapper.mapFormErrorCode(FormError.ErrorCode.INVALID_OPERATION, "x") is AdError.Internal)
        assertTrue(GoogleErrorMapper.mapFormErrorCode(99, "x") is AdError.Internal)
        assertTrue(GoogleErrorMapper.map(FormError(FormError.ErrorCode.TIME_OUT, "slow")) is AdError.Timeout)
    }

    @Test
    fun `Ad Inspector errors map`() {
        assertTrue(GoogleErrorMapper.mapAdInspectorErrorCode("NOT_IN_TEST_MODE", "x") is AdError.InvalidConfiguration)
        AdInspectorError.ErrorCode.values().filter { it.name != "NOT_IN_TEST_MODE" }.forEach {
            assertTrue(it.name, GoogleErrorMapper.mapAdInspectorErrorCode(it.name, "x") is AdError.Internal)
        }
    }
}
