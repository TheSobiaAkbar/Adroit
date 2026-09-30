package io.github.thesobiaakbar.adroit.internal.google

import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.SdkErrorDetails
import io.github.thesobiaakbar.adroit.internal.port.SdkLoadResult
import com.google.android.libraries.ads.mobile.sdk.common.AdInspectorError
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.ump.FormError

/**
 * Maps Google and UMP errors to [AdError] (architecture §24). Each Google overload delegates to a function over the
 * error code's name, so the table is testable without Google objects, and an unknown code from a newer SDK maps to
 * a safe default instead of failing.
 *
 * Google enum constants are matched by identity (`when` with an `else` branch), never through `Enum.name`: the SDK's
 * consumer rules don't keep enum constant names, so a host app's R8 may rename them.
 */
internal object GoogleErrorMapper {
    const val UMP_DOMAIN: String = "com.google.android.ump"

    fun map(error: LoadAdError): SdkLoadResult.Failure = mapLoadErrorCode(
        name = codeName(error.code) ?: error.code.name,
        code = error.code.value,
        message = error.message,
        domain = null,
        responseId = error.responseInfo?.responseId,
    )

    fun mapLoadErrorCode(name: String, code: Int, message: String, domain: String?, responseId: String?): SdkLoadResult.Failure {
        val details = SdkErrorDetails(domain, code, name, message, responseId)
        return when (name) {
            "NO_FILL" -> SdkLoadResult.Failure(AdError.NoFill(details), retryable = true)
            "NETWORK_ERROR" -> SdkLoadResult.Failure(AdError.NetworkError(details), retryable = true)
            "TIMEOUT" -> SdkLoadResult.Failure(AdError.Timeout(details), retryable = true)
            "INVALID_REQUEST", "APP_ID_MISSING", "NOT_FOUND" -> SdkLoadResult.Failure(AdError.LoadFailed(details), retryable = false)
            "INTERNAL_ERROR", "CANCELLED" -> SdkLoadResult.Failure(AdError.LoadFailed(details), retryable = true)
            // REQUEST_ID_MISMATCH, INVALID_AD_RESPONSE, AD_RESPONSE_ALREADY_USED and codes added by newer SDKs.
            else -> SdkLoadResult.Failure(AdError.LoadFailed(details), retryable = true)
        }
    }

    fun map(error: FullScreenContentError): AdError = mapShowErrorCode(
        name = codeName(error.code) ?: error.code.name,
        code = error.code.value,
        message = error.message,
        domain = error.mediationAdError?.domain,
    )

    /** `AD_REUSED` means the library showed an ad twice; the adapter logs it as an internal bug. */
    fun mapShowErrorCode(name: String, code: Int, message: String, domain: String?): AdError =
        when (name) {
            "APP_NOT_FOREGROUND" -> AdError.ActivityUnavailable
            else -> AdError.ShowFailed(SdkErrorDetails(domain, code, name, message, responseId = null))
        }

    fun map(error: AdInspectorError): AdError = mapAdInspectorErrorCode(codeName(error.code) ?: error.code.name, error.message)

    fun mapAdInspectorErrorCode(name: String, message: String): AdError =
        when (name) {
            "NOT_IN_TEST_MODE" -> AdError.InvalidConfiguration("Ad Inspector needs a registered test device: $message")
            else -> AdError.Internal("Ad Inspector error $name: $message")
        }

    // The `else` branches below are redundant at compile time but not at runtime: without them, a constant added by
    // a newer SDK (the host app can resolve a newer version) would throw NoWhenBranchMatchedException.

    /** The canonical name of [code], or null for a constant this table doesn't know. */
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    fun codeName(code: LoadAdError.ErrorCode): String? = when (code) {
        LoadAdError.ErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
        LoadAdError.ErrorCode.INVALID_REQUEST -> "INVALID_REQUEST"
        LoadAdError.ErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
        LoadAdError.ErrorCode.NO_FILL -> "NO_FILL"
        LoadAdError.ErrorCode.TIMEOUT -> "TIMEOUT"
        LoadAdError.ErrorCode.CANCELLED -> "CANCELLED"
        LoadAdError.ErrorCode.NOT_FOUND -> "NOT_FOUND"
        LoadAdError.ErrorCode.APP_ID_MISSING -> "APP_ID_MISSING"
        LoadAdError.ErrorCode.REQUEST_ID_MISMATCH -> "REQUEST_ID_MISMATCH"
        LoadAdError.ErrorCode.INVALID_AD_RESPONSE -> "INVALID_AD_RESPONSE"
        LoadAdError.ErrorCode.AD_RESPONSE_ALREADY_USED -> "AD_RESPONSE_ALREADY_USED"
        else -> null
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    fun codeName(code: FullScreenContentError.ErrorCode): String? = when (code) {
        FullScreenContentError.ErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
        FullScreenContentError.ErrorCode.AD_REUSED -> "AD_REUSED"
        FullScreenContentError.ErrorCode.APP_NOT_FOREGROUND -> "APP_NOT_FOREGROUND"
        FullScreenContentError.ErrorCode.MEDIATION_SHOW_ERROR -> "MEDIATION_SHOW_ERROR"
        FullScreenContentError.ErrorCode.H5_SHOW_AD_NOT_LOADED -> "H5_SHOW_AD_NOT_LOADED"
        else -> null
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    fun codeName(code: AdInspectorError.ErrorCode): String? = when (code) {
        AdInspectorError.ErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
        AdInspectorError.ErrorCode.FAILED_TO_LOAD -> "FAILED_TO_LOAD"
        AdInspectorError.ErrorCode.NOT_IN_TEST_MODE -> "NOT_IN_TEST_MODE"
        AdInspectorError.ErrorCode.ALREADY_OPEN -> "ALREADY_OPEN"
        AdInspectorError.ErrorCode.DISABLED -> "DISABLED"
        else -> null
    }

    fun map(error: FormError): AdError = mapFormErrorCode(error.errorCode, error.message)

    /**
     * UMP reports int codes. Network and timeout failures keep their details; the rest are internal errors, because
     * [AdError.Internal] can't carry [SdkErrorDetails] without a public API change. Whether ads may still be
     * requested is decided from `canRequestAds()`, not from the error.
     */
    fun mapFormErrorCode(code: Int, message: String): AdError {
        val name = formErrorName(code)
        val details = SdkErrorDetails(UMP_DOMAIN, code, name, message, responseId = null)
        return when (code) {
            FormError.ErrorCode.INTERNET_ERROR -> AdError.NetworkError(details)
            FormError.ErrorCode.TIME_OUT -> AdError.Timeout(details)
            else -> AdError.Internal("UMP error ${name ?: code}: $message")
        }
    }

    private fun formErrorName(code: Int): String? = when (code) {
        FormError.ErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
        FormError.ErrorCode.INTERNET_ERROR -> "INTERNET_ERROR"
        FormError.ErrorCode.INVALID_OPERATION -> "INVALID_OPERATION"
        FormError.ErrorCode.TIME_OUT -> "TIME_OUT"
        else -> null
    }
}
