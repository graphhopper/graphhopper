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

import com.graphhopper.ResponsePath
import com.graphhopper.routing.InstructionsFromEdges
import com.graphhopper.routing.Path
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.util.details.PathDetailsBuilderFactory
import com.graphhopper.util.details.PathDetailsFromEdges
import com.graphhopper.util.exceptions.ConnectionNotFoundException
import kotlin.math.abs

/**
 * This class merges multiple [Path] objects into one continuous object that
 * can be used in the [ResponsePath]. There will be a Path between every waypoint.
 * So for two waypoints there will be only one Path object. For three waypoints there will be
 * two Path objects.
 *
 * The instructions are generated per Path object and are merged into one continuous InstructionList.
 * The PointList per Path object are merged and optionally simplified.
 *
 * @author Peter Karich
 * @author ratrun
 * @author Robin Boldt
 */
class PathMerger(private val graph: Graph, weighting: Weighting) {

    private val weighting: Weighting = graph.wrapWeighting(weighting)

    private var enableInstructions = true
    private var enableViaPointInstructions = true
    private var simplifyResponse = true
    private var ramerDouglasPeucker = RDP
    private var calcPoints = true
    private var pathBuilderFactory: PathDetailsBuilderFactory? = null
    private var requestedPathDetails: List<String> = emptyList()
    private var favoredHeading = Double.NaN

    fun setCalcPoints(calcPoints: Boolean): PathMerger {
        this.calcPoints = calcPoints
        return this
    }

    fun setRamerDouglasPeucker(ramerDouglasPeucker: RamerDouglasPeucker): PathMerger {
        this.ramerDouglasPeucker = ramerDouglasPeucker
        return this
    }

    fun setPathDetailsBuilders(pathBuilderFactory: PathDetailsBuilderFactory, requestedPathDetails: List<String>): PathMerger {
        this.pathBuilderFactory = pathBuilderFactory
        this.requestedPathDetails = requestedPathDetails
        return this
    }

    fun setSimplifyResponse(simplifyRes: Boolean): PathMerger {
        this.simplifyResponse = simplifyRes
        return this
    }

    fun setEnableInstructions(enableInstructions: Boolean): PathMerger {
        this.enableInstructions = enableInstructions
        return this
    }

    fun setEnableViaPointInstructions(enableViaPointInstructions: Boolean): PathMerger {
        this.enableViaPointInstructions = enableViaPointInstructions
        return this
    }

    fun doWork(waypoints: PointList, paths: List<Path>, evLookup: EncodedValueLookup, tr: Translation?): ResponsePath {
        val responsePath = ResponsePath()
        var origPoints = 0
        var fullTimeInMillis = 0L
        var fullWeight = 0.0
        var fullDistance_mm = 0L
        var allFound = true

        var fullInstructions = InstructionList(tr)
        var fullPoints = PointList.EMPTY
        val description = ArrayList<String>()
        val wayPointIndices = ArrayList<Int>()
        for (pathIndex in paths.indices) {
            val path = paths[pathIndex]
            if (!path.isFound()) {
                allFound = false
                continue
            }
            description.addAll(path.getDescription())
            fullTimeInMillis += path.getTime()
            fullDistance_mm += path.getDistance_mm()
            fullWeight += path.getWeight()
            if (enableInstructions) {
                val il = InstructionsFromEdges.calcInstructions(path, graph, weighting, evLookup, tr!!)

                if (!il.isEmpty()) {
                    fullInstructions.addAll(il)

                    // for all paths except the last replace the FinishInstruction with a ViaInstruction
                    if (pathIndex + 1 < paths.size) {
                        val newInstr = ViaInstruction(fullInstructions[fullInstructions.size - 1])
                        newInstr.viaCount = pathIndex + 1
                        fullInstructions[fullInstructions.size - 1] = newInstr
                    }
                }
            }
            if (calcPoints || enableInstructions) {
                val tmpPoints = path.calcPoints()
                if (fullPoints.isEmpty)
                    fullPoints = PointList(tmpPoints.size(), tmpPoints.is3D)

                // Remove duplicated points, see #1138
                if (pathIndex + 1 < paths.size) {
                    tmpPoints.removeLastPoint()
                }

                fullPoints.add(tmpPoints)
                responsePath.addPathDetails(PathDetailsFromEdges.calcDetails(path, evLookup, weighting, requestedPathDetails, pathBuilderFactory, origPoints, graph))
                wayPointIndices.add(origPoints)
                if (pathIndex == paths.size - 1)
                    wayPointIndices.add(fullPoints.size() - 1)
                origPoints = fullPoints.size()
            }

            allFound = allFound && path.isFound()
        }

        if (!fullPoints.isEmpty && fullPoints.is3D)
            calcAscendDescend(responsePath, fullPoints)

        if (enableInstructions) {
            fullInstructions = updateInstructionsWithContext(fullInstructions)
            responsePath.setInstructions(fullInstructions)
        }

        if (!allFound) {
            responsePath.addError(ConnectionNotFoundException("Connection between locations not found", emptyMap()))
        }

        // make sure the way point indices actually point to the points in waypoints...
        if (allFound && !waypoints.isEmpty) { // we use empty waypoints for map-matching...
            for (i in wayPointIndices.indices) {
                val index = wayPointIndices[i]
                if (waypoints.getLat(i) != fullPoints.getLat(index) || waypoints.getLon(i) != fullPoints.getLon(index))
                    throw IllegalStateException("waypoints are not included in points, or waypoint indices are wrong")
            }
        }

        responsePath.setDescription(description)
                .setPoints(fullPoints)
                .setRouteWeight(fullWeight)
                .setDistance(fullDistance_mm / 1000.0)
                .setTime(fullTimeInMillis)
                .setWaypoints(waypoints)
                .setWaypointIndices(wayPointIndices)

        if (allFound && simplifyResponse && (calcPoints || enableInstructions)) {
            PathSimplification.simplify(responsePath, ramerDouglasPeucker, enableInstructions)
        }
        return responsePath
    }

