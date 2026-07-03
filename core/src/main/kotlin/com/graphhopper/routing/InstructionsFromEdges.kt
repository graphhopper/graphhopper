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

import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.Lanes
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.ev.RoadClassLink
import com.graphhopper.routing.ev.RoadEnvironment
import com.graphhopper.routing.ev.Roundabout
import com.graphhopper.routing.ev.VehicleAccess
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.Graph
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.AngleCalc
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIteratorState
import com.graphhopper.util.FetchMode
import com.graphhopper.util.FinishInstruction
import com.graphhopper.util.GHUtility
import com.graphhopper.util.Helper
import com.graphhopper.util.Instruction
import com.graphhopper.util.InstructionList
import com.graphhopper.util.Parameters.Details.MOTORWAY_JUNCTION
import com.graphhopper.util.Parameters.Details.STREET_DESTINATION
import com.graphhopper.util.Parameters.Details.STREET_DESTINATION_REF
import com.graphhopper.util.Parameters.Details.STREET_NAME
import com.graphhopper.util.Parameters.Details.STREET_REF
import com.graphhopper.util.PointList
import com.graphhopper.util.RoundaboutInstruction
import com.graphhopper.util.Translation
import kotlin.math.abs

/**
 * This class calculates instructions from the edges in a Path.
 *
 * @author Peter Karich
 * @author Robin Boldt
 * @author jan soe
 */
class InstructionsFromEdges(graph: Graph, private val weighting: Weighting, evLookup: EncodedValueLookup,
                            private val ways: InstructionList) : Path.EdgeVisitor {

    private val nodeAccess: NodeAccess

    private val outEdgeExplorer: EdgeExplorer
    private val allExplorer: EdgeExplorer
    private val roundaboutEnc: BooleanEncodedValue
    private val roadClassLinkEnc: BooleanEncodedValue
    private val roadClassEnc: EnumEncodedValue<RoadClass>
    private val roadEnvEnc: EnumEncodedValue<RoadEnvironment>
    private val lanesEnc: IntEncodedValue?
    private val maxSpeedEnc: DecimalEncodedValue

    /**
     * True when the current instruction started on an unnamed link road and we want to
     * replace its blank name later with the first suitable named non-link major road
     * encountered on the actual routed continuation of the same instruction.
     */
    private var prevInstructionNeedsNameFallback: Boolean

    /*
     * We need three points to make directions
     *
     *        (1)----(2)
     *        /
     *       /
     *    (0)
     *
     * 0 is the node visited at t-2, 1 is the node visited
     * at t-1 and 2 is the node being visited at instant t.
     * orientation is the angle of the vector(1->2) expressed
     * as atan2, while previousOrientation is the angle of the
     * vector(0->1)
     * Intuitively, if orientation is smaller than
     * previousOrientation, then we have to turn right, while
     * if it is greater we have to turn left. To make this
     * algorithm work, we need to make the comparison by
     * considering orientation belonging to the interval
     * [ - pi + previousOrientation , + pi + previousOrientation ]
     */
    private var prevEdge: EdgeIteratorState? = null
    private var prevLat = 0.0
    private var prevLon = 0.0
    private var doublePrevLat = 0.0
    private var doublePrevLon = 0.0 // Lat and Lon of node t-2
    private var prevNode: Int
    private var prevOrientation = 0.0
    private var prevInstructionPrevOrientation = Double.NaN
    private var prevInstruction: Instruction? = null
    private var prevInRoundabout: Boolean
    private var prevDestinationAndRef: String? = null
    private var prevName: String?
    private var prevRoadEnv: RoadEnvironment?
    private var prevInstructionName: String? = null

    init {
        this.roundaboutEnc = evLookup.getBooleanEncodedValue(Roundabout.KEY)
        this.roadEnvEnc = evLookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment::class.java)
        this.roadClassEnc = evLookup.getEnumEncodedValue(RoadClass.KEY, RoadClass::class.java)
        this.roadClassLinkEnc = evLookup.getBooleanEncodedValue(RoadClassLink.KEY)
        this.maxSpeedEnc = evLookup.getDecimalEncodedValue(MaxSpeed.KEY)
        this.lanesEnc = if (evLookup.hasEncodedValue(Lanes.KEY)) evLookup.getIntEncodedValue(Lanes.KEY) else null
        this.nodeAccess = graph.nodeAccess
        prevNode = -1
        prevInRoundabout = false
        prevName = null
        prevRoadEnv = null
        prevInstructionNeedsNameFallback = false

        val carAccessEnc = evLookup.getBooleanEncodedValue(VehicleAccess.key("car"))
        outEdgeExplorer = graph.createEdgeExplorer { edge -> edge.get(carAccessEnc) }
        allExplorer = graph.createEdgeExplorer()
    }

    companion object {
        private const val MAX_U_TURN_DISTANCE = 35

        /**
         * @return the list of instructions for this path.
         */
        @JvmStatic
        fun calcInstructions(path: Path, graph: Graph, weighting: Weighting, evLookup: EncodedValueLookup, tr: Translation): InstructionList {
            val ways = InstructionList(tr)
            if (path.isFound()) {
                if (path.getEdgeCount() == 0) {
                    ways.add(FinishInstruction(graph.nodeAccess, path.getEndNode()))
                } else {
                    path.forEveryEdge(InstructionsFromEdges(graph, weighting, evLookup, ways))
                }
            }
            return ways
        }
    }

    override fun next(edge: EdgeIteratorState, index: Int, prevEdgeId: Int) {
        // baseNode is the current node and adjNode is the next
        val adjNode = edge.adjNode
        val baseNode = edge.baseNode

        if (prevNode == -1) {
            prevLat = this.nodeAccess.getLat(baseNode)
            prevLon = this.nodeAccess.getLon(baseNode)
        }

        val adjLat = nodeAccess.getLat(adjNode)
        val adjLon = nodeAccess.getLon(adjNode)
        val latitude: Double
        val longitude: Double

        val wayGeo = edge.fetchWayGeometry(FetchMode.ALL)
        val isRoundabout = edge.get(roundaboutEnc)

        if (wayGeo.size() <= 2) {
            latitude = adjLat
            longitude = adjLon
        } else {
            latitude = wayGeo.getLat(1)
            longitude = wayGeo.getLon(1)
            assert(prevLat.compareTo(nodeAccess.getLat(baseNode)) == 0)
            assert(prevLon.compareTo(nodeAccess.getLon(baseNode)) == 0)
        }

        val name = edge.getValue(STREET_NAME) as String?
        val ref = edge.getValue(STREET_REF) as String?
        val destination = edge.getValue(STREET_DESTINATION) as String? // getValue is fast if it does not exist in edge
        val destinationRef = edge.getValue(STREET_DESTINATION_REF) as String?
        val motorwayJunction = edge.getValue(MOTORWAY_JUNCTION) as String?
        val roadEnv = edge.get(roadEnvEnc)

        if (prevInstruction == null && !isRoundabout) // very first instruction (if not in Roundabout)
        {
            val sign = Instruction.CONTINUE_ON_STREET
            val instruction = Instruction(sign, name, PointList(10, nodeAccess.is3D()))
            prevInstruction = instruction
            instruction.setExtraInfo(STREET_REF, ref)
            instruction.setExtraInfo(STREET_DESTINATION, destination)
            instruction.setExtraInfo(STREET_DESTINATION_REF, destinationRef)
            instruction.setExtraInfo(MOTORWAY_JUNCTION, motorwayJunction)
            instruction.setExtraInfo("ferry", InstructionsHelper.createFerryInfo(roadEnv, prevRoadEnv))

            val startLat = nodeAccess.getLat(baseNode)
            val startLon = nodeAccess.getLon(baseNode)
            val heading = AngleCalc.ANGLE_CALC.calcAzimuth(startLat, startLon, latitude, longitude)
            instruction.setExtraInfo("heading", Helper.round(heading, 2))
            ways.add(instruction)

            // If the route starts on an unnamed link road, keep a deferred fallback pending.
            prevInstructionNeedsNameFallback = isBlank(name) && isLinkRoad(edge)

            prevName = name
            prevRoadEnv = roadEnv
            prevDestinationAndRef = "$destination$destinationRef"
        } else if (isRoundabout) {
            // remark: names and annotations within roundabout are ignored
            if (!prevInRoundabout) //just entered roundabout
            {
                val sign = Instruction.USE_ROUNDABOUT
                val roundaboutInstruction = RoundaboutInstruction(sign, name,
                    PointList(10, nodeAccess.is3D()))
                prevInstructionPrevOrientation = prevOrientation
                if (prevInstruction != null) {
                    // check if there is an exit at the same node the roundabout was entered
                    val edgeIter = outEdgeExplorer.setBaseNode(baseNode)
                    while (edgeIter.next()) {
                        if (edgeIter.adjNode != prevNode && !edgeIter.get(roundaboutEnc)) {
                            roundaboutInstruction.increaseExitNumber()
                            break
                        }
                    }

                    // previous orientation is last orientation before entering roundabout
                    prevOrientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon)

                    // calculate direction of entrance turn to determine direction of rotation
                    // right turn == counterclockwise and vice versa
                    var orientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude)
                    orientation = AngleCalc.ANGLE_CALC.alignOrientation(prevOrientation, orientation)
                    val delta = orientation - prevOrientation
                    roundaboutInstruction.setDirOfRotation(delta)
                } else // first instructions is roundabout instruction
                {
                    prevOrientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude)
                    prevName = name
                    prevRoadEnv = roadEnv
                    prevDestinationAndRef = "$destination$destinationRef"
                }
                prevInstruction = roundaboutInstruction
                ways.add(roundaboutInstruction)
            }

            // once in roundabout, deferred fallback should not leak into roundabout instructions
            prevInstructionNeedsNameFallback = false

            // Add passed exits to instruction. A node is counted if there is at least one outgoing edge
            // out of the roundabout
            val edgeIter = outEdgeExplorer.setBaseNode(edge.adjNode)
            while (edgeIter.next()) {
                if (!edgeIter.get(roundaboutEnc)) {
                    (prevInstruction as RoundaboutInstruction).increaseExitNumber()
                    break
                }
            }
        } else if (prevInRoundabout) //previously in roundabout but not anymore
        {
            val instruction = prevInstruction!!
            instruction.setName(name ?: "")
            instruction.setExtraInfo(STREET_REF, ref)
            instruction.setExtraInfo(STREET_DESTINATION, destination)
            instruction.setExtraInfo(STREET_DESTINATION_REF, destinationRef)
            instruction.setExtraInfo(MOTORWAY_JUNCTION, motorwayJunction)
            instruction.setExtraInfo("ferry", InstructionsHelper.createFerryInfo(roadEnv, prevRoadEnv))

            // calc angle between roundabout entrance and exit
            var orientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude)
            orientation = AngleCalc.ANGLE_CALC.alignOrientation(prevOrientation, orientation)
            val deltaInOut = orientation - prevOrientation

            // calculate direction of exit turn to determine direction of rotation
            // right turn == counterclockwise and vice versa
            val recentOrientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon)
            orientation = AngleCalc.ANGLE_CALC.alignOrientation(recentOrientation, orientation)
            val deltaOut = orientation - recentOrientation

            prevInstruction = (instruction as RoundaboutInstruction)
                .setRadian(deltaInOut)
                .setDirOfRotation(deltaOut)
                .setExited()

            // exiting roundabout: no deferred fallback from previous instruction
            prevInstructionNeedsNameFallback = false

            prevInstructionName = prevName
            prevName = name
            prevRoadEnv = roadEnv
            prevDestinationAndRef = "$destination$destinationRef"
        } else {
            val sign = getTurn(edge, baseNode, prevNode, adjNode, name, "$destination$destinationRef")

            if (prevInstructionNeedsNameFallback
                && sign == Instruction.IGNORE
                && isMajorNonLinkRoad(edge)
                && hasUsableRoadLabel(name, ref)
                && !hasDestinationInfo(prevInstruction!!)
            ) {
                prevInstruction!!.setName(name)
                prevInstruction!!.setExtraInfo(STREET_REF, ref)
                prevInstructionNeedsNameFallback = false
            }

            if (sign != Instruction.IGNORE) {
                /*
                    Check if the next instruction is likely to only be a short connector to execute a u-turn
                    --A->--
                           |    <-- This is the short connector
                    --B-<--
                    Road A and Road B have to have the same name and roughly the same, but opposite orientation, otherwise we are assuming this is no u-turn.

                    Note: This approach only works if there a turn instruction for A->Connector and Connector->B.
                    Currently we don't create a turn instruction if there is no other possible turn
                    We only create a u-turn if edge B is a one-way, see #1073 for more details.
                  */

                var isUTurn = false
                var uTurnType = Instruction.U_TURN_UNKNOWN
                if (!prevInstructionPrevOrientation.isNaN()
                    && prevInstruction!!.getDistance() < MAX_U_TURN_DISTANCE
                    && (sign < 0) == (prevInstruction!!.getSign() < 0)
                    && (abs(sign) == Instruction.TURN_SLIGHT_RIGHT || abs(sign) == Instruction.TURN_RIGHT || abs(sign) == Instruction.TURN_SHARP_RIGHT)
                    && (abs(prevInstruction!!.getSign()) == Instruction.TURN_SLIGHT_RIGHT || abs(prevInstruction!!.getSign()) == Instruction.TURN_RIGHT || abs(prevInstruction!!.getSign()) == Instruction.TURN_SHARP_RIGHT)
                    && weighting.calcEdgeWeight(edge, false).isFinite() != weighting.calcEdgeWeight(edge, true).isFinite()
                    && InstructionsHelper.isSameName(prevInstructionName, name)
                ) {
                    // Chances are good that this is a u-turn, we only need to check if the orientation matches
                    val point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess)
                    val lat = point.lat
                    val lon = point.lon
                    val currentOrientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, lat, lon, false)

                    val diff = abs(prevInstructionPrevOrientation - currentOrientation)
                    if (diff > (Math.PI * .9) && diff < (Math.PI * 1.1)) {
                        isUTurn = true
                        uTurnType = if (sign < 0) {
                            Instruction.U_TURN_LEFT
                        } else {
                            Instruction.U_TURN_RIGHT
                        }
                    }
                }

                if (isUTurn) {
                    prevInstruction!!.setSign(uTurnType)
                    prevInstruction!!.setName(name)
                    prevInstructionNeedsNameFallback = false
                } else {
                    val needsDeferredFallback = isBlank(name) && isLinkRoad(edge)

                    val instruction = Instruction(sign, name, PointList(10, nodeAccess.is3D()))
                    prevInstruction = instruction
                    // Remember the Orientation and name of the road, before doing this maneuver
                    prevInstructionPrevOrientation = prevOrientation
                    prevInstructionName = prevName
                    ways.add(instruction)

                    prevInstructionNeedsNameFallback = needsDeferredFallback
                }
                prevInstruction!!.setExtraInfo(STREET_REF, ref)
                prevInstruction!!.setExtraInfo(STREET_DESTINATION, destination)
                prevInstruction!!.setExtraInfo(STREET_DESTINATION_REF, destinationRef)
                prevInstruction!!.setExtraInfo(MOTORWAY_JUNCTION, motorwayJunction)
                prevInstruction!!.setExtraInfo("ferry", InstructionsHelper.createFerryInfo(roadEnv, prevRoadEnv))
            }
            // Update the prevName, since we don't always create an instruction on name changes the previous
            // name can be an old name. This leads to incorrect turn instructions due to name changes
            prevName = name
            prevRoadEnv = roadEnv
            prevDestinationAndRef = "$destination$destinationRef"
        }

        updatePointsAndInstruction(edge, wayGeo)

        if (wayGeo.size() <= 2) {
            doublePrevLat = prevLat
            doublePrevLon = prevLon
        } else {
            val beforeLast = wayGeo.size() - 2
            doublePrevLat = wayGeo.getLat(beforeLast)
            doublePrevLon = wayGeo.getLon(beforeLast)
        }

        prevInRoundabout = isRoundabout
        prevNode = baseNode
        prevLat = adjLat
        prevLon = adjLon
        prevEdge = edge
    }

    override fun finish() {
        if (prevInRoundabout) {
            // calc angle between roundabout entrance and finish
            var orientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon)
            orientation = AngleCalc.ANGLE_CALC.alignOrientation(prevOrientation, orientation)
            val delta = orientation - prevOrientation
            (prevInstruction as RoundaboutInstruction).setRadian(delta)
        }

        val finishInstruction: Instruction = FinishInstruction(nodeAccess, prevEdge!!.adjNode)
        // This is the heading how the edge ended
        finishInstruction.setExtraInfo("last_heading", AngleCalc.ANGLE_CALC.calcAzimuth(doublePrevLat, doublePrevLon, prevLat, prevLon))
        ways.add(finishInstruction)
    }

    private fun getTurn(edge: EdgeIteratorState, baseNode: Int, prevNode: Int, adjNode: Int, name: String?, destinationAndRef: String?): Int {
        if (edge.edge == prevEdge!!.edge)
            // this is the simplest turn to recognize, a plain u-turn.
            return Instruction.U_TURN_UNKNOWN
        val roadEnv = edge.get(roadEnvEnc)
        if (InstructionsHelper.isToFerry(roadEnv, prevRoadEnv)) return Instruction.FERRY

        val point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess)
        val lat = point.lat
        val lon = point.lon
        prevOrientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon)
        val sign = InstructionsHelper.calculateSign(prevLat, prevLon, lat, lon, prevOrientation)

        val outgoingEdges = InstructionsOutgoingEdges(prevEdge!!, edge, weighting, maxSpeedEnc,
            roadClassEnc, roadClassLinkEnc, lanesEnc, allExplorer, nodeAccess, prevNode, baseNode, adjNode)
        val nrOfPossibleTurns = outgoingEdges.getAllowedTurns()

        // there is no other turn possible
        if (nrOfPossibleTurns <= 1) {
            if (abs(sign) > 1 && outgoingEdges.getVisibleTurns() > 1 && !outgoingEdges.mergedOrSplitWay()
                || InstructionsHelper.isFromFerry(roadEnv, prevRoadEnv)
            ) {
                // This is an actual turn because |sign| > 1
                // There could be some confusion, if we would not create a turn instruction, even though it is the only
                // possible turn, also see #1048
                // TODO for motorways or trunks: merge left/right onto A4
                return sign
            }
            return Instruction.IGNORE
        }

        // Very certain, this is a turn
        if (abs(sign) > 1) {
            // Don't show an instruction if the user is following a street, even though the street is
            // bending. We should only do this, if following the street is the obvious choice.
            if (InstructionsHelper.isSameName(name, prevName) && outgoingEdges.outgoingEdgesAreSlowerByFactor(2.0)
                || InstructionsHelper.isFromFerry(roadEnv, prevRoadEnv)
                || outgoingEdges.mergedOrSplitWay()
            ) {
                return Instruction.IGNORE
            }

            return sign
        }

        /*
        The current state is a bit uncertain. So we are going more or less straight sign < 2
        So it really depends on the surrounding street if we need a turn instruction or not
        In most cases this will be a simple follow the current street and we don't necessarily
        need a turn instruction
         */
        if (prevEdge == null) {
            // TODO Should we log this case?
            return sign
        }

        val outgoingEdgesAreSlower = outgoingEdges.outgoingEdgesAreSlowerByFactor(1.0)

        // There is at least one other possibility to turn, and we are almost going straight
        // Check the other turns if one of them is also going almost straight
        // If not, we don't need a turn instruction
        val otherContinue = outgoingEdges.getOtherContinue(prevLat, prevLon, prevOrientation)

        // Signs provide too less detail, so we use the delta for a precise comparison
        val delta = InstructionsHelper.calculateOrientationDelta(prevLat, prevLon, lat, lon, prevOrientation)

        if (InstructionsHelper.isFromFerry(roadEnv, prevRoadEnv))
            return Instruction.CONTINUE_ON_STREET

        // This state is bad! Two streets are going more or less straight
        if (otherContinue != null) {
            // We are at a fork
            if (!InstructionsHelper.isSameName(name, prevName)
                || !InstructionsHelper.isSameName(destinationAndRef, prevDestinationAndRef)
                || InstructionsHelper.isSameName(otherContinue.name, prevName)
                || !outgoingEdgesAreSlower
            ) {
                val roadClass = edge.get(roadClassEnc)
                val prevRoadClass = prevEdge!!.get(roadClassEnc)
                val otherRoadClass = otherContinue.get(roadClassEnc)
                val link = edge.get(roadClassLinkEnc)
                val prevLink = prevEdge!!.get(roadClassLinkEnc)
                val otherLink = otherContinue.get(roadClassLinkEnc)
                // We know this is a fork, but we only need an instruction if highways are actually changing,
                // this approach only works for major roads, for minor roads it can be hard to differentiate easily in real life
                if (roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.TRUNK || roadClass == RoadClass.PRIMARY || roadClass == RoadClass.SECONDARY || roadClass == RoadClass.TERTIARY) {
                    if ((roadClass == prevRoadClass && link == prevLink) && (otherRoadClass != prevRoadClass || otherLink != prevLink)) {
                        return Instruction.IGNORE
                    }
                }

                val tmpPoint = InstructionsHelper.getPointForOrientationCalculation(otherContinue, nodeAccess)
                val otherDelta = InstructionsHelper.calculateOrientationDelta(prevLat, prevLon, tmpPoint.lat, tmpPoint.lon, prevOrientation)

                // This is required to avoid keep left/right on the motorway at off-ramps/motorway_links
                if (abs(delta) < .1 /* ~5.7° */ && abs(otherDelta) > .15 /* ~8.6° */ && InstructionsHelper.isSameName(name, prevName)) {
                    return Instruction.CONTINUE_ON_STREET
                }

                return if (otherDelta < delta) {
                    Instruction.KEEP_LEFT
                } else {
                    Instruction.KEEP_RIGHT
                }
            }
        }

        if (!outgoingEdgesAreSlower
            && !outgoingEdges.mergedOrSplitWay()
            && (abs(delta) > .6 || outgoingEdges.isLeavingCurrentStreet(prevName, name))
        ) {
            // Leave the current road -> create instruction
            return sign
        }

        return Instruction.IGNORE
    }

    private fun updatePointsAndInstruction(edge: EdgeIteratorState, pl: PointList) {
        // skip adjNode
        val len = pl.size() - 1
        for (i in 0 until len) {
            prevInstruction!!.getPoints().add(pl, i)
        }
        val newDist = edge.distance
        prevInstruction!!.setDistance(newDist + prevInstruction!!.getDistance())
        val prevEdge = this.prevEdge
        if (prevEdge != null)
            prevInstruction!!.setTime(GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdge.edge) + prevInstruction!!.getTime())
        else
            prevInstruction!!.setTime(weighting.calcEdgeMillis(edge, false) + prevInstruction!!.getTime())
    }

    private fun isLinkRoad(edge: EdgeIteratorState): Boolean {
        return edge.get(roadClassLinkEnc)
    }

    private fun hasUsableRoadLabel(name: String?, ref: String?): Boolean {
        return !isBlank(name) || !isBlank(ref)
    }

    private fun isMajorNonLinkRoad(edge: EdgeIteratorState): Boolean {
        if (edge.get(roadClassLinkEnc)) return false

        val rc = edge.get(roadClassEnc)
        return rc == RoadClass.MOTORWAY
                || rc == RoadClass.TRUNK
                || rc == RoadClass.PRIMARY
                || rc == RoadClass.SECONDARY
                || rc == RoadClass.TERTIARY
    }

    private fun isBlank(value: String?): Boolean {
        return value == null || value.isBlank()
    }

    private fun hasDestinationInfo(instruction: Instruction): Boolean {
        val destination = instruction.getExtraInfoJSON().get(STREET_DESTINATION) as String?
        val destinationRef = instruction.getExtraInfoJSON().get(STREET_DESTINATION_REF) as String?
        val motorwayJunction = instruction.getExtraInfoJSON().get(MOTORWAY_JUNCTION) as String?

        return !isBlank(destination) || !isBlank(destinationRef) || !isBlank(motorwayJunction)
    }
}
