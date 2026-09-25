package com.company.admob.config.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** An [AdConfigProvider] with no overrides: only the local configuration applies. The default. */
public object StaticConfigProvider : AdConfigProvider {
    override fun overrides(): Flow<AdOverrides> = emptyFlow()

    override fun toString(): String = "StaticConfigProvider"
}
