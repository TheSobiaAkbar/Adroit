package com.company.admob

import com.company.admob.internal.native.NativeAdHandleImpl

/** Hooks for CompanyAdMob's own modules only (ADR-012). See [InternalCompanyAdMobApi]. */
@InternalCompanyAdMobApi
public object CompanyAdMobInternal {
    /**
     * Detaches [handle] from the view it was last rendered into, without destroying the ad, so a holder that keeps
     * the handle across configuration changes (`NativeAdHolder`) retains no View or Context between renders. Main
     * thread only. Does nothing for a handle that isn't from `native.load()`.
     */
    @JvmStatic
    public fun detachRenderedView(handle: NativeAdHandle) {
        (handle as? NativeAdHandleImpl)?.detachView()
    }
}
