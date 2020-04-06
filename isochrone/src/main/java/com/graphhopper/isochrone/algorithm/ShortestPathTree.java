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
package com.graphhopper.isochrone.algorithm;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.procedures.IntObjectProcedure;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathExtractor;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;
import java.util.function.Consumer;

import static com.graphhopper.isochrone.algorithm.ShortestPathTree.ExploreType.DISTANCE;
import static com.graphhopper.isochrone.algorithm.ShortestPathTree.ExploreType.TIME;

/**
 * @author Peter Karich
 */
public class ShortestPathTree extends AbstractRoutingAlgorithm {

    enum ExploreType {TIME, DISTANCE}

    static class IsoLabel extends SPTEntry {

        IsoLabel(int edgeId, int adjNode, double weight, long time, double distance) {
            super(edgeId, adjNode, weight);
            this.time = time;
            this.distance = distance;
        }

        public long time;
        public double distance;

        @Override
        public String toString() {
            return super.toString() + ", time:" + time + ", distance:" + distance;
        }
    }

    private IntObjectHashMap<IsoLabel> fromMap;
    private PriorityQueue<IsoLabel> fromHeap;
    private IsoLabel currEdge;
    private int visitedNodes;
    private double finishLimit = -1;
    private ExploreType exploreType = TIME;
    private final boolean reverseFlow;

    public ShortestPathTree(Graph g, Weighting weighting, boolean reverseFlow) {
        super(g, weighting, TraversalMode.NODE_BASED);
        fromHeap = new PriorityQueue<>(1000);
        fromMap = new GHIntObjectHashMap<>(1000);
        this.reverseFlow = reverseFlow;
    }

    @Override
    public Path calcPath(int from, int to) {
        throw new IllegalStateException("call search instead");
    }

    /**
     * Time limit in milliseconds
     */
    public void setTimeLimit(double limit) {
        exploreType = TIME;
        // we explore until all spt-entries are '>timeLimitInSeconds'
        // and add some more into this bucket for car we need a bit more as 
        // we otherwise get artifacts for motorway endings
        this.finishLimit = limit + Math.max(limit * 0.14, 200_000);
    }

    /**
     * Distance limit in meter
     */
    public void setDistanceLimit(double limit) {
        exploreType = DISTANCE;
        this.finishLimit = limit + Math.max(limit * 0.14, 2_000);
    }

    public static class IsoLabelWithCoordinates {
        public final int nodeId;
        public int edgeId, prevEdgeId, prevNodeId;
        public int timeMillis, prevTimeMillis;
        public int distance, prevDistance;
        public GHPoint coordinate, prevCoordinate;

        public IsoLabelWithCoordinates(int nodeId) {
            this.nodeId = nodeId;
        }
    }

    public void search(int from, final Consumer<IsoLabelWithCoordinates> callback) {
        searchInternal(from);

        final NodeAccess na = graph.getNodeAccess();
        fromMap.forEach((IntObjectProcedure<IsoLabel>) (nodeId, label) -> {
            double lat = na.getLatitude(nodeId);
            double lon = na.getLongitude(nodeId);
            IsoLabelWithCoordinates isoLabelWC = new IsoLabelWithCoordinates(nodeId);
            isoLabelWC.coordinate = new GHPoint(lat, lon);
            isoLabelWC.timeMillis = Math.round(label.time);
            isoLabelWC.distance = (int) Math.round(label.distance);
            isoLabelWC.edgeId = label.edge;
            if (label.parent != null) {
                IsoLabel prevLabel = (IsoLabel) label.parent;
                nodeId = prevLabel.adjNode;
                double prevLat = na.getLatitude(nodeId);
                double prevLon = na.getLongitude(nodeId);
                isoLabelWC.prevNodeId = nodeId;
                isoLabelWC.prevEdgeId = prevLabel.edge;
                isoLabelWC.prevCoordinate = new GHPoint(prevLat, prevLon);
                isoLabelWC.prevDistance = (int) Math.round(prevLabel.distance);
                isoLabelWC.prevTimeMillis = Math.round(prevLabel.time);
            }
            callback.accept(isoLabelWC);
        });
    }

    private void searchInternal(int from) {
        checkAlreadyRun();
        currEdge = new IsoLabel(-1, from, 0, 0, 0);
        fromMap.put(from, currEdge);
        EdgeFilter filter = reverseFlow ? inEdgeFilter : outEdgeFilter;
        while (true) {
            visitedNodes++;
            if (finished()) {
                break;
            }

            int neighborNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(neighborNode);
            while (iter.next()) {
                if (!accept(iter, currEdge.edge)) {
                    continue;
                }

                // todo: for #1776/#1835 move the access check into weighting
                double tmpWeight = !filter.accept(iter)
                        ? Double.POSITIVE_INFINITY
                        : (GHUtility.calcWeightWithTurnWeight(weighting, iter, reverseFlow, currEdge.edge) + currEdge.weight);
                if (Double.isInfinite(tmpWeight))
                    continue;

                double tmpDistance = iter.getDistance() + currEdge.distance;
                long tmpTime = GHUtility.calcMillisWithTurnMillis(weighting, iter, reverseFlow, currEdge.edge) + currEdge.time;
                int tmpNode = iter.getAdjNode();
                IsoLabel nEdge = fromMap.get(tmpNode);
                if (nEdge == null) {
                    nEdge = new IsoLabel(iter.getEdge(), tmpNode, tmpWeight, tmpTime, tmpDistance);
                    nEdge.parent = currEdge;
                    fromMap.put(tmpNode, nEdge);
                    fromHeap.add(nEdge);
                } else if (nEdge.weight > tmpWeight) {
                    fromHeap.remove(nEdge);
                    nEdge.edge = iter.getEdge();
                    nEdge.weight = tmpWeight;
                    nEdge.distance = tmpDistance;
                    nEdge.time = tmpTime;
                    nEdge.parent = currEdge;
                    fromHeap.add(nEdge);
                }
            }

            if (fromHeap.isEmpty()) {
                break;
            }

            currEdge = fromHeap.poll();
            if (currEdge == null) {
                throw new AssertionError("Empty edge cannot happen");
            }
        }
    }

    private double getExploreValue(IsoLabel label) {
        if (exploreType == TIME)
            return label.time;
        // if(exploreType == DISTANCE)
        return label.distance;
    }

    @Override
    protected boolean finished() {
        return getExploreValue(currEdge) >= finishLimit;
    }

    @Override
    protected Path extractPath() {
        if (currEdge == null || !finished()) {
            return createEmptyPath();
        }
        return PathExtractor.extractPath(graph, weighting, currEdge);
    }

    @Override
    public String getName() {
        return "reachability";
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }
}
