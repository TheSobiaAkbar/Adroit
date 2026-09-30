package io.github.thesobiaakbar.adroit.config

import android.app.Activity
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.config.remote.StaticConfigProvider
import io.github.thesobiaakbar.adroit.events.AdEventListener
import io.github.thesobiaakbar.adroit.internal.config.ConfigIssueCode
import io.github.thesobiaakbar.adroit.internal.config.ConfigValidator
import io.github.thesobiaakbar.adroit.logging.AndroidAdLogger
import io.github.thesobiaakbar.adroit.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AdMobConfigDslTest {
    private val listener = AdEventListener { }

    private fun kotlinConfig() = adMobConfig {
        environment = AdEnvironment.STAGING
        appId = "ca-app-pub-1234567890123456~1234567890"
        testing {
            testDeviceIds += "33BE2250B43518CCDA7DE426D04EE231"
            useDemoAdUnits = false
        }
        privacy {
            ageTreatment = AgeTreatment.TEEN
            maxAdContentRating = MaxAdContentRating.T
            consentSyncId = "sync-user-42"
        }
        logging { level = LogLevel.INFO }
        advanced { failFast = false }
        eventListeners += listener
        placements {
            banner(BannerPlacement("home_banner"), "ca-app-pub-1234567890123456/0000000001")
            interstitial(InterstitialPlacement("level_complete"), "ca-app-pub-1234567890123456/0000000002")
            rewarded(RewardedPlacement("extra_life"), "ca-app-pub-1234567890123456/0000000003")
            rewardedInterstitial(RewardedInterstitialPlacement("bonus_round"), "ca-app-pub-1234567890123456/0000000004")
            appOpen(AppOpenPlacement("app_open"), "ca-app-pub-1234567890123456/0000000005")
            native(NativePlacement("article_native"), "ca-app-pub-1234567890123456/0000000006")
        }
    }

    @Test
    fun `placement configs name their public type without reflection, which R8 would rename`() {
        kotlinConfig().placements.forEach { config ->
            assertTrue(config.toString(), config.toString().startsWith("${config::class.simpleName}(placement="))
        }
    }

    @Test
    fun `DSL and Java builder produce equal configs`() {
        val fromKotlin = kotlinConfig()
        val fromJava = JavaConfigBuilder.build(listener)
        assertEquals(fromKotlin, fromJava)
        assertEquals(fromKotlin.hashCode(), fromJava.hashCode())
        assertEquals(fromKotlin.toString(), fromJava.toString())
    }

    @Test
    fun `configs built separately from the same code are equal`() {
        assertEquals(kotlinConfig(), kotlinConfig())
        assertEquals(adMobConfig { }, AdMobConfig.Builder().build())
    }

    @Test
    fun `any changed setting makes configs unequal`() {
        val base = kotlinConfig()
        val variants = listOf(
            adMobConfig { environment = AdEnvironment.PRODUCTION },
            adMobConfig { privacy { restrictedDataProcessing = true } },
            adMobConfig { frequency { minIntervalBetweenFullScreenAds = 10.seconds } },
            adMobConfig { advanced { showTimeout = 6.seconds } },
        )
        variants.forEach { assertNotEquals(base, it) }
        assertNotEquals(adMobConfig { }, adMobConfig { frequency { includeRewardedInGlobalInterval = true } })
        assertNotEquals(
            adMobConfig { placements { interstitial(InterstitialPlacement("a"), TestIds.unit(1)) } },
            adMobConfig { placements { interstitial(InterstitialPlacement("a"), TestIds.unit(1)) { enabled = false } } },
        )
        assertNotEquals(
            adMobConfig { placements { banner(BannerPlacement("a"), TestIds.unit(1)) } },
            adMobConfig { placements { banner(BannerPlacement("a"), TestIds.unit(1)) { size = BannerSize.Standard } } },
        )
        assertNotEquals(
            adMobConfig { placements { interstitial(InterstitialPlacement("a"), TestIds.unit(1)) } },
            adMobConfig { placements { rewarded(RewardedPlacement("a"), TestIds.unit(1)) } },
        )
    }

    @Test
    fun `the consent sync ID identifies a user, so toString only says it's set`() {
        val config = kotlinConfig()
        assertEquals("sync-user-42", config.privacy.consentSyncId)
        assertFalse(config.toString().contains("sync-user-42"))
        assertTrue(config.privacy.toString().contains("consentSyncId=<set>"))
    }

    @Test
    fun `defaults match the architecture`() {
        val config = adMobConfig { }
        assertEquals(AdEnvironment.DEVELOPMENT, config.environment)
        assertNull(config.appId)
        assertEquals(emptySet<String>(), config.testing.testDeviceIds)
        assertNull(config.testing.useDemoAdUnits)
        assertEquals(AgeTreatment.UNSPECIFIED, config.privacy.ageTreatment)
        assertNull(config.privacy.maxAdContentRating)
        assertEquals(false, config.privacy.restrictedDataProcessing)
        assertEquals(DebugGeography.DISABLED, config.privacy.debugGeography)
        assertNull(config.privacy.consentSyncId)
        assertEquals(30.seconds, config.frequency.minIntervalBetweenFullScreenAds)
        assertEquals(false, config.frequency.includeRewardedInGlobalInterval)
        assertNull(config.logging.level)
        assertTrue(config.logging.logger is AndroidAdLogger)
        assertEquals(5.seconds, config.advanced.showTimeout)
        assertNull(config.advanced.failFast)
        assertEquals(false, config.advanced.disableSdkCrashReporting)
        assertEquals(emptyList<PlacementConfig>(), config.placements)
        assertEquals(emptyList<AdEventListener>(), config.eventListeners)
        assertEquals(StaticConfigProvider, config.remoteConfig)
    }

    @Test
    fun `placement defaults match the architecture`() {
        val config = adMobConfig {
            placements {
                banner(BannerPlacement("b"), TestIds.unit(1))
                appOpen(AppOpenPlacement("ao"), TestIds.unit(2))
                native(NativePlacement("n"), TestIds.unit(3))
            }
        }
        val banner = config.placements[0] as BannerPlacementConfig
        assertEquals(BannerSize.AnchoredAdaptive, banner.size)
        assertEquals(true, banner.enabled)
        assertEquals(FrequencyRule(), banner.frequency)
        assertEquals(PreloadStrategy.AFTER_READY, banner.preload)
        assertNull(banner.retry)
        assertNull(banner.expiry)

        val appOpen = config.placements[1] as AppOpenPlacementConfig
        assertEquals(4.minutes, appOpen.cooldown)
        assertEquals(2, appOpen.minLaunchesBeforeFirstShow)
        assertEquals(emptySet<String>(), appOpen.excludedActivityClassNames)
        assertEquals(true, appOpen.showOnColdStartAllowed)
        assertEquals(5.seconds, appOpen.suppressAfterFullScreenAd)

        assertEquals(1, (config.placements[2] as NativePlacementConfig).poolSize)
    }

    @Test
    fun `built config is not affected by later builder changes`() {
        val builder = AdMobConfig.Builder()
        builder.testing.testDeviceIds += "A"
        builder.placements.interstitial(InterstitialPlacement("a"), TestIds.unit(1))
        builder.eventListeners += listener
        val config = builder.build()

        builder.environment = AdEnvironment.PRODUCTION
        builder.testing.testDeviceIds += "B"
        builder.placements.interstitial(InterstitialPlacement("b"), TestIds.unit(2))
        builder.eventListeners += AdEventListener { }
        builder.privacy.maxAdContentRating = MaxAdContentRating.G

        assertEquals(AdEnvironment.DEVELOPMENT, config.environment)
        assertEquals(setOf("A"), config.testing.testDeviceIds)
        assertEquals(1, config.placements.size)
        assertEquals(1, config.eventListeners.size)
        assertNull(config.privacy.maxAdContentRating)
    }

    @Test
    fun `built collections can't be mutated through casts`() {
        val config = adMobConfig {
            testing { testDeviceIds += "A" }
            eventListeners += listener
            placements {
                interstitial(InterstitialPlacement("a"), TestIds.unit(1))
                appOpen(AppOpenPlacement("ao"), TestIds.unit(2)) { excludeActivities(Activity::class.java) }
            }
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (config.placements as MutableList<PlacementConfig>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (config.eventListeners as MutableList<AdEventListener>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (config.testing.testDeviceIds as MutableSet<String>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            ((config.placements[1] as AppOpenPlacementConfig).excludedActivityClassNames as MutableSet<String>).clear()
        }
    }

    @Test
    fun `excludeActivities stores class names`() {
        val config = adMobConfig {
            placements { appOpen(AppOpenPlacement("ao"), TestIds.unit(1)) { excludeActivities(Activity::class.java) } }
        }
        assertEquals(setOf("android.app.Activity"), (config.placements.single() as AppOpenPlacementConfig).excludedActivityClassNames)
    }

    @Test
    fun `duplicate placement keys build but are rejected by validation`() {
        val config = adMobConfig {
            environment = AdEnvironment.PRODUCTION
            placements {
                interstitial(InterstitialPlacement("level_complete"), TestIds.unit(1))
                interstitial(InterstitialPlacement("level_complete"), TestIds.unit(2))
            }
        }
        assertEquals(2, config.placements.size)
        val result = ConfigValidator.validate(config, TestIds.APP_ID, isDebuggable = false)
        assertEquals(listOf(ConfigIssueCode.DUPLICATE_PLACEMENT_KEY), result.errors.map { it.code })
    }

    @Test
    fun `nested frequency block starts from the current rule`() {
        val config = adMobConfig {
            placements {
                interstitial(InterstitialPlacement("a"), TestIds.unit(1)) {
                    frequency = FrequencyRule(maxPerSession = 4)
                    frequency { minInterval = 60.seconds }
                }
            }
        }
        assertEquals(FrequencyRule(60.seconds, 4), config.placements.single().frequency)
    }
}
