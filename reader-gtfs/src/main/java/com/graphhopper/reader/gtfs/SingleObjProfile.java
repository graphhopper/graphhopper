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
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

/**
 * Profile query Dijstra impl for lexico first problems. Among all earliest
 * arrivals it returns the one with least transfers for all profile results.
 */
class SingleObjProfile {

    private final PtFlagEncoder flagEncoder;
    private final Weighting weighting;
    private final SetMultimap<Integer, Label> fromMap;
    private final PriorityQueue<Label> fromHeap;
    private final int maxVisitedNodes;
    private final boolean reverse;
    private long rangeQueryEndTime;
    private int visitedNodes;
    private final GraphExplorer explorer;
    int reverseFactor = 1;

    SingleObjProfile(Graph graph, Weighting weighting, int maxVisitedNodes, GraphExplorer explorer, boolean reverse) {
        this.weighting = weighting;
        this.flagEncoder = (PtFlagEncoder) weighting.getFlagEncoder();
        this.maxVisitedNodes = maxVisitedNodes;
        this.explorer = explorer;
        this.reverse = reverse;
        reverseFactor = reverse ? -1 : 1;
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        fromHeap = new PriorityQueue<>(size, new Comparator<Label>() {
            @Override
            public int compare(Label o1, Label o) {
                return compareLabels(o1, o);
            }

            @Override
            public boolean equals(Object obj) {
                return false;
            }
        });
        fromMap = HashMultimap.create();
    }

    private int compareLabels(Label o1, Label o) {
        if (reverseFactor * o1.currentTime < reverseFactor * o.currentTime) {
            return -1;
        }
        if (reverseFactor * o1.currentTime > reverseFactor * o.currentTime) {
            return 1;
        }
        if (o1.nTransfers > o.nTransfers) {
            return 1;
        }
        if (o1.nTransfers < o.nTransfers) {
            return -1;
        }
        if (reverseFactor * o1.firstPtDepartureTime < reverseFactor * o.firstPtDepartureTime) {
            return 1;
        }
        return -1;
    }

    Set<Label> calcPaths(int from, Set<Integer> to, long startTime, long rangeQueryEndTime) {
        rangeQueryEndTime = Long.MAX_VALUE;
        this.rangeQueryEndTime = rangeQueryEndTime;
        Set<Label> targetLabels = new HashSet<>();
        Label label = new Label(startTime, EdgeIterator.NO_EDGE, from, 0, Long.MAX_VALUE, null);
        Set<Label> foundSolutions = new HashSet<>();

        fromMap.put(from, label);
        if (to.contains(from)) {
            targetLabels.add(label);
        }
        while (true) {
            visitedNodes++;
            /*
             * if (maxVisitedNodes < visitedNodes) break;
             */
            // often we do not get many results (e.g. 10) for a pure profile
            // query. something wrong here. we should be able to get as many
            // results as we want as long as the day allows.probably my
            // understanding to the
            // impl of the graph. so when about 10 results, the algorithm fails
            // to find them and stops after exploring the whole graph. set
            // maxVisited to infinity and also rangeQueryEndTime.
            if (foundSolutions.size() >= 4)
                break;
            for (EdgeIteratorState edge : explorer.exploreEdgesAround(label)) {
                GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edge.getFlags());
                int tmpNTransfers = label.nTransfers;
                long tmpFirstPtDepartureTime = label.firstPtDepartureTime;
                long nextTime;
                int rev = reverse ? -1 : 1;
                nextTime = label.currentTime
                        + rev * ((TimeDependentWeighting) weighting).calcTravelTimeSeconds(edge, label.currentTime);

                tmpNTransfers += ((TimeDependentWeighting) weighting).calcNTransfers(edge);
                // need to know the actual departure time. i.e if some wait
                // happens at the source then a departure, the wait should be
                // excluded. should be multiple departures
                if (!reverse && edgeType == GtfsStorage.EdgeType.BOARD && tmpFirstPtDepartureTime == Long.MAX_VALUE) {
                    tmpFirstPtDepartureTime = nextTime;
                }
                if (reverse && edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK
                        && tmpFirstPtDepartureTime == Long.MAX_VALUE) {
                    tmpFirstPtDepartureTime = nextTime;
                }

                Set<Label> sptEntries = fromMap.get(edge.getAdjNode());
                Label nEdge = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), tmpNTransfers,
                        tmpFirstPtDepartureTime, label);
                if (to.contains(edge.getAdjNode())) {
                    addToFoundSolutions(nEdge, foundSolutions);
                    break;
                }
                if (improves(nEdge, sptEntries)) {
                    removeDominated(nEdge, sptEntries);
                    fromMap.put(edge.getAdjNode(), nEdge);
                    if (to.contains(edge.getAdjNode())) {
                        targetLabels.add(nEdge);
                    }
                    fromHeap.add(nEdge);
                }
            }

            if (fromHeap.isEmpty())
                break;

            label = fromHeap.poll();
            if (label == null)
                throw new AssertionError("Empty edge cannot happen");
        }
        return foundSolutions;
    }

    private boolean improves(Label me, Set<Label> sptEntries) {
        for (Label they : sptEntries) {
            int compResult = compareLabels(they, me);
            if (compResult == -1 || compResult == 0) {
                return false;
            }
        }
        return true;
    }

    private void removeDominated(Label me, Set<Label> sptEntries) {
        for (Iterator<Label> iterator = sptEntries.iterator(); iterator.hasNext();) {
            Label sptEntry = iterator.next();
            if (dominates(me, sptEntry)) {
                fromHeap.remove(sptEntry);
                iterator.remove();
            }
        }
    }

    private boolean dominates(Label me, Label they) {
        return compareLabels(me, they) == -1 ? true : false;
    }

    private void addToFoundSolutions(Label newSolution, Set<Label> foundSolutions) {

        for (Iterator<Label> iterator = foundSolutions.iterator(); iterator.hasNext();) {
            Label existingSolution = iterator.next();
            if (ParetoDominates(newSolution, existingSolution)) {
                iterator.remove();
            } else if (ParetoDominates(existingSolution, newSolution)) {
                return;
            }
        }

        foundSolutions.add(newSolution);
    }

    private boolean ParetoDominates(Label solution1, Label solution2) {
        if (reverseFactor * solution1.currentTime <= reverseFactor * solution2.currentTime
                && solution1.nTransfers <= solution2.nTransfers
                && reverseFactor * solution1.firstPtDepartureTime >= reverseFactor * solution2.firstPtDepartureTime)
            if (reverseFactor * solution1.currentTime < reverseFactor * solution2.currentTime
                    || solution1.nTransfers < solution2.nTransfers
                    || reverseFactor * solution1.firstPtDepartureTime > reverseFactor * solution2.firstPtDepartureTime)
                return true;
        return false;

    }

    int getVisitedNodes() {
        return visitedNodes;
    }

}
