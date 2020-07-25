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
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import static com.graphhopper.isochrone.algorithm.ShortestPathTree.ExploreType.*;
import static java.util.Comparator.comparingDouble;
import static java.util.Comparator.comparingLong;

/**
 * Computes a shortest path tree by a given weighting. Terminates when all shortest paths up to
 * a given travel time or distance have been explored. The catch is that the function for termination
 * is different from the function for search. This implementation uses a second queue to keep track of
 * the termination criterion.
 * <p>
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

        public int node;
        public int edge;
        public long time;
        public double distance;
        public double weight;
        public IsoLabel parent;
        public boolean deleted = false;

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
    private PriorityQueue<IsoLabel> queueByWeighting; // a.k.a. the Dijkstra queue
    private PriorityQueue<IsoLabel> queueByZ; // so we know when we are finished
    private int visitedNodes;
    private double limit = -1;
    private ExploreType exploreType = TIME;
    private final boolean reverseFlow;

    public ShortestPathTree(Graph g, Weighting weighting, boolean reverseFlow, TraversalMode traversalMode) {
        super(g, weighting, traversalMode);
        queueByWeighting = new PriorityQueue<>(1000, comparingDouble(l -> l.weight));
        queueByZ = new PriorityQueue<>(1000);
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
        this.queueByZ = new PriorityQueue<>(1000, comparingLong(l -> l.time));
    }

    /**
     * Distance limit in meter
     */
    public void setDistanceLimit(double limit) {
        exploreType = DISTANCE;
        this.limit = limit;
        this.queueByZ = new PriorityQueue<>(1000, comparingDouble(l -> l.distance));
    }

    public void setWeightLimit(double limit) {
        exploreType = WEIGHT;
        this.limit = limit;
        this.queueByZ = new PriorityQueue<>(1000, comparingDouble(l -> l.weight));
    }

    public void search(int from, final Consumer<IsoLabel> consumer) {
        checkAlreadyRun();
        IsoLabel currentLabel = new IsoLabel(from, -1, 0, 0, 0, null);
        queueByWeighting.add(currentLabel);
        queueByZ.add(currentLabel);
        if (traversalMode == TraversalMode.NODE_BASED) {
            fromMap.put(from, currentLabel);
        }
        EdgeFilter filter = reverseFlow ? inEdgeFilter : outEdgeFilter;
        while (!finished()) {
            currentLabel = queueByWeighting.poll();
            if (currentLabel.deleted)
                continue;
            if (getExploreValue(currentLabel) <= limit) {
                consumer.accept(currentLabel);
            }
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
                IsoLabel nextLabel = fromMap.get(nextTraversalId);
                if (nextLabel == null) {
                    nextLabel = new IsoLabel(iter.getAdjNode(), iter.getEdge(), nextWeight, nextTime, nextDistance, currentLabel);
                    fromMap.put(nextTraversalId, nextLabel);
                    queueByWeighting.add(nextLabel);
                    queueByZ.add(nextLabel);
                } else if (nextLabel.weight > nextWeight) {
                    nextLabel.deleted = true;
                    nextLabel = new IsoLabel(iter.getAdjNode(), iter.getEdge(), nextWeight, nextTime, nextDistance, currentLabel);
                    fromMap.put(nextTraversalId, nextLabel);
                    queueByWeighting.add(nextLabel);
                    queueByZ.add(nextLabel);
                }
            }
        }
    }

    public Collection<Coordinate> searchSites(int startNode) {
        final NodeAccess na = graph.getNodeAccess();
        Collection<Coordinate> sites = new ArrayList<>();
        search(startNode, label -> {
            double exploreValue;
            if (exploreType == WEIGHT) {
                exploreValue = label.weight;
            } else if (exploreType == DISTANCE) {
                exploreValue = label.distance;
            } else {
                exploreValue = label.time;
            }
            double lat = na.getLatitude(label.node);
            double lon = na.getLongitude(label.node);
            Coordinate site = new Coordinate(lon, lat);
            site.z = exploreValue;
            sites.add(site);

            // add a pillar node to increase precision a bit for longer roads
            if (label.parent != null) {
                EdgeIteratorState edge = graph.getEdgeIteratorState(label.edge, label.node);
                PointList innerPoints = edge.fetchWayGeometry(FetchMode.PILLAR_ONLY);
                if (innerPoints.getSize() > 0) {
                    int midIndex = innerPoints.getSize() / 2;
                    double lat2 = innerPoints.getLat(midIndex);
                    double lon2 = innerPoints.getLon(midIndex);
                    Coordinate site2 = new Coordinate(lon2, lat2);
                    site2.z = exploreValue;
                    sites.add(site2);
                }
            }
        });
        return sites;
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
        while (queueByZ.peek() != null && queueByZ.peek().deleted)
            queueByZ.poll();
        if (queueByZ.peek() == null)
            return true;
        return getExploreValue(queueByZ.peek()) >= limit;
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
