package com.company.admob.testing

import android.app.Activity
import com.company.admob.AdsState
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialPlacement
import com.company.admob.events.AdEvent
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
class CompanyAdMobTestRuleTest {
    private val level = InterstitialPlacement("level_end")

    /** Runs [body] inside [rule], as JUnit would for a test annotated with it. */
    private fun CompanyAdMobTestRule.around(body: () -> Unit) {
        apply(
            object : Statement() {
                override fun evaluate() = body()
            },
            Description.createTestDescription(javaClass, "inner"),
        ).evaluate()
    }

    @Test
    fun `the facade is the fake during the test and restored afterwards`() {
        val realState = CompanyAdMob.state
        assertEquals(AdsState.NotInitialized, realState.value)
        val rule = CompanyAdMobTestRule()

        rule.around {
            assertSame(rule.client.state, CompanyAdMob.state)
            assertTrue(CompanyAdMob.isReady)
            rule.client.enqueueInterstitialResult(level, FullScreenResult.Dismissed(clicked = true))
            val results = mutableListOf<FullScreenResult>()
            CompanyAdMob.showInterstitial(Activity(), level) { results += it }
            assertEquals(listOf<FullScreenResult>(FullScreenResult.Dismissed(clicked = true)), results)
            CompanyAdMob.setAdFree(true)
            assertEquals(listOf(FakeCall.ShowInterstitial("level_end"), FakeCall.SetAdFree(true)), rule.client.recordedCalls)
        }

        assertSame(realState, CompanyAdMob.state)
        assertNotSame(rule.client.state, CompanyAdMob.state)
        assertEquals(AdsState.NotInitialized, CompanyAdMob.state.value)
        // The real (uninitialized) facade answers again: the fake records nothing more.
        CompanyAdMob.showInterstitial(Activity(), level) {}
        assertEquals(2, rule.client.recordedCalls.size)
    }

    @Test
    fun `events emitted by the fake reach CompanyAdMob_events collectors`() = runTest {
        val rule = CompanyAdMobTestRule()
        rule.around {
            val received = mutableListOf<AdEvent>()
            val job = backgroundScope.launch { received += CompanyAdMob.events.first() }
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
        val outer = CompanyAdMobTestRule()
        val inner = CompanyAdMobTestRule()
        outer.around {
            inner.around { assertSame(inner.client.state, CompanyAdMob.state) }
            assertSame(outer.client.state, CompanyAdMob.state)
        }
        assertEquals(AdsState.NotInitialized, CompanyAdMob.state.value)
    }
}
