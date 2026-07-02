package com.graphhopper.routing.ev

object FerrySpeed {
    const val KEY = "ferry_speed"

    @JvmStatic
    fun create(): DecimalEncodedValue = DecimalEncodedValueImpl(KEY, 5, 2.0, false)
}
