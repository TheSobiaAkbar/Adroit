package com.company.admob.internal.config

import android.graphics.Color
import com.company.admob.AdFormat
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.InterstitialPlacement
import com.company.admob.NativeStyle
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AppOpenPlacementConfig
import com.company.admob.config.BannerPlacementConfig
import com.company.admob.config.InterstitialPlacementConfig
import com.company.admob.config.NativePlacementConfig
import com.company.admob.config.RewardedPlacementConfig
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.config.remote.PlacementOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Robolectric: org.json is part of Android, only stubbed in plain JVM tests.
@RunWith(RobolectricTestRunner::class)
class AdProfilesTest {
    private val file = """
        {
          "settings": { "adsEnabled": true, "maxInterstitialsPerSession": 4, "minSecondsBetweenFullScreenAds": 10 },
          "ads": {
            "SplashAd": { "format": "interstitial", "splash": true, "splashTimeoutSeconds": 6,
                          "adUnitIds": ["${TestIds.unit(1)}"] },
            "HistoryAd": { "format": "interstitial", "enabled": true, "counter": 3, "timerSeconds": 25,
                           "maxPerSession": 0, "adUnitIds": ["${TestIds.unit(2)}", "${TestIds.unit(3)}"],
                           "someFutureField": "ignored" },
            "Reward": { "format": "rewarded", "maxPerSession": 2, "adUnitIds": "${TestIds.unit(4)}" },
            "Open": { "format": "app_open", "timerSeconds": 60, "adUnitIds": ["${TestIds.unit(5)}"],
                      "excludedActivities": ["com.example.SplashActivity"] },
            "HomeBanner": { "format": "banner", "style": "collapsible_bottom", "adUnitIds": ["${TestIds.unit(6)}"] },
            "FeedNative": { "format": "native", "style": "large_compact", "enabled": false, "poolSize": 2,
                            "adUnitIds": ["${TestIds.unit(7)}"] }
          }
        }
    """.trimIndent()

    @Test
    fun `the file becomes placements and settings, with every field mapped`() {
        val parsed = AdProfiles.parseFile(file)
        assertEquals(emptyList<String>(), parsed.issues)
        val byKey = parsed.placements.associateBy { it.placement.key }
        assertEquals(listOf("SplashAd", "HistoryAd", "Reward", "Open", "HomeBanner", "FeedNative"), byKey.keys.toList())

        val splash = byKey.getValue("SplashAd") as InterstitialPlacementConfig
        assertTrue(splash.splash)
        assertEquals(6.seconds, splash.splashTimeout)

        val history = byKey.getValue("HistoryAd") as InterstitialPlacementConfig
        assertFalse(history.splash)
        assertEquals(listOf(TestIds.unit(2), TestIds.unit(3)), history.adUnitIds.map { it.value })
        assertEquals(3, history.frequency.counter)
        assertEquals(25.seconds, history.frequency.minInterval)
        assertEquals("0 means no limit", Int.MAX_VALUE, history.frequency.maxPerSession)

        val reward = byKey.getValue("Reward") as RewardedPlacementConfig
        assertEquals("a single string is a one-item list", listOf(TestIds.unit(4)), reward.adUnitIds.map { it.value })
        assertEquals(2, reward.frequency.maxPerSession)

        val open = byKey.getValue("Open") as AppOpenPlacementConfig
        assertEquals(60.seconds, open.cooldown)
        assertEquals(setOf("com.example.SplashActivity"), open.excludedActivityClassNames)
        assertEquals(
            BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
            (byKey.getValue("HomeBanner") as BannerPlacementConfig).size,
        )
        val feed = byKey.getValue("FeedNative") as NativePlacementConfig
        assertEquals(NativeStyle.LARGE_COMPACT, feed.style)
        assertEquals(2, feed.poolSize)
        assertFalse(feed.enabled)

        assertEquals(4, parsed.settings.maxInterstitialsPerSession)
        assertEquals(10.seconds, parsed.settings.minIntervalBetweenFullScreenAds)
        assertTrue(parsed.settings.adsEnabled)
    }

    @Test
    fun `adMobConfig profiles() adds the placements and applies the settings`() {
        val config = adMobConfig {
            environment = AdEnvironment.PRODUCTION
            frequency { maxInterstitialsPerSession = 99 }
            profiles(file)
        }
        assertEquals(6, config.placements.size)
        assertEquals(4, config.frequency.maxInterstitialsPerSession)
        assertEquals(10.seconds, config.frequency.minIntervalBetweenFullScreenAds)
        assertTrue(config.profileIssues.isEmpty())
        assertTrue(ConfigValidator.validate(config, TestIds.APP_ID, isDebuggable = false).isValid)
    }

