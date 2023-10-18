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
package com.graphhopper.util;

import com.graphhopper.ResponsePath;
import com.graphhopper.routing.InstructionsFromEdges;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.details.PathDetailsBuilderFactory;
import com.graphhopper.util.details.PathDetailsFromEdges;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class merges multiple {@link Path} objects into one continuous object that
 * can be used in the {@link ResponsePath}. There will be a Path between every waypoint.
 * So for two waypoints there will be only one Path object. For three waypoints there will be
 * two Path objects.
 * <p>
 * The instructions are generated per Path object and are merged into one continuous InstructionList.
 * The PointList per Path object are merged and optionally simplified.
 *
 * @author Peter Karich
 * @author ratrun
 * @author Robin Boldt
 */
public class PathMerger {
    private static final RamerDouglasPeucker RDP = new RamerDouglasPeucker();
    private final Graph graph;
    private final Weighting weighting;

    private boolean enableInstructions = true;
    private boolean simplifyResponse = true;
    private RamerDouglasPeucker ramerDouglasPeucker = RDP;
    private boolean calcPoints = true;
    private PathDetailsBuilderFactory pathBuilderFactory;
    private List<String> requestedPathDetails = Collections.emptyList();
    private double favoredHeading = Double.NaN;

    public PathMerger(Graph graph, Weighting weighting) {
        this.graph = graph;
        this.weighting = graph.wrapWeighting(weighting);
    }

    public PathMerger setCalcPoints(boolean calcPoints) {
        this.calcPoints = calcPoints;
        return this;
    }

    public PathMerger setRamerDouglasPeucker(RamerDouglasPeucker ramerDouglasPeucker) {
        this.ramerDouglasPeucker = ramerDouglasPeucker;
        return this;
    }

    public PathMerger setPathDetailsBuilders(PathDetailsBuilderFactory pathBuilderFactory, List<String> requestedPathDetails) {
        this.pathBuilderFactory = pathBuilderFactory;
        this.requestedPathDetails = requestedPathDetails;
        return this;
    }

    public PathMerger setSimplifyResponse(boolean simplifyRes) {
        this.simplifyResponse = simplifyRes;
        return this;
    }

    public PathMerger setEnableInstructions(boolean enableInstructions) {
        this.enableInstructions = enableInstructions;
        return this;
    }

    public ResponsePath doWork(PointList waypoints, List<Path> paths, EncodedValueLookup evLookup, Translation tr) {
        ResponsePath responsePath = new ResponsePath();
        int origPoints = 0;
        long fullTimeInMillis = 0;
        double fullWeight = 0;
        double fullDistance = 0;
        boolean allFound = true;

        InstructionList fullInstructions = new InstructionList(tr);
        PointList fullPoints = PointList.EMPTY;
        List<String> description = new ArrayList<>();
        List<Integer> wayPointIndices = new ArrayList<>();
        for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
            Path path = paths.get(pathIndex);
            if (!path.isFound()) {
                allFound = false;
                continue;
            }
            description.addAll(path.getDescription());
            fullTimeInMillis += path.getTime();
            fullDistance += path.getDistance();
            fullWeight += path.getWeight();
            if (enableInstructions) {
                InstructionList il = InstructionsFromEdges.calcInstructions(path, graph, weighting, evLookup, tr);

                if (!il.isEmpty()) {
                    fullInstructions.addAll(il);

                    // for all paths except the last replace the FinishInstruction with a ViaInstruction
                    if (pathIndex + 1 < paths.size()) {
                        ViaInstruction newInstr = new ViaInstruction(fullInstructions.get(fullInstructions.size() - 1));
                        newInstr.setViaCount(pathIndex + 1);
                        fullInstructions.set(fullInstructions.size() - 1, newInstr);
                    }
                }

            }
            if (calcPoints || enableInstructions) {
                PointList tmpPoints = path.calcPoints();
                if (fullPoints.isEmpty())
                    fullPoints = new PointList(tmpPoints.size(), tmpPoints.is3D());

                // Remove duplicated points, see #1138
                if (pathIndex + 1 < paths.size()) {
                    tmpPoints.removeLastPoint();
                }

                fullPoints.add(tmpPoints);
                responsePath.addPathDetails(PathDetailsFromEdges.calcDetails(path, evLookup, weighting, requestedPathDetails, pathBuilderFactory, origPoints, graph));
                wayPointIndices.add(origPoints);
                if (pathIndex == paths.size() - 1)
                    wayPointIndices.add(fullPoints.size() - 1);
                origPoints = fullPoints.size();
            }

            allFound = allFound && path.isFound();
        }

