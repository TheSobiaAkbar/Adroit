package com.company.admob.internal.google

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.NativeAdAssets
import com.company.admob.Reward
import com.company.admob.events.AdSourceInfo
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.FullScreenAdListener
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.NativeAdListener
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.ServerSideVerificationOptions
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import java.util.concurrent.atomic.AtomicBoolean

/** A loaded Google full-screen ad. Every event reaches the engine on the main thread. */
internal class NextGenFullScreenAd(
    override val format: AdFormat,
    ad: Ad,
    override val source: AdSourceInfo?,
    private val log: AdLog,
) : LoadedFullScreenAd {
    @Volatile
    private var ad: Ad? = ad

    @Volatile
    private var listener: MainThreadFullScreenAdListener? = null

    override fun setServerSideVerification(customData: String?) {
        if (customData == null) return
        try {
            // The library has no user ID to send; Google requires a non-null value.
            val options = ServerSideVerificationOptions("", customData)
            when (val current = ad) {
                is RewardedAd -> current.setServerSideVerificationOptions(options)
                is RewardedInterstitialAd -> current.setServerSideVerificationOptions(options)
                else -> Unit
            }
        } catch (e: Exception) {
            log.e(e) { "Setting server-side verification options failed" }
        }
    }

    override fun show(activity: Activity, listener: FullScreenAdListener) {
        val mainListener = MainThreadFullScreenAdListener(listener)
        this.listener = mainListener
        val current = ad
        if (current == null) {
            mainListener.onFailedToShow(AdError.Internal("The ad was released before it was shown"))
            return
        }
        val events = FullScreenEvents(mainListener, log)
        try {
            when (current) {
                is InterstitialAd -> {
                    current.adEventCallback = events
                    current.show(activity)
                }
                is RewardedAd -> {
                    current.adEventCallback = events
                    current.show(activity, events)
                }
                is RewardedInterstitialAd -> {
                    current.adEventCallback = events
                    current.show(activity, events)
                }
                is AppOpenAd -> {
                    current.adEventCallback = events
                    current.show(activity)
                }
                else -> mainListener.onFailedToShow(AdError.Internal("Unsupported ad type ${current.javaClass.name}"))
            }
        } catch (e: Exception) {
            log.e(e) { "Showing a $format ad failed" }
            mainListener.onFailedToShow(AdError.Internal("Showing the ad failed", e))
        }
    }

    /** Destroys the Google ad and stops all callbacks. The engine calls this only once the show has ended. */
    override fun release() {
        listener?.clear()
        listener = null
        val current = ad ?: return
        ad = null
        try {
            current.destroy()
        } catch (e: Exception) {
            log.w(e) { "Destroying a $format ad failed" }
        }
    }
}

/** Maps Google's full-screen event and reward callbacks, which arrive on background threads, to the engine listener. */
private class FullScreenEvents(
    private val listener: MainThreadFullScreenAdListener,
    private val log: AdLog,
) : InterstitialAdEventCallback,
    RewardedAdEventCallback,
    RewardedInterstitialAdEventCallback,
    AppOpenAdEventCallback,
    OnUserEarnedRewardListener {
    override fun onAdShowedFullScreenContent() = listener.onShown()

    override fun onAdDismissedFullScreenContent() = listener.onDismissed()

    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
        if (fullScreenContentError.code == FullScreenContentError.ErrorCode.AD_REUSED) {
            log.e { "Internal bug: a full-screen ad was shown twice (${fullScreenContentError.message})" }
        }
        listener.onFailedToShow(GoogleErrorMapper.map(fullScreenContentError))
    }

    override fun onAdImpression() = listener.onImpression()

    override fun onAdClicked() = listener.onClicked()

    override fun onAdPaid(value: AdValue) = listener.onPaid(GoogleRevenueMapper.map(value))

    override fun onUserEarnedReward(reward: RewardItem) = listener.onRewardEarned(Reward(reward.type, reward.amount))
}

/** A loaded Google native ad. The owner must call [destroy]. */
internal class NextGenNativeAd(
    private val nativeAd: NativeAd,
    override val source: AdSourceInfo?,
    private val log: AdLog,
) : LoadedNativeAd {
    private val destroyed = AtomicBoolean(false)

    @Volatile
    private var listener: MainThreadNativeAdListener? = null

    override val assets: NativeAdAssets = NativeAdAssets(
        headline = nativeAd.headline.orEmpty(),
        body = nativeAd.body,
        callToAction = nativeAd.callToAction,
        advertiser = nativeAd.advertiser,
        starRating = nativeAd.starRating,
        price = nativeAd.price,
        store = nativeAd.store,
        icon = nativeAd.icon?.drawable,
        hasVideo = nativeAd.mediaContent?.hasVideoContent == true,
        mediaAspectRatio = nativeAd.mediaContent?.aspectRatio?.takeIf { it > 0f },
    )

    override fun setListener(listener: NativeAdListener?) {
        this.listener?.clear()
        val mainListener = listener?.let(::MainThreadNativeAdListener)
        this.listener = mainListener
        if (destroyed.get()) return
        try {
            nativeAd.adEventCallback = NativeEvents(mainListener)
        } catch (e: Exception) {
            log.w(e) { "Setting the native ad event callback failed" }
        }
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        listener?.clear()
        listener = null
        try {
            nativeAd.destroy()
        } catch (e: Exception) {
            log.w(e) { "Destroying a native ad failed" }
        }
    }

    override fun bindInternal(): Any = nativeAd
}

private class NativeEvents(private val listener: MainThreadNativeAdListener?) : NativeAdEventCallback {
    override fun onAdImpression() {
        listener?.onImpression()
    }

    override fun onAdClicked() {
        listener?.onClicked()
    }

    override fun onAdPaid(value: AdValue) {
        listener?.onPaid(GoogleRevenueMapper.map(value))
    }
}
