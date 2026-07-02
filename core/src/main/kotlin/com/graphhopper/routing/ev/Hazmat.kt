package com.graphhopper.routing.ev

import com.graphhopper.util.Helper

/**
 * Defines general restrictions for the transport of hazardous materials.<br></br>
 * If not tagged it will be [YES]
 */
enum class Hazmat {
    YES, NO;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        const val KEY = "hazmat"

        @JvmStatic
        fun create(): EnumEncodedValue<Hazmat> = EnumEncodedValue(KEY, Hazmat::class.java)
    }
}
