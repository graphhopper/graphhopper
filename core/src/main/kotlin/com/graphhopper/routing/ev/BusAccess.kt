package com.graphhopper.routing.ev

object BusAccess {
    const val KEY = "bus_access"

    @JvmStatic
    fun create(): BooleanEncodedValue = SimpleBooleanEncodedValue(KEY, true)
}
