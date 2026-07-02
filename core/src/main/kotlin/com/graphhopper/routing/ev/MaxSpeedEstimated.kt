package com.graphhopper.routing.ev

object MaxSpeedEstimated {
    const val KEY = "max_speed_estimated"

    @JvmStatic
    fun create(): BooleanEncodedValue = SimpleBooleanEncodedValue(KEY)
}
