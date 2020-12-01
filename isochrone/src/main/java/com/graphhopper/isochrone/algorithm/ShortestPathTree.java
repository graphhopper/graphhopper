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
import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import static com.graphhopper.isochrone.algorithm.ShortestPathTree.ExploreType.*;
import static java.util.Comparator.comparingDouble;

/**
 * Computes a shortest path tree by a given weighting. Terminates when all shortest paths up to
 * a given travel time or distance have been explored. The catch is that the function for termination
 * is different from the function for search. This implementation uses a second queue to keep track of
 * the termination criterion.
 *
 * IMPLEMENTATION NOTE:
 * util.PriorityQueue doesn't support efficient removes. We work around this by giving the labels
 * a deleted flag, not remove()ing them, and popping deleted elements off both queues.
 * Note to self/others: If you think this optimization is not needed, please test it with a scenario
 * where updates actually occur a lot, such as using finite, non-zero u-turn costs.
 *
 * @author Peter Karich
 * @author Michael Zilske
 */
public class ShortestPathTree extends AbstractRoutingAlgorithm {

    enum ExploreType {TIME, DISTANCE, WEIGHT}

    public static class IsoLabel {

        IsoLabel(int node, int edge, double weight, long time, double distance, IsoLabel parent) {
            this.node = node;
            this.edge = edge;
            this.weight = weight;
            this.time = time;
            this.distance = distance;
            this.parent = parent;
        }

        public boolean deleted = false;
        public int node;
        public int edge;
        public double weight;
        public long time;
        public double distance;
        public IsoLabel parent;

        @Override
        public String toString() {
            return "IsoLabel{" +
                    "node=" + node +
                    ", edge=" + edge +
                    ", weight=" + weight +
                    ", time=" + time +
                    ", distance=" + distance +
                    '}';
        }
    }

    private IntObjectHashMap<IsoLabel> fromMap;
    private PriorityQueue<IsoLabel> queueByWeighting;
    private int visitedNodes;
    private double limit = -1;
    private ExploreType exploreType = TIME;
    private final boolean reverseFlow;

    public ShortestPathTree(Graph g, Weighting weighting, boolean reverseFlow, TraversalMode traversalMode) {
        super(g, weighting, traversalMode);
        queueByWeighting = new PriorityQueue<>(1000, comparingDouble(l -> l.weight));
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

    public void setWeightLimit(double limit) {
        exploreType = WEIGHT;
        this.limit = limit;
    }

    public void search(int from, final Consumer<IsoLabel> consumer) {
        checkAlreadyRun();
        IsoLabel currentLabel = new IsoLabel(from, -1, 0, 0, 0, null);
        queueByWeighting.add(currentLabel);
        if (traversalMode == TraversalMode.NODE_BASED) {
            fromMap.put(from, currentLabel);
        }
        EdgeFilter filter = reverseFlow ? inEdgeFilter : outEdgeFilter;
        while (!finished()) {
            currentLabel = queueByWeighting.poll();
            if (currentLabel.deleted)
                continue;
            consumer.accept(currentLabel);
            currentLabel.deleted = true;
            visitedNodes++;

            EdgeIterator iter = edgeExplorer.setBaseNode(currentLabel.node);
            while (iter.next()) {
                if (!accept(iter, currentLabel.edge)) {
                    continue;
                }

                // todo: for #1835 move the access check into weighting
                double nextWeight = !filter.accept(iter)
                        ? Double.POSITIVE_INFINITY
                        : (GHUtility.calcWeightWithTurnWeight(weighting, iter, reverseFlow, currentLabel.edge) + currentLabel.weight);
                if (Double.isInfinite(nextWeight))
                    continue;

                double nextDistance = iter.getDistance() + currentLabel.distance;
                long nextTime = GHUtility.calcMillisWithTurnMillis(weighting, iter, reverseFlow, currentLabel.edge) + currentLabel.time;
                int nextTraversalId = traversalMode.createTraversalId(iter, reverseFlow);
                IsoLabel label = fromMap.get(nextTraversalId);
                if (label == null) {
                    label = new IsoLabel(iter.getAdjNode(), iter.getEdge(), nextWeight, nextTime, nextDistance, currentLabel);
                    fromMap.put(nextTraversalId, label);
                    if (getExploreValue(label) <= limit) {
                        queueByWeighting.add(label);
                    }
                } else if (label.weight > nextWeight) {
                    label.deleted = true;
                    label = new IsoLabel(iter.getAdjNode(), iter.getEdge(), nextWeight, nextTime, nextDistance, currentLabel);
                    fromMap.put(nextTraversalId, label);
                    if (getExploreValue(label) <= limit) {
                        queueByWeighting.add(label);
                    }
                }
            }
        }
    }

    public Collection<IsoLabel> getIsochroneEdges() {
        // assert alreadyRun
        ArrayList<IsoLabel> result = new ArrayList<>();
        for (ObjectCursor<IsoLabel> cursor : fromMap.values()) {
            if (getExploreValue(cursor.value) > limit) {
                assert cursor.value.parent == null || getExploreValue(cursor.value.parent) <= limit;
                result.add(cursor.value);
            }
        }
        return result;
    }

    private double getExploreValue(IsoLabel label) {
        if (exploreType == TIME)
            return label.time;
        if (exploreType == WEIGHT)
            return label.weight;
        return label.distance;
    }

    @Override
    protected boolean finished() {
        return queueByWeighting.isEmpty();
    }

    @Override
    protected Path extractPath() {
        throw new UnsupportedOperationException();
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
