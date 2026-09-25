package com.company.admob.views

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Attaches a banner in onViewCreated, bound to the view lifecycle, as the docs recommend. */
class BannerFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(inflater.context)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (view as ViewGroup).attachBanner(viewLifecycleOwner, HOME)
    }
}

class OtherFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(inflater.context)
}

@RunWith(RobolectricTestRunner::class)
class AttachBannerFragmentTest {
    private lateinit var client: TestAdsClient

    @Before
    fun setUp() {
        client = TestAdsClient().install()
    }

    @After
    fun tearDown() = TestAdsClient.uninstall()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun host(): Pair<FragmentActivity, Int> {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val containerId = View.generateViewId()
        activity.setContentView(FrameLayout(activity).apply { id = containerId })
        return activity to containerId
    }

    @Test
    fun `bound to viewLifecycleOwner - destroyed in onDestroyView, while the Fragment lives on`() {
        val (activity, containerId) = host()
        val fragment = BannerFragment()
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment).commitNow()
        val handle = client.banner.created.single().handle
        assertSame(fragment.viewLifecycleOwner, handle.boundOwner)

        activity.supportFragmentManager.beginTransaction()
            .replace(containerId, OtherFragment())
            .addToBackStack(null)
            .commit()
        activity.supportFragmentManager.executePendingTransactions()
        idle()

        assertTrue("destroyed in onDestroyView", handle.isDestroyed)
        assertEquals("the Fragment itself lives on, on the back stack", Lifecycle.State.CREATED, fragment.lifecycle.currentState)
    }

    @Test
    fun `replace then pop back - the old banner is destroyed and exactly one live banner remains`() {
        val (activity, containerId) = host()
        val manager = activity.supportFragmentManager
        manager.beginTransaction().add(containerId, BannerFragment()).commitNow()
        repeat(3) {
            manager.beginTransaction().replace(containerId, OtherFragment()).addToBackStack(null).commit()
            manager.executePendingTransactions()
            manager.popBackStackImmediate()
            idle()
        }

        assertEquals("one banner per view creation", 4, client.banner.created.size)
        assertEquals("no duplicates", 1, client.banner.live.size)
        assertSame(client.banner.created.last().handle, client.banner.live.single())
    }
}
