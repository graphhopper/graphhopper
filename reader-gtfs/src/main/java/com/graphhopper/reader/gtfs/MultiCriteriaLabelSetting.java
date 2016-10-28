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
package com.graphhopper.reader.gtfs;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.TimeDependentRoutingAlgorithm;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

/**
 * Implements a Multi-Criteria Label Setting (MLS) path finding algorithm
 * with the criteria earliest arrival time and number of transfers.
 * <p>
 *
 * @author Michael Zilske
 * @author Peter Karich
 */
class MultiCriteriaLabelSetting implements TimeDependentRoutingAlgorithm {
    private final Graph graph;
    private final FlagEncoder flagEncoder;
    private final Weighting weighting;
    private final SetMultimap<Integer, SPTEntry> fromMap;
    private final PriorityQueue<SPTEntry> fromHeap;
    private final int maxVisitedNodes;
    private int visitedNodes;

    MultiCriteriaLabelSetting(Graph graph, Weighting weighting, int maxVisitedNodes) {
        this.graph = graph;
        this.weighting = weighting;
        this.flagEncoder = weighting.getFlagEncoder();
        this.maxVisitedNodes = maxVisitedNodes;
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        fromHeap = new PriorityQueue<>(size);
        fromMap = HashMultimap.create();
    }

    @Override
    public Path calcPath(int from, int to, int earliestDepartureTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Path> calcPaths(int from, int to, int earliestDepartureTime) {
        SPTEntry currEdge = new SPTEntry(EdgeIterator.NO_EDGE, from, earliestDepartureTime);
        fromMap.put(from, currEdge);
        EdgeExplorer explorer = graph.createEdgeExplorer(new DefaultEdgeFilter(flagEncoder, false, true));
        while (true) {
            visitedNodes++;
            if (maxVisitedNodes < getVisitedNodes())
                break;

            int startNode = currEdge.adjNode;
            EdgeIterator iter = explorer.setBaseNode(startNode);
            while (iter.next()) {
                if (iter.getEdge() == currEdge.edge)
                    continue;

                double tmpWeight;
                int tmpNTransfers = currEdge.nTransfers;
                if (weighting instanceof TimeDependentWeighting) {
                    tmpWeight = ((TimeDependentWeighting) weighting).calcWeight(iter, false, currEdge.edge, currEdge.weight) + currEdge.weight;
                    tmpNTransfers += ((TimeDependentWeighting) weighting).calcNTransfers(iter);
                } else {
                    tmpWeight = weighting.calcWeight(iter, false, currEdge.edge) + currEdge.weight;
                }
                if (Double.isInfinite(tmpWeight))
                    continue;

                Set<SPTEntry> sptEntries = fromMap.get(iter.getAdjNode());
                if (isNotDominatedBy(tmpWeight, tmpNTransfers, sptEntries) && isNotDominatedBy(tmpWeight, tmpNTransfers, fromMap.get(to))) {
                    SPTEntry nEdge;
                    nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight, tmpNTransfers);
                    nEdge.parent = currEdge;
                    fromMap.put(iter.getAdjNode(), nEdge);
                    fromHeap.add(nEdge);
                    Set<SPTEntry> iDominate = new HashSet<>();
                    for (SPTEntry sptEntry : sptEntries) {
                        if (tmpWeight < sptEntry.weight && tmpNTransfers < sptEntry.nTransfers) {
                            iDominate.add(sptEntry);
                        }
                    }
                    for (SPTEntry sptEntry : iDominate) {
                        fromHeap.remove(sptEntry);
                        fromMap.remove(iter.getAdjNode(), sptEntry);
                    }
                }
            }

            if (fromHeap.isEmpty())
                break;

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }
        List<Path> result = new ArrayList<>();
        for (SPTEntry solution : fromMap.get(to)) {
            result.add(new Path(graph, weighting)
                    .setWeight(solution.weight)
                    .setSPTEntry(solution)
                    .setEarliestDepartureTime(earliestDepartureTime)
                    .extract());
        }
        return result;
    }

    private boolean isNotDominatedBy(double tmpWeight, int tmpNTransfers, Set<SPTEntry> sptEntries) {
        Set<SPTEntry> dominatesMe = new HashSet<>();
        for (SPTEntry sptEntry : sptEntries) {
            if (tmpWeight >= sptEntry.weight && tmpNTransfers >= sptEntry.nTransfers) {
                dominatesMe.add(sptEntry);
            }
        }
        return dominatesMe.isEmpty();
    }

    @Override
    public Path calcPath(int from, int to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxVisitedNodes(int numberOfNodes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    @Override
    public String getName() {
        return "Ulrich";
    }
}