    @Test
    fun `settings default to ads on, no interstitial cap and no global gap`() {
        val config = adMobConfig { profiles("""{ "ads": {} }""") }
        assertTrue(config.adsEnabledByDefault)
        assertEquals(Int.MAX_VALUE, config.frequency.maxInterstitialsPerSession)
        assertEquals(Duration.ZERO, config.frequency.minIntervalBetweenFullScreenAds)
    }

    @Test
    fun `adsEnabled false in the file turns every ad off until a remote override`() {
        val config = adMobConfig { profiles("""{ "settings": { "adsEnabled": false }, "ads": {} }""") }
        assertFalse(config.adsEnabledByDefault)
        assertFalse(EffectiveConfig.from(config).adsEnabled)
    }

    @Test
    fun `problems in the file are errors reported by validation`() {
        val bad = """
            { "ads": {
                "NoFormat": { "adUnitIds": ["${TestIds.unit(1)}"] },
                "Odd": { "format": "video", "adUnitIds": ["${TestIds.unit(1)}"] },
                "BadCounter": { "format": "interstitial", "counter": -1, "adUnitIds": ["${TestIds.unit(1)}"] },
                "BadStyle": { "format": "native", "style": "huge ad!", "adUnitIds": ["${TestIds.unit(2)}"] },
                "bad-key": { "format": "banner", "adUnitIds": ["${TestIds.unit(3)}"] }
            } }
        """.trimIndent()
        val config = adMobConfig { environment = AdEnvironment.PRODUCTION; profiles(bad) }
        val issues = config.profileIssues
        assertTrue(issues.any { "NoFormat" in it && "format" in it })
        assertTrue(issues.any { "Odd" in it && "video" in it })
        assertTrue(issues.any { "BadCounter" in it && "counter" in it })
        assertTrue(issues.any { "BadStyle" in it && "style" in it })
        assertTrue(issues.any { "bad-key" in it })
        val result = ConfigValidator.validate(config, TestIds.APP_ID, isDebuggable = false)
        assertFalse(result.isValid)
        assertEquals(issues.size, result.errors.count { it.code == ConfigIssueCode.INVALID_AD_PROFILE })
    }

    @Test
    fun `invalid JSON and a missing ads object are reported, and nothing is added`() {
        assertTrue(AdProfiles.parseFile("{ not json").issues.single().contains("not valid JSON"))
        val noAds = AdProfiles.parseFile("""{ "settings": {} }""")
        assertTrue(noAds.issues.single().contains("\"ads\""))
        assertTrue(noAds.placements.isEmpty())
    }

    @Test
    fun `a malformed ad unit ID in the file is a validation error, and a demo ID too in PRODUCTION`() {
        val text = """{ "ads": { "A": { "format": "interstitial", "adUnitIds": ["oops", "${GoogleDemoAdUnits.INTERSTITIAL}"] } } }"""
        val result = ConfigValidator.validate(
            adMobConfig { environment = AdEnvironment.PRODUCTION; profiles(text) },
            TestIds.APP_ID,
            isDebuggable = false,
        )
        val codes = result.errors.map { it.code }
        assertTrue(ConfigIssueCode.AD_UNIT_ID_MALFORMED in codes)
        assertTrue(ConfigIssueCode.DEMO_ID_IN_PRODUCTION in codes)
    }

