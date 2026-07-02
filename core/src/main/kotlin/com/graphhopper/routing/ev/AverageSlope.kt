package com.graphhopper.routing.ev

/**
 * Average elevation. Will be negated in reverse direction.
 */
object AverageSlope {
    const val KEY = "average_slope"

    @JvmStatic
    fun create(): DecimalEncodedValue = DecimalEncodedValueImpl(
        KEY, 5, 0.0, 1.0,
        true, false, false
    )
}
