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

    public List<Path> calcPaths(int from, Set<Integer> to) {
        Set<SPTEntry> targetLabels = new HashSet<>();
        SPTEntry currEdge = new SPTEntry(EdgeIterator.NO_EDGE, from, 0.0, 0);
        fromMap.put(from, currEdge);
        if (to.contains(from)) {
            targetLabels.add(currEdge);
        }
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
                SPTEntry nEdge = new SPTEntry(iter.getEdge(), iter.getAdjNode(), tmpWeight, tmpNTransfers);
                nEdge.parent = currEdge;
                if (improves(nEdge, sptEntries) && isNotDominatedBy(nEdge, targetLabels)) {
                    removeDominated(nEdge, sptEntries);
                    if (to.contains(iter.getAdjNode())) {
                        removeDominated(nEdge, targetLabels);
                    }
                    fromMap.put(iter.getAdjNode(), nEdge);
                    if (to.contains(iter.getAdjNode())) {
                        targetLabels.add(nEdge);
                    }
                    fromHeap.add(nEdge);
                }
            }

            if (fromHeap.isEmpty())
                break;

            currEdge = fromHeap.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");
        }
        List<Path> result = new ArrayList<>();
        for (SPTEntry solution : targetLabels) {
            result.add(new Path(graph, weighting)
                    .setWeight(solution.weight)
                    .setSPTEntry(solution)
                    .extract());
        }
        return result;
    }

    private boolean improves(SPTEntry me, Set<SPTEntry> sptEntries) {
        for (SPTEntry they : sptEntries) {
            if (they.nTransfers <= me.nTransfers && they.weight <= me.weight) {
                return false;
            }
        }
        return true;
    }

    private void removeDominated(SPTEntry me, Set<SPTEntry> sptEntries) {
        for (Iterator<SPTEntry> iterator = sptEntries.iterator(); iterator.hasNext();) {
            SPTEntry sptEntry = iterator.next();
            if (dominates(me, sptEntry)) {
                fromHeap.remove(sptEntry);
                iterator.remove();
            }
        }
    }

    @Override
    public List<Path> calcPaths(int from, int to, int earliestDepartureTime) {
        throw new UnsupportedOperationException();
    }

    private boolean isNotDominatedBy(SPTEntry me, Set<SPTEntry> sptEntries) {
        for (SPTEntry they : sptEntries) {
            if (dominates(they, me)) {
                return false;
            }
        }
        return true;
    }

    private boolean dominates(SPTEntry me, SPTEntry they) {
        if (me.weight > they.weight) {
            return false;
        }
        if (me.nTransfers > they.nTransfers) {
            return false;
        }
        if (me.weight < they.weight) {
            return true;
        }
        if (me.nTransfers < they.nTransfers) {
            return true;
        }
        return false;
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
