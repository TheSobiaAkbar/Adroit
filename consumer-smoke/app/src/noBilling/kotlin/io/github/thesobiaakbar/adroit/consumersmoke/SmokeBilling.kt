package io.github.thesobiaakbar.adroit.consumersmoke

import android.app.Application

/** `-Psmoke.billing=false`: the Kotlin 2.1 matrix rows, which billing doesn't support (it needs Kotlin 2.2). */
internal object SmokeBilling {
    fun initialize(application: Application) = Unit
}