    /**
     * This method iterates over all instructions and uses the available context to improve the instructions.
     * If the requests contains a heading, this method can transform the first continue to a u-turn if the heading
     * points into the opposite direction of the route.
     * At a waypoint it can transform the continue to a u-turn if the route involves turning.
     */
    private fun updateInstructionsWithContext(instructions: InstructionList): InstructionList {
        var i = 0
        while (i < instructions.size - 1) {
            val instruction = instructions[i]

            if (i == 0 && !favoredHeading.isNaN() && instruction.extraInfoJSON.containsKey("heading")) {
                val heading = instruction.extraInfoJSON["heading"] as Double
                val diff = abs(heading - favoredHeading) % 360
                if (diff > 170 && diff < 190) {
                    // The requested heading points into the opposite direction of the calculated heading
                    // therefore we change the continue instruction to a u-turn
                    instruction.sign = Instruction.U_TURN_UNKNOWN
                }
            }

            if (instruction.sign == Instruction.REACHED_VIA) {
                // Remove the Via Point Instruction
                if (!enableViaPointInstructions) {
                    // exactly like java's remove(i--) + loop increment: re-check the same index
                    instructions.removeAt(i)
                    continue
                }

                val nextInstruction = instructions[i + 1]
                if (nextInstruction.sign != Instruction.CONTINUE_ON_STREET
                        || !instruction.extraInfoJSON.containsKey("last_heading")
                        || !nextInstruction.extraInfoJSON.containsKey("heading")) {
                    // TODO throw exception?
                    i++
                    continue
                }
                val lastHeading = instruction.extraInfoJSON["last_heading"] as Double
                val heading = nextInstruction.extraInfoJSON["heading"] as Double

                // Since it's supposed to go back the same edge, we can be very strict with the diff
                val diff = abs(lastHeading - heading) % 360
                if (diff > 179 && diff < 181) {
                    nextInstruction.sign = Instruction.U_TURN_UNKNOWN
                }
            }
            i++
        }

        return instructions
    }

    private fun calcAscendDescend(responsePath: ResponsePath, pointList: PointList) {
        var ascendMeters = 0.0
        var descendMeters = 0.0
        var lastEle = pointList.getEle(0)
        for (i in 1 until pointList.size()) {
            val ele = pointList.getEle(i)
            val diff = abs(ele - lastEle)

            if (ele > lastEle)
                ascendMeters += diff
            else
                descendMeters += diff

            lastEle = ele
        }
        responsePath.setAscend(ascendMeters)
        responsePath.setDescend(descendMeters)
    }

    fun setFavoredHeading(favoredHeading: Double) {
        this.favoredHeading = favoredHeading
    }

    companion object {
        private val RDP = RamerDouglasPeucker()
    }
}
