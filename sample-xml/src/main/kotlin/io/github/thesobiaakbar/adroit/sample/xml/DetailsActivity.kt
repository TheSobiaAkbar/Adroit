package io.github.thesobiaakbar.adroit.sample.xml

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.thesobiaakbar.adroit.config.bannerPlacement
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import io.github.thesobiaakbar.adroit.views.attachBanner
import io.github.thesobiaakbar.adroit.sample.xml.databinding.ActivityDetailsBinding

/**
 * A banner attached from code (`attachBanner`, destroyed with this Activity) and a native ad declared in XML
 * (`AdNativeView`, `app:adroit_placement="ArticleNative"`). Their looks come from SampleAds: a collapsible
 * banner and the large native template.
 */
class DetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.bannerContainer.attachBanner(this, SampleAds.DetailsBanner.bannerPlacement())
    }
}
