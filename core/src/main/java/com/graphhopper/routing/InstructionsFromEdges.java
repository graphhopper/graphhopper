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
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import static com.graphhopper.routing.util.EncodingManager.getKey;

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
    private final EdgeExplorer outEdgeExplorer;
    private final EdgeExplorer crossingExplorer;
    private final BooleanEncodedValue roundaboutEnc;
    private final BooleanEncodedValue accessEnc;
    private final BooleanEncodedValue getOffBikeEnc;
    private final BooleanEncodedValue roadClassLinkEnc;
    private final EnumEncodedValue<RouteNetwork> bikeRouteEnc;
    private final EnumEncodedValue<RoadClass> roadClassEnc;
    private final EnumEncodedValue<RoadEnvironment> roadEnvEnc;
    private final EnumEncodedValue<RoadAccess> roadAccessEnc;
    private final EnumEncodedValue<Toll> tollEnc;
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
    private String prevName;
    private String prevInstructionName;
    private InstructionAnnotation prevAnnotation;

    private final int MAX_U_TURN_DISTANCE = 35;

    public InstructionsFromEdges(Graph graph, Weighting weighting, EncodedValueLookup evLookup,
                                 Translation tr, InstructionList ways) {
        this.encoder = weighting.getFlagEncoder();
        this.weighting = weighting;
        this.accessEnc = evLookup.getBooleanEncodedValue(getKey(encoder.toString(), "access"));
        this.roundaboutEnc = evLookup.getBooleanEncodedValue(Roundabout.KEY);

        // both EncodedValues are optional; And return annotation only when instructions for bike encoder is requested
        String key = RouteNetwork.key("bike");
        this.bikeRouteEnc = evLookup.hasEncodedValue(key) ? evLookup.getEnumEncodedValue(key, RouteNetwork.class) : null;
        this.getOffBikeEnc = encoder.getTransportationMode() == TransportationMode.BICYCLE && evLookup.hasEncodedValue(GetOffBike.KEY)
                ? evLookup.getBooleanEncodedValue(GetOffBike.KEY) : null;
        this.tollEnc = evLookup.hasEncodedValue(Toll.KEY) ? evLookup.getEnumEncodedValue(Toll.KEY, Toll.class) : null;

        this.roadClassEnc = evLookup.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        this.roadClassLinkEnc = evLookup.getBooleanEncodedValue(RoadClassLink.KEY);
        this.maxSpeedEnc = evLookup.getDecimalEncodedValue(MaxSpeed.KEY);
        this.roadEnvEnc = evLookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        this.roadAccessEnc = evLookup.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class);
        this.nodeAccess = graph.getNodeAccess();
        this.tr = tr;
        this.ways = ways;
        prevNode = -1;
        prevInRoundabout = false;
        prevName = null;
        outEdgeExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(encoder));
        crossingExplorer = graph.createEdgeExplorer(DefaultEdgeFilter.allEdges(encoder));
    }

    /**
     * @return the list of instructions for this path.
     */
    public static InstructionList calcInstructions(Path path, Graph graph, Weighting weighting, EncodedValueLookup evLookup, final Translation tr) {
        final InstructionList ways = new InstructionList(tr);
        if (path.isFound()) {
            if (path.getSize() == 0) {
                ways.add(new FinishInstruction(graph.getNodeAccess(), path.getEndNode()));
            } else {
                path.forEveryEdge(new InstructionsFromEdges(graph, weighting, evLookup, tr, ways));
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
            prevLat = this.nodeAccess.getLatitude(baseNode);
            prevLon = this.nodeAccess.getLongitude(baseNode);
        }

        double adjLat = nodeAccess.getLatitude(adjNode);
        double adjLon = nodeAccess.getLongitude(adjNode);
        double latitude, longitude;

        PointList wayGeo = edge.fetchWayGeometry(FetchMode.ALL);
        boolean isRoundabout = edge.get(roundaboutEnc);

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
        String info = "";
        int importance = 1;
        if (getOffBikeEnc != null) {
            if (edge.get(roadClassEnc) == RoadClass.CYCLEWAY
                    || bikeRouteEnc != null && edge.get(bikeRouteEnc) != RouteNetwork.OTHER) {
                // for backward compatibility
                importance = 0;
                info = tr.tr("cycleway");
            } else if (edge.get(getOffBikeEnc)) {
                info = tr.tr("off_bike");
            }
        }

        RoadEnvironment re = edge.get(roadEnvEnc);
        if (re == RoadEnvironment.FORD) {
            importance = 2; // could be dangerous
            info = tr.tr("way_contains_ford");
        } else if (re == RoadEnvironment.FERRY) {
            info = tr.tr("way_contains_ferry");
        }

        // if private access is allowed we need a warning
        RoadAccess ra = edge.get(roadAccessEnc);
        if (ra == RoadAccess.PRIVATE)
            info = info.isEmpty() ? tr.tr("way_contains_private") : info + ", " + tr.tr("way_contains_private");

        if (tollEnc != null && edge.get(tollEnc) != Toll.NO)
            info = info.isEmpty() ? tr.tr("way_contains_toll") : info + ", " + tr.tr("way_contains_toll");

        InstructionAnnotation annotation = info.isEmpty()
                ? InstructionAnnotation.EMPTY
                : new InstructionAnnotation(importance, info);

        if ((prevName == null) && (!isRoundabout)) // very first instruction (if not in Roundabout)
        {
            int sign = Instruction.CONTINUE_ON_STREET;
            prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
            double startLat = nodeAccess.getLat(baseNode);
            double startLon = nodeAccess.getLon(baseNode);
            double heading = AngleCalc.ANGLE_CALC.calcAzimuth(startLat, startLon, latitude, longitude);
            prevInstruction.setExtraInfo("heading", Helper.round(heading, 2));
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
                prevInstructionPrevOrientation = prevOrientation;
                if (prevName != null) {
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
                    prevAnnotation = annotation;
                }
                prevInstruction = roundaboutInstruction;
                ways.add(prevInstruction);
            }

            // Add passed exits to instruction. A node is counted if there is at least one outgoing edge
            // out of the roundabout
            EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(edge.getAdjNode());
            while (edgeIter.next()) {
                if (!roundaboutEnc.getBool(false, edgeIter.getFlags())) {
                    ((RoundaboutInstruction) prevInstruction).increaseExitNumber();
                    break;
                }
            }

        } else if (prevInRoundabout) //previously in roundabout but not anymore
        {

            prevInstruction.setName(name);

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
            prevAnnotation = annotation;

        } else {
            int sign = getTurn(edge, baseNode, prevNode, adjNode, annotation, name);

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
                        && edge.get(accessEnc) != edge.getReverse(accessEnc)
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
                    prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
                    // Remember the Orientation and name of the road, before doing this maneuver
                    prevInstructionPrevOrientation = prevOrientation;
                    prevInstructionName = prevName;
                    ways.add(prevInstruction);
                    prevAnnotation = annotation;
                }
            }
            // Update the prevName, since we don't always create an instruction on name changes the previous
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

    private int getTurn(EdgeIteratorState edge, int baseNode, int prevNode, int adjNode, InstructionAnnotation annotation, String name) {
        GHPoint point = InstructionsHelper.getPointForOrientationCalculation(edge, nodeAccess);
        double lat = point.getLat();
        double lon = point.getLon();
        prevOrientation = AngleCalc.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
        int sign = InstructionsHelper.calculateSign(prevLat, prevLon, lat, lon, prevOrientation);

        boolean forceInstruction = false;

        if (!annotation.equals(prevAnnotation) && !annotation.isEmpty()) {
            forceInstruction = true;
        }

        InstructionsOutgoingEdges outgoingEdges = new InstructionsOutgoingEdges(prevEdge, edge, encoder, maxSpeedEnc, roadClassEnc, roadClassLinkEnc, crossingExplorer, nodeAccess, prevNode, baseNode, adjNode);
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
            return returnForcedInstructionOrIgnore(forceInstruction, sign);
        }

        // Very certain, this is a turn
        if (Math.abs(sign) > 1) {
            /*
             * Don't show an instruction if the user is following a street, even though the street is
             * bending. We should only do this, if following the street is the obvious choice.
             */
            if (InstructionsHelper.isNameSimilar(name, prevName) && outgoingEdges.outgoingEdgesAreSlowerByFactor(2)) {
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
                        return returnForcedInstructionOrIgnore(forceInstruction, sign);
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

        if (!outgoingEdgesAreSlower) {
            if (Math.abs(delta) > .6
                    || outgoingEdges.isLeavingCurrentStreet(prevName, name)) {
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
        // todo: why do we not account for turn times here ?
        prevInstruction.setTime(weighting.calcEdgeMillis(edge, false) + prevInstruction.getTime());
    }

}