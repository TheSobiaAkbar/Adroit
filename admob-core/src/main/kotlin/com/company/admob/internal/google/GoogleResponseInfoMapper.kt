package com.company.admob.internal.google

import com.company.admob.events.AdSourceInfo
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo

/** Maps Google's `ResponseInfo` to [AdSourceInfo]. */
internal object GoogleResponseInfoMapper {
    const val EXTRA_MEDIATION_GROUP_NAME: String = "mediation_group_name"
    const val EXTRA_AB_TEST_NAME: String = "mediation_ab_test_name"
    const val EXTRA_AB_TEST_VARIANT: String = "mediation_ab_test_variant"

    fun map(info: ResponseInfo?): AdSourceInfo? {
        if (info == null) return null
        val loaded = info.loadedAdSourceResponseInfo
        val extras = info.responseExtras
        return map(
            responseId = info.responseId,
            responseAdapterClassName = info.adapterClassName,
            sourceName = loaded?.name,
            sourceId = loaded?.id,
            sourceInstanceName = loaded?.instanceName,
            sourceInstanceId = loaded?.instanceId,
            sourceAdapterClassName = loaded?.adapterClassName,
            extra = { key -> extras.getString(key) },
        )
    }

    /** Plain-data form of [map]. The loaded source's adapter class wins over the response-level one. */
    fun map(
        responseId: String?,
        responseAdapterClassName: String?,
        sourceName: String?,
        sourceId: String?,
        sourceInstanceName: String?,
        sourceInstanceId: String?,
        sourceAdapterClassName: String?,
        extra: (String) -> String?,
    ): AdSourceInfo = AdSourceInfo(
        name = sourceName.nullIfBlank(),
        id = sourceId.nullIfBlank(),
        instanceName = sourceInstanceName.nullIfBlank(),
        instanceId = sourceInstanceId.nullIfBlank(),
        adapterClassName = sourceAdapterClassName.nullIfBlank() ?: responseAdapterClassName.nullIfBlank(),
        responseId = responseId.nullIfBlank(),
        mediationGroupName = extra(EXTRA_MEDIATION_GROUP_NAME).nullIfBlank(),
        abTestName = extra(EXTRA_AB_TEST_NAME).nullIfBlank(),
        abTestVariant = extra(EXTRA_AB_TEST_VARIANT).nullIfBlank(),
    )

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
}
