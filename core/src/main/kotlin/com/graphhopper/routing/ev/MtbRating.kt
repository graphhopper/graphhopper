package com.graphhopper.routing.ev

object MtbRating {
    const val KEY = "mtb_rating"

    @JvmStatic
    fun create(): IntEncodedValue = IntEncodedValueImpl(KEY, 3, false)
}
