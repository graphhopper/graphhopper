package com.graphhopper.routing.ev

/**
 * Maximum elevation change in m/100m.
 */
object MaxSlope {
    const val KEY = "max_slope"

    @JvmStatic
    fun create(): DecimalEncodedValue = DecimalEncodedValueImpl(KEY, 5, 0.0, 1.0, true, false, false)
}
