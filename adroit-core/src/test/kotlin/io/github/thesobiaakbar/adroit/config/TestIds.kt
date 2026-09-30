package io.github.thesobiaakbar.adroit.config

/** Made-up but well-formed IDs that don't belong to Google's demo publisher. */
internal object TestIds {
    const val APP_ID = "ca-app-pub-1234567890123456~1234567890"

    /** A distinct, well-formed, non-demo ad unit ID. */
    fun unit(index: Int): String = "ca-app-pub-1234567890123456/%010d".format(index)
}
