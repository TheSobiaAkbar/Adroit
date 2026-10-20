package io.github.thesobiaakbar.adroit.billing.compose

import android.content.pm.ApplicationInfo
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.testing.FakeBilling
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Supports RTL, as every app with an Arabic or Hebrew translation does; set before the test's Activity starts. */
class RtlApp : android.app.Application() {
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
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = TestScope(StandardTestDispatcher())
    private val billing = FakeBilling()

    private val texts = PaywallTexts(
        title = "Go premium and unlock everything",
        features = listOf("No ads anywhere in the app", "Every filter and every future one"),
        footer = "Payment is charged to your Google Play account at confirmation of purchase.",
    )

    private fun check(template: PaywallTemplate) {
        val spec = Fixtures.spec(template = template, texts = texts, close = CloseBehavior(delaySeconds = 0))
        val controller = PaywallController.forModel(TestPaywall(spec), Fixtures.model(spec), "home", scope, billing)
        var direction: LayoutDirection? = null
        rule.setContent {
            direction = LocalLayoutDirection.current
            Paywall(controller, onClose = {})
        }
        rule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, direction)

        val screenWidth = ApplicationProvider.getApplicationContext<android.app.Application>().resources.displayMetrics.widthPixels
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("$template rendered no text", nodes.isNotEmpty())
        for (node in nodes) {
            val text = node.config[SemanticsProperties.Text].joinToString()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            // Cut off means lost lines: too little height, or an ellipsis. Text wraps, so its width can't be cut.
            layouts.forEach {
                val ellipsized = (0 until it.lineCount).any(it::isLineEllipsized)
                assertTrue("$template: \"$text\" is cut off (${it.size}, ${it.layoutInput.constraints})", !it.didOverflowHeight && !ellipsized)
            }
            val bounds = node.boundsInWindow
            assertTrue("$template: \"$text\" leaves the screen ($bounds)", bounds.left >= -0.5f && bounds.right <= screenWidth + 0.5f)
        }
        assertEquals(View.LAYOUT_DIRECTION_RTL, rule.activity.window.decorView.layoutDirection)
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
