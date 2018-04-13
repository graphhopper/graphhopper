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

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;

import java.util.PriorityQueue;

/**
 * Generic implementation of bidirectional Dijkstra algorithm that can be used with different shortest path entry types.
 *
 * @author Peter Karich
 * @author ammagamma
 */
public abstract class GenericDijkstraBidirection<T extends SPTEntry> extends AbstractBidirAlgo {
    protected IntObjectMap<T> bestWeightMapFrom;
    protected IntObjectMap<T> bestWeightMapTo;
    protected IntObjectMap<T> bestWeightMapOther;
    protected T currFrom;
    protected T currTo;
    protected PathBidirRef bestPath;
    PriorityQueue<T> pqOpenSetFrom;
    PriorityQueue<T> pqOpenSetTo;
    private boolean updateBestPath = true;

    public GenericDijkstraBidirection(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 150_000);
        initCollections(size);
    }

    protected void initCollections(int size) {
        pqOpenSetFrom = new PriorityQueue<>(size);
        bestWeightMapFrom = new GHIntObjectHashMap<>(size);

        pqOpenSetTo = new PriorityQueue<>(size);
        bestWeightMapTo = new GHIntObjectHashMap<>(size);
    }

    @Override
    protected void initFrom(int from, double weight) {
        currFrom = createStartEntry(from, weight, false);
        pqOpenSetFrom.add(currFrom);
        if (!traversalMode.isEdgeBased()) {
            bestWeightMapFrom.put(from, currFrom);
        }
    }


    @Override
    protected void initTo(int to, double weight) {
        currTo = createStartEntry(to, weight, true);
        pqOpenSetTo.add(currTo);
        if (!traversalMode.isEdgeBased()) {
            bestWeightMapTo.put(to, currTo);
        }
    }

    @Override
    protected void postInit(int from, int to) {
        if (!traversalMode.isEdgeBased()) {
            if (updateBestPath) {
                bestWeightMapOther = bestWeightMapFrom;
                updateBestPath(GHUtility.getEdge(graph, currFrom.adjNode, to), currFrom, to, true);
            }
        } else if (from == to) {
            // special case of identical start and end
            bestPath.sptEntry = currFrom;
            bestPath.edgeTo = currTo;
            finishedFrom = true;
            finishedTo = true;
        }
    }

    @Override
    protected Path createAndInitPath() {
        bestPath = new PathBidirRef(graph, weighting);
        return bestPath;
    }

    @Override
    protected Path extractPath() {
        if (finished())
            return bestPath.extract();

        return bestPath;
    }

    @Override
    protected double getCurrentFromWeight() {
        return currFrom.weight;
    }

    @Override
    protected double getCurrentToWeight() {
        return currTo.weight;
    }

    @Override
    boolean fillEdgesFrom() {
        if (pqOpenSetFrom.isEmpty()) {
            return false;
        }
        currFrom = pqOpenSetFrom.poll();
        visitedCountFrom++;
        if (fromEntryCanBeSkipped()) {
            return true;
        }
        if (fwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        return true;
    }

    @Override
    boolean fillEdgesTo() {
        if (pqOpenSetTo.isEmpty()) {
            return false;
        }
        currTo = pqOpenSetTo.poll();
        visitedCountTo++;
        if (toEntryCanBeSkipped()) {
            return true;
        }
        if (bwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, pqOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        return true;
    }

    protected boolean fromEntryCanBeSkipped() {
        return false;
    }

    protected boolean fwdSearchCanBeStopped() {
        return false;
    }

    protected boolean toEntryCanBeSkipped() {
        return false;
    }

    protected boolean bwdSearchCanBeStopped() {
        return false;
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the best path!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ
    @Override
    protected boolean finished() {
        if (finishedFrom || finishedTo)
            return true;

        return currFrom.weight + currTo.weight >= bestPath.getWeight();
    }

    private void fillEdges(T currEdge, PriorityQueue<T> prioQueue,
                           IntObjectMap<T> bestWeightMap, EdgeExplorer explorer, boolean reverse) {
        EdgeIterator iter = explorer.setBaseNode(currEdge.adjNode);
        while (iter.next()) {
            if (!accept(iter, currEdge, reverse))
                continue;

            final int origEdgeId = getOrigEdgeId(iter, reverse);
            final int traversalId = getTraversalId(iter, origEdgeId, reverse);
            if (!acceptTraversalId(traversalId, reverse)) {
                continue;
            }
            final double weight = calcWeight(iter, currEdge, reverse);
            if (Double.isInfinite(weight))
                continue;
            T entry = bestWeightMap.get(traversalId);
            if (entry == null) {
                entry = createEntry(iter, origEdgeId, weight, currEdge, reverse);
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
            } else if (entry.getWeightOfVisitedPath() > weight) {
                prioQueue.remove(entry);
                updateEntry(entry, iter, origEdgeId, weight, currEdge, reverse);
                prioQueue.add(entry);
            } else
                continue;

            if (updateBestPath)
                updateBestPath(iter, entry, traversalId, reverse);
        }
    }

    protected void updateBestPath(EdgeIteratorState edgeState, T entryCurrent, int traversalId, boolean reverse) {
        SPTEntry entryOther = bestWeightMapOther.get(traversalId);
        if (entryOther == null)
            return;

        // update μ
        double weight = entryCurrent.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath();
        if (traversalMode.isEdgeBased()) {
            if (entryOther.edge != entryCurrent.edge)
                throw new IllegalStateException("cannot happen for edge based execution of " + getName());

            if (entryOther.adjNode != entryCurrent.adjNode) {
                // prevents the path to contain the edge at the meeting point twice and subtract the weight (excluding turn weight => no previous edge)
                entryCurrent = getParent(entryCurrent);
                weight -= weighting.calcWeight(edgeState, reverse, EdgeIterator.NO_EDGE);
            } else if (!traversalMode.hasUTurnSupport())
                // we detected a u-turn at meeting point, skip if not supported
                return;
        }

        if (weight < bestPath.getWeight()) {
            bestPath.setSwitchToFrom(reverse);
            bestPath.setSPTEntry(entryCurrent);
            bestPath.setSPTEntryTo(entryOther);
            bestPath.setWeight(weight);
        }
    }

    protected abstract T createStartEntry(int node, double weight, boolean reverse);

    protected abstract T createEntry(EdgeIteratorState edge, int edgeId, double weight, T parent, boolean reverse);

    protected void updateEntry(T entry, EdgeIteratorState edge, int edgeId, double weight, T parent, boolean reverse) {
        entry.edge = edge.getEdge();
        entry.weight = weight;
        entry.parent = parent;
    }

    protected abstract T getParent(T entry);

    protected boolean accept(EdgeIteratorState edge, T currEdge, boolean reverse) {
        return accept(edge, currEdge.edge);
    }

    protected int getOrigEdgeId(EdgeIteratorState edge, boolean reverse) {
        return edge.getEdge();
    }

    protected int getTraversalId(EdgeIteratorState edge, int origEdgeId, boolean reverse) {
        return traversalMode.createTraversalId(edge, reverse);
    }

    protected boolean acceptTraversalId(int traversalId, boolean revers) {
        return true;
    }

    protected double calcWeight(EdgeIteratorState iter, T currEdge, boolean reverse) {
        return weighting.calcWeight(iter, reverse, currEdge.edge) + currEdge.getWeightOfVisitedPath();
    }

    IntObjectMap<T> getBestFromMap() {
        return bestWeightMapFrom;
    }

    IntObjectMap<T> getBestToMap() {
        return bestWeightMapTo;
    }

    void setBestOtherMap(IntObjectMap<T> other) {
        bestWeightMapOther = other;
    }

    protected void setUpdateBestPath(boolean b) {
        updateBestPath = b;
    }

    void setBestPath(PathBidirRef bestPath) {
        this.bestPath = bestPath;
    }

    void setFromDataStructures(GenericDijkstraBidirection<T> other) {
        pqOpenSetFrom = other.pqOpenSetFrom;
        bestWeightMapFrom = other.bestWeightMapFrom;
        finishedFrom = other.finishedFrom;
        currFrom = other.currFrom;
        visitedCountFrom = other.visitedCountFrom;
        // outEdgeExplorer
    }

    void setToDataStructures(GenericDijkstraBidirection<T> other) {
        pqOpenSetTo = other.pqOpenSetTo;
        bestWeightMapTo = other.bestWeightMapTo;
        finishedTo = other.finishedTo;
        currTo = other.currTo;
        visitedCountTo = other.visitedCountTo;
        // inEdgeExplorer
    }

}
