package com.company.admob.internal.config

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/** Reads the AdMob app ID that the Google Mobile Ads and UMP SDKs take from the manifest. */
internal object ManifestAppIdReader {
    const val META_DATA_KEY: String = "com.google.android.gms.ads.APPLICATION_ID"

    /**
     * The raw meta-data value, or null when it's missing, blank, or not a string. Never throws. The value isn't
     * checked here; [ConfigValidator] reports malformed IDs.
     */
    fun read(context: Context): String? {
        val info = try {
            applicationInfo(context.packageManager, context.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return info.metaData?.getString(META_DATA_KEY)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun applicationInfo(packageManager: PackageManager, packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }
}
