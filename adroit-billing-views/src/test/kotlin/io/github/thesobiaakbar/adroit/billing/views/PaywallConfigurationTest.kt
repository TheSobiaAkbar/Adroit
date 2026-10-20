package io.github.thesobiaakbar.adroit.billing.views

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.testing.FakeBilling
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Supports RTL, as every app with an Arabic or Hebrew translation does; set before the test's Activity starts. */
class RtlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    }
}

/**
 * Every template in Arabic (right to left), dark, at font scale 2.0 on a small phone (B22): no text is cut off,
 * nothing leaves the screen sideways, and the layout really is right to left.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ar-w360dp-h640dp-night", fontScale = 2.0f, application = RtlApp::class)
@RunWith(RobolectricTestRunner::class)
class PaywallConfigurationTest {
    private val billing = FakeBilling()
    private val defaults = AdroitPaywall.definitions to AdroitPaywall.controllers

    @After
    fun reset() {
        AdroitPaywall.definitions = defaults.first
        AdroitPaywall.controllers = defaults.second
    }

    private val texts = PaywallTexts(
        title = "Go premium and unlock everything",
        features = listOf("No ads anywhere in the app", "Every filter and every future one"),
        footer = "Payment is charged to your Google Play account at confirmation of purchase.",
    )

    private fun check(template: PaywallTemplate) {
        val paywall = TestPaywall(Fixtures.spec(template = template, texts = texts, close = CloseBehavior(delaySeconds = 0)))
        AdroitPaywall.definitions = { name -> paywall.takeIf { it.name == name } }
        AdroitPaywall.controllers = { definition, placement, scope ->
            PaywallController.forModel(definition, Fixtures.model(definition.defaults), placement, scope, billing)
        }
        val intent = AdroitPaywall.intent(ApplicationProvider.getApplicationContext(), paywall, "home")
        ActivityScenario.launch<AdroitPaywallActivity>(intent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                assertEquals(View.LAYOUT_DIRECTION_RTL, decor.layoutDirection)
                val screenWidth = activity.resources.displayMetrics.widthPixels
                val shown = textViews(decor).filter { it.isShown && it.text.isNotEmpty() }
                assertTrue("$template rendered no text", shown.isNotEmpty())
                for (view in shown) {
                    val layout = checkNotNull(view.layout) { "${view.text} isn't laid out" }
                    val room = view.height - view.totalPaddingTop - view.totalPaddingBottom
                    val ellipsized = (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
                    assertTrue("$template: \"${view.text}\" is cut off (${layout.height} px in $room)", layout.height <= room && !ellipsized)
                    val at = IntArray(2).also(view::getLocationOnScreen)
                    assertTrue("$template: \"${view.text}\" leaves the screen", at[0] >= 0 && at[0] + view.width <= screenWidth)
                }
            }
        }
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun plans() = check(PaywallTemplate.PLANS)

    @Test
    fun single() = check(PaywallTemplate.SINGLE)

    @Test
    fun dialog() = check(PaywallTemplate.DIALOG)

    @Test
    fun bottomSheet() = check(PaywallTemplate.BOTTOM_SHEET)
}