        if (!fullPoints.isEmpty() && fullPoints.is3D)
            calcAscendDescend(responsePath, fullPoints);

        if (enableInstructions) {
            fullInstructions = updateInstructionsWithContext(fullInstructions);
            responsePath.setInstructions(fullInstructions);
        }

        if (!allFound) {
            responsePath.addError(new ConnectionNotFoundException("Connection between locations not found", Collections.emptyMap()));
        }

        // make sure the way point indices actually point to the points in waypoints...
        if (allFound && !waypoints.isEmpty()) { // we use empty waypoints for map-matching...
            for (int i = 0; i < wayPointIndices.size(); i++) {
                int index = wayPointIndices.get(i);
                if (waypoints.getLat(i) != fullPoints.getLat(index) || waypoints.getLon(i) != fullPoints.getLon(index))
                    throw new IllegalStateException("waypoints are not included in points, or waypoint indices are wrong");
            }
        }

        responsePath.setDescription(description).
                setPoints(fullPoints).
                setRouteWeight(fullWeight).
                setDistance(fullDistance).
                setTime(fullTimeInMillis).
                setWaypoints(waypoints).
                setWaypointIndices(wayPointIndices);

        if (allFound && simplifyResponse && (calcPoints || enableInstructions)) {
            PathSimplification.simplify(responsePath, ramerDouglasPeucker, enableInstructions);
        }
        return responsePath;
    }

    /**
     * This method iterates over all instructions and uses the available context to improve the instructions.
     * If the requests contains a heading, this method can transform the first continue to a u-turn if the heading
     * points into the opposite direction of the route.
     * At a waypoint it can transform the continue to a u-turn if the route involves turning.
     */
    private InstructionList updateInstructionsWithContext(InstructionList instructions) {
        Instruction instruction;
        Instruction nextInstruction;

        for (int i = 0; i < instructions.size() - 1; i++) {
            instruction = instructions.get(i);

            if (i == 0 && !Double.isNaN(favoredHeading) && instruction.extraInfo.containsKey("heading")) {
                double heading = (double) instruction.extraInfo.get("heading");
                double diff = Math.abs(heading - favoredHeading) % 360;
                if (diff > 170 && diff < 190) {
                    // The requested heading points into the opposite direction of the calculated heading
                    // therefore we change the continue instruction to a u-turn
                    instruction.setSign(Instruction.U_TURN_UNKNOWN);
                }
            }

            if (instruction.getSign() == Instruction.REACHED_VIA) {
                nextInstruction = instructions.get(i + 1);
                if (nextInstruction.getSign() != Instruction.CONTINUE_ON_STREET
                        || !instruction.extraInfo.containsKey("last_heading")
                        || !nextInstruction.extraInfo.containsKey("heading")) {
                    // TODO throw exception?
                    continue;
                }
                double lastHeading = (double) instruction.extraInfo.get("last_heading");
                double heading = (double) nextInstruction.extraInfo.get("heading");

                // Since it's supposed to go back the same edge, we can be very strict with the diff
                double diff = Math.abs(lastHeading - heading) % 360;
                if (diff > 179 && diff < 181) {
                    nextInstruction.setSign(Instruction.U_TURN_UNKNOWN);
                }
            }
        }

        return instructions;
    }

    private void calcAscendDescend(final ResponsePath responsePath, final PointList pointList) {
        double ascendMeters = 0;
        double descendMeters = 0;
        double lastEle = pointList.getEle(0);
        for (int i = 1; i < pointList.size(); ++i) {
            double ele = pointList.getEle(i);
            double diff = Math.abs(ele - lastEle);

            if (ele > lastEle)
                ascendMeters += diff;
            else
                descendMeters += diff;

            lastEle = ele;

        }
        responsePath.setAscend(ascendMeters);
        responsePath.setDescend(descendMeters);
    }

    public void setFavoredHeading(double favoredHeading) {
        this.favoredHeading = favoredHeading;
    }
}