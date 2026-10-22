package io.github.thesobiaakbar.adroit.internal

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import io.github.thesobiaakbar.adroit.AdCloseButton
import io.github.thesobiaakbar.adroit.R
import io.github.thesobiaakbar.adroit.internal.logging.AdLog

/**
 * The frame every banner and native ad is drawn in: [margin] on each side (`adroit_ad_margin`) and, when the ad has
 * one, a close button in a row of its own above or below it (`adroit_ad_close_size`), clear of the ad's content and
 * its AdChoices icon. Apps change both by overriding the resources. Main thread only.
 */
internal object AdFrame {
    fun margin(context: Context): Int = context.resources.getDimensionPixelSize(R.dimen.adroit_ad_margin)

    /** The height the close row adds to an ad with a close button. */
    fun closeRow(context: Context): Int = context.resources.getDimensionPixelSize(R.dimen.adroit_ad_close_size)

    /**
     * The close button for [position] (anything but [AdCloseButton.NONE]), with the app's drawable [iconName] if it has
     * one, as a child of a [FrameLayout]: square, at the row's start or end.
     */
    fun closeButton(context: Context, position: AdCloseButton, iconName: String?, log: AdLog, onClose: () -> Unit): ImageButton {
        val size = closeRow(context)
        return ImageButton(context).apply {
            background = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(icon(context, iconName, log))
            contentDescription = context.getString(R.string.adroit_ad_close)
            layoutParams = FrameLayout.LayoutParams(
                size,
                size,
                (if (position.isTop) Gravity.TOP else Gravity.BOTTOM) or (if (position.isStart) Gravity.START else Gravity.END),
            )
            setOnClickListener { onClose() }
        }
    }

    // A Firebase value names the drawable, so there's no R reference to it.
    @SuppressLint("DiscouragedApi")
    private fun icon(context: Context, name: String?, log: AdLog): Int {
        if (name == null) return R.drawable.adroit_ic_ad_close
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) log.w { "Close button icon \"$name\" isn't a drawable in the app; the library's shows" }
        return if (id != 0) id else R.drawable.adroit_ic_ad_close
    }
}
