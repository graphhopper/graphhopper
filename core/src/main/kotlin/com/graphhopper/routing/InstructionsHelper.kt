/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing

import com.graphhopper.routing.ev.RoadEnvironment
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.AngleCalc
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.Instruction
import com.graphhopper.util.shapes.GHPoint
import kotlin.math.abs

/**
 * Simple helper class used during the instruction generation
 */
internal object InstructionsHelper {

    fun calculateOrientationDelta(prevLatitude: Double, prevLongitude: Double, latitude: Double, longitude: Double, prevOrientation: Double): Double {
        var orientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLatitude, prevLongitude, latitude, longitude, false)
        orientation = AngleCalc.ANGLE_CALC.alignOrientation(prevOrientation, orientation)
        return orientation - prevOrientation
    }

    fun calculateSign(prevLatitude: Double, prevLongitude: Double, latitude: Double, longitude: Double, prevOrientation: Double): Int {
        val delta = calculateOrientationDelta(prevLatitude, prevLongitude, latitude, longitude, prevOrientation)
        val absDelta = abs(delta)

        return if (absDelta < 0.2) {
            // 0.2 ~= 11°
            Instruction.CONTINUE_ON_STREET
        } else if (absDelta < 0.8) {
            // 0.8 ~= 40°
            if (delta > 0)
                Instruction.TURN_SLIGHT_LEFT
            else
                Instruction.TURN_SLIGHT_RIGHT
        } else if (absDelta < 1.8) {
            // 1.8 ~= 103°
            if (delta > 0)
                Instruction.TURN_LEFT
            else
                Instruction.TURN_RIGHT
        } else if (delta > 0)
            Instruction.TURN_SHARP_LEFT
        else
            Instruction.TURN_SHARP_RIGHT
    }

    fun isSameName(name1: String?, name2: String?): Boolean {
        // We don't want two empty names to be similar (they usually don't have names if they are random tracks)
        if (name1 == null || name2 == null || name1.isEmpty() || name2.isEmpty())
            return false
        return name1 == name2
    }

    fun getPointForOrientationCalculation(edgeIteratorState: EdgeIteratorState, nodeAccess: NodeAccess): GHPoint {
        val tmpLat: Double
        val tmpLon: Double
        val tmpWayGeo = edgeIteratorState.fetchWayGeometry(FetchMode.ALL)
        if (tmpWayGeo.size() <= 2) {
            tmpLat = nodeAccess.getLat(edgeIteratorState.adjNode)
            tmpLon = nodeAccess.getLon(edgeIteratorState.adjNode)
        } else {
            tmpLat = tmpWayGeo.getLat(1)
            tmpLon = tmpWayGeo.getLon(1)
        }
        return GHPoint(tmpLat, tmpLon)
    }

    fun isToFerry(re: RoadEnvironment, prev: RoadEnvironment?): Boolean {
        return re == RoadEnvironment.FERRY && re != prev
    }

    fun isFromFerry(re: RoadEnvironment, prev: RoadEnvironment?): Boolean {
        return prev == RoadEnvironment.FERRY && re != prev
    }

    fun createFerryInfo(re: RoadEnvironment, prev: RoadEnvironment?): String? {
        if (re == prev) return null
        if (re == RoadEnvironment.FERRY) return "board_ferry"
        if (prev == RoadEnvironment.FERRY) return "leave_ferry"
        return null
    }
}
