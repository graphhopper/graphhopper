package com.graphhopper.routing.ev

import com.graphhopper.util.Helper

enum class Crossing {
    MISSING, // no information
    RAILWAY_BARRIER, // railway crossing with barrier
    RAILWAY, // railway crossing with road
    TRAFFIC_SIGNALS, // with light signals
    UNCONTROLLED, // with crosswalk, without traffic lights
    MARKED, // with crosswalk, with or without traffic lights
    UNMARKED, // without markings or traffic lights
    NO; // crossing is impossible or illegal

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        const val KEY = "crossing"

        @JvmStatic
        fun create(): EnumEncodedValue<Crossing> = EnumEncodedValue(KEY, Crossing::class.java)

        @JvmStatic
        fun find(name: String?): Crossing {
            if (name == null)
                return MISSING
            return try {
                valueOf(Helper.toUpperCase(name))
            } catch (ex: IllegalArgumentException) {
                MISSING
            }
        }
    }
}
