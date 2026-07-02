package com.graphhopper.routing.ev

import com.graphhopper.util.Helper

/**
 * Defines general restrictions for the transport of goods through water protection areas.<br></br>
 * If not tagged it will be [YES]
 */
enum class HazmatWater {
    YES, PERMISSIVE, NO;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        const val KEY = "hazmat_water"

        @JvmStatic
        fun create(): EnumEncodedValue<HazmatWater> = EnumEncodedValue(KEY, HazmatWater::class.java)
    }
}
