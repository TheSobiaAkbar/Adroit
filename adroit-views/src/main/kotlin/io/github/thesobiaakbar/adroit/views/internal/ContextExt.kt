package io.github.thesobiaakbar.adroit.views.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind this Context, unwrapping `ContextWrapper`s (themes, Fragment contexts); null if none. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
