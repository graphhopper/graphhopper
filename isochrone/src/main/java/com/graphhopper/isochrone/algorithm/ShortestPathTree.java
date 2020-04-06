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
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.PathExtractor;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

import java.util.PriorityQueue;
import java.util.function.Consumer;

import static com.graphhopper.isochrone.algorithm.ShortestPathTree.ExploreType.DISTANCE;
import static com.graphhopper.isochrone.algorithm.ShortestPathTree.ExploreType.TIME;

/**
 * @author Peter Karich
 */
public class ShortestPathTree extends AbstractRoutingAlgorithm {

    enum ExploreType {TIME, DISTANCE}

    public static class IsoLabel extends SPTEntry {

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
    private double limit = -1;
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
        this.limit = limit;
    }

    /**
     * Distance limit in meter
     */
    public void setDistanceLimit(double limit) {
        exploreType = DISTANCE;
        this.limit = limit;
    }

    public void search(int from, final Consumer<IsoLabel> consumer) {
        checkAlreadyRun();
        currEdge = new IsoLabel(-1, from, 0, 0, 0);
        fromMap.put(from, currEdge);
        EdgeFilter filter = reverseFlow ? inEdgeFilter : outEdgeFilter;
        while (true) {
            consumer.accept(currEdge);
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
        return getExploreValue(currEdge) >= limit;
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
