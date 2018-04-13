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
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

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
    protected boolean updateBestPath = true;

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

    protected abstract T createEntry(EdgeIteratorState edge, double weight, T parent, boolean reverse);

    protected void updateEntry(T entry, EdgeIteratorState edge, double weight, T parent, boolean reverse) {
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

    void setBestOtherMap(IntObjectMap<T> other) {
        bestWeightMapOther = other;
    }

    IntObjectMap<T> getBestFromMap() {
        return bestWeightMapFrom;
    }

    IntObjectMap<T> getBestToMap() {
        return bestWeightMapTo;
    }

}
