package io.github.thesobiaakbar.adroit.docs.guides

import android.app.Activity
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import io.github.thesobiaakbar.adroit.docs.R

// The progress bar helper shared by the XML splash screens of docs/guides/*_XML.md, step 6.

// region guide-splash-progress-xml
/** Shows the splash timer's progress (0..100), or hides the bar with null (update check, consent). */
fun Activity.showSplashProgress(percent: Int?) {
    findViewById<View>(R.id.progress_group).isVisible = percent != null
    if (percent == null) return
    findViewById<ProgressBar>(R.id.progress).progress = percent
    findViewById<TextView>(R.id.progress_text).text = "$percent%"
}
// endregion
