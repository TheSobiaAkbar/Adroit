package io.github.thesobiaakbar.adroit

/**
 * The outcome of a native ad load. Delivered exactly once, on the main thread.
 *
 * Stability: the two outcomes won't change before the next major version, so an exhaustive `when` is safe.
 */
public sealed interface NativeAdResult {
    /** A native ad loaded. The caller owns [handle] and must [NativeAdHandle.destroy] it when done. */
    public class Loaded(public val handle: NativeAdHandle) : NativeAdResult {
        override fun toString(): String = "Loaded($handle)"
    }

    /** The load failed because of [error]. */
    public data class Failed(public val error: AdError) : NativeAdResult
}
