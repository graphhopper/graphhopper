package com.graphhopper.util.details

import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.weighting.custom.CustomWeightingHelper
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.Parameters.Details.CHANGE_ANGLE
import kotlin.math.abs

/**
 * This class handles the calculation for the change_angle path detail, i.e. the angle between the
 * edges calculated from the 'orientation' of an edge.
 */
class ChangeAngleDetails(private val orientationEv: DecimalEncodedValue) : AbstractPathDetailsBuilder(CHANGE_ANGLE) {

    private var prevAzimuth: Double? = null
    private var changeAngle: Double? = null

    override fun getCurrentValue(): Any? = changeAngle

    override fun isEdgeDifferentToLastEdge(edge: EdgeIteratorState): Boolean {
        val prev = prevAzimuth
        if (prev != null) {
            val azimuth = edge.getReverse(orientationEv)
            val tmp = CustomWeightingHelper.calcChangeAngle(prev, azimuth)
            val tmpRound = Math.round(tmp).toDouble()

            val curChangeAngle = changeAngle
            if (curChangeAngle == null || abs(tmpRound - curChangeAngle) > 0) {
                prevAzimuth = edge.get(orientationEv)
                changeAngle = tmpRound
                return true
            }
        }

        prevAzimuth = edge.get(orientationEv)
        return changeAngle == null
    }
}
