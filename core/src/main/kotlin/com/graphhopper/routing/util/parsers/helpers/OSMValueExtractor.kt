package com.graphhopper.routing.util.parsers.helpers

import com.graphhopper.reader.ReaderWay
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EdgeIntAccess
import com.graphhopper.util.Helper.toLowerCase
import java.util.regex.Pattern

object OSMValueExtractor {

    private val TON_PATTERN = Pattern.compile("tons?")
    private val MGW_PATTERN = Pattern.compile("mgw")
    private val WSPACE_PATTERN = Pattern.compile("\\s")
    private val METER_PATTERN = Pattern.compile("meters?|mtrs?|mt|m\\.")
    private val INCH_PATTERN = Pattern.compile("\"|''")
    private val FEET_PATTERN = Pattern.compile("'|feet")
    private val APPROX_PATTERN = Pattern.compile("~|approx")

    @JvmStatic
    fun extractTons(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, valueEncoder: DecimalEncodedValue, keys: List<String>) {
        val rawValue = way.getFirstValue(keys)
        var value = stringToTons(rawValue)

        if (value.isNaN()) value = Double.POSITIVE_INFINITY

        valueEncoder.setDecimal(false, edgeId, edgeIntAccess, value)
    }

    /**
     * This parses the weight for a conditional value like "delivery @ (weight > 7.5)"
     */
    @JvmStatic
    fun conditionalWeightToTons(value: String): Double {
        try {
            var index = value.indexOf("weight>") // maxweight or weight
            if (index < 0) {
                index = value.indexOf("weight >")
                if (index > 0) index += "weight >".length
            } else {
                index += "weight>".length
            }
            if (index > 0) {
                var lastIndex = value.indexOf(')', index) // (value) or value
                if (lastIndex < 0) lastIndex = value.length
                if (lastIndex > index)
                    return stringToTons(value.substring(index, lastIndex))
            }
            return Double.NaN
        } catch (ex: Exception) {
            throw RuntimeException("value $value", ex)
        }
    }

    @JvmStatic
    fun stringToTons(value: String): Double {
        var v = TON_PATTERN.matcher(toLowerCase(value)).replaceAll("t")
        v = MGW_PATTERN.matcher(v).replaceAll("").trim()
        if (isInvalidValue(v))
            return Double.NaN

        var factor = 1.0
        if (v.endsWith("st")) {
            v = v.substring(0, v.length - 2)
            factor = 0.907194048807
        } else if (v.endsWith("t")) {
            v = v.substring(0, v.length - 1)
        } else if (v.endsWith("lbs")) {
            v = v.substring(0, v.length - 3)
            factor = 0.00045359237
        } else if (v.endsWith("kg")) {
            v = v.substring(0, v.length - 2)
            factor = 0.001
        }

        return try {
            v.toDouble() * factor
        } catch (e: NumberFormatException) {
            Double.NaN
        }
    }

    @JvmStatic
    fun extractMeter(edgeId: Int, edgeIntAccess: EdgeIntAccess, way: ReaderWay, valueEncoder: DecimalEncodedValue, keys: List<String>) {
        val rawValue = way.getFirstValue(keys)
        var value = stringToMeter(rawValue)

        if (value.isNaN()) value = Double.POSITIVE_INFINITY

        valueEncoder.setDecimal(false, edgeId, edgeIntAccess, value)
    }

    @JvmStatic
    fun stringToMeter(value: String): Double {
        var v = WSPACE_PATTERN.matcher(toLowerCase(value)).replaceAll("")
        v = METER_PATTERN.matcher(v).replaceAll("m")
        v = INCH_PATTERN.matcher(v).replaceAll("in")
        v = FEET_PATTERN.matcher(v).replaceAll("ft")
        if (isInvalidValue(v))
            return Double.NaN
        var factor = 1.0
        var offset = 0.0
        if (v.startsWith("~") || v.contains("approx")) {
            v = APPROX_PATTERN.matcher(v).replaceAll("").trim()
            factor = 0.8
        }

        if (v.endsWith("in")) {
            var startIndex = v.indexOf("ft")
            if (startIndex < 0) {
                startIndex = 0
            } else {
                startIndex += 2
            }

            val inchValue = v.substring(startIndex, v.length - 2)
            v = v.substring(0, startIndex)
            try {
                offset = inchValue.toDouble() * 0.0254
            } catch (e: NumberFormatException) {
                return Double.NaN
            }
        }

        if (v.endsWith("ft")) {
            v = v.substring(0, v.length - 2)
            factor *= 0.3048
        } else if (v.endsWith("cm")) {
            v = v.substring(0, v.length - 2)
            factor *= 0.01
        } else if (v.endsWith("m")) {
            v = v.substring(0, v.length - 1)
        }

        if (v.isEmpty()) {
            return offset
        }

        return try {
            v.toDouble() * factor + offset
        } catch (e: NumberFormatException) {
            Double.NaN
        }
    }

    @JvmStatic
    fun isInvalidValue(value: String): Boolean {
        val v = toLowerCase(value)
        return v.isEmpty() || v.startsWith("default") || v == "none" || v == "unknown"
                || v.contains("unrestricted") || v.startsWith("〜")
                || v.contains("narrow") || v == "unsigned" || v == "fixme" || v == "small"
                || v.contains(";") || v.contains(":") || v.contains("(")
                || v.contains(">") || v.contains("<") || v.contains("-")
                // only support '.' and no German decimals
                || v.contains(",")
    }
}
