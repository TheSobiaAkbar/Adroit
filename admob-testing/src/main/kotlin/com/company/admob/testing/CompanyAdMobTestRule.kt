package com.company.admob.testing

import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A JUnit4 rule that makes the `CompanyAdMob` object behave as [client] during each test, and restores it afterwards,
 * for code that calls `CompanyAdMob` directly instead of an injected `AdsClient`:
 * ```kotlin
 * @get:Rule val ads = CompanyAdMobTestRule()
 *
 * @Test fun showsAnAdAfterTheLevel() {
 *     ads.client.enqueueInterstitialResult(Placements.LevelEnd, FullScreenResult.Dismissed(clicked = false))
 *     ...
 * }
 * ```
 * Prefer injecting `AdsClient` and passing a [FakeAdsClient] directly: that needs no rule and no global state.
 * JUnit is a `compileOnly` dependency of admob-testing, so your test classpath provides it.
 */
public class CompanyAdMobTestRule @JvmOverloads constructor(
    public val client: FakeAdsClient = FakeAdsClient(),
) : TestWatcher() {
    private var restore: (() -> Unit)? = null

    @OptIn(InternalCompanyAdMobApi::class)
    override fun starting(description: Description) {
        restore = CompanyAdMobInternal.installForTesting(client)
    }

    override fun finished(description: Description) {
        restore?.invoke()
        restore = null
    }
}
