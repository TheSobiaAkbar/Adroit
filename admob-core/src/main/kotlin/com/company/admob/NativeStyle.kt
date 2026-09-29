package com.company.admob

/**
 * The look of a native placement's ads, chosen in the configuration (or an ad profile) so it can change without a
 * release. The `admob-views` and `admob-compose` helpers map each style to a template (`NativeTemplates`) when the
 * code doesn't pass a layout of its own. In Firebase, a style is its name in lower case (`"small_button_top"`); an
 * ad profile's `"style"` can also name a layout the app registered with `NativeTemplates.register`.
 *
 * Stability: new styles may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class NativeStyle {
    /** Icon, headline and call to action in one row. */
    SMALL,

    /** Header, body, media and call to action. The default. */
    MEDIUM,

    /** Full-width media first, then every asset. */
    LARGE,

    /** Icon, headline and body, with a full-width call to action below. No media. */
    SMALL_BUTTON_BOTTOM,

    /** A full-width call to action, with the icon, headline and body below it. No media. */
    SMALL_BUTTON_TOP,

    /** Icon, headline and body, with the call to action at the end of the same row. No media. */
    SMALL_BUTTON_END,

    /** Icon, headline and body, then full-width media and a full-width call to action. */
    LARGE_BUTTON_BOTTOM,

    /** A full-width call to action, then the icon, headline and body, then full-width media. */
    LARGE_BUTTON_TOP,

    /** Like [LARGE_BUTTON_BOTTOM], with a larger icon and shorter media: for tighter screens. */
    LARGE_COMPACT,

    /** Icon, headline and body beside the media, with a full-width call to action below. */
    SIDE_MEDIA_BUTTON_BOTTOM,

    /** A full-width call to action, with the icon, headline and body beside the media below it. */
    SIDE_MEDIA_BUTTON_TOP,
}
