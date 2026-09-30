package io.github.thesobiaakbar.adroit.testing

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.events.AdEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AdroitTestRuleTest {
    private val level = InterstitialPlacement("level_end")

    /** Runs [body] inside [rule], as JUnit would for a test annotated with it. */
    private fun AdroitTestRule.around(body: () -> Unit) {
        apply(
            object : Statement() {
                override fun evaluate() = body()
            },
            Description.createTestDescription(javaClass, "inner"),
        ).evaluate()
    }

    @Test
    fun `the facade is the fake during the test and restored afterwards`() {
        val realState = Adroit.state
        assertEquals(AdsState.NotInitialized, realState.value)
        val rule = AdroitTestRule()

        rule.around {
            assertSame(rule.client.state, Adroit.state)
            assertTrue(Adroit.isReady)
            rule.client.enqueueInterstitialResult(level, FullScreenResult.Dismissed(clicked = true))
            val results = mutableListOf<FullScreenResult>()
            Adroit.showInterstitial(Activity(), level) { results += it }
            assertEquals(listOf<FullScreenResult>(FullScreenResult.Dismissed(clicked = true)), results)
            Adroit.setAdFree(true)
            assertEquals(listOf(FakeCall.ShowInterstitial("level_end"), FakeCall.SetAdFree(true)), rule.client.recordedCalls)
        }

        assertSame(realState, Adroit.state)
        assertNotSame(rule.client.state, Adroit.state)
        assertEquals(AdsState.NotInitialized, Adroit.state.value)
        // The real (uninitialized) facade answers again: the fake records nothing more.
        Adroit.showInterstitial(Activity(), level) {}
        assertEquals(2, rule.client.recordedCalls.size)
    }

    @Test
    fun `events emitted by the fake reach Adroit_events collectors`() = runTest {
        val rule = AdroitTestRule()
        rule.around {
            val received = mutableListOf<AdEvent>()
            val job = backgroundScope.launch { received += Adroit.events.first() }
            runCurrent()
            val event = AdEvent.ConfigurationWarning("hello", 0L)
            rule.client.emit(event)
            runCurrent()
            assertEquals(listOf<AdEvent>(event), received)
            job.cancel()
        }
    }

    @Test
    fun `nested rules restore in order`() {
        val outer = AdroitTestRule()
        val inner = AdroitTestRule()
        outer.around {
            inner.around { assertSame(inner.client.state, Adroit.state) }
            assertSame(outer.client.state, Adroit.state)
        }
        assertEquals(AdsState.NotInitialized, Adroit.state.value)
    }
}
