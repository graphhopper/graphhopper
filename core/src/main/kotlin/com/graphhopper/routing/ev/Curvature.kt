package com.graphhopper.routing.ev

object Curvature {
    const val KEY = "curvature"

    @JvmStatic
    fun create(): DecimalEncodedValue =
        // for now save a bit: ignore all too low values and set them to the minimum value instead
        DecimalEncodedValueImpl(
            KEY, 4, 0.25, 0.05,
            false, false, false
        )
}
