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
import com.graphhopper.util.*;

import java.util.PriorityQueue;

/**
 * Calculates best path in bidirectional way.
 * <p>
 * 'Ref' stands for reference implementation and is using the normal Java-'reference'-way.
 * <p>
 *
 * @author Peter Karich
 */
public class DijkstraBidirectionRef extends GenericDijkstraBidirection<SPTEntry> {
    public DijkstraBidirectionRef(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, tMode);
    }

    @Override
    public void initFrom(int from, double weight) {
        currFrom = createStartEntry(from, weight, false);
        pqOpenSetFrom.add(currFrom);
        if (!traversalMode.isEdgeBased()) {
            bestWeightMapFrom.put(from, currFrom);
            if (currTo != null) {
                bestWeightMapOther = bestWeightMapTo;
                updateBestPath(GHUtility.getEdge(graph, from, currTo.adjNode), currTo, from, false);
            }
        } else if (currTo != null && currTo.adjNode == from) {
            // special case of identical start and end
            bestPath.sptEntry = currFrom;
            bestPath.edgeTo = currTo;
            finishedFrom = true;
            finishedTo = true;
        }
    }

    @Override
    public void initTo(int to, double weight) {
        currTo = createStartEntry(to, weight, true);
        pqOpenSetTo.add(currTo);
        if (!traversalMode.isEdgeBased()) {
            bestWeightMapTo.put(to, currTo);
            if (currFrom != null) {
                bestWeightMapOther = bestWeightMapFrom;
                updateBestPath(GHUtility.getEdge(graph, currFrom.adjNode, to), currFrom, to, true);
            }
        } else if (currFrom != null && currFrom.adjNode == to) {
            // special case of identical start and end
            bestPath.sptEntry = currFrom;
            bestPath.edgeTo = currTo;
            finishedFrom = true;
            finishedTo = true;
        }
    }

    @Override
    public boolean fillEdgesFrom() {
        if (pqOpenSetFrom.isEmpty())
            return false;

        currFrom = pqOpenSetFrom.poll();
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        visitedCountFrom++;
        return true;
    }

    @Override
    public boolean fillEdgesTo() {
        if (pqOpenSetTo.isEmpty())
            return false;
        currTo = pqOpenSetTo.poll();
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, pqOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        visitedCountTo++;
        return true;
    }

    void fillEdges(SPTEntry currEdge, PriorityQueue<SPTEntry> prioQueue,
                   IntObjectMap<SPTEntry> bestWeightMap, EdgeExplorer explorer, boolean reverse) {
        EdgeIterator iter = explorer.setBaseNode(currEdge.adjNode);
        while (iter.next()) {
            if (!accept(iter, currEdge.edge))
                continue;

            int traversalId = traversalMode.createTraversalId(iter, reverse);
            if (!acceptTraversalId(traversalId, reverse)) {
                continue;
            }
            double weight = calcWeight(iter, currEdge, reverse);
            if (Double.isInfinite(weight))
                continue;
            SPTEntry entry = bestWeightMap.get(traversalId);
            if (entry == null) {
                entry = new SPTEntry(iter.getEdge(), iter.getAdjNode(), weight);
                entry.parent = currEdge;
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
            } else if (entry.weight > weight) {
                prioQueue.remove(entry);
                entry.edge = iter.getEdge();
                entry.weight = weight;
                entry.parent = currEdge;
                prioQueue.add(entry);
            } else
                continue;

            if (updateBestPath)
                updateBestPath(iter, entry, traversalId, reverse);
        }
    }

    @Override
    protected void updateBestPath(EdgeIteratorState edgeState, SPTEntry entryCurrent, int traversalId, boolean reverse) {
        SPTEntry entryOther = bestWeightMapOther.get(traversalId);
        if (entryOther == null)
            return;

        // update μ
        double newWeight = entryCurrent.weight + entryOther.weight;
        if (traversalMode.isEdgeBased()) {
            if (entryOther.edge != entryCurrent.edge)
                throw new IllegalStateException("cannot happen for edge based execution of " + getName());

            if (entryOther.adjNode != entryCurrent.adjNode) {
                // prevents the path to contain the edge at the meeting point twice and subtract the weight (excluding turn weight => no previous edge)
                entryCurrent = entryCurrent.parent;
                newWeight -= weighting.calcWeight(edgeState, reverse, EdgeIterator.NO_EDGE);
            } else if (!traversalMode.hasUTurnSupport())
                // we detected a u-turn at meeting point, skip if not supported
                return;
        }

        if (newWeight < bestPath.getWeight()) {
            bestPath.setSwitchToFrom(reverse);
            bestPath.setSPTEntry(entryCurrent);
            bestPath.setWeight(newWeight);
            bestPath.setSPTEntryTo(entryOther);
        }
    }

    @Override
    protected SPTEntry createStartEntry(int node, double weight, boolean reverse) {
        return new SPTEntry(node, weight);
    }


    void setFromDataStructures(DijkstraBidirectionRef dijkstra) {
        pqOpenSetFrom = dijkstra.pqOpenSetFrom;
        bestWeightMapFrom = dijkstra.bestWeightMapFrom;
        finishedFrom = dijkstra.finishedFrom;
        currFrom = dijkstra.currFrom;
        visitedCountFrom = dijkstra.visitedCountFrom;
        // outEdgeExplorer
    }

    void setToDataStructures(DijkstraBidirectionRef dijkstra) {
        pqOpenSetTo = dijkstra.pqOpenSetTo;
        bestWeightMapTo = dijkstra.bestWeightMapTo;
        finishedTo = dijkstra.finishedTo;
        currTo = dijkstra.currTo;
        visitedCountTo = dijkstra.visitedCountTo;
        // inEdgeExplorer
    }

    protected void setUpdateBestPath(boolean b) {
        updateBestPath = b;
    }

    void setBestPath(PathBidirRef bestPath) {
        this.bestPath = bestPath;
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.DIJKSTRA_BI;
    }
}
