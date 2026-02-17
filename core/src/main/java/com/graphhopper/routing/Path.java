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
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class represents the result of a shortest path calculation. It also provides methods to extract further
 * information about the found path, like instructions etc.
 *
 * @author Peter Karich
 * @author Ottavio Campana
 * @author jan soe
 * @author easbar
 */
public class Path {
    final Graph graph;
    private final NodeAccess nodeAccess;
    private double weight = Double.MAX_VALUE;
    private double distanceMM;
    private long time;
    private IntArrayList edgeIds = new IntArrayList();
    private int fromNode = -1;
    private int endNode = -1;
    private List<String> description;
    private boolean found;
    private String debugInfo = "";

    public Path(Graph graph) {
        this.graph = graph;
        this.nodeAccess = graph.getNodeAccess();
    }

    public Graph getGraph() {
        return graph;
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

    public IntArrayList getEdges() {
        return edgeIds;
    }

    public void setEdges(IntArrayList edgeIds) {
        this.edgeIds = edgeIds;
    }

    public void addEdge(int edge) {
        edgeIds.add(edge);
    }

    public int getEdgeCount() {
        return edgeIds.size();
    }

    public int getEndNode() {
        return endNode;
    }

    public Path setEndNode(int end) {
        endNode = end;
        return this;
    }

    public int getFromNode() {
        if (fromNode < 0)
            throw new IllegalStateException("fromNode < 0 should not happen");

        return fromNode;
    }

    /**
     * We need to remember fromNode explicitly as its not saved in one edgeId of edgeIds.
     */
    public Path setFromNode(int from) {
        fromNode = from;
        return this;
    }

    public boolean isFound() {
        return found;
    }

    public Path setFound(boolean found) {
        this.found = found;
        return this;
    }

    public Path addDistanceMM(double distanceMM) {
        this.distanceMM += distanceMM;
        return this;
    }

    /**
     * @return distance in meter
     */
    public double getDistance() {
        return distanceMM / 1000.0;
    }

    public double getDistanceMM() {
        return distanceMM;
    }

    /**
     * @return time in millis
     */
    public long getTime() {
        return time;
    }

    public Path setTime(long time) {
        this.time = time;
        return this;
    }

    public Path addTime(long time) {
        this.time += time;
        return this;
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
     * Yields the final edge of the path
     */
    public EdgeIteratorState getFinalEdge() {
        return graph.getEdgeIteratorState(edgeIds.get(edgeIds.size() - 1), endNode);
    }

    public void setDebugInfo(String debugInfo) {
        this.debugInfo = debugInfo;
    }

    public String getDebugInfo() {
        return debugInfo;
    }

    /**
     * Iterates over all edges in this path sorted from start to end and calls the visitor callback
     * for every edge.
     * <p>
     *
     * @param visitor callback to handle every edge. The edge is decoupled from the iterator and can
     *                be stored.
     */
    public void forEveryEdge(EdgeVisitor visitor) {
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
        visitor.finish();
    }

    /**
     * Returns the list of all edges.
     */
    public List<EdgeIteratorState> calcEdges() {
        final List<EdgeIteratorState> edges = new ArrayList<>(edgeIds.size());
        if (edgeIds.isEmpty())
            return edges;

        forEveryEdge(new EdgeVisitor() {
            @Override
            public void next(EdgeIteratorState eb, int index, int prevEdgeId) {
                edges.add(eb);
            }

            @Override
            public void finish() {

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
            public void next(EdgeIteratorState eb, int index, int prevEdgeId) {
                nodes.add(eb.getAdjNode());
            }

            @Override
            public void finish() {

            }
        });
        return nodes;
    }

    /**
     * This method calculated a list of points for this path
     * <p>
     *
     * @return the geometry of this path
     */
    public PointList calcPoints() {
        final PointList points = new PointList(edgeIds.size() + 1, nodeAccess.is3D());
        if (edgeIds.isEmpty()) {
            if (isFound()) {
                points.add(nodeAccess, endNode);
            }
            return points;
        }

        int tmpNode = getFromNode();
        points.add(nodeAccess, tmpNode);
        forEveryEdge(new EdgeVisitor() {
            @Override
            public void next(EdgeIteratorState eb, int index, int prevEdgeId) {
                PointList pl = eb.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ);
                for (int j = 0; j < pl.size(); j++) {
                    points.add(pl, j);
                }
            }

            @Override
            public void finish() {

            }
        });
        return points;
    }

    @Override
    public String toString() {
        return "found: " + found + ", weight: " + weight + ", time: " + time + ", distance: " + distanceMM + ", edges: " + edgeIds.size();
    }

    /**
     * The callback used in forEveryEdge.
     */
    public interface EdgeVisitor {
        void next(EdgeIteratorState edge, int index, int prevEdgeId);

        void finish();
    }
}
