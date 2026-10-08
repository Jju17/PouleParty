package dev.rahier.pouleparty.util

import java.util.Date
import javax.inject.Inject

/** The current time, injected so tests can pin "now". */
fun interface AppClock {
    fun millis(): Long
    fun now(): Date = Date(millis())
}

class SystemClock @Inject constructor() : AppClock {
    override fun millis(): Long = System.currentTimeMillis()
}
