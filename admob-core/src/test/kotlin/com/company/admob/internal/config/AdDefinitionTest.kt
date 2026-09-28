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
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.config.bannerPlacement
import com.company.admob.config.interstitialPlacement
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
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

    // --- Loading ---

    private enum class LoadingAds(override val defaults: AdProfile) : AdDefinition {
        Once(AdProfile.interstitial()),
        Again(AdProfile.interstitial(loadAgain = true)),
        AgainRewarded(AdProfile.rewarded(loadAgain = true)),
        OnceAppOpen(AdProfile.appOpen()),
        AgainNative(AdProfile.native(loadAgain = true)),
        Banner(AdProfile.banner()),
    }

    @Test
    fun `every profile loads only when asked, loadAgain is false unless set, and the JSON carries it`() {
        val config = adMobConfig {
            appId = TestIds.APP_ID
            profiles(LoadingAds.entries, AdSettings())
        }
        val fullScreen = config.placements.filter { it.format != AdFormat.BANNER }
        assertTrue(fullScreen.all { it.preload == PreloadStrategy.NONE })
        assertEquals(setOf("Again", "AgainRewarded", "AgainNative"), config.loadAgainPlacementKeys)
        val effective = EffectiveConfig.from(config)
        assertEquals(true, effective.active("Again")?.loadAgain)
        assertEquals(false, effective.active("OnceAppOpen")?.loadAgain)

        assertEquals(false, JSONObject(LoadingAds.Once.defaults.toJson()).getBoolean("loadAgain"))
        assertEquals(true, JSONObject(LoadingAds.Again.defaults.toJson()).getBoolean("loadAgain"))
        assertFalse("banners load with their view", JSONObject(LoadingAds.Banner.defaults.toJson()).has("loadAgain"))
        assertFalse(LoadingAds.Banner.defaults.loadAgain)
    }

    @Test
    fun `an ads file sets loadAgain per ad, and a banner's is ignored with a message`() {
        val parsed = AdProfiles.parseFile(
            """{ "ads": {
                "A": { "format": "interstitial", "loadAgain": true },
                "B": { "format": "interstitial" },
                "C": { "format": "banner", "loadAgain": true } } }""",
        )
        assertEquals(setOf("A"), parsed.loadAgainKeys)
        assertTrue(parsed.placements.filter { it.format != AdFormat.BANNER }.all { it.preload == PreloadStrategy.NONE })
        assertTrue(parsed.issues.single().contains("banners"))
    }

    @Test
    fun `Firebase can turn loadAgain on or off per ad, but not for a banner`() {
        val parsed = AdProfiles.parseRemote(
            mapOf("A" to AdFormat.INTERSTITIAL, "B" to AdFormat.NATIVE, "C" to AdFormat.BANNER, "D" to AdFormat.REWARDED),
            mapOf(
                "A" to """{ "loadAgain": true }""",
                "B" to """{ "loadAgain": false }""",
                "C" to """{ "loadAgain": true }""",
                "D" to """{ "loadAgain": "yes" }""",
            ),
            settings = null,
        )
        val placements = parsed.overrides.placements
        assertEquals(true, placements.getValue("A").loadAgain)
        assertEquals(false, placements.getValue("B").loadAgain)
        assertEquals(PlacementOverride(), placements.getValue("C"))
        assertEquals(null, placements.getValue("D").loadAgain)
        assertEquals(2, parsed.issues.size)

        val config = adMobConfig {
            appId = TestIds.APP_ID
            profiles(LoadingAds.entries, AdSettings())
        }
        val remote = AdOverrides(placements = mapOf("Once" to placements.getValue("A"), "Again" to placements.getValue("B")))
        val effective = EffectiveConfig.from(config).withOverrides(remote)
        assertEquals(true, effective.active("Once")?.loadAgain)
        assertEquals(false, effective.active("Again")?.loadAgain)
    }

    // --- Loading behaviour (PROMPT 29) ---

    private enum class BehaviorAds(override val defaults: AdProfile) : AdDefinition {
        Plain(AdProfile.interstitial()),
        Own(AdProfile.rewarded(loadTimeoutSeconds = 4, showLoading = false, loadingSeconds = 2)),
        Banner(AdProfile.banner(showLoading = false)),
    }

    @Test
    fun `loading fields resolve from the ad, then Firebase, then AdSettings, then the defaults`() {
        val config = adMobConfig {
            appId = TestIds.APP_ID
            profiles(BehaviorAds.entries, AdSettings(loadTimeoutSeconds = 6))
        }
        val local = EffectiveConfig.from(config)
        assertEquals(AdBehavior(6.seconds, true, 1.seconds), local.active("Plain")?.behavior)
        assertEquals(AdBehavior(4.seconds, false, 2.seconds), local.active("Own")?.behavior)
        assertEquals(false, local.active("Banner")?.behavior?.showLoading)

        val remote = AdProfiles.parseRemote(
            mapOf("Plain" to AdFormat.INTERSTITIAL, "Own" to AdFormat.REWARDED),
            mapOf("Own" to """{ "loadTimeoutSeconds": 9 }"""),
            settings = """{ "loadTimeoutSeconds": 3, "showLoading": false }""",
        )
        assertEquals(emptyList<String>(), remote.issues)
        val live = local.withOverrides(remote.overrides)
        assertEquals("Firebase AdSettings beat the app's", AdBehavior(3.seconds, false, 1.seconds), live.active("Plain")?.behavior)
        assertEquals("the ad's Firebase value beats everything", 9.seconds, live.active("Own")?.behavior?.loadTimeout)
        assertEquals("the ad's own value beats AdSettings", false, live.active("Own")?.behavior?.showLoading)

        val json = JSONObject(BehaviorAds.Own.defaults.toJson())
        assertEquals(4, json.getInt("loadTimeoutSeconds"))
        assertFalse("unset: follows AdSettings", JSONObject(BehaviorAds.Plain.defaults.toJson()).has("loadTimeoutSeconds"))
        assertEquals(6, JSONObject(AdSettings(loadTimeoutSeconds = 6).toJson()).getInt("loadTimeoutSeconds"))
    }

    @Test
    fun `timeouts on a banner or native ad are ignored with a message`() {
        val remote = AdProfiles.parseRemote(
            mapOf("B" to AdFormat.BANNER),
            mapOf("B" to """{ "loadTimeoutSeconds": 3, "showLoading": false }"""),
            settings = null,
        )
        assertEquals(AdBehavior(showLoading = false), remote.overrides.placements.getValue("B").behavior)
        assertTrue(remote.issues.single().contains("full-screen ads only"))
    }
}
