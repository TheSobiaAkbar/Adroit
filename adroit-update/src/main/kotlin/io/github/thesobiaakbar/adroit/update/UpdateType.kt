package io.github.thesobiaakbar.adroit.update

/** How [AdroitUpdate.check] offers a newer version from Google Play (Firebase `AppUpdate.type`, docs/APP_UPDATE.md). */
public enum class UpdateType {
    /** Google Play's update prompt: the user may decline and continue; the update downloads in the background. */
    FLEXIBLE,

    /** Google Play's full-screen update: the user can't continue without updating, and backing out closes the app. */
    FORCE,

    /** No check. */
    OFF,
}
