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
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import static com.graphhopper.util.Parameters.Details.*;

/**
 * This class calculates instructions from the edges in a Path.
 *
 * @author Peter Karich
 * @author Robin Boldt
 * @author jan soe
 */
public class InstructionsFromEdges implements Path.EdgeVisitor {

    private final Weighting weighting;
    private final NodeAccess nodeAccess;

    private final InstructionList ways;
    private final EdgeExplorer outEdgeExplorer;
    private final EdgeExplorer allExplorer;
    private final BooleanEncodedValue roundaboutEnc;
    private final BooleanEncodedValue roadClassLinkEnc;
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final IntEncodedValue lanesEnc;
    private final DecimalEncodedValue maxSpeedEnc;

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
    private double prevInstructionPrevOrientation = Double.NaN;
    private Instruction prevInstruction;
    private boolean prevInRoundabout;
    private String prevDestinationAndRef;
    private String prevName;
    private String prevInstructionName;

    private static final int MAX_U_TURN_DISTANCE = 35;

    public InstructionsFromEdges(Graph graph, Weighting weighting, EncodedValueLookup evLookup,
                                 InstructionList ways) {
        this.weighting = weighting;
        this.roundaboutEnc = evLookup.getBooleanEncodedValue(Roundabout.KEY);
        this.roadClassEnc = evLookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        this.roadClassLinkEnc = evLookup.getBooleanEncodedValue(RoadClassLink.KEY);
        this.maxSpeedEnc = evLookup.getDecimalEncodedValue(MaxSpeed.KEY);
        this.lanesEnc = evLookup.hasEncodedValue(Lanes.KEY) ? evLookup.getIntEncodedValue(Lanes.KEY) : null;
        this.nodeAccess = graph.getNodeAccess();
        this.ways = ways;
        prevNode = -1;
        prevInRoundabout = false;
        prevName = null;
        outEdgeExplorer = graph.createEdgeExplorer(edge -> Double.isFinite(weighting.calcEdgeWeight(edge, false)));
        allExplorer = graph.createEdgeExplorer();
    }

    /**
     * @return the list of instructions for this path.
     */
    public static InstructionList calcInstructions(Path path, Graph graph, Weighting weighting, EncodedValueLookup evLookup, final Translation tr) {
        final InstructionList ways = new InstructionList(tr);
        if (path.isFound()) {
            if (path.getEdgeCount() == 0) {
                ways.add(new FinishInstruction(graph.getNodeAccess(), path.getEndNode()));
            } else {
                path.forEveryEdge(new InstructionsFromEdges(graph, weighting, evLookup, ways));
            }
        }
        return ways;
    }

    @Override
    public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
        // baseNode is the current node and adjNode is the next
        int adjNode = edge.getAdjNode();
        int baseNode = edge.getBaseNode();

        if (prevNode == -1) {
            prevLat = this.nodeAccess.getLat(baseNode);
            prevLon = this.nodeAccess.getLon(baseNode);
        }

        double adjLat = nodeAccess.getLat(adjNode);
        double adjLon = nodeAccess.getLon(adjNode);
        double latitude, longitude;

        PointList wayGeo = edge.fetchWayGeometry(FetchMode.ALL);
        boolean isRoundabout = edge.get(roundaboutEnc);

        if (wayGeo.size() <= 2) {
            latitude = adjLat;
            longitude = adjLon;
        } else {
            latitude = wayGeo.getLat(1);
            longitude = wayGeo.getLon(1);
            assert Double.compare(prevLat, nodeAccess.getLat(baseNode)) == 0;
            assert Double.compare(prevLon, nodeAccess.getLon(baseNode)) == 0;
        }

