package io.github.thesobiaakbar.adroit

/**
 * The look of a native placement's ads, chosen in the configuration (or an ad profile) so it can change without a
 * release. The `adroit-views` and `adroit-compose` helpers map each style to a template (`NativeTemplates`) when the
 * code doesn't pass a layout of its own. In Firebase, a style is its name in lower case (`"small_button_end"`); an
 * ad profile's `"style"` can also name a layout the app registered with `NativeTemplates.register`.
 *
 * Stability: new styles may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class NativeStyle {
    /** Icon, "Ad" badge, headline and body, with the call to action at the end of the same row. No media. */
    SMALL_BUTTON_END,

    /** Icon, "Ad" badge, headline and body, with a full-width call to action below. No media. */
    SMALL_BUTTON_BOTTOM,

    /** Icon, "Ad" badge, headline and body, then full-width media and a full-width call to action. The default. */
    LARGE_BUTTON_BOTTOM,

    /** Like [LARGE_BUTTON_BOTTOM], with a larger icon and shorter media: for tighter screens. */
    LARGE_COMPACT,

    /** Media at the start; the "Ad" badge, headline, body and call to action beside it, at the end. */
    MEDIA_START,
    ;

    internal companion object {
        /** A native ad's style when nothing sets one. */
        val DEFAULT: NativeStyle = LARGE_BUTTON_BOTTOM
    }
}
