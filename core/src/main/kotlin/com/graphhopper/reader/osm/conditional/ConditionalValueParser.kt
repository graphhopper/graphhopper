package com.graphhopper.reader.osm.conditional

import java.text.ParseException

/**
 * This interface defines how to parse a OSM value from conditional restrictions.
 */
interface ConditionalValueParser {

    /**
     * This method checks if the condition is satisfied for this parser.
     */
    @Throws(ParseException::class)
    fun checkCondition(conditionalValue: String): ConditionState

    enum class ConditionState(val isValid: Boolean, private val checkPassed: Boolean) {
        TRUE(true, true),
        FALSE(true, false),
        INVALID(false, false);

        val isCheckPassed: Boolean
            get() {
                if (!isValid)
                    throw IllegalStateException("Cannot call this method for invalid state")

                return checkPassed
            }
    }
}
