package io.github.thesobiaakbar.adroit.events

/**
 * Which ad source (network) served an ad, mapped from the Google SDK's response information. Every field is
 * optional because availability depends on the SDK, mediation setup, and ad source.
 *
 * Immutable, with identity equality. New properties may be added in minor versions.
 */
public class AdSourceInfo(
    /** Ad source name, for example `"AdMob Network"`. */
    public val name: String?,
    /** Ad source ID. */
    public val id: String?,
    /** Ad source instance (mapping) name. */
    public val instanceName: String?,
    /** Ad source instance (mapping) ID. */
    public val instanceId: String?,
    /** Mediation adapter class name, useful to tell which network served a mediated ad. */
    public val adapterClassName: String?,
    /** Ad response ID, useful when contacting Google support. */
    public val responseId: String?,
    /** Mediation group name, when the ad was served through a mediation group. */
    public val mediationGroupName: String?,
    /** Mediation A/B test name, when applicable. */
    public val abTestName: String?,
    /** Mediation A/B test variant, when applicable. */
    public val abTestVariant: String?,
) {
    override fun toString(): String =
        "AdSourceInfo(name=$name, id=$id, instanceName=$instanceName, instanceId=$instanceId, " +
            "adapterClassName=$adapterClassName, responseId=$responseId, mediationGroupName=$mediationGroupName, " +
            "abTestName=$abTestName, abTestVariant=$abTestVariant)"
}
