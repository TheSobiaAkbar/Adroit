package io.github.thesobiaakbar.adroit.testing

import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A JUnit4 rule that makes the `Adroit` object behave as [client] during each test, and restores it afterwards,
 * for code that calls `Adroit` directly instead of an injected `AdsClient`:
 * ```kotlin
 * @get:Rule val ads = AdroitTestRule()
 *
 * @Test fun showsAnAdAfterTheLevel() {
 *     ads.client.enqueueInterstitialResult(Placements.LevelEnd, FullScreenResult.Dismissed(clicked = false))
 *     ...
 * }
 * ```
 * Prefer injecting `AdsClient` and passing a [FakeAdsClient] directly: that needs no rule and no global state.
 * JUnit is a `compileOnly` dependency of adroit-testing, so your test classpath provides it.
 */
public class AdroitTestRule @JvmOverloads constructor(
    public val client: FakeAdsClient = FakeAdsClient(),
) : TestWatcher() {
    private var restore: (() -> Unit)? = null

    @OptIn(InternalAdroitApi::class)
    override fun starting(description: Description) {
        restore = AdroitInternal.installForTesting(client)
    }

    override fun finished(description: Description) {
        restore?.invoke()
        restore = null
    }
}
