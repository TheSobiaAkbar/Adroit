package com.company.admob

/** The outcome of a native ad load. Delivered exactly once, on the main thread. */
public sealed interface NativeAdResult {
    /** A native ad loaded. The caller owns [handle] and must [NativeAdHandle.destroy] it when done. */
    public class Loaded(public val handle: NativeAdHandle) : NativeAdResult {
        override fun toString(): String = "Loaded($handle)"
    }

    /** The load failed because of [error]. */
    public data class Failed(public val error: AdError) : NativeAdResult
}
