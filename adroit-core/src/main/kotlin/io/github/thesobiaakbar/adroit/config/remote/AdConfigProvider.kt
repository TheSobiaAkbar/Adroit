package io.github.thesobiaakbar.adroit.config.remote

import kotlinx.coroutines.flow.Flow

/**
 * A source of remote configuration overrides, for example Firebase Remote Config. The library collects [overrides]
 * after initialization and applies the latest value on the main thread. Overrides are validated like the local
 * configuration; invalid ones are ignored and logged.
 *
 * Precedence: an ad-free user beats a remote `adsEnabled = false`, which beats a remote placement override, which
 * beats the local configuration.
 */
public fun interface AdConfigProvider {
    /** A stream of override sets. Each emission replaces the previous one entirely. Must not throw. */
    public fun overrides(): Flow<AdOverrides>
}
