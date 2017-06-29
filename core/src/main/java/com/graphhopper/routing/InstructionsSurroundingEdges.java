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

import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * This class maintains the surrounding edges for a single turn instruction.
 * <p>
 * There a different sets of edges.
 * The previous edge is the edge we are comming from.
 * The current edge is the edge we turn on.
 * The reachable edges are all edges we could turn on, without the prev edge and the current edge.
 * The surrounding edges are all edges surrounding the turn, without the prev edge and the current edge.
 *
 * @author Robin Boldt
 */
class InstructionsSurroundingEdges {

    final EdgeIteratorState prevEdge;
    final EdgeIteratorState currentEdge;

    // Streets that are alternative turns, excluding oneways in the wrong direction
    final List<EdgeIteratorState> reachableEdges;

    // All Streets surrounding the turn, including oneways in the wrong direction
    final List<EdgeIteratorState> surroundingEdges;

    final FlagEncoder encoder;
    final NodeAccess nodeAccess;

    public InstructionsSurroundingEdges(EdgeIteratorState prevEdge,
                                        EdgeIteratorState currentEdge,
                                        FlagEncoder encoder,
                                        EdgeExplorer crossingExplorer,
                                        NodeAccess nodeAccess,
                                        int prevNode,
                                        int baseNode,
                                        int adjNode) {
        this.prevEdge = prevEdge;
        this.currentEdge = currentEdge;
        this.encoder = encoder;
        this.nodeAccess = nodeAccess;

        EdgeIteratorState tmpEdge;

        surroundingEdges = new ArrayList<>();
        reachableEdges = new ArrayList<>();
        EdgeIterator edgeIter = crossingExplorer.setBaseNode(baseNode);
        while (edgeIter.next()) {
            if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                tmpEdge = edgeIter.detach(false);
                surroundingEdges.add(tmpEdge);
                if (encoder.isForward(tmpEdge.getFlags())) {
                    reachableEdges.add(tmpEdge);
                }
            }
        }
    }

    /**
     * Calculates the Number of possible turns, including the current turn.
     * If there is only one turn possible, e.g. continue straight on the road is a turn,
     * the method will return 1.
     */
    public int nrOfPossibleTurns() {
        return 1 + reachableEdges.size();
    }

    /**
     * Checks if the surrounding streets are slower. If they are, this indicates, that we are staying
     * on the prominent street that one would follow anyway.
     */
    public boolean surroundingStreetsAreSlowerByFactor(double factor) {
        double tmpSpeed = getSpeed(currentEdge);
        double pathSpeed = getSpeed(prevEdge);

        // Speed-Change on the path indicates, that we change road types, show instruction
        if (pathSpeed != tmpSpeed || pathSpeed < 1) {
            return false;
        }

        double maxSurroundingSpeed = -1;

        for (EdgeIteratorState edge : surroundingEdges) {
            tmpSpeed = getSpeed(edge);
            if (tmpSpeed < 1) {
                // This might happen for the DataFlagEncoder, might create unnecessary turn instructions
                return false;
            }
            if (tmpSpeed > maxSurroundingSpeed) {
                maxSurroundingSpeed = tmpSpeed;
            }
        }

        // Surrounding streets need to be slower by a factor
        return maxSurroundingSpeed * factor < pathSpeed;
    }

    private double getSpeed(EdgeIteratorState edge) {
        if (encoder instanceof DataFlagEncoder) {
            return ((DataFlagEncoder) encoder).getMaxspeed(edge, 0, false);
        } else {
            return encoder.getSpeed(edge.getFlags());
        }
    }

    /**
     * Returns an edge that is going into more or less straight compared to the prevEdge.
     * If there is none, return null.
     */
    public EdgeIteratorState getOtherContinue(double prevLat, double prevLon, double prevOrientation) {
        int tmpSign;
        for (EdgeIteratorState edge : reachableEdges) {
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
        if (InstructionsHelper.isNameSimilar(name, prevName)) {
            return false;
        }

        // If flags are changing, there might be a chance we find these flags on a different edge
        boolean checkFlag = currentEdge.getFlags() != prevEdge.getFlags();
        for (EdgeIteratorState edge : reachableEdges) {
            String edgeName = edge.getName();
            long edgeFlag = edge.getFlags();
            // leave the current street || enter a different street
            if (isTheSameStreet(prevName, prevEdge.getFlags(), edgeName, edgeFlag, checkFlag)
                    || isTheSameStreet(name, currentEdge.getFlags(), edgeName, edgeFlag, checkFlag)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTheSameStreet(String name1, long flags1, String name2, long flags2, boolean checkFlag) {
        if (InstructionsHelper.isNameSimilar(name1, name2)) {
            if (!checkFlag || flags1 == flags2) {
                return true;
            }
        }
        return false;
    }

}