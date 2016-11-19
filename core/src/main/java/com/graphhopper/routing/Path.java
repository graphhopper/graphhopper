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
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.*;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores the nodes for the found path of an algorithm. It additionally needs the edgeIds to make
 * edge determination faster and less complex as there could be several edges (u,v) especially for
 * graphs with shortcuts.
 * <p>
 *
 * @author Peter Karich
 * @author Ottavio Campana
 * @author jan soe
 */
public class Path {
    private static final AngleCalc AC = Helper.ANGLE_CALC;
    final StopWatch extractSW = new StopWatch("extract");
    protected Graph graph;
    protected double distance;
    // we go upwards (via SPTEntry.parent) from the goal node to the origin node
    protected boolean reverseOrder = true;
    protected long time;
    /**
     * Shortest path tree entry
     */
    protected SPTEntry sptEntry;
    protected int endNode = -1;
    protected final Weighting weighting;
    private List<String> description;
    private FlagEncoder encoder;
    private boolean found;
    private int fromNode = -1;
    private TIntList edgeIds;
    private double weight;
    private NodeAccess nodeAccess;    

    public Path(Graph graph, Weighting weighting) {
        this.weight = Double.MAX_VALUE;
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        this.weighting = weighting;
        this.encoder = weighting.getFlagEncoder();
        this.edgeIds = new TIntArrayList();
    }

    /**
     * Populates an unextracted path instances from the specified path p.
     */
    Path(Path p) {
        this(p.graph, p.weighting);
        weight = p.weight;
        edgeIds = new TIntArrayList(p.edgeIds);
        sptEntry = p.sptEntry;
    }

    /**
     * @return the description of this route alternative to make it meaningful for the user e.g. it
     * displays one or two main roads of the route.
     */
    public List<String> getDescription() {
        if (description == null)
            return Collections.emptyList();
        return description;
    }

    public Path setDescription(List<String> description) {
        this.description = description;
        return this;
    }

    public Path setSPTEntry(SPTEntry sptEntry) {
        this.sptEntry = sptEntry;
        return this;
    }

    protected void addEdge(int edge) {
        edgeIds.add(edge);
    }

    protected Path setEndNode(int end) {
        endNode = end;
        return this;
    }

    /**
     * @return the first node of this Path.
     */
    private int getFromNode() {
        if (fromNode < 0)
            throw new IllegalStateException("Call extract() before retrieving fromNode");

        return fromNode;
    }

    /**
     * We need to remember fromNode explicitly as its not saved in one edgeId of edgeIds.
     */
    protected Path setFromNode(int from) {
        fromNode = from;
        return this;
    }

    public int getEdgeCount() {
        return edgeIds.size();
    }

    public boolean isFound() {
        return found;
    }

    public Path setFound(boolean found) {
        this.found = found;
        return this;
    }

    void reverseOrder() {
        if (!reverseOrder)
            throw new IllegalStateException("Switching order multiple times is not supported");

        reverseOrder = false;
        edgeIds.reverse();
    }

    public Path setDistance(double distance) {
        this.distance = distance;
        return this;
    }

    /**
     * @return distance in meter
     */
    public double getDistance() {
        return distance;
    }

    /**
     * @return time in millis
     */
    public long getTime() {
        return time;
    }

    /**
     * This weight will be updated during the algorithm. The initial value is maximum double.
     */
    public double getWeight() {
        return weight;
    }

    public Path setWeight(double w) {
        this.weight = w;
        return this;
    }

