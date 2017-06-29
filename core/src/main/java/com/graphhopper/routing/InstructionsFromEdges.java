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

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

/**
 * This class calculates instructions from the edges in a Path.
 *
 * @author Peter Karich
 * @author Robin Boldt
 * @author jan soe
 */
public class InstructionsFromEdges implements Path.EdgeVisitor {

    private final Weighting weighting;
    private final FlagEncoder encoder;
    private final NodeAccess nodeAccess;

    private final Translation tr;
    private final InstructionList ways;
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
    private EdgeIteratorState prevEdge;
    private double prevLat;
    private double prevLon;
    private double doublePrevLat, doublePrevLon; // Lat and Lon of node t-2
    private int prevNode;
    private double prevOrientation;
    private Instruction prevInstruction;
    private boolean prevInRoundabout;
    private String prevName;
    private InstructionAnnotation prevAnnotation;
    private EdgeExplorer outEdgeExplorer;
    private EdgeExplorer crossingExplorer;

    public InstructionsFromEdges(int tmpNode, Graph graph, Weighting weighting, FlagEncoder encoder, NodeAccess nodeAccess, Translation tr, InstructionList ways) {
        this.weighting = weighting;
        this.encoder = encoder;
        this.nodeAccess = nodeAccess;
        this.tr = tr;
        this.ways = ways;
        prevLat = this.nodeAccess.getLatitude(tmpNode);
        prevLon = this.nodeAccess.getLongitude(tmpNode);
        prevNode = -1;
        prevInRoundabout = false;
        prevName = null;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(this.encoder, false, true));
        crossingExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, true));
    }


    @Override
    public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
        // baseNode is the current node and adjNode is the next
        int adjNode = edge.getAdjNode();
        int baseNode = edge.getBaseNode();
        long flags = edge.getFlags();
        double adjLat = nodeAccess.getLatitude(adjNode);
        double adjLon = nodeAccess.getLongitude(adjNode);
        double latitude, longitude;

        PointList wayGeo = edge.fetchWayGeometry(3);
        boolean isRoundabout = encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT);

        if (wayGeo.getSize() <= 2) {
            latitude = adjLat;
            longitude = adjLon;
        } else {
            latitude = wayGeo.getLatitude(1);
            longitude = wayGeo.getLongitude(1);
            assert Double.compare(prevLat, nodeAccess.getLatitude(baseNode)) == 0;
            assert Double.compare(prevLon, nodeAccess.getLongitude(baseNode)) == 0;
        }

        String name = edge.getName();
        InstructionAnnotation annotation = encoder.getAnnotation(flags, tr);

        if ((prevName == null) && (!isRoundabout)) // very first instruction (if not in Roundabout)
        {
            int sign = Instruction.CONTINUE_ON_STREET;
            prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
            ways.add(prevInstruction);
            prevName = name;
            prevAnnotation = annotation;

        } else if (isRoundabout) {
            // remark: names and annotations within roundabout are ignored
            if (!prevInRoundabout) //just entered roundabout
            {
                int sign = Instruction.USE_ROUNDABOUT;
                RoundaboutInstruction roundaboutInstruction = new RoundaboutInstruction(sign, name,
                        annotation, new PointList(10, nodeAccess.is3D()));
                if (prevName != null) {
                    // check if there is an exit at the same node the roundabout was entered
                    EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
                    while (edgeIter.next()) {
                        if ((edgeIter.getAdjNode() != prevNode)
                                && !encoder.isBool(edgeIter.getFlags(), FlagEncoder.K_ROUNDABOUT)) {
                            roundaboutInstruction.increaseExitNumber();
                            break;
                        }
                    }

                    // previous orientation is last orientation before entering roundabout
                    prevOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);

                    // calculate direction of entrance turn to determine direction of rotation
                    // right turn == counterclockwise and vice versa
                    double orientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
                    double delta = (orientation - prevOrientation);
                    roundaboutInstruction.setDirOfRotation(delta);

                } else // first instructions is roundabout instruction
                {
                    prevOrientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    prevName = name;
                    prevAnnotation = annotation;
                }
                prevInstruction = roundaboutInstruction;
                ways.add(prevInstruction);
            }

            // Add passed exits to instruction. A node is counted if there is at least one outgoing edge
            // out of the roundabout
            EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(edge.getAdjNode());
            while (edgeIter.next()) {
                if (!encoder.isBool(edgeIter.getFlags(), FlagEncoder.K_ROUNDABOUT)) {
                    ((RoundaboutInstruction) prevInstruction).increaseExitNumber();
                    break;
                }
            }

        } else if (prevInRoundabout) //previously in roundabout but not anymore
        {

            prevInstruction.setName(name);

            // calc angle between roundabout entrance and exit
            double orientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
            orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double deltaInOut = (orientation - prevOrientation);

            // calculate direction of exit turn to determine direction of rotation
            // right turn == counterclockwise and vice versa
            double recentOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
            orientation = Helper.ANGLE_CALC.alignOrientation(recentOrientation, orientation);
            double deltaOut = (orientation - recentOrientation);

            prevInstruction = ((RoundaboutInstruction) prevInstruction)
                    .setRadian(deltaInOut)
                    .setDirOfRotation(deltaOut)
                    .setExited();

            prevName = name;
            prevAnnotation = annotation;

        } else {
            int sign = getTurn(edge, baseNode, prevNode, adjNode, annotation, name);

            if (sign != Instruction.IGNORE) {
                prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
                ways.add(prevInstruction);
                prevAnnotation = annotation;
            }
            // Updated the prevName, since we don't always create an instruction on name changes the previous
            // name can be an old name. This leads to incorrect turn instructions due to name changes
            prevName = name;
        }

        updatePointsAndInstruction(edge, wayGeo);

        if (wayGeo.getSize() <= 2) {
            doublePrevLat = prevLat;
            doublePrevLon = prevLon;
        } else {
            int beforeLast = wayGeo.getSize() - 2;
            doublePrevLat = wayGeo.getLatitude(beforeLast);
            doublePrevLon = wayGeo.getLongitude(beforeLast);
        }

        prevInRoundabout = isRoundabout;
        prevNode = baseNode;
        prevLat = adjLat;
        prevLon = adjLon;
        prevEdge = edge;
    }

    @Override
    public void finish() {
        if (prevInRoundabout) {
            // calc angle between roundabout entrance and finish
            double orientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
            orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double delta = (orientation - prevOrientation);
            ((RoundaboutInstruction) prevInstruction).setRadian(delta);

        }
        ways.add(new FinishInstruction(nodeAccess, prevEdge.getAdjNode()));
    }

    private int getTurn(EdgeIteratorState edge, int baseNode, int prevNode, int adjNode, InstructionAnnotation annotation, String name) {
        GHPoint point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess);
        double lat = point.getLat();
        double lon = point.getLon();
        prevOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
        int sign = InstructionsHelper.calculateSign(prevLat, prevLon, lat, lon, prevOrientation);

        boolean forceInstruction = false;

        if (!annotation.equals(prevAnnotation) && !annotation.isEmpty()) {
            forceInstruction = true;
        }

        InstructionsSurroundingEdges surroundingEdges = new InstructionsSurroundingEdges(prevEdge, edge, encoder, crossingExplorer, nodeAccess, prevNode, baseNode, adjNode);
        int nrOfPossibleTurns = surroundingEdges.nrOfPossibleTurns();

        // there is no other turn possible
        if (nrOfPossibleTurns <= 1) {
            return returnForcedInstructionOrIgnore(forceInstruction, sign);
        }

        // Very certain, this is a turn
        if (Math.abs(sign) > 1) {
                        /*
                         * Don't show an instruction if the user is following a street, even though the street is
                         * bending. We should only do this, if following the street is the obvious choice.
                         */
            if (InstructionsHelper.isNameSimilar(name, prevName) && surroundingEdges.surroundingStreetsAreSlowerByFactor(2)) {
                return returnForcedInstructionOrIgnore(forceInstruction, sign);
            }

            return sign;
        }

        /*
        The current state is a bit uncertain. So we are going more or less straight sign < 2
        So it really depends on the surrounding street if we need a turn instruction or not
        In most cases this will be a simple follow the current street and we don't necessarily
        need a turn instruction
         */
        if (prevEdge == null) {
            // TODO Should we log this case?
            return sign;
        }

        long flag = edge.getFlags();
        long prevFlag = prevEdge.getFlags();

        boolean surroundingStreetsAreSlower = surroundingEdges.surroundingStreetsAreSlowerByFactor(1);

        // There is at least one other possibility to turn, and we are almost going straight
        // Check the other turns if one of them is also going almost straight
        // If not, we don't need a turn instruction
        EdgeIteratorState otherContinue = surroundingEdges.getOtherContinue(prevLat, prevLon, prevOrientation);

        // Signs provide too less detail, so we use the delta for a precise comparision
        double delta = InstructionsHelper.calculateOrientationDelta(prevLat, prevLon, lat, lon, prevOrientation);

        // This state is bad! Two streets are going more or less straight
        // Happens a lot for trunk_links
        // For _links, comparing flags works quite good, as links usually have different speeds => different flags
        if (otherContinue != null) {
            //We are at a fork
            if (!InstructionsHelper.isNameSimilar(name, prevName)
                    || InstructionsHelper.isNameSimilar(otherContinue.getName(), prevName)
                    || prevFlag != flag
                    || prevFlag == otherContinue.getFlags()
                    || !surroundingStreetsAreSlower) {
                GHPoint tmpPoint = InstructionsHelper.getPointForOrientationCalculation(otherContinue, nodeAccess);
                double otherDelta = InstructionsHelper.calculateOrientationDelta(prevLat, prevLon, tmpPoint.getLat(), tmpPoint.getLon(), prevOrientation);

                if (Math.abs(delta) < .1 && Math.abs(otherDelta) > .15 && InstructionsHelper.isNameSimilar(name, prevName)) {
                    return Instruction.CONTINUE_ON_STREET;
                }

                if (otherDelta < delta) {
                    // TODO Use keeps once we have a robust client
                    //return Instruction.KEEP_LEFT;
                    return Instruction.TURN_SLIGHT_LEFT;
                } else {
                    // TODO Use keeps once we have a robust client
                    //return Instruction.KEEP_RIGHT;
                    return Instruction.TURN_SLIGHT_RIGHT;
                }


            }
        }

        if (!surroundingStreetsAreSlower) {
            if (Math.abs(delta) > .4
                    || surroundingEdges.isLeavingCurrentStreet(prevName, name)) {
                // Leave the current road -> create instruction
                return sign;

            }
        }

        return returnForcedInstructionOrIgnore(forceInstruction, sign);
    }

    private int returnForcedInstructionOrIgnore(boolean forceInstruction, int sign) {
        if (forceInstruction)
            return sign;
        return Instruction.IGNORE;
    }

    private void updatePointsAndInstruction(EdgeIteratorState edge, PointList pl) {
        // skip adjNode
        int len = pl.size() - 1;
        for (int i = 0; i < len; i++) {
            prevInstruction.getPoints().add(pl, i);
        }
        double newDist = edge.getDistance();
        prevInstruction.setDistance(newDist + prevInstruction.getDistance());
        prevInstruction.setTime(weighting.calcMillis(edge, false, EdgeIterator.NO_EDGE)
                + prevInstruction.getTime());
    }

}