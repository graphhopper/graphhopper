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
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.storage.NodeAccess
import com.graphhopper.util.EdgeExplorer
import com.graphhopper.util.EdgeIteratorState
import kotlin.math.abs

/**
 * This class handles the outgoing edges for a single turn instruction.
 * There are different sets of edges.
 * The previous edge is the edge we are coming from.
 * The current edge is the edge we turn on.
 * The allowedAlternativeTurns contains all edges that the current vehicle is allowed(*) to turn on to, excluding the prev edge and the current edge.
 * The visibleAlternativeTurns contains all edges surrounding this turn instruction, without the prev edge and the current edge.
 * (*): This might not consider turn restrictions, but only simple access values.
 * Here is an example:
 * <pre>
 * A --> B --> C
 *       ^
 *       |
 *       X
 * </pre>
 * For the route from A->B->C and baseNode=B, adjacentNode=C:
 * - the previous edge is A->B
 * - the current edge is B->C
 * - the allowedAlternativeTurns are B->C => return value of [getAllowedTurns] is 1
 * - the visibleAlternativeTurns are B->X and B->C => return values of [getVisibleTurns] is 2
 *
 * @author Robin Boldt
 */
internal class InstructionsOutgoingEdges(
    private val prevEdge: EdgeIteratorState,
    private val currentEdge: EdgeIteratorState,
    private val weighting: Weighting,
    private val maxSpeedEnc: DecimalEncodedValue,
    private val roadClassEnc: EnumEncodedValue<RoadClass>,
    private val roadClassLinkEnc: BooleanEncodedValue,
    private val lanesEnc: IntEncodedValue?,
    private val allExplorer: EdgeExplorer,
    private val nodeAccess: NodeAccess,
    prevNode: Int,
    private val baseNode: Int,
    adjNode: Int
) {
    // edges that one can turn onto
    private val allowedAlternativeTurns: MutableList<EdgeIteratorState>

    // edges, including oneways in the wrong direction
    private val visibleAlternativeTurns: MutableList<EdgeIteratorState>

    init {
        visibleAlternativeTurns = ArrayList()
        allowedAlternativeTurns = ArrayList()
        val edgeIter = allExplorer.setBaseNode(baseNode)
        while (edgeIter.next()) {
            if (edgeIter.adjNode != prevNode && edgeIter.adjNode != adjNode) {
                if (weighting.calcEdgeWeight(edgeIter, false).isFinite()) {
                    val tmpEdge = edgeIter.detach(false)
                    allowedAlternativeTurns.add(tmpEdge)
                    visibleAlternativeTurns.add(tmpEdge)
                } else if (weighting.calcEdgeWeight(edgeIter, true).isFinite()) {
                    visibleAlternativeTurns.add(edgeIter.detach(false))
                }
            }
        }
    }

    /**
     * This method calculates the number of allowed outgoing edges, which could be considered the number of possible
     * roads one might take at the intersection. This excludes the road you are coming from and inaccessible roads.
     */
    fun getAllowedTurns(): Int = 1 + allowedAlternativeTurns.size

    /**
     * This method calculates the number of all outgoing edges, which could be considered the number of roads you see
     * at the intersection. This excludes the road you are coming from and also inaccessible roads.
     */
    fun getVisibleTurns(): Int = 1 + visibleAlternativeTurns.size

    /**
     * Checks if the outgoing edges are slower by the provided factor. If they are, this indicates, that we are staying
     * on the prominent street that one would follow anyway.
     */
    fun outgoingEdgesAreSlowerByFactor(factor: Double): Boolean {
        var tmpSpeed = getSpeed(currentEdge)
        val pathSpeed = getSpeed(prevEdge)

        // speed change indicates that we change road types
        if (abs(pathSpeed - tmpSpeed) >= 1) {
            return false
        }

        var maxSurroundingSpeed = -1.0

        for (edge in allowedAlternativeTurns) {
            tmpSpeed = getSpeed(edge)
            if (tmpSpeed > maxSurroundingSpeed) {
                maxSurroundingSpeed = tmpSpeed
            }
        }

        // surrounding streets need to be slower by a factor and call round() so that tiny differences are ignored
        return Math.round(maxSurroundingSpeed * factor) < Math.round(pathSpeed)
    }

    /**
     * Will return the tagged maxspeed, if available, if not, we use the average speed
     * TODO: Should we rely only on the tagged maxspeed?
     */
    private fun getSpeed(edge: EdgeIteratorState): Double {
        val maxSpeed = edge.get(maxSpeedEnc)
        if (maxSpeed.isInfinite())
            return edge.distance / weighting.calcEdgeMillis(edge, false) * 3600
        return maxSpeed
    }

    /**
     * Returns an edge that has more or less in the same orientation as the prevEdge, but is not the currentEdge.
     * If there is one, this indicates that we might need an instruction to help finding the correct edge out of the different choices.
     * If there is none, return null.
     */
    fun getOtherContinue(prevLat: Double, prevLon: Double, prevOrientation: Double): EdgeIteratorState? {
        var tmpSign: Int
        for (edge in allowedAlternativeTurns) {
            val point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess)
            tmpSign = InstructionsHelper.calculateSign(prevLat, prevLon, point.lat, point.lon, prevOrientation)
            if (abs(tmpSign) <= 1) {
                return edge
            }
        }
        return null
    }

    /**
     * If the name and prevName changes this method checks if either the current street is continued on a
     * different edge or if the edge we are turning onto is continued on a different edge.
     * If either of these properties is true, we can be quite certain that a turn instruction should be provided.
     */
    fun isLeavingCurrentStreet(prevName: String?, name: String?): Boolean {
        if (InstructionsHelper.isSameName(name, prevName)) {
            return false
        }

        val roadClassOrLinkChange = !isTheSameRoadClassAndLink(prevEdge, currentEdge)
        for (edge in allowedAlternativeTurns) {
            val edgeName = edge.name
            // leave the current street
            if (InstructionsHelper.isSameName(prevName, edgeName) || (roadClassOrLinkChange && isTheSameRoadClassAndLink(prevEdge, edge))) {
                return true
            }
            // enter a different street
            if (InstructionsHelper.isSameName(name, edgeName) || (roadClassOrLinkChange && isTheSameRoadClassAndLink(currentEdge, edge))) {
                return true
            }
        }
        return false
    }

    private fun isTheSameRoadClassAndLink(edge1: EdgeIteratorState, edge2: EdgeIteratorState): Boolean {
        return edge1.get(roadClassEnc) == edge2.get(roadClassEnc) && edge1.get(roadClassLinkEnc) == edge2.get(roadClassLinkEnc)
    }

    // for cases like in #2946 we should not create instructions as they are only "tagging artifacts"
    fun mergedOrSplitWay(): Boolean {
        if (lanesEnc == null) return false

        val name = currentEdge.name
        val roadClass = currentEdge.get(roadClassEnc)
        if (!InstructionsHelper.isSameName(name, prevEdge.name) || roadClass != prevEdge.get(roadClassEnc))
            return false

        // search another edge with the same name where at least one direction is accessible
        val edgeIter = allExplorer.setBaseNode(baseNode)
        var otherEdge: EdgeIteratorState? = null
        while (edgeIter.next()) {
            if (currentEdge.edge != edgeIter.edge
                && prevEdge.edge != edgeIter.edge
                && roadClass == edgeIter.get(roadClassEnc)
                && InstructionsHelper.isSameName(name, edgeIter.name)
                && (weighting.calcEdgeWeight(edgeIter, false).isFinite()
                        || weighting.calcEdgeWeight(edgeIter, true).isFinite())
            ) {
                if (otherEdge != null) return false // too many possible other edges
                otherEdge = edgeIter.detach(false)
            }
        }
        if (otherEdge == null) return false

        if (weighting.calcEdgeWeight(currentEdge, true).isFinite()) {
            // assume two ways are merged into one way
            // -> prev ->
            //              <- edge ->
            // -> other ->
            if (weighting.calcEdgeWeight(prevEdge, true).isFinite()) return false
            // otherEdge has direction from junction outwards
            if (!weighting.calcEdgeWeight(otherEdge, false).isFinite()) return false
            if (weighting.calcEdgeWeight(otherEdge, true).isFinite()) return false

            val delta = abs(prevEdge.get(lanesEnc) + otherEdge.get(lanesEnc) - currentEdge.get(lanesEnc))
            return delta <= 1
        }

        // assume one way is split into two ways
        //             -> edge ->
        // <- prev ->
        //             -> other ->
        if (!weighting.calcEdgeWeight(prevEdge, true).isFinite()) return false
        // otherEdge has direction from junction outwards
        if (weighting.calcEdgeWeight(otherEdge, false).isFinite()) return false
        if (!weighting.calcEdgeWeight(otherEdge, true).isFinite()) return false

        val delta = prevEdge.get(lanesEnc) - (currentEdge.get(lanesEnc) + otherEdge.get(lanesEnc))
        return delta <= 1
    }
}
