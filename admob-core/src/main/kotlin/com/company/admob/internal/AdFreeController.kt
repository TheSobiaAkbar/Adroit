package com.company.admob.internal

import com.company.admob.UnavailableReason
import com.company.admob.internal.logging.AdLog

/** Told when ads get blocked (ad-free, consent withdrawn, remote disable), so engines can release what they hold. */
internal fun interface AdsBlockedListener {
    fun onAdsBlocked(reason: UnavailableReason)
}

/**
 * The host's ad-free flag, and the [AdsBlockedListener]s notified when ads get blocked for any reason. The state
 * change itself (`Ready` ↔ `Unavailable(AD_FREE_USER)`) is made by the init coordinator, which owns `AdsState`.
 * Main thread only.
 *
 * Deliberately **not persisted**: the host owns the entitlement (purchases, subscriptions, refunds, family sharing,
 * account switches). A copy stored here could outlive a refund or an expired subscription, so the host applies the
 * current value on every launch instead.
 */
internal class AdFreeController(private val log: AdLog) {
    var isAdFree: Boolean = false
        private set

    private val listeners = mutableListOf<AdsBlockedListener>()

    /** Returns true if the value changed. */
    fun setAdFree(adFree: Boolean): Boolean {
        if (isAdFree == adFree) return false
        isAdFree = adFree
        return true
    }

    fun addListener(listener: AdsBlockedListener) {
        listeners += listener
    }

    fun removeListener(listener: AdsBlockedListener) {
        listeners -= listener
    }

    fun notifyBlocked(reason: UnavailableReason) {
        for (listener in listeners.toList()) {
            try {
                listener.onAdsBlocked(reason)
            } catch (e: Exception) {
                log.e(e) { "An ads-blocked listener threw" }
            }
        }
    }
}
