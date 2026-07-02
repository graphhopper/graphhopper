package com.graphhopper.routing.ev

object HorseRating {
    const val KEY = "horse_rating"

    @JvmStatic
    fun create(): IntEncodedValue = IntEncodedValueImpl(KEY, 3, false)
}
