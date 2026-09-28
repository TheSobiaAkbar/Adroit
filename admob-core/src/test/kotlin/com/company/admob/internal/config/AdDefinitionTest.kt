package com.company.admob.internal.config

import com.company.admob.AdFormat
import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.NativeStyle
import com.company.admob.config.AdDefinition
import com.company.admob.config.AdDefinitions
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdProfile
import com.company.admob.config.AdSettings
import com.company.admob.config.BannerPlacementConfig
import com.company.admob.config.InterstitialPlacementConfig
import com.company.admob.config.NativePlacementConfig
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.config.bannerPlacement
import com.company.admob.config.interstitialPlacement
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

// Robolectric: org.json is part of Android.
@RunWith(RobolectricTestRunner::class)
class AdDefinitionTest {
    /** How an app declares its ads: the enum's entry names are the keys. */
    private enum class AppAds(override val defaults: AdProfile) : AdDefinition {
        SplashAd(AdProfile.interstitial(ids = listOf(TestIds.unit(1)), splash = true, splashTimeoutSeconds = 6)),
        HistoryAd(AdProfile.interstitial(ids = listOf(TestIds.unit(2), TestIds.unit(3)), counter = 3, timerSeconds = 25)),
        HomeBanner(AdProfile.banner(ids = listOf(TestIds.unit(4)), style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))),
        FeedNative(AdProfile.native(ids = listOf(TestIds.unit(5)), style = NativeStyle.LARGE, poolSize = 2)),
        NoIdsBanner(AdProfile.banner()),
        NoIdsRewarded(AdProfile.rewarded(ids = emptyList())),
    }

    private val settings = AdSettings(maxInterstitialsPerSession = 4, minSecondsBetweenFullScreenAds = 10)

    private fun config(environment: AdEnvironment = AdEnvironment.DEVELOPMENT) = adMobConfig {
        this.environment = environment
        appId = TestIds.APP_ID
        profiles(AppAds.entries, settings)
    }

    @Test
    fun `the enum becomes placements keyed by entry name, with every field mapped`() {
        val config = config()
        assertEquals(emptyList<String>(), config.profileIssues)
        val byKey = config.placements.associateBy { it.placement.key }
        assertEquals(AppAds.entries.map { it.name }, byKey.keys.toList())

        val splash = byKey.getValue("SplashAd") as InterstitialPlacementConfig
        assertTrue(splash.splash)
        assertEquals(6.seconds, splash.splashTimeout)
        val history = byKey.getValue("HistoryAd") as InterstitialPlacementConfig
        assertEquals(listOf(TestIds.unit(2), TestIds.unit(3)), history.adUnitIds.map { it.value })
        assertEquals(3, history.frequency.counter)
        assertEquals(25.seconds, history.frequency.minInterval)
        assertEquals(
            BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
            (byKey.getValue("HomeBanner") as BannerPlacementConfig).size,
        )
        val native = byKey.getValue("FeedNative") as NativePlacementConfig
        assertEquals(NativeStyle.LARGE, native.style)
        assertEquals(2, native.poolSize)
        assertEquals(4, config.frequency.maxInterstitialsPerSession)
        assertEquals(10.seconds, config.frequency.minIntervalBetweenFullScreenAds)
    }

    @Test
    fun `an ad without IDs uses Google's test ad unit for its format`() {
        val byKey = config().placements.associateBy { it.placement.key }
        assertEquals(listOf(GoogleDemoAdUnits.ADAPTIVE_BANNER), byKey.getValue("NoIdsBanner").adUnitIds.map { it.value })
        assertEquals(listOf(GoogleDemoAdUnits.REWARDED), byKey.getValue("NoIdsRewarded").adUnitIds.map { it.value })
    }

    @Test
    fun `in PRODUCTION, test IDs by default are a warning, but a listed demo ID is still an error`() {
        val result = ConfigValidator.validate(config(AdEnvironment.PRODUCTION), TestIds.APP_ID, isDebuggable = false)
        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(
            setOf("NoIdsBanner", "NoIdsRewarded"),
            result.warnings.filter { it.code == ConfigIssueCode.TEST_AD_UNITS_BY_DEFAULT }.map { it.placementKey }.toSet(),
        )

        val listedDemo = adMobConfig {
            environment = AdEnvironment.PRODUCTION
            appId = TestIds.APP_ID
            profiles(listOf(definition("X", AdProfile.rewarded(ids = listOf(GoogleDemoAdUnits.REWARDED)))))
        }
        val errors = ConfigValidator.validate(listedDemo, TestIds.APP_ID, isDebuggable = false).errors
        assertEquals(listOf(ConfigIssueCode.DEMO_ID_IN_PRODUCTION), errors.map { it.code })
    }

    @Test
    fun `outside PRODUCTION, test IDs by default raise no warning`() {
        val result = ConfigValidator.validate(config(), TestIds.APP_ID, isDebuggable = true)
        assertFalse(result.warnings.any { it.code == ConfigIssueCode.TEST_AD_UNITS_BY_DEFAULT })
    }

    @Test
    fun `an ads file entry without adUnitIds also gets the test ad unit`() {
        val parsed = AdProfiles.parseFile("""{"ads": {"Inter": {"format": "interstitial"}}}""")
        assertEquals(emptyList<String>(), parsed.issues)
        assertEquals(setOf("Inter"), parsed.testIdKeys)
        assertEquals(listOf(GoogleDemoAdUnits.INTERSTITIAL), parsed.placements.single().adUnitIds.map { it.value })
    }

    @Test
    fun `an entry's JSON is its Firebase value, and Firebase overrides it by entry name`() {
        val json = JSONObject(AppAds.HistoryAd.defaults.toJson())
        assertEquals("interstitial", json.getString("format"))
        assertEquals(3, json.getInt("counter"))
        assertEquals(2, json.getJSONArray("adUnitIds").length())

        val formats = config().placements.associate { it.placement.key to it.format }
        val remote = AdProfiles.parseRemote(formats, mapOf("HistoryAd" to """{"counter": 5}"""), null)
        assertEquals(emptyList<String>(), remote.issues)
        assertEquals(5, remote.overrides.placements.getValue("HistoryAd").counter)
    }

    @Test
    fun `each entry's own JSON, imported into Firebase as is, applies without warnings`() {
        val formats = config().placements.associate { it.placement.key to it.format }
        val values = AppAds.entries.associate { it.name to it.defaults.toJson() }
        val remote = AdProfiles.parseRemote(formats, values, settings.toJson())
        assertEquals(emptyList<String>(), remote.issues)
        assertEquals(AppAds.entries.size, remote.overrides.placements.size)
    }

    @Test
    fun `every profile survives the JSON round trip`() {
        val file = AdProfiles.parseFile(AdDefinitionJson.file(AppAds.entries, settings).toString())
        assertEquals(emptyList<String>(), file.issues)
        assertEquals(AppAds.entries.size, file.placements.size)
    }

    @Test
    fun `the Firebase template has one JSON parameter per ad plus the settings`() {
        val parameters = JSONObject(AdDefinitions.firebaseTemplate(AppAds.entries, settings)).getJSONObject("parameters")
        assertEquals(setOf("AdSettings") + AppAds.entries.map { it.name }, parameters.keys().asSequence().toSet())
        val history = parameters.getJSONObject("HistoryAd")
        assertEquals("JSON", history.getString("valueType"))
        assertEquals(AppAds.HistoryAd.defaults.toJson(), history.getJSONObject("defaultValue").getString("value"))
        assertEquals(4, JSONObject(parameters.getJSONObject("AdSettings").getJSONObject("defaultValue").getString("value")).getInt("maxInterstitialsPerSession"))
    }

    @Test
    fun `typed placements come from the entry, and a wrong format is refused`() {
        assertEquals("HistoryAd", AppAds.HistoryAd.interstitialPlacement().key)
        assertEquals(AdFormat.BANNER, AppAds.HomeBanner.bannerPlacement().format)
        assertThrows(IllegalArgumentException::class.java) { AppAds.HomeBanner.interstitialPlacement() }
    }

    private fun definition(name: String, profile: AdProfile) = object : AdDefinition {
        override val name: String = name
        override val defaults: AdProfile = profile
    }
}
