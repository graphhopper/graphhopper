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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.coll.GHIntArrayList;
import com.graphhopper.debatty.java.stringsimilarity.JaroWinkler;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final Logger logger = LoggerFactory.getLogger(getClass());

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
    private List<String> description;
    protected Weighting weighting;
    private FlagEncoder encoder;
    private boolean found;
    private int fromNode = -1;
    private GHIntArrayList edgeIds;
    private double weight;
    private NodeAccess nodeAccess;

    public Path(Graph graph, Weighting weighting) {
        this.weight = Double.MAX_VALUE;
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
        this.weighting = weighting;
        this.encoder = weighting.getFlagEncoder();
        this.edgeIds = new GHIntArrayList();
    }

    /**
     * Populates an unextracted path instances from the specified path p.
     */
    Path(Path p) {
        this(p.graph, p.weighting);
        weight = p.weight;
        edgeIds = new GHIntArrayList(p.edgeIds);
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
        SPTEntry currEdge = sptEntry;
        setEndNode(currEdge.adjNode);
        boolean nextEdgeValid = EdgeIterator.Edge.isValid(currEdge.edge);
        int nextEdge;
        while (nextEdgeValid) {
            // the reverse search needs the next edge
            nextEdgeValid = EdgeIterator.Edge.isValid(currEdge.parent.edge);
            nextEdge = nextEdgeValid ? currEdge.parent.edge : EdgeIterator.NO_EDGE;
            processEdge(currEdge.edge, currEdge.adjNode, nextEdge);
            currEdge = currEdge.parent;
        }

        setFromNode(currEdge.adjNode);
        reverseOrder();
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
     * Calculates the distance and time of the specified edgeId. Also it adds the edgeId to the path list.
     *
     * @param prevEdgeId here the edge that comes before edgeId is necessary. I.e. for the reverse search we need the
     *                   next edge.
     */
    protected void processEdge(int edgeId, int adjNode, int prevEdgeId) {
        EdgeIteratorState iter = graph.getEdgeIteratorState(edgeId, adjNode);
        distance += iter.getDistance();
        time += weighting.calcMillis(iter, false, prevEdgeId);
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
        for (int i = 0; i < len; i++) {
            EdgeIteratorState edgeBase = graph.getEdgeIteratorState(edgeIds.get(i), tmpNode);
            if (edgeBase == null)
                throw new IllegalStateException("Edge " + edgeIds.get(i) + " was empty when requested with node " + tmpNode
                        + ", array index:" + i + ", edges:" + edgeIds.size());

            tmpNode = edgeBase.getBaseNode();
            // more efficient swap, currently not implemented for virtual edges: visitor.next(edgeBase.detach(true), i);
            edgeBase = graph.getEdgeIteratorState(edgeBase.getEdge(), tmpNode);
            visitor.next(edgeBase, i);
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
            public void next(EdgeIteratorState eb, int i) {
                edges.add(eb);
            }
        });
        return edges;
    }

    /**
     * @return the uncached node indices of the tower nodes in this path.
     */
    public IntIndexedContainer calcNodes() {
        final IntArrayList nodes = new IntArrayList(edgeIds.size() + 1);
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
            public void next(EdgeIteratorState eb, int i) {
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
            public void next(EdgeIteratorState eb, int index) {
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
            private double doublePrevLat, doublePrevLon; // Lat and Lon of node t-2
            private int prevNode = -1;
            private double prevOrientation;
            private Instruction prevInstruction;
            private boolean prevInRoundabout = false;
            private String name, prevName = null;
            private InstructionAnnotation annotation, prevAnnotation;
            private EdgeExplorer outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, false, true));
            private EdgeExplorer crossingExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, true));

            @Override
            public void next(EdgeIteratorState edge, int index) {
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
                            prevOrientation = AC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);

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
                    double recentOrientation = AC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
                    orientation = AC.alignOrientation(recentOrientation, orientation);
                    double deltaOut = (orientation - recentOrientation);

                    prevInstruction = ((RoundaboutInstruction) prevInstruction)
                            .setRadian(deltaInOut)
                            .setDirOfRotation(deltaOut)
                            .setExited();

                    prevName = name;
                    prevAnnotation = annotation;

                } else {
                    int sign = getTurn(edge, baseNode, prevNode, adjNode);

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

                boolean lastEdge = index == edgeIds.size() - 1;
                if (lastEdge) {
                    if (isRoundabout) {
                        // calc angle between roundabout entrance and finish
                        double orientation = AC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
                        orientation = AC.alignOrientation(prevOrientation, orientation);
                        double delta = (orientation - prevOrientation);
                        ((RoundaboutInstruction) prevInstruction).setRadian(delta);

                    }
                    ways.add(new FinishInstruction(nodeAccess, adjNode));
                }
            }

            private int getTurn(EdgeIteratorState edge, int baseNode, int prevNode, int adjNode) {
                GHPoint point = getPointForOrientationCalculation(edge);
                double lat = point.getLat();
                double lon = point.getLon();
                int sign = calculateSign(lat, lon);

                boolean forceInstruction = false;

                if (!annotation.equals(prevAnnotation) && !annotation.isEmpty()) {
                    forceInstruction = true;
                }

                int nrOfPossibleTurns = nrOfPossibleTurns(baseNode, prevNode, adjNode);

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
                    if (isNameSimilar(name, prevName) && surroundingStreetsAreSlowerByFactor(baseNode, prevNode, adjNode, 2)) {
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

                int prevEdge = -1;
                EdgeIterator flagIter = crossingExplorer.setBaseNode(baseNode);
                while (flagIter.next()) {
                    if (flagIter.getAdjNode() == prevNode || flagIter.getBaseNode() == prevNode)
                        prevEdge = flagIter.getEdge();

                }
                if (prevEdge == -1) {
                    throw new IllegalStateException("Couldn't find the edges for " + prevNode + "-" + baseNode + "-" + adjNode);
                }

                long flag = edge.getFlags();
                long prevFlag = graph.getEdgeIteratorState(prevEdge, baseNode).getFlags();

                boolean surroundingStreetsAreSlower = surroundingStreetsAreSlowerByFactor(baseNode, prevNode, adjNode, 1);

                // There is at least one other possibility to turn, and we are almost going straight
                // Check the other turns if one of them is also going almost straight
                // If not, we don't need a turn instruction
                EdgeIteratorState otherContinue = getOtherContinue(baseNode, prevNode, adjNode);

                // Signs provide too less detail, so we use the delta for a precise comparision
                double delta = calculateOrientationDelta(lat, lon);

                // This state is bad! Two streets are going more or less straight
                // Happens a lot for trunk_links
                // For _links, comparing flags works quite good, as links usually have different speeds => different flags
                if (otherContinue != null) {
                    //We are at a fork
                    if (!isNameSimilar(name, prevName)
                            || isNameSimilar(otherContinue.getName(), prevName)
                            || prevFlag != flag
                            || prevFlag == otherContinue.getFlags()
                            || !surroundingStreetsAreSlower) {
                        GHPoint tmpPoint = getPointForOrientationCalculation(otherContinue);
                        double otherDelta = calculateOrientationDelta(tmpPoint.getLat(), tmpPoint.getLon());

                        if (Math.abs(delta) < .1 && Math.abs(otherDelta) > .15 && isNameSimilar(name, prevName)) {
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
                            || isLeavingCurrentStreet(flag, prevFlag, baseNode, prevNode, adjNode)) {
                        // Leave the current road -> create instruction
                        return sign;

                    }
                }

                return returnForcedInstructionOrIgnore(forceInstruction, sign);
            }

            private GHPoint getPointForOrientationCalculation(EdgeIteratorState edgeIteratorState) {
                double tmpLat;
                double tmpLon;
                PointList tmpWayGeo = edgeIteratorState.fetchWayGeometry(3);
                if (tmpWayGeo.getSize() <= 2) {
                    tmpLat = nodeAccess.getLatitude(edgeIteratorState.getAdjNode());
                    tmpLon = nodeAccess.getLongitude(edgeIteratorState.getAdjNode());
                } else {
                    tmpLat = tmpWayGeo.getLatitude(1);
                    tmpLon = tmpWayGeo.getLongitude(1);
                }
                return new GHPoint(tmpLat, tmpLon);
            }

            private int returnForcedInstructionOrIgnore(boolean forceInstruction, int sign) {
                if (forceInstruction)
                    return sign;
                return Instruction.IGNORE;
            }

            /**
             * If the name and prevName changes this method checks if either the current street is continued on a
             * different edge or if the edge we are turning onto is continued on a different edge.
             * If either of these properties is true, we can be quite certain that a turn instruction should be provided.
             */
            private boolean isLeavingCurrentStreet(long flag, long prevFlag, int baseNode, int prevNode, int adjNode) {
                if (isNameSimilar(name, prevName)) {
                    return false;
                }

                // If flags are changing, there might be a chance we find these flags on a different edge
                boolean checkFlag = flag != prevFlag;

                EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
                while (edgeIter.next()) {
                    if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                        String edgeName = edgeIter.getName();
                        long edgeFlag = edgeIter.getFlags();
                        // leave the current street || enter a different street
                        if (isTheSameStreet(prevName, prevFlag, edgeName, edgeFlag, checkFlag)
                                || isTheSameStreet(name, flag, edgeName, edgeFlag, checkFlag)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private boolean isTheSameStreet(String name1, long flags1, String name2, long flags2, boolean checkFlag) {
                if (isNameSimilar(name1, name2)) {
                    if (!checkFlag || flags1 == flags2) {
                        return true;
                    }
                }
                return false;
            }

            /**
             * Checks the other crossings for a street that is going more or less straight
             */
            private EdgeIteratorState getOtherContinue(int baseNode, int prevNode, int adjNode) {
                EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
                int tmpSign;
                while (edgeIter.next()) {
                    if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                        GHPoint point = getPointForOrientationCalculation(edgeIter);
                        tmpSign = calculateSign(point.getLat(), point.getLon());
                        if (Math.abs(tmpSign) <= 1) {
                            return edgeIter;
                        }
                    }
                }
                return null;
            }

            /**
             * Checks if the surrounding streets are slower. If they are, this indicates, that we are staying
             * on the prominent street that one would follow anyway.
             */
            private boolean surroundingStreetsAreSlowerByFactor(int baseNode, int prevNode, int adjNode, double factor) {
                EdgeIterator edgeIter = crossingExplorer.setBaseNode(baseNode);
                double pathSpeed = -1;
                double maxSurroundingSpeed = -1;
                double tmpSpeed;
                while (edgeIter.next()) {
                    if (encoder instanceof DataFlagEncoder) {
                        tmpSpeed = ((DataFlagEncoder) encoder).getMaxspeed(edgeIter, 0, false);
                        // If one of the edges has no SpeedLimit set, we are not sure
                        // TODO we should calculate the true speed limits here
                        if (tmpSpeed < 1)
                            return false;
                    } else {
                        tmpSpeed = encoder.getSpeed(edgeIter.getFlags());
                    }

                    if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                        if (tmpSpeed > maxSurroundingSpeed)
                            maxSurroundingSpeed = tmpSpeed;
                    } else {
                        if (pathSpeed < 0) {
                            pathSpeed = tmpSpeed;
                        } else {
                            // Speed-Change on the path indicates, that we change road types, show instruction
                            if (tmpSpeed != pathSpeed) {
                                return false;
                            }
                        }
                    }
                }

                // Surrounding streets need to be slower by a factor
                return maxSurroundingSpeed * factor < pathSpeed;
            }

            /**
             * This method calculates the number of possible turns.
             * The edge we are comming from and the edge we are going to is ignored.
             */
            private int nrOfPossibleTurns(int baseNode, int prevNode, int adjNode) {
                EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
                int count = 1;
                while (edgeIter.next()) {
                    if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode)
                        count++;
                }
                return count;
            }

            private boolean isNameSimilar(String name1, String name2) {
                // We don't want two empty names to be similar
                // The idea is, if there are only a random tracks, they usually don't have names
                if (name1.isEmpty() && name2.isEmpty()) {
                    return false;
                }
                if (name1.equals(name2)) {
                    return true;
                }
                return false;
            }


            private double calculateOrientationDelta(double latitude, double longitude) {
                prevOrientation = AC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
                double orientation = AC.calcOrientation(prevLat, prevLon, latitude, longitude);
                orientation = AC.alignOrientation(prevOrientation, orientation);
                return orientation - prevOrientation;
            }

            private int calculateSign(double latitude, double longitude) {
                double delta = calculateOrientationDelta(latitude, longitude);
                double absDelta = Math.abs(delta);

                // TODO not only calculate the mathematical orientation, but also compare to other streets
                // TODO If there is one street turning slight right and one right, but no straight street
                // TODO We can assume the slight right street would be a continue
                if (absDelta < 0.2) {
                    // 0.2 ~= 11°
                    return Instruction.CONTINUE_ON_STREET;

                } else if (absDelta < 0.8) {
                    // 0.8 ~= 40°
                    if (delta > 0)
                        return Instruction.TURN_SLIGHT_LEFT;
                    else
                        return Instruction.TURN_SLIGHT_RIGHT;

                } else if (absDelta < 1.8) {
                    // 1.8 ~= 103°
                    if (delta > 0)
                        return Instruction.TURN_LEFT;
                    else
                        return Instruction.TURN_RIGHT;

                } else if (delta > 0)
                    return Instruction.TURN_SHARP_LEFT;
                else
                    return Instruction.TURN_SHARP_RIGHT;
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
        void next(EdgeIteratorState edgeBase, int index);
    }
}
