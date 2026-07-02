package com.graphhopper.routing.ev

object HikeRating {
    const val KEY = "hike_rating"

    @JvmStatic
    fun create(): IntEncodedValue = IntEncodedValueImpl(KEY, 3, false)
}