    /**
     * Extracts the Path from the shortest-path-tree determined by sptEntry.
     */
    public Path extract() {
        if (isFound())
            throw new IllegalStateException("Extract can only be called once");

        extractSW.start();
        SPTEntry goalEdge = sptEntry;
        int prevEdge = EdgeIterator.NO_EDGE;
        setEndNode(goalEdge.adjNode);
        while (EdgeIterator.Edge.isValid(goalEdge.edge)) {
            processEdge(goalEdge.edge, goalEdge.adjNode, prevEdge);
            prevEdge = goalEdge.edge;
            goalEdge = goalEdge.parent;
        }

        setFromNode(goalEdge.adjNode);
        reverseOrder();

        if (weighting instanceof TimeDependentWeighting) {
            // We have to calculate times again because the previously (backwardly) calculated travel times are wrong.
            // See processEdge.
            time = (long) goalEdge.weight * 1000;
            distance = 0;
            forEveryEdge(new EdgeVisitor() {
                @Override
                public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
                    double dist = edge.getDistance();
                    distance += dist;
                    if (weighting != null && weighting instanceof TimeDependentWeighting) {
                        // TODO This should already be in the SPT, we shouldn't need to calculate it again here.
                        time += ((TimeDependentWeighting) weighting).calcTravelTimeSeconds(edge, time / 1000.0) * 1000.0;
                    } else {
                        time += weighting.calcMillis(edge, false, prevEdgeId);
                    }
                }
            });
        }
        extractSW.stop();
        return setFound(true);
    }

    /**
     * Yields the final edge of the path
     */
    public EdgeIteratorState getFinalEdge() {
        return graph.getEdgeIteratorState(edgeIds.get(edgeIds.size() - 1), endNode);
    }

    /**
     * @return the time it took to extract the path in nano (!) seconds
     */
    public long getExtractTime() {
        return extractSW.getNanos();
    }

    public String getDebugInfo() {
        return extractSW.toString();
    }

    /**
     * Calls getDistance and adds the edgeId.
     */
    protected void processEdge(int edgeId, int adjNode, int prevEdgeId) {
        if (weighting instanceof TimeDependentWeighting) {
            // Don't do anything here since we have to calculate travel times in the forward pass (see extract).
        } else {
            EdgeIteratorState iter = graph.getEdgeIteratorState(edgeId, adjNode);
            double dist = iter.getDistance();
            distance += dist;
            time += weighting.calcMillis(iter, false, prevEdgeId);
        }
        addEdge(edgeId);
    }

    /**
     * Iterates over all edges in this path sorted from start to end and calls the visitor callback
     * for every edge.
     * <p>
     *
     * @param visitor callback to handle every edge. The edge is decoupled from the iterator and can
     *                be stored.
     */
    private void forEveryEdge(EdgeVisitor visitor) {
        int tmpNode = getFromNode();
        int len = edgeIds.size();
        int prevEdgeId = EdgeIterator.NO_EDGE;
        for (int i = 0; i < len; i++) {
            EdgeIteratorState edgeBase = graph.getEdgeIteratorState(edgeIds.get(i), tmpNode);
            if (edgeBase == null)
                throw new IllegalStateException("Edge " + edgeIds.get(i) + " was empty when requested with node " + tmpNode
                        + ", array index:" + i + ", edges:" + edgeIds.size());

            tmpNode = edgeBase.getBaseNode();
            // more efficient swap, currently not implemented for virtual edges: visitor.next(edgeBase.detach(true), i);
            edgeBase = graph.getEdgeIteratorState(edgeBase.getEdge(), tmpNode);
            visitor.next(edgeBase, i, prevEdgeId);
            
            prevEdgeId = edgeBase.getEdge();
        }
    }

    /**
     * Returns the list of all edges.
     */
    public List<EdgeIteratorState> calcEdges() {
        final List<EdgeIteratorState> edges = new ArrayList<EdgeIteratorState>(edgeIds.size());
        if (edgeIds.isEmpty())
            return edges;

        forEveryEdge(new EdgeVisitor() {
            @Override
            public void next(EdgeIteratorState eb, int index, int prevEdgeId) {
                edges.add(eb);
            }
        });
        return edges;
    }

    /**
     * @return the uncached node indices of the tower nodes in this path.
     */
    public TIntList calcNodes() {
        final TIntArrayList nodes = new TIntArrayList(edgeIds.size() + 1);
        if (edgeIds.isEmpty()) {
            if (isFound()) {
                nodes.add(endNode);
            }
            return nodes;
        }

        int tmpNode = getFromNode();
        nodes.add(tmpNode);
        forEveryEdge(new EdgeVisitor() {
            @Override
            public void next(EdgeIteratorState eb, int index, int prevEdgeId) {
                nodes.add(eb.getAdjNode());
            }
        });
        return nodes;
    }

    /**
     * This method calculated a list of points for this path
     * <p>
     *
     * @return this path its geometry
     */
    public PointList calcPoints() {
        final PointList points = new PointList(edgeIds.size() + 1, nodeAccess.is3D());
        if (edgeIds.isEmpty()) {
            if (isFound()) {
                points.add(graph.getNodeAccess(), endNode);
            }
            return points;
        }

        int tmpNode = getFromNode();
        points.add(nodeAccess, tmpNode);
        forEveryEdge(new EdgeVisitor() {
            @Override
            public void next(EdgeIteratorState eb, int index, int prevEdgeId) {
                PointList pl = eb.fetchWayGeometry(2);
                for (int j = 0; j < pl.getSize(); j++) {
                    points.add(pl, j);
                }
            }
        });
        return points;
    }

    /**
     * @return the list of instructions for this path.
     */
    public InstructionList calcInstructions(final Translation tr) {
        final InstructionList ways = new InstructionList(edgeIds.size() / 4, tr);
        if (edgeIds.isEmpty()) {
            if (isFound()) {
                ways.add(new FinishInstruction(nodeAccess, endNode));
            }
            return ways;
        }

        final int tmpNode = getFromNode();
        forEveryEdge(new EdgeVisitor() {
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
            private double prevLat = nodeAccess.getLatitude(tmpNode);
            private double prevLon = nodeAccess.getLongitude(tmpNode);
            private double doublePrevLat, doublePrevLong; // Lat and Lon of node t-2
            private int prevNode = -1;
            private double prevOrientation;
            private Instruction prevInstruction;
            private boolean prevInRoundabout = false;
            private String name, prevName = null;
            private InstructionAnnotation annotation, prevAnnotation;
            private EdgeExplorer outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
            private long time = 0;

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
                    assert java.lang.Double.compare(prevLat, nodeAccess.getLatitude(baseNode)) == 0;
                    assert java.lang.Double.compare(prevLon, nodeAccess.getLongitude(baseNode)) == 0;
                }

                name = edge.getName();
                annotation = encoder.getAnnotation(flags, tr);

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
                            prevOrientation = AC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);

                            // calculate direction of entrance turn to determine direction of rotation
                            // right turn == counterclockwise and vice versa
                            double orientation = AC.calcOrientation(prevLat, prevLon, latitude, longitude);
                            orientation = AC.alignOrientation(prevOrientation, orientation);
                            double delta = (orientation - prevOrientation);
                            roundaboutInstruction.setDirOfRotation(delta);

                        } else // first instructions is roundabout instruction
                        {
                            prevOrientation = AC.calcOrientation(prevLat, prevLon, latitude, longitude);
                            prevName = name;
                            prevAnnotation = annotation;
                        }
                        prevInstruction = roundaboutInstruction;
                        ways.add(prevInstruction);
                    }

                    // Add passed exits to instruction. A node is counted if there is at least one outgoing edge
                    // out of the roundabout
                    EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(adjNode);
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
                    double orientation = AC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    orientation = AC.alignOrientation(prevOrientation, orientation);
                    double deltaInOut = (orientation - prevOrientation);

                    // calculate direction of exit turn to determine direction of rotation
                    // right turn == counterclockwise and vice versa
                    double recentOrientation = AC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);
                    orientation = AC.alignOrientation(recentOrientation, orientation);
                    double deltaOut = (orientation - recentOrientation);

                    prevInstruction = ((RoundaboutInstruction) prevInstruction)
                            .setRadian(deltaInOut)
                            .setDirOfRotation(deltaOut)
                            .setExited();

                    prevName = name;
                    prevAnnotation = annotation;

                } else if ((!name.equals(prevName)) || (!annotation.equals(prevAnnotation))) {
                    prevOrientation = AC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);
                    double orientation = AC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    orientation = AC.alignOrientation(prevOrientation, orientation);
                    double delta = orientation - prevOrientation;
                    double absDelta = Math.abs(delta);
                    int sign;

                    if (absDelta < 0.2) {
                        // 0.2 ~= 11°
                        sign = Instruction.CONTINUE_ON_STREET;

                    } else if (absDelta < 0.8) {
                        // 0.8 ~= 40°
                        if (delta > 0)
                            sign = Instruction.TURN_SLIGHT_LEFT;
                        else
                            sign = Instruction.TURN_SLIGHT_RIGHT;

                    } else if (absDelta < 1.8) {
                        // 1.8 ~= 103°
                        if (delta > 0)
                            sign = Instruction.TURN_LEFT;
                        else
                            sign = Instruction.TURN_RIGHT;

                    } else if (delta > 0)
                        sign = Instruction.TURN_SHARP_LEFT;
                    else
                        sign = Instruction.TURN_SHARP_RIGHT;
                    prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
                    ways.add(prevInstruction);
                    prevName = name;
                    prevAnnotation = annotation;
                }

                updatePointsAndInstruction(edge, wayGeo, prevEdgeId);

                if (wayGeo.getSize() <= 2) {
                    doublePrevLat = prevLat;
                    doublePrevLong = prevLon;
                } else {
                    int beforeLast = wayGeo.getSize() - 2;
                    doublePrevLat = wayGeo.getLatitude(beforeLast);
                    doublePrevLong = wayGeo.getLongitude(beforeLast);
                }

                prevInRoundabout = isRoundabout;
                prevNode = baseNode;
                prevLat = adjLat;
                prevLon = adjLon;

                boolean lastEdge = index == edgeIds.size() - 1;
                if (lastEdge) {
                    if (isRoundabout) {
                        // calc angle between roundabout entrance and finish
                        double orientation = AC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);
                        orientation = AC.alignOrientation(prevOrientation, orientation);
                        double delta = (orientation - prevOrientation);
                        ((RoundaboutInstruction) prevInstruction).setRadian(delta);

                    }
                    ways.add(new FinishInstruction(nodeAccess, adjNode));
                }
            }

            private void updatePointsAndInstruction(EdgeIteratorState edge, PointList pl, int prevEdgeId) {
                // skip adjNode
                int len = pl.size() - 1;
                for (int i = 0; i < len; i++) {
                    prevInstruction.getPoints().add(pl, i);
                }
                double newDist = edge.getDistance();
                prevInstruction.setDistance(newDist + prevInstruction.getDistance());
                if (weighting != null && weighting instanceof TimeDependentWeighting) {
                    // TODO This should already be in the SPT, we shouldn't need to calculate it again here.
                    double edgeTime = ((TimeDependentWeighting) weighting).calcTravelTimeSeconds(edge, time / 1000.0) * 1000.0;
                    time += edgeTime;
                    prevInstruction.setTime((long) edgeTime + prevInstruction.getTime());
                } else {
                    long edgeTime = weighting.calcMillis(edge, false, prevEdgeId);
                    prevInstruction.setTime(edgeTime + prevInstruction.getTime());
                    time += edgeTime;
                }
            }
        });

        return ways;
    }

    @Override
    public String toString() {
        return "distance:" + getDistance() + ", edges:" + edgeIds.size();
    }

    public String toDetailsString() {
        String str = "";
        for (int i = 0; i < edgeIds.size(); i++) {
            if (i > 0)
                str += "->";

            str += edgeIds.get(i);
        }
        return toString() + ", found:" + isFound() + ", " + str;
    }

    /**
     * The callback used in forEveryEdge.
     */
    private static interface EdgeVisitor {
        void next(EdgeIteratorState edge, int index, int prevEdgeId);
    }
}
