@file:Suppress("unused")

package com.company.admob.docs

import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.company.admob.firebase.RemoteValue
import kotlinx.coroutines.launch
import org.json.JSONObject

// region remote-values
/** The app's own Firebase parameters, each with the value used until Firebase sets one. */
object AppConfig {
    val showRateDialog = RemoteValue.boolean("show_rate_dialog", default = false)
    val freeLevels = RemoteValue.long("free_levels", default = 5)
    val coinPrice = RemoteValue.double("coin_price", default = 0.99)
    val welcomeText = RemoteValue.string("welcome_text", default = "Welcome!")
    val features = RemoteValue.json("features", default = JSONObject().put("chat", false))
}
// endregion

// region remote-values-read
fun onLevelFinished(level: Int, openPaywall: () -> Unit, showRateDialog: () -> Unit) {
    // .value is the value in force now: it never waits for Firebase.
    if (level >= AppConfig.freeLevels.value) openPaywall()
    if (AppConfig.showRateDialog.value) showRateDialog()
    val chatEnabled = AppConfig.features.value.optBoolean("chat")
}
// endregion

// region remote-values-flow
fun showWelcome(scope: LifecycleCoroutineScope, label: TextView) {
    // The value now, then the new one if this session's fetch changes it while the screen is open.
    scope.launch { AppConfig.welcomeText.updates().collect { label.text = it } }
}
// endregion

// region remote-values-custom
data class Offer(val percent: Int, val title: String)

/** A JSON object read into the app's own class; an invalid value reads as the default. */
val offer = RemoteValue.custom("offer", default = Offer(0, "")) { text ->
    val json = JSONObject(text)
    Offer(json.getInt("percent"), json.getString("title"))
}
// endregion
