package com.pinguine.spiele.redux

import kotlin.time.Clock
import kotlin.uuid.Uuid

internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal fun newId(): String = Uuid.random().toString()
