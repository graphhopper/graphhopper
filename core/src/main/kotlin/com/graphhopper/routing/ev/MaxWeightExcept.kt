package com.graphhopper.routing.ev

import com.graphhopper.util.Helper

/**
 * When the max_weight EncodedValue is not legally binding. E.g. if there is a sign that a delivery vehicle can access
 * the road (even if larger than maxweight tag) then DELIVERY of this enum will be set.
 */
enum class MaxWeightExcept {
    MISSING, DELIVERY, DESTINATION, FORESTRY;

    override fun toString(): String = Helper.toLowerCase(super.toString())

    companion object {
        const val KEY = "max_weight_except"

        @JvmStatic
        fun create(): EnumEncodedValue<MaxWeightExcept> =
            EnumEncodedValue(KEY, MaxWeightExcept::class.java)

        @JvmStatic
        fun find(name: String?): MaxWeightExcept {
            if (name.isNullOrEmpty())
                return MISSING

            // "maxweight:conditional=none @ private" is rare and seems to be known from a few mappers only
            if (name.equals("permit", ignoreCase = true) || name.equals("private", ignoreCase = true))
                return DELIVERY

            return try {
                valueOf(Helper.toUpperCase(name))
            } catch (ex: IllegalArgumentException) {
                MISSING
            }
        }
    }
}
