package io.github.thesobiaakbar.adroit

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** User privacy and consent, backed by Google's User Messaging Platform. Obtain it from [AdsClient.privacy]. */
public interface PrivacyApi {
    /** Current consent information. Emits on the main thread. */
    public val consentState: StateFlow<ConsentState>

    /**
     * True when the app must offer a privacy options entry point, for example a "Privacy options" item in
     * settings. Show your entry point only while this is true.
     */
    public val isPrivacyOptionsRequired: StateFlow<Boolean>

    /**
     * Shows the privacy options form so the user can change their choices. [onDismissed] is called on the main
     * thread with null on success, or with an error. If the user withdraws consent, ads stop.
     */
    public fun showPrivacyOptions(activity: Activity, onDismissed: (AdError?) -> Unit = {})

    /** Resets consent so the consent form shows again. For testing only; ignored in production. */
    public fun resetConsentForTesting()
}

/**
 * A snapshot of the user's consent information. Immutable. New properties may be added in minor versions.
 *
 * @property status the consent status reported by UMP.
 * @property canRequestAds whether ads may be requested.
 * @property privacyOptionsRequired whether a privacy options entry point is required.
 * @property lastError the last consent error, if the latest update failed.
 */
public class ConsentState(
    public val status: ConsentStatus,
    public val canRequestAds: Boolean,
    public val privacyOptionsRequired: Boolean,
    public val lastError: AdError?,
) {
    override fun equals(other: Any?): Boolean =
        other is ConsentState && other.status == status && other.canRequestAds == canRequestAds &&
            other.privacyOptionsRequired == privacyOptionsRequired && other.lastError == lastError

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + canRequestAds.hashCode()
        result = 31 * result + privacyOptionsRequired.hashCode()
        result = 31 * result + (lastError?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "ConsentState(status=$status, canRequestAds=$canRequestAds, " +
            "privacyOptionsRequired=$privacyOptionsRequired, lastError=${lastError?.code})"

    /** Well-known consent states. */
    public companion object {
        /** Before consent information has been requested. */
        public val Unknown: ConsentState = ConsentState(ConsentStatus.UNKNOWN, false, false, null)
    }
}

/**
 * Consent status reported by UMP.
 *
 * Stability: new values may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class ConsentStatus {
    /** Not known yet; consent information hasn't been requested. */
    UNKNOWN,

    /** Consent is required but not obtained. */
    REQUIRED,

    /** Consent is not required, for example outside regulated regions. */
    NOT_REQUIRED,

    /** Consent was obtained. */
    OBTAINED,
}
