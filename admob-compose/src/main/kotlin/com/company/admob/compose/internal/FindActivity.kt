package com.company.admob.compose.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The Activity behind this Context, unwrapping `ContextWrapper`s (themes, `ComposeView` hosts); null if none.
 *
 * Used instead of `LocalActivity`, which needs `activity-compose` 1.10+, a dependency this module otherwise avoids.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
