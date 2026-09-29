package com.company.admob.internal.google

import android.app.Activity
import android.content.Context
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.BannerSize
import com.company.admob.SdkErrorDetails
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.PreloadedBanner
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkBannerView
import com.company.admob.internal.port.SdkInitResult
import com.company.admob.internal.port.SdkLoadResult
import com.company.admob.internal.port.SdkRequestConfig
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdPreloader
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
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
import java.util.concurrent.atomic.AtomicInteger

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

    /** Numbers each banner preload, for its preloader ID. */
    private val preloadIds = AtomicInteger()

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
        beforeInit: () -> Unit,
        onComplete: (SdkInitResult) -> Unit,
    ) {
        val done = OnceCallback(onComplete)
        scope.launch {
            val result = withContext(ioDispatcher) {
                beforeInit()
                initializeBlocking(appContext, appId, request, disableCrashReporting)
            }
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

    override fun preloadBanner(
        context: Context,
        adUnitId: String,
        size: BannerSize,
        widthDp: Int,
        extras: RequestExtras,
        onResult: (SdkLoadResult<PreloadedBanner>) -> Unit,
    ) {
        val result = OnceCallback(onResult)
        scope.launch(loadDispatcher) {
            try {
                val request = requestFactory.bannerAdRequest(adUnitId, GoogleRequestFactory.adSize(context, size, widthDp), extras)
                // Google's preloader, used for one ad: a buffer of one, stopped as soon as its ad is taken, so it never
                // loads another on its own (ad profiles load only when asked).
                val preloadId = "company_admob_banner_${preloadIds.incrementAndGet()}"
                BannerAdPreloader.start(preloadId, PreloadConfiguration(request, 1), BannerPreload(result, log))
            } catch (e: Exception) {
                log.e(e) { "Preloading a banner failed" }
                result(SdkLoadResult.Failure(AdError.Internal("Loading the banner failed", e), retryable = true))
            }
        }
    }

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
/** One banner from Google's preloader: taken as soon as it's ready, then the preloader is stopped. */
private class BannerPreload(
    private val result: OnceCallback<SdkLoadResult<PreloadedBanner>>,
    private val log: AdLog,
) : PreloadCallback {
    override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
        try {
            val ad = BannerAdPreloader.pollAd(preloadId)
            stop(preloadId)
            if (ad == null) {
                result(SdkLoadResult.Failure(AdError.Internal("The preloaded banner was gone"), retryable = true))
                return
            }
            val source = GoogleResponseInfoMapper.map(ad.getResponseInfo())
            if (!result(SdkLoadResult.Success(NextGenPreloadedBanner(ad, log), source))) ad.destroy()
        } catch (e: Exception) {
            log.e(e) { "Handling a preloaded banner failed" }
            stop(preloadId)
            result(SdkLoadResult.Failure(AdError.Internal("Handling the loaded banner failed", e), retryable = true))
        }
    }

    override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
        stop(preloadId)
        result(GoogleErrorMapper.map(adError))
    }

    private fun stop(preloadId: String) {
        try {
            BannerAdPreloader.destroy(preloadId)
        } catch (e: Exception) {
            log.w(e) { "Stopping the banner preloader failed" }
        }
    }
}

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
