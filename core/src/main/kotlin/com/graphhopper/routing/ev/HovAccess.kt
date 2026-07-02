package com.graphhopper.routing.ev

/**
 * High-occupancy vehicle (carpool, diamond, transit, T2, or T3).
 * See [also here](https://wiki.openstreetmap.org/wiki/Key:hov).
 */
object HovAccess {
    const val KEY = "hov_access"

    @JvmStatic
    fun create(): BooleanEncodedValue = SimpleBooleanEncodedValue(KEY, true)
}
