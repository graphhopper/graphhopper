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
package com.graphhopper.routing;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

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
 * - the allowedAlternativeTurns are B->C => return value of {@link #getAllowedTurns()} is 1
 * - the visibleAlternativeTurns are B->X and B->C => return values of {@link #getVisibleTurns()} is 2
 *
 * @author Robin Boldt
 */
class InstructionsOutgoingEdges {

    private final EdgeIteratorState prevEdge;
    private final EdgeIteratorState currentEdge;
    // edges that one can turn onto
    private final List<EdgeIteratorState> allowedAlternativeTurns;
    // edges, including oneways in the wrong direction
    private final List<EdgeIteratorState> visibleAlternativeTurns;
    private final DecimalEncodedValue maxSpeedEnc;
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final BooleanEncodedValue roadClassLinkEnc;
    private final IntEncodedValue lanesEnc;
    private final NodeAccess nodeAccess;
    private final Weighting weighting;
    private final int baseNode;
    private final EdgeExplorer allExplorer;

    public InstructionsOutgoingEdges(EdgeIteratorState prevEdge,
                                     EdgeIteratorState currentEdge,
                                     Weighting weighting,
                                     DecimalEncodedValue maxSpeedEnc,
                                     EnumEncodedValue<RoadClass> roadClassEnc,
                                     BooleanEncodedValue roadClassLinkEnc,
                                     IntEncodedValue lanesEnc,
                                     EdgeExplorer allExplorer,
                                     NodeAccess nodeAccess,
                                     int prevNode,
                                     int baseNode,
                                     int adjNode) {
        this.prevEdge = prevEdge;
        this.currentEdge = currentEdge;
        this.weighting = weighting;
        this.maxSpeedEnc = maxSpeedEnc;
        this.roadClassEnc = roadClassEnc;
        this.roadClassLinkEnc = roadClassLinkEnc;
        this.lanesEnc = lanesEnc;
        this.nodeAccess = nodeAccess;
        this.baseNode = baseNode;
        this.allExplorer = allExplorer;

        visibleAlternativeTurns = new ArrayList<>();
        allowedAlternativeTurns = new ArrayList<>();
        EdgeIterator edgeIter = allExplorer.setBaseNode(baseNode);
        while (edgeIter.next()) {
            if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                if (Double.isFinite(weighting.calcEdgeWeight(edgeIter, false))) {
                    EdgeIteratorState tmpEdge = edgeIter.detach(false);
                    allowedAlternativeTurns.add(tmpEdge);
                    visibleAlternativeTurns.add(tmpEdge);
                } else if (Double.isFinite(weighting.calcEdgeWeight(edgeIter, true))) {
                    visibleAlternativeTurns.add(edgeIter.detach(false));
                }
            }
        }
    }

    /**
     * This method calculates the number of allowed outgoing edges, which could be considered the number of possible
     * roads one might take at the intersection. This excludes the road you are coming from and inaccessible roads.
     */
    public int getAllowedTurns() {
        return 1 + allowedAlternativeTurns.size();
    }

    /**
     * This method calculates the number of all outgoing edges, which could be considered the number of roads you see
     * at the intersection. This excludes the road you are coming from and also inaccessible roads.
     */
    public int getVisibleTurns() {
        return 1 + visibleAlternativeTurns.size();
    }

    /**
     * Checks if the outgoing edges are slower by the provided factor. If they are, this indicates, that we are staying
     * on the prominent street that one would follow anyway.
     */
    public boolean outgoingEdgesAreSlowerByFactor(double factor) {
        double tmpSpeed = getSpeed(currentEdge);
        double pathSpeed = getSpeed(prevEdge);

        // speed change indicates that we change road types
        if (Math.abs(pathSpeed - tmpSpeed) >= 1) {
            return false;
        }

        double maxSurroundingSpeed = -1;

        for (EdgeIteratorState edge : allowedAlternativeTurns) {
            tmpSpeed = getSpeed(edge);
            if (tmpSpeed > maxSurroundingSpeed) {
                maxSurroundingSpeed = tmpSpeed;
            }
        }

        // surrounding streets need to be slower by a factor and call round() so that tiny differences are ignored
        return Math.round(maxSurroundingSpeed * factor) < Math.round(pathSpeed);
    }

    /**
     * Will return the tagged maxspeed, if available, if not, we use the average speed
     * TODO: Should we rely only on the tagged maxspeed?
     */
    private double getSpeed(EdgeIteratorState edge) {
        double maxSpeed = edge.get(maxSpeedEnc);
        if (Double.isInfinite(maxSpeed))
            return edge.getDistance() / weighting.calcEdgeMillis(edge, false) * 3600;
        return maxSpeed;
    }

    /**
     * Returns an edge that has more or less in the same orientation as the prevEdge, but is not the currentEdge.
     * If there is one, this indicates that we might need an instruction to help finding the correct edge out of the different choices.
     * If there is none, return null.
     */
    public EdgeIteratorState getOtherContinue(double prevLat, double prevLon, double prevOrientation) {
        int tmpSign;
        for (EdgeIteratorState edge : allowedAlternativeTurns) {
            GHPoint point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess);
            tmpSign = InstructionsHelper.calculateSign(prevLat, prevLon, point.getLat(), point.getLon(), prevOrientation);
            if (Math.abs(tmpSign) <= 1) {
                return edge;
            }
        }
        return null;
    }

    /**
     * If the name and prevName changes this method checks if either the current street is continued on a
     * different edge or if the edge we are turning onto is continued on a different edge.
     * If either of these properties is true, we can be quite certain that a turn instruction should be provided.
     */
    public boolean isLeavingCurrentStreet(String prevName, String name) {
        if (InstructionsHelper.isSameName(name, prevName)) {
            return false;
        }

        boolean roadClassOrLinkChange = !isTheSameRoadClassAndLink(prevEdge, currentEdge);
        for (EdgeIteratorState edge : allowedAlternativeTurns) {
            String edgeName = edge.getName();
            // leave the current street
            if (InstructionsHelper.isSameName(prevName, edgeName) || (roadClassOrLinkChange && isTheSameRoadClassAndLink(prevEdge, edge))) {
                return true;
            }
            // enter a different street
            if (InstructionsHelper.isSameName(name, edgeName) || (roadClassOrLinkChange && isTheSameRoadClassAndLink(currentEdge, edge))) {
                return true;
            }
        }
        return false;
    }

    private boolean isTheSameRoadClassAndLink(EdgeIteratorState edge1, EdgeIteratorState edge2) {
        return edge1.get(roadClassEnc) == edge2.get(roadClassEnc) && edge1.get(roadClassLinkEnc) == edge2.get(roadClassLinkEnc);
    }

    // for cases like in #2946 we should not create instructions as they are only "tagging artifacts"
    public boolean mergedOrSplitWay() {
        if (lanesEnc == null) return false;

        String name = currentEdge.getName();
        RoadClass roadClass = currentEdge.get(roadClassEnc);
        if (!InstructionsHelper.isSameName(name, prevEdge.getName()) || roadClass != prevEdge.get(roadClassEnc))
            return false;

        // search another edge with the same name where at least one direction is accessible
        EdgeIterator edgeIter = allExplorer.setBaseNode(baseNode);
        EdgeIteratorState otherEdge = null;
        while (edgeIter.next()) {
            if (currentEdge.getEdge() != edgeIter.getEdge()
                    && prevEdge.getEdge() != edgeIter.getEdge()
                    && roadClass == edgeIter.get(roadClassEnc)
                    && InstructionsHelper.isSameName(name, edgeIter.getName())
                    && (Double.isFinite(weighting.calcEdgeWeight(edgeIter, false))
                    || Double.isFinite(weighting.calcEdgeWeight(edgeIter, true)))) {
                if (otherEdge != null) return false; // too many possible other edges
                otherEdge = edgeIter.detach(false);
            }
        }
        if (otherEdge == null) return false;

        if (Double.isFinite(weighting.calcEdgeWeight(currentEdge, true))) {
            // assume two ways are merged into one way
            // -> prev ->
            //              <- edge ->
            // -> other ->
            if (Double.isFinite(weighting.calcEdgeWeight(prevEdge, true))) return false;
            // otherEdge has direction from junction outwards
            if (!Double.isFinite(weighting.calcEdgeWeight(otherEdge, false))) return false;
            if (Double.isFinite(weighting.calcEdgeWeight(otherEdge, true))) return false;

            int delta = Math.abs(prevEdge.get(lanesEnc) + otherEdge.get(lanesEnc) - currentEdge.get(lanesEnc));
            return delta <= 1;
        }

        // assume one way is split into two ways
        //             -> edge ->
        // <- prev ->
        //             -> other ->
        if (!Double.isFinite(weighting.calcEdgeWeight(prevEdge, true))) return false;
        // otherEdge has direction from junction outwards
        if (Double.isFinite(weighting.calcEdgeWeight(otherEdge, false))) return false;
        if (!Double.isFinite(weighting.calcEdgeWeight(otherEdge, true))) return false;

        int delta = prevEdge.get(lanesEnc) - (currentEdge.get(lanesEnc) + otherEdge.get(lanesEnc));
        return delta <= 1;
    }
}