    @Test
    fun `a Firebase value becomes an override for its key, and fields left out keep the file's values`() {
        val formats = mapOf("HistoryAd" to AdFormat.INTERSTITIAL, "HomeBanner" to AdFormat.BANNER, "FeedNative" to AdFormat.NATIVE)
        val parsed = AdProfiles.parseRemote(
            formats,
            mapOf(
                "HistoryAd" to """{ "counter": 5, "timerSeconds": 30, "maxPerSession": 0,
                                    "adUnitIds": ["${TestIds.unit(8)}", "${TestIds.unit(9)}"] }""",
                "HomeBanner" to """{ "style": "rectangle" }""",
                "FeedNative" to """{ "enabled": false, "style": "small_button_end" }""",
                "NotInTheApp" to """{ "enabled": false }""",
            ),
            settings = """{ "adsEnabled": false, "maxInterstitialsPerSession": 0, "minSecondsBetweenFullScreenAds": 15 }""",
        )
        assertEquals(emptyList<String>(), parsed.issues)
        val o = parsed.overrides
        assertEquals(setOf("HistoryAd", "HomeBanner", "FeedNative"), o.placements.keys)
        val history = o.placements.getValue("HistoryAd")
        assertEquals(5, history.counter)
        assertEquals(30.seconds, history.minInterval)
        assertEquals(Int.MAX_VALUE, history.maxPerSession)
        assertEquals(listOf(TestIds.unit(8), TestIds.unit(9)), history.adUnitIds)
        assertNull(history.enabled)
        assertEquals(PlacementOverride(bannerSize = BannerSize.MediumRectangle), o.placements.getValue("HomeBanner"))
        assertEquals(PlacementOverride(enabled = false, nativeStyle = NativeStyle.SMALL_BUTTON_END), o.placements.getValue("FeedNative"))
        assertEquals(false, o.adsEnabled)
        assertEquals(Int.MAX_VALUE, o.maxInterstitialsPerSession)
        assertEquals(15.seconds, o.minIntervalBetweenFullScreenAds)
    }

    @Test
    fun `in a Firebase value a bad field is dropped and the rest applies`() {
        val parsed = AdProfiles.parseRemote(
            mapOf("HistoryAd" to AdFormat.INTERSTITIAL),
            mapOf("HistoryAd" to """{ "counter": "three", "enabled": false, "timerSeconds": -5, "splash": true }"""),
            settings = null,
        )
        assertEquals(PlacementOverride(enabled = false), parsed.overrides.placements.getValue("HistoryAd"))
        assertTrue(parsed.issues.any { "counter" in it })
        assertTrue(parsed.issues.any { "timerSeconds" in it })
        assertTrue(parsed.issues.any { "splash" in it })
    }

    @Test
    fun `a Firebase value with another format, or invalid JSON, is ignored whole`() {
        val parsed = AdProfiles.parseRemote(
            mapOf("HistoryAd" to AdFormat.INTERSTITIAL, "HomeBanner" to AdFormat.BANNER),
            mapOf("HistoryAd" to """{ "format": "banner", "enabled": false }""", "HomeBanner" to "{ nope"),
            settings = "[]",
        )
        assertTrue(parsed.overrides.placements.isEmpty())
        assertNull(parsed.overrides.adsEnabled)
        assertEquals(3, parsed.issues.size)
    }

    @Test
    fun `blank Firebase values are skipped silently`() {
        val parsed = AdProfiles.parseRemote(mapOf("A" to AdFormat.BANNER), mapOf("A" to ""), settings = "")
        assertTrue(parsed.overrides.placements.isEmpty())
        assertTrue(parsed.issues.isEmpty())
    }

    @Test
    fun `remote ad unit lists drop bad entries one by one, and a list with none left is ignored`() {
        val config = adMobConfig {
            environment = AdEnvironment.PRODUCTION
            placements {
                interstitial(InterstitialPlacement("HistoryAd"), TestIds.unit(1))
                interstitial(InterstitialPlacement("MenuAd"), TestIds.unit(2))
            }
        }
        val validation = ConfigValidator.validateOverrides(
            com.company.admob.config.remote.AdOverrides(
                placements = mapOf(
                    "HistoryAd" to PlacementOverride(adUnitIds = listOf("bad", TestIds.unit(5), GoogleDemoAdUnits.INTERSTITIAL)),
                    "MenuAd" to PlacementOverride(adUnitIds = listOf("bad"), counter = 2),
                ),
            ),
            config,
        )
        val accepted = validation.accepted.placements
        assertEquals(listOf(TestIds.unit(5)), accepted.getValue("HistoryAd").adUnitIds)
        assertNull(accepted.getValue("MenuAd").adUnitIds)
        assertEquals(2, accepted.getValue("MenuAd").counter)
        assertEquals(3, validation.issues.size)
        assertEquals(TestIds.unit(2), EffectiveConfig.from(config).withOverrides(validation.accepted).placement(InterstitialPlacement("MenuAd"))!!.adUnitId)
    }

    @Test
    fun `mixed-case keys work as placement keys`() {
        val config = adMobConfig { profiles(file) }
        assertTrue(config.placements.any { it.placement == InterstitialPlacement("HistoryAd") })
        assertTrue(config.placements.any { it.placement == BannerPlacement("HomeBanner") })
    }