        final String name = (String) edge.getValue(STREET_NAME);
        final String ref = (String) edge.getValue(STREET_REF);
        final String destination = (String) edge.getValue(STREET_DESTINATION); // getValue is fast if it does not exist in edge
        final String destinationRef = (String) edge.getValue(STREET_DESTINATION_REF);
        final String motorwayJunction = (String) edge.getValue(MOTORWAY_JUNCTION);
        if ((prevInstruction == null) && (!isRoundabout)) // very first instruction (if not in Roundabout)
        {
            int sign = Instruction.CONTINUE_ON_STREET;
            prevInstruction = new Instruction(sign, name, new PointList(10, nodeAccess.is3D()));
            prevInstruction.setExtraInfo(STREET_REF, ref);
            prevInstruction.setExtraInfo(STREET_DESTINATION, destination);
            prevInstruction.setExtraInfo(STREET_DESTINATION_REF, destinationRef);
            prevInstruction.setExtraInfo(MOTORWAY_JUNCTION, motorwayJunction);
            double startLat = nodeAccess.getLat(baseNode);
            double startLon = nodeAccess.getLon(baseNode);
            double heading = AngleCalc.ANGLE_CALC.calcAzimuth(startLat, startLon, latitude, longitude);
            prevInstruction.setExtraInfo("heading", Helper.round(heading, 2));
            ways.add(prevInstruction);
            prevName = name;
            prevDestinationAndRef = destination + destinationRef;

        } else if (isRoundabout) {
            // remark: names and annotations within roundabout are ignored
            if (!prevInRoundabout) //just entered roundabout
            {
                int sign = Instruction.USE_ROUNDABOUT;
                RoundaboutInstruction roundaboutInstruction = new RoundaboutInstruction(sign, name,
                        new PointList(10, nodeAccess.is3D()));
                prevInstructionPrevOrientation = prevOrientation;
                if (prevInstruction != null) {
                    // check if there is an exit at the same node the roundabout was entered
                    EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
                    while (edgeIter.next()) {
                        if ((edgeIter.getAdjNode() != prevNode) && !edgeIter.get(roundaboutEnc)) {
                            roundaboutInstruction.increaseExitNumber();
                            break;
                        }
                    }

                    // previous orientation is last orientation before entering roundabout
                    prevOrientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);

                    // calculate direction of entrance turn to determine direction of rotation
                    // right turn == counterclockwise and vice versa
                    double orientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    orientation = AngleCalc.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
                    double delta = (orientation - prevOrientation);
                    roundaboutInstruction.setDirOfRotation(delta);

                } else // first instructions is roundabout instruction
                {
                    prevOrientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    prevName = name;
                    prevDestinationAndRef = destination + destinationRef;
                }
                prevInstruction = roundaboutInstruction;
                ways.add(prevInstruction);
            }

