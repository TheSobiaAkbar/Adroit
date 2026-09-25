package com.company.admob.internal.google

import android.app.Activity
import android.content.Context
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.SdkErrorDetails
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkBannerView
import com.company.admob.internal.port.SdkInitResult
import com.company.admob.internal.port.SdkLoadResult
import com.company.admob.internal.port.SdkRequestConfig
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [AdsSdk] over the GMA Next-Gen SDK, the only production implementation.
 *
 * Threading: `MobileAds.initialize` blocks and must not run on the main thread, so it runs on [ioDispatcher]. Loads
 * are started from [loadDispatcher]. Google calls back on background threads; every result goes through
 * [OnceCallback] and every event through the main-thread listeners, so the engines only ever see the main thread.
 * Every Google call is wrapped, so an SDK exception becomes an [AdError] instead of a crash.
 */
internal class NextGenAdsSdk(
    private val scope: CoroutineScope,
    private val log: AdLog,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AdsSdk {
    private val requestFactory = GoogleRequestFactory(log)

    override val sdkVersion: String?
        get() = try {
            MobileAds.getVersion().let { "${it.majorVersion}.${it.minorVersion}.${it.microVersion}" }
        } catch (e: Exception) {
            log.w(e) { "Reading the Google Mobile Ads SDK version failed" }
            null
        }

    override fun initialize(
        appContext: Context,
        appId: String,
        request: SdkRequestConfig,
        disableCrashReporting: Boolean,
        onComplete: (SdkInitResult) -> Unit,
    ) {
        val done = OnceCallback(onComplete)
        scope.launch {
            val result = withContext(ioDispatcher) { initializeBlocking(appContext, appId, request, disableCrashReporting) }
            done(result)
        }
    }

    // Next-Gen 1.4.0 has no initialization-failure callback: `initialize` returns once the SDK is usable and reports
    // failure by throwing. The listener only reports mediation adapter status, which the SDK caps at 30 seconds.
    private fun initializeBlocking(
        appContext: Context,
        appId: String,
        request: SdkRequestConfig,
        disableCrashReporting: Boolean,
    ): SdkInitResult = try {
        val config = InitializationConfig.Builder(appId)
            .setRequestConfiguration(GoogleRequestConfigMapper.map(request))
            .apply { if (disableCrashReporting) disableSdkCrashReporting() }
            .build()
        MobileAds.initialize(appContext, config) { status ->
            log.d { "Mediation adapters initialized: ${status.adapterStatusMap.keys}" }
        }
        SdkInitResult.Success
    } catch (e: Exception) {
        log.e(e) { "Google Mobile Ads SDK initialization failed" }
        SdkInitResult.Failure(AdError.SdkInitFailed(e.toSdkErrorDetails()))
    }

    override fun updateRequestConfig(request: SdkRequestConfig) {
        try {
            MobileAds.setRequestConfiguration(GoogleRequestConfigMapper.map(request))
        } catch (e: Exception) {
            log.e(e) { "Updating the Google request configuration failed" }
        }
    }

    override fun loadFullScreen(
        format: AdFormat,
        adUnitId: String,
        extras: RequestExtras,
        onResult: (SdkLoadResult<LoadedFullScreenAd>) -> Unit,
    ) {
        val result = OnceCallback(onResult)
        scope.launch(loadDispatcher) {
            try {
                val request = requestFactory.adRequest(adUnitId, extras)
                when (format) {
                    AdFormat.INTERSTITIAL -> InterstitialAd.load(request, FullScreenLoad(format, result, log))
                    AdFormat.REWARDED -> RewardedAd.load(request, FullScreenLoad(format, result, log))
                    AdFormat.REWARDED_INTERSTITIAL -> RewardedInterstitialAd.load(request, FullScreenLoad(format, result, log))
                    AdFormat.APP_OPEN -> AppOpenAd.load(request, FullScreenLoad(format, result, log))
                    else -> result(SdkLoadResult.Failure(AdError.Internal("$format is not a full-screen format"), retryable = false))
                }
            } catch (e: Exception) {
                log.e(e) { "Loading a $format ad failed" }
                result(SdkLoadResult.Failure(AdError.Internal("Loading the ad failed", e), retryable = true))
            }
        }
    }

    override fun loadNative(adUnitId: String, extras: RequestExtras, onResult: (SdkLoadResult<LoadedNativeAd>) -> Unit) {
        val result = OnceCallback(onResult)
        scope.launch(loadDispatcher) {
            try {
                NativeAdLoader.load(requestFactory.nativeAdRequest(adUnitId, extras), NativeLoad(result, log))
            } catch (e: Exception) {
                log.e(e) { "Loading a native ad failed" }
                result(SdkLoadResult.Failure(AdError.Internal("Loading the ad failed", e), retryable = true))
            }
        }
    }

    override fun createBannerView(activity: Activity): SdkBannerView = NextGenBannerView(activity, requestFactory, log)

    override fun openAdInspector(onClosed: (AdError?) -> Unit) {
        val closed = OnceCallback(onClosed)
        try {
            MobileAds.openAdInspector { error -> closed(error?.let(GoogleErrorMapper::map)) }
        } catch (e: Exception) {
            log.e(e) { "Opening Ad Inspector failed" }
            closed(AdError.Internal("Opening Ad Inspector failed", e))
        }
    }

    private fun Exception.toSdkErrorDetails() =
        SdkErrorDetails(domain = null, code = -1, codeName = javaClass.name, message = message.orEmpty(), responseId = null)
}

/** Delivers one full-screen load result. An ad arriving after a result was already delivered is destroyed. */
private class FullScreenLoad<T : Ad>(
    private val format: AdFormat,
    private val result: OnceCallback<SdkLoadResult<LoadedFullScreenAd>>,
    private val log: AdLog,
) : AdLoadCallback<T> {
    override fun onAdLoaded(ad: T) {
        try {
            val source = GoogleResponseInfoMapper.map(ad.getResponseInfo())
            if (!result(SdkLoadResult.Success(NextGenFullScreenAd(format, ad, source, log), source))) ad.destroy()
        } catch (e: Exception) {
            log.e(e) { "Handling a loaded $format ad failed" }
            result(SdkLoadResult.Failure(AdError.Internal("Handling the loaded ad failed", e), retryable = true))
        }
    }

    override fun onAdFailedToLoad(adError: LoadAdError) {
        result(GoogleErrorMapper.map(adError))
    }
}

/** Delivers one native load result. Only `NATIVE` ads are requested, so other ad types never arrive. */
private class NativeLoad(
    private val result: OnceCallback<SdkLoadResult<LoadedNativeAd>>,
    private val log: AdLog,
) : NativeAdLoaderCallback {
    override fun onNativeAdLoaded(nativeAd: NativeAd) {
        try {
            val source = GoogleResponseInfoMapper.map(nativeAd.getResponseInfo())
            if (!result(SdkLoadResult.Success(NextGenNativeAd(nativeAd, source, log), source))) nativeAd.destroy()
        } catch (e: Exception) {
            log.e(e) { "Handling a loaded native ad failed" }
            result(SdkLoadResult.Failure(AdError.Internal("Handling the loaded ad failed", e), retryable = true))
        }
    }

    override fun onAdFailedToLoad(adError: LoadAdError) {
        result(GoogleErrorMapper.map(adError))
    }
}
