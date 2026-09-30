package io.github.thesobiaakbar.adroit.config

/**
 * An AdMob ad unit ID, for example `ca-app-pub-1234567890123456/1234567890`. Constructing one never throws;
 * configuration validation reports IDs that aren't [isWellFormed].
 */
@JvmInline
public value class AdUnitId(public val value: String) {
    /** True when [value] has the shape `ca-app-pub-<16 digits>/<10 digits>`. */
    public val isWellFormed: Boolean get() = PATTERN.matches(value)

    override fun toString(): String = value

    private companion object {
        val PATTERN = Regex("""^ca-app-pub-\d{16}/\d{10}$""")
    }
}

/**
 * An AdMob app ID, for example `ca-app-pub-1234567890123456~1234567890`. Constructing one never throws;
 * configuration validation reports IDs that aren't [isWellFormed].
 */
@JvmInline
public value class AdMobAppId(public val value: String) {
    /** True when [value] has the shape `ca-app-pub-<16 digits>~<10 digits>`. */
    public val isWellFormed: Boolean get() = PATTERN.matches(value)

    override fun toString(): String = value

    private companion object {
        val PATTERN = Regex("""^ca-app-pub-\d{16}~\d{10}$""")
    }
}