            // Add passed exits to instruction. A node is counted if there is at least one outgoing edge
            // out of the roundabout
            EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(edge.getAdjNode());
            while (edgeIter.next()) {
                if (!edgeIter.get(roundaboutEnc)) {
                    ((RoundaboutInstruction) prevInstruction).increaseExitNumber();
                    break;
                }
            }

        } else if (prevInRoundabout) //previously in roundabout but not anymore
        {
            prevInstruction.setName(name);
            prevInstruction.setExtraInfo(STREET_REF, ref);
            prevInstruction.setExtraInfo(STREET_DESTINATION, destination);
            prevInstruction.setExtraInfo(STREET_DESTINATION_REF, destinationRef);
            prevInstruction.setExtraInfo(MOTORWAY_JUNCTION, motorwayJunction);

            // calc angle between roundabout entrance and exit
            double orientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
            orientation = AngleCalc.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double deltaInOut = (orientation - prevOrientation);

            // calculate direction of exit turn to determine direction of rotation
            // right turn == counterclockwise and vice versa
            double recentOrientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
            orientation = AngleCalc.ANGLE_CALC.alignOrientation(recentOrientation, orientation);
            double deltaOut = (orientation - recentOrientation);

            prevInstruction = ((RoundaboutInstruction) prevInstruction)
                    .setRadian(deltaInOut)
                    .setDirOfRotation(deltaOut)
                    .setExited();

            prevInstructionName = prevName;
            prevName = name;
            prevDestinationAndRef = destination + destinationRef;

        } else {
            int sign = getTurn(edge, baseNode, prevNode, adjNode, name, destination + destinationRef);
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

                boolean isUTurn = false;
                int uTurnType = Instruction.U_TURN_UNKNOWN;
                if (!Double.isNaN(prevInstructionPrevOrientation)
                        && prevInstruction.getDistance() < MAX_U_TURN_DISTANCE
                        && (sign < 0) == (prevInstruction.getSign() < 0)
                        && (Math.abs(sign) == Instruction.TURN_SLIGHT_RIGHT || Math.abs(sign) == Instruction.TURN_RIGHT || Math.abs(sign) == Instruction.TURN_SHARP_RIGHT)
                        && (Math.abs(prevInstruction.getSign()) == Instruction.TURN_SLIGHT_RIGHT || Math.abs(prevInstruction.getSign()) == Instruction.TURN_RIGHT || Math.abs(prevInstruction.getSign()) == Instruction.TURN_SHARP_RIGHT)
                        && Double.isFinite(weighting.calcEdgeWeight(edge, false)) != Double.isFinite(weighting.calcEdgeWeight(edge, true))
                        && InstructionsHelper.isNameSimilar(prevInstructionName, name)) {
                    // Chances are good that this is a u-turn, we only need to check if the orientation matches
                    GHPoint point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess);
                    double lat = point.getLat();
                    double lon = point.getLon();
                    double currentOrientation = AngleCalc.ANGLE_CALC.calcOrientation(prevLat, prevLon, lat, lon, false);

                    double diff = Math.abs(prevInstructionPrevOrientation - currentOrientation);
                    if (diff > (Math.PI * .9) && diff < (Math.PI * 1.1)) {
                        isUTurn = true;
                        if (sign < 0) {
                            uTurnType = Instruction.U_TURN_LEFT;
                        } else {
                            uTurnType = Instruction.U_TURN_RIGHT;
                        }
                    }
                }

                if (isUTurn) {
                    prevInstruction.setSign(uTurnType);
                    prevInstruction.setName(name);
                } else {
                    prevInstruction = new Instruction(sign, name, new PointList(10, nodeAccess.is3D()));
                    // Remember the Orientation and name of the road, before doing this maneuver
                    prevInstructionPrevOrientation = prevOrientation;
                    prevInstructionName = prevName;
                    ways.add(prevInstruction);
                }
                prevInstruction.setExtraInfo(STREET_REF, ref);
                prevInstruction.setExtraInfo(STREET_DESTINATION, destination);
                prevInstruction.setExtraInfo(STREET_DESTINATION_REF, destinationRef);
                prevInstruction.setExtraInfo(MOTORWAY_JUNCTION, motorwayJunction);
            }
            // Update the prevName, since we don't always create an instruction on name changes the previous
            // name can be an old name. This leads to incorrect turn instructions due to name changes
            prevName = name;
            prevDestinationAndRef = destination + destinationRef;
        }

        updatePointsAndInstruction(edge, wayGeo);

        if (wayGeo.size() <= 2) {
            doublePrevLat = prevLat;
            doublePrevLon = prevLon;
        } else {
            int beforeLast = wayGeo.size() - 2;
            doublePrevLat = wayGeo.getLat(beforeLast);
            doublePrevLon = wayGeo.getLon(beforeLast);
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
            double orientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
            orientation = AngleCalc.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double delta = (orientation - prevOrientation);
            ((RoundaboutInstruction) prevInstruction).setRadian(delta);

        }

        Instruction finishInstruction = new FinishInstruction(nodeAccess, prevEdge.getAdjNode());
        // This is the heading how the edge ended
        finishInstruction.setExtraInfo("last_heading", AngleCalc.ANGLE_CALC.calcAzimuth(doublePrevLat, doublePrevLon, prevLat, prevLon));
        ways.add(finishInstruction);
    }

    private int getTurn(EdgeIteratorState edge, int baseNode, int prevNode, int adjNode, String name, String destinationAndRef) {
        if (edge.getEdge() == prevEdge.getEdge())
            // this is the simplest turn to recognize, a plain u-turn.
            return Instruction.U_TURN_UNKNOWN;
        GHPoint point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess);
        double lat = point.getLat();
        double lon = point.getLon();
        prevOrientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
        int sign = InstructionsHelper.calculateSign(prevLat, prevLon, lat, lon, prevOrientation);

        InstructionsOutgoingEdges outgoingEdges = new InstructionsOutgoingEdges(prevEdge, edge, weighting, maxSpeedEnc,
                roadClassEnc, roadClassLinkEnc, allExplorer, nodeAccess, prevNode, baseNode, adjNode);
        int nrOfPossibleTurns = outgoingEdges.getAllowedTurns();

        // there is no other turn possible
        if (nrOfPossibleTurns <= 1) {
            if (Math.abs(sign) > 1 && outgoingEdges.getVisibleTurns() > 1) {
                // This is an actual turn because |sign| > 1
                // There could be some confusion, if we would not create a turn instruction, even though it is the only
                // possible turn, also see #1048
                // TODO if we see issue with this approach we could consider checking if the edge is a oneway
                return sign;
            }
            return Instruction.IGNORE;
        }

        // Very certain, this is a turn
        if (Math.abs(sign) > 1) {
            // Don't show an instruction if the user is following a street, even though the street is
            // bending. We should only do this, if following the street is the obvious choice.
            if (InstructionsHelper.isNameSimilar(name, prevName)
                    && (outgoingEdges.outgoingEdgesAreSlowerByFactor(2) || isDirectionSeparatelyTagged(edge, prevEdge))) {
                return Instruction.IGNORE;
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

        boolean outgoingEdgesAreSlower = outgoingEdges.outgoingEdgesAreSlowerByFactor(1);

        // There is at least one other possibility to turn, and we are almost going straight
        // Check the other turns if one of them is also going almost straight
        // If not, we don't need a turn instruction
        EdgeIteratorState otherContinue = outgoingEdges.getOtherContinue(prevLat, prevLon, prevOrientation);

        // Signs provide too less detail, so we use the delta for a precise comparison
        double delta = InstructionsHelper.calculateOrientationDelta(prevLat, prevLon, lat, lon, prevOrientation);

        // This state is bad! Two streets are going more or less straight
        // Happens a lot for trunk_links
        // For _links, comparing flags works quite good, as links usually have different speeds => different flags
        if (otherContinue != null) {
            // We are at a fork
            if (!InstructionsHelper.isNameSimilar(name, prevName)
                    || !InstructionsHelper.isNameSimilar(destinationAndRef, prevDestinationAndRef)
                    || InstructionsHelper.isNameSimilar(otherContinue.getName(), prevName)
                    || !outgoingEdgesAreSlower) {

                final RoadClass roadClass = edge.get(roadClassEnc);
                final RoadClass prevRoadClass = prevEdge.get(roadClassEnc);
                final RoadClass otherRoadClass = otherContinue.get(roadClassEnc);
                final boolean link = edge.get(roadClassLinkEnc);
                final boolean prevLink = prevEdge.get(roadClassLinkEnc);
                final boolean otherLink = otherContinue.get(roadClassLinkEnc);
                // We know this is a fork, but we only need an instruction if highways are actually changing,
                // this approach only works for major roads, for minor roads it can be hard to differentiate easily in real life
                if (roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.TRUNK || roadClass == RoadClass.PRIMARY || roadClass == RoadClass.SECONDARY || roadClass == RoadClass.TERTIARY) {
                    if ((roadClass == prevRoadClass && link == prevLink) && (otherRoadClass != prevRoadClass || otherLink != prevLink)) {
                        return Instruction.IGNORE;
                    }
                }

                GHPoint tmpPoint = InstructionsHelper.getPointForOrientationCalculation(otherContinue, nodeAccess);
                double otherDelta = InstructionsHelper.calculateOrientationDelta(prevLat, prevLon, tmpPoint.getLat(), tmpPoint.getLon(), prevOrientation);

                // This is required to avoid keep left/right on the motorway at off-ramps/motorway_links
                if (Math.abs(delta) < .1 && Math.abs(otherDelta) > .15 && InstructionsHelper.isNameSimilar(name, prevName)) {
                    return Instruction.CONTINUE_ON_STREET;
                }

                if (otherDelta < delta) {
                    return Instruction.KEEP_LEFT;
                } else {
                    return Instruction.KEEP_RIGHT;
                }
            }
        }

        if (!outgoingEdgesAreSlower && !isDirectionSeparatelyTagged(edge, prevEdge)
                && (Math.abs(delta) > .6 || outgoingEdges.isLeavingCurrentStreet(prevName, name))) {
            // Leave the current road -> create instruction
            return sign;
        }

        return Instruction.IGNORE;
    }

    private boolean isDirectionSeparatelyTagged(EdgeIteratorState edge, EdgeIteratorState prevEdge) {
        if (lanesEnc == null) return false;
        // for cases like in #2946 we should not create instructions as they are only "tagging artifacts"
        int lanes = edge.get(lanesEnc);
        int prevLanes = prevEdge.get(lanesEnc);
        // Usually it is a 2+2 split and then the equal sign applies. In case of a "3+2 split" we need ">=".
        return lanes * 2 >= prevLanes || lanes <= 2 * prevLanes;
    }

    private void updatePointsAndInstruction(EdgeIteratorState edge, PointList pl) {
        // skip adjNode
        int len = pl.size() - 1;
        for (int i = 0; i < len; i++) {
            prevInstruction.getPoints().add(pl, i);
        }
        double newDist = edge.getDistance();
        prevInstruction.setDistance(newDist + prevInstruction.getDistance());
        if (prevEdge != null)
            prevInstruction.setTime(GHUtility.calcMillisWithTurnMillis(weighting, edge, false, prevEdge.getEdge()) + prevInstruction.getTime());
        else
            prevInstruction.setTime(weighting.calcEdgeMillis(edge, false) + prevInstruction.getTime());
    }

}
