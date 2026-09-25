package com.company.admob.sample.mixed

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import com.company.admob.CompanyAdMobVersion

/** Placeholder: an AppCompat (XML-world) Activity hosting Compose. Built out in PROMPT 19. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    MaterialTheme { Text("CompanyAdMob mixed sample ${CompanyAdMobVersion.NAME}") }
                }
            }
        )
    }
}
