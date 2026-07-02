package com.graphhopper.routing.ev

object GetOffBike {
    const val KEY = "get_off_bike"

    @JvmStatic
    fun create(): BooleanEncodedValue = SimpleBooleanEncodedValue(KEY, true)
}