    @Test
    fun `a native style is a library style's name or a registered layout's name`() {
        val file = """
            { "ads": {
                "Top": { "format": "native", "style": "small_button_bottom" },
                "Side": { "format": "native", "style": "Media_Start" },
                "Promo": { "format": "native", "style": "my_promo" },
                "Bad": { "format": "native", "style": "not a name!" }
            } }
        """.trimIndent()
        val parsed = AdProfiles.parseFile(file)
        val byKey = parsed.placements.associateBy { it.placement.key }.mapValues { it.value as NativePlacementConfig }
        assertEquals(NativeStyle.SMALL_BUTTON_BOTTOM, byKey.getValue("Top").style)
        assertNull(byKey.getValue("Top").layoutName)
        assertEquals("case doesn't matter", NativeStyle.MEDIA_START, byKey.getValue("Side").style)
        assertEquals("my_promo", byKey.getValue("Promo").layoutName)
        assertEquals("the fallback template", NativeStyle.LARGE_BUTTON_BOTTOM, byKey.getValue("Promo").style)
        assertNull(byKey.getValue("Bad").layoutName)
        assertEquals(1, parsed.issues.size)
        assertTrue(parsed.issues.single(), "style" in parsed.issues.single())
    }

    @Test
    fun `a Firebase style replaces the ad's look, whether a library style or a registered layout`() {
        val parsed = AdProfiles.parseRemote(
            mapOf("A" to AdFormat.NATIVE, "B" to AdFormat.NATIVE, "C" to AdFormat.BANNER),
            mapOf(
                "A" to """{ "style": "promo" }""",
                "B" to """{ "style": "large_button_bottom" }""",
                "C" to """{ "format": "native", "style": "promo", "adUnitIds": ["${TestIds.unit(3)}"] }""",
            ),
            settings = null,
        )
        assertEquals(emptyList<String>(), parsed.issues)
        val a = parsed.overrides.placements.getValue("A")
        assertEquals("promo", a.nativeLayoutName)
        assertNull(a.nativeStyle)
        assertEquals(NativeStyle.LARGE_BUTTON_BOTTOM, parsed.overrides.placements.getValue("B").nativeStyle)
        assertEquals("a banner swapped to native takes the layout", "promo", parsed.overrides.placements.getValue("C").nativeLayoutName)
    }

    @Test
    fun `native colors come from an ad's Firebase value or the settings, and are checked`() {
        val parsed = AdProfiles.parseRemote(
            mapOf("Feed" to AdFormat.NATIVE, "Bad" to AdFormat.NATIVE, "Inter" to AdFormat.INTERSTITIAL),
            mapOf(
                "Feed" to """{ "ctaColors": ["#FF6D00", "#FFC107"], "ctaTextColor": "#FFFFFF", "attributionColor": "#80FFC107" }""",
                "Bad" to """{ "ctaColors": ["#FF6D00", "#FFC107", "#000000"], "attributionColor": "gold" }""",
                "Inter" to """{ "ctaColors": "#FF6D00" }""",
            ),
            settings = """{ "ctaColors": "#123456", "attributionTextColor": "#000000" }""",
        )
        val feed = checkNotNull(parsed.overrides.placements.getValue("Feed").behavior)
        assertEquals(listOf(Color.parseColor("#FF6D00"), Color.parseColor("#FFC107")), feed.ctaColors)
        assertEquals(Color.WHITE, feed.ctaTextColor)
        assertEquals(Color.parseColor("#80FFC107"), feed.attributionColor)
        assertNull("three colors and a color name are rejected", parsed.overrides.placements["Bad"]?.behavior)
        assertTrue(parsed.issues.toString(), parsed.issues.any { "Bad" in it && "ctaColors" in it })
        assertTrue(parsed.issues.any { "Bad" in it && "attributionColor" in it })
        assertTrue(parsed.issues.any { "Inter" in it && "native ads only" in it })
        val settings = checkNotNull(parsed.overrides.behavior)
        assertEquals("a single color is a one-color list", listOf(Color.parseColor("#123456")), settings.ctaColors)

        // The ad's own colors win; what it leaves out comes from the settings.
        val resolved = feed.orElse(settings)
        assertEquals(feed.ctaColors, resolved.ctaColors)
        assertEquals(Color.BLACK, resolved.attributionTextColor)
    }
}
