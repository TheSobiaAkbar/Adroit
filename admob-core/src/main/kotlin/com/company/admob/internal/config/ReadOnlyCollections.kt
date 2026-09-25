package com.company.admob.internal.config

import java.util.Collections

// Defensive copies that can't be mutated through a cast to MutableList/MutableSet, so built configs stay immutable.

internal fun <T> Collection<T>.toReadOnlyList(): List<T> = Collections.unmodifiableList(ArrayList(this))

internal fun <T> Collection<T>.toReadOnlySet(): Set<T> = Collections.unmodifiableSet(LinkedHashSet(this))
