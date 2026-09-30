package io.github.thesobiaakbar.adroit.sample.xml.styles

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.nativePlacement
import io.github.thesobiaakbar.adroit.sample.xml.R
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import io.github.thesobiaakbar.adroit.views.AdNativeView
import io.github.thesobiaakbar.adroit.views.AdShimmerView
import io.github.thesobiaakbar.adroit.views.NativeTemplates

/**
 * Every native style, each with a live test ad: the library's 11 templates, then the sample's own layout registered
 * as "sample_promo" (SampleApplication). "Show the shimmers" swaps each ad for the shimmer its layout gets while
 * loading. The styles use SampleAds.ArticleNative's ad units; each view loads its own ad.
 *
 * The colors come from the templates' resources, two of them overridden by this app
 * (res/values/adroit_overrides.xml): the golden "Ad" badge and its text.
 */
class NativeStylesActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private var showingShimmers = false

    private val styles: List<Pair<String, NativeAdLayout>> =
        NativeStyle.entries.map { it.name.lowercase() to NativeTemplates.forStyle(it) } +
            ("sample_promo (the app's own layout)" to checkNotNull(NativeTemplates.registered("sample_promo")))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val density = resources.displayMetrics.density
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val toggle = Button(this).apply {
            setText(R.string.native_styles_show_shimmers)
            setOnClickListener {
                showingShimmers = !showingShimmers
                setText(if (showingShimmers) R.string.native_styles_show_ads else R.string.native_styles_show_shimmers)
                fill()
            }
        }
        list.addView(toggle)
        val scroll = ScrollView(this).apply { addView(list) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        fill()
    }

    /** One title and one ad (or its shimmer) per style, under the toggle. */
    @OptIn(InternalAdroitApi::class) // AdShimmerView: shown here only to preview each layout's shimmer.
    private fun fill() {
        list.removeViews(1, list.childCount - 1)
        val density = resources.displayMetrics.density
        for ((name, layout) in styles) {
            list.addView(
                TextView(this).apply {
                    text = name
                    setPadding(0, (20 * density).toInt(), 0, (6 * density).toInt())
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                },
            )
            val ad: View = if (showingShimmers) {
                AdShimmerView(this).apply { showLayout(layout) }
            } else {
                AdNativeView(this).apply { setPlacement(SampleAds.ArticleNative.nativePlacement(), layout) }
            }
            list.addView(ad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}
