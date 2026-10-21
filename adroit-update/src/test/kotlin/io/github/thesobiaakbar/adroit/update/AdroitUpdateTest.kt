package io.github.thesobiaakbar.adroit.update

import android.app.Activity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdroitUpdateTest {
    @Test
    fun `without Google Play and without Firebase, check returns and nothing is ready to install`() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        AdroitUpdate.check(activity)
        AdroitUpdate.install()
        assertFalse(AdroitUpdate.readyToInstall.value)
        assertFalse(activity.isFinishing)
    }
}
