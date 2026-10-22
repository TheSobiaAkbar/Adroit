package io.github.thesobiaakbar.adroit

/**
 * Where a banner or native ad shows its close button, if it has one. The button sits outside the ad, in a row of its
 * own above or below it, so it never covers the ad's content or its AdChoices icon. Tapping it changes nothing in the
 * slot: it emits `AdEvent.CloseClicked`, and the app decides what follows (docs/AD_PROFILES.md, "Close button").
 *
 * In Firebase, a position is its name in lower case (`"top_end"`). Start and end follow the layout direction.
 */
public enum class AdCloseButton {
    /** No close button. The default. */
    NONE,

    /** Above the ad, at its start. */
    TOP_START,

    /** Above the ad, at its end. */
    TOP_END,

    /** Below the ad, at its start. */
    BOTTOM_START,

    /** Below the ad, at its end. */
    BOTTOM_END,
    ;

    internal val isTop: Boolean get() = this == TOP_START || this == TOP_END

    internal val isStart: Boolean get() = this == TOP_START || this == BOTTOM_START
}
