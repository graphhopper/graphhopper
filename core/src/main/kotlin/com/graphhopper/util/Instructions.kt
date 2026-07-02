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
package com.graphhopper.util

object Instructions {
    /**
     * This method is useful for navigation devices to find the next instruction for the specified
     * coordinate (e.g. the current position).
     *
     * @param instructions the instructions to query
     * @param maxDistance the maximum acceptable distance to the instruction (in meter)
     * @return the next Instruction or null if too far away.
     */
    @JvmStatic
    fun find(instructions: InstructionList, lat: Double, lon: Double, maxDistance: Double): Instruction? {
        // handle special cases
        if (instructions.size == 0) {
            return null
        }
        var points = instructions[0].points
        var prevLat = points.getLat(0)
        var prevLon = points.getLon(0)
        val distCalc: DistanceCalc = DistanceCalcEarth.DIST_EARTH
        var foundMinDistance = distCalc.calcNormalizedDist(lat, lon, prevLat, prevLon)
        var foundInstruction = 0

        // Search the closest edge to the query point
        if (instructions.size > 1) {
            for (instructionIndex in 0 until instructions.size) {
                points = instructions[instructionIndex].points
                for (pointIndex in 0 until points.size()) {
                    val currLat = points.getLat(pointIndex)
                    val currLon = points.getLon(pointIndex)

                    if (!(instructionIndex == 0 && pointIndex == 0)) {
                        // calculate the distance from the point to the edge
                        val distance: Double
                        var index = instructionIndex
                        if (distCalc.validEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon)) {
                            distance = distCalc.calcNormalizedEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon)
                            if (pointIndex > 0)
                                index++
                        } else {
                            distance = distCalc.calcNormalizedDist(lat, lon, currLat, currLon)
                            if (pointIndex > 0)
                                index++
                        }

                        if (distance < foundMinDistance) {
                            foundMinDistance = distance
                            foundInstruction = index
                        }
                    }
                    prevLat = currLat
                    prevLon = currLon
                }
            }
        }

        if (distCalc.calcDenormalizedDist(foundMinDistance) > maxDistance)
            return null

        // special case finish condition
        if (foundInstruction == instructions.size)
            foundInstruction--

        return instructions[foundInstruction]
    }
}
