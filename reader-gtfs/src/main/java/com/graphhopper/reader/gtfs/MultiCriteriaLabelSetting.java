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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implements a Multi-Criteria Label Setting (MLS) path finding algorithm
 * with the criteria earliest arrival time and number of transfers.
 * <p>
 *
 * @author Michael Zilske
 * @author Peter Karich
 * @author Wesam Herbawi
 */
class MultiCriteriaLabelSetting {

    private final Comparator<Label> queueComparator;
    private long startTime;
    private int blockedRouteTypes;
    private final PtFlagEncoder flagEncoder;
    private final Multimap<Integer, Label> fromMap;
    private final PriorityQueue<Label> fromHeap;
    private final int maxVisitedNodes;
    private final boolean reverse;
    private final double maxWalkDistancePerLeg;
    private final boolean ptOnly;
    private final boolean mindTransfers;
    private final boolean profileQuery;
    private int visitedNodes;
    private final GraphExplorer explorer;
    private double betaTransfers;
    private double betaWalkTime = 1.0;

    MultiCriteriaLabelSetting(GraphExplorer explorer, PtFlagEncoder flagEncoder, boolean reverse, double maxWalkDistancePerLeg, boolean ptOnly, boolean mindTransfers, boolean profileQuery, int maxVisitedNodes) {
        this.flagEncoder = flagEncoder;
        this.maxVisitedNodes = maxVisitedNodes;
        this.explorer = explorer;
        this.reverse = reverse;
        this.maxWalkDistancePerLeg = maxWalkDistancePerLeg;
        this.ptOnly = ptOnly;
        this.mindTransfers = mindTransfers;
        this.profileQuery = profileQuery;

        queueComparator = Comparator.<Label>comparingLong(l2 -> l2.impossible ? 1 : 0)
                .thenComparing(Comparator.comparingLong(l2 -> weight(l2)))
                .thenComparing(Comparator.comparingLong(l1 -> l1.nTransfers))
                .thenComparing(Comparator.comparingLong(l1 -> l1.nWalkDistanceConstraintViolations))
                .thenComparing(Comparator.comparingLong(l -> departureTimeCriterion(l) != null ? departureTimeCriterion(l) : 0));
        fromHeap = new PriorityQueue<>(queueComparator);
        fromMap = ArrayListMultimap.create();
    }

    Stream<Label> calcLabels(int from, int to, Instant startTime, int blockedRouteTypes) {
        this.startTime = startTime.toEpochMilli();
        this.blockedRouteTypes = blockedRouteTypes;
        return StreamSupport.stream(new MultiCriteriaLabelSettingSpliterator(from, to), false)
                .limit(maxVisitedNodes)
                .peek(label -> visitedNodes++);
    }

    // experimental
    void setBetaTransfers(double betaTransfers) {
        this.betaTransfers = betaTransfers;
    }

    // experimental
    void setBetaWalkTime(double betaWalkTime) {
        this.betaWalkTime = betaWalkTime;
    }

    private class MultiCriteriaLabelSettingSpliterator extends Spliterators.AbstractSpliterator<Label> {

        private final int from;
        private final int to;
        private final Collection<Label> targetLabels;

        MultiCriteriaLabelSettingSpliterator(int from, int to) {
            super(0, 0);
            this.from = from;
            this.to = to;
            targetLabels = new ArrayList<>();
            Label label = new Label(startTime, EdgeIterator.NO_EDGE, from, 0, 0, 0.0, null, 0, 0,false,null);
            fromMap.put(from, label);
            fromHeap.add(label);
            if (to == from) {
                targetLabels.add(label);
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super Label> action) {
            if (fromHeap.isEmpty()) {
                return false;
            } else {
                Label label = fromHeap.poll();
                action.accept(label);
                explorer.exploreEdgesAround(label).forEach(edge -> {
                    GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edge.getFlags());
                    if (edgeType == GtfsStorage.EdgeType.ENTER_PT && ((reverse?edge.getAdjNode():edge.getBaseNode()) != (reverse?to:from)) && ptOnly) return;
                    if (edgeType == GtfsStorage.EdgeType.EXIT_PT && ((reverse?edge.getBaseNode():edge.getAdjNode()) != (reverse?from:to)) && ptOnly) return;
                    if ((edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT) && (blockedRouteTypes & (1 << flagEncoder.getValidityId(edge.getFlags()))) != 0) return;
                    long nextTime;
                    if (reverse) {
                        nextTime = label.currentTime - explorer.calcTravelTimeMillis(edge, label.currentTime);
                    } else {
                        nextTime = label.currentTime + explorer.calcTravelTimeMillis(edge, label.currentTime);
                    }
                    int nTransfers = label.nTransfers + explorer.calcNTransfers(edge);
                    Long firstPtDepartureTime = label.departureTime;
                    if (!reverse && (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT)) {
                        if (label.nTransfers == 0) {
                            firstPtDepartureTime = nextTime - label.walkTime;
                        }
                    } else if (reverse && (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL)) {
                        if (label.nTransfers == 0) {
                            firstPtDepartureTime = nextTime - label.walkTime;
                        }
                    }
                    double walkDistanceOnCurrentLeg = (!reverse && edgeType == GtfsStorage.EdgeType.BOARD || reverse && edgeType == GtfsStorage.EdgeType.ALIGHT) ? 0 : (label.walkDistanceOnCurrentLeg + edge.getDistance());
                    boolean isTryingToReEnterPtAfterWalking = (!reverse && edgeType == GtfsStorage.EdgeType.ENTER_PT || reverse && edgeType == GtfsStorage.EdgeType.EXIT_PT) && label.nTransfers > 0;
                    long walkTime = label.walkTime + (edgeType == GtfsStorage.EdgeType.HIGHWAY || edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT ? nextTime - label.currentTime : 0);
                    int nWalkDistanceConstraintViolations = Math.min(1, label.nWalkDistanceConstraintViolations + (
                            isTryingToReEnterPtAfterWalking ? 1 : (label.walkDistanceOnCurrentLeg <= maxWalkDistancePerLeg && walkDistanceOnCurrentLeg > maxWalkDistancePerLeg ? 1 : 0)));
                    Collection<Label> sptEntries = fromMap.get(edge.getAdjNode());
                    boolean impossible = label.impossible
                            || explorer.isBlocked(edge)
                            || (!reverse) && edgeType == GtfsStorage.EdgeType.BOARD && label.residualDelay > 0
                            || reverse && edgeType == GtfsStorage.EdgeType.ALIGHT && label.residualDelay < explorer.getDelayFromAlightEdge(edge, label.currentTime);
                    long residualDelay;
                    if (!reverse) {
                        if (edgeType == GtfsStorage.EdgeType.WAIT || edgeType == GtfsStorage.EdgeType.TRANSFER) {
                            residualDelay = Math.max(0, label.residualDelay - explorer.calcTravelTimeMillis(edge, label.currentTime));
                        } else if (edgeType == GtfsStorage.EdgeType.ALIGHT) {
                            residualDelay = label.residualDelay + explorer.getDelayFromAlightEdge(edge, label.currentTime);
                        } else if (edgeType == GtfsStorage.EdgeType.BOARD) {
                            residualDelay = -explorer.getDelayFromBoardEdge(edge, label.currentTime);
                        } else {
                            residualDelay = label.residualDelay;
                        }
                    } else {
                        if (edgeType == GtfsStorage.EdgeType.WAIT || edgeType == GtfsStorage.EdgeType.TRANSFER) {
                            residualDelay = label.residualDelay + explorer.calcTravelTimeMillis(edge, label.currentTime);
                        } else {
                            residualDelay = 0;
                        }
                    }
                    if (!reverse && edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK && residualDelay > 0) {
                        Label newImpossibleLabelForDelayedTrip = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, nWalkDistanceConstraintViolations, walkDistanceOnCurrentLeg, firstPtDepartureTime, walkTime, residualDelay, true, label);
                        insertIfNotDominated(edge, sptEntries, newImpossibleLabelForDelayedTrip);
                        nextTime += residualDelay;
                        residualDelay = 0;
                        Label newLabel = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, nWalkDistanceConstraintViolations, walkDistanceOnCurrentLeg, firstPtDepartureTime, walkTime, residualDelay, impossible, label);
                        insertIfNotDominated(edge, sptEntries, newLabel);
                    } else {
                        Label newLabel = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, nWalkDistanceConstraintViolations, walkDistanceOnCurrentLeg, firstPtDepartureTime, walkTime, residualDelay, impossible, label);
                        insertIfNotDominated(edge, sptEntries, newLabel);
                    }
                });
                return true;
            }
        }

        private void insertIfNotDominated(EdgeIteratorState edge, Collection<Label> sptEntries, Label nEdge) {
            if (isNotDominatedByAnyOf(nEdge, sptEntries) && isNotDominatedByAnyOf(nEdge, targetLabels)) {
                removeDominated(nEdge, sptEntries);
                if (to == edge.getAdjNode()) {
                    removeDominated(nEdge, targetLabels);
                }
                fromMap.put(edge.getAdjNode(), nEdge);
                if (to == edge.getAdjNode()) {
                    targetLabels.add(nEdge);
                }
                fromHeap.add(nEdge);
            }
        }
    }

    private boolean isNotDominatedByAnyOf(Label me, Collection<Label> sptEntries) {
        if (me.nWalkDistanceConstraintViolations > 0) {
            return false;
        }
        for (Label they : sptEntries) {
            if (dominates(they, me)) {
                return false;
            }
        }
        return true;
    }


    private void removeDominated(Label me, Collection<Label> sptEntries) {
        for (Iterator<Label> iterator = sptEntries.iterator(); iterator.hasNext();) {
            Label sptEntry = iterator.next();
            if (dominates(me, sptEntry)) {
                fromHeap.remove(sptEntry);
                iterator.remove();
            }
        }
    }

    private boolean dominates(Label me, Label they) {
        if (weight(me) > weight(they))
            return false;

        if (profileQuery) {
            if (me.departureTime != null && they.departureTime != null) {
                if (departureTimeCriterion(me) > departureTimeCriterion(they))
                    return false;
            } else {
                if (travelTimeCriterion(me) > travelTimeCriterion(they))
                    return false;
            }
        }

        if (mindTransfers && me.nTransfers > they.nTransfers)
            return false;
        if (me.nWalkDistanceConstraintViolations  > they.nWalkDistanceConstraintViolations)
            return false;
        if (me.impossible && !they.impossible)
            return false;

        if (weight(me) < weight(they))
            return true;
        if (profileQuery) {
            if (me.departureTime != null && they.departureTime != null) {
                if (departureTimeCriterion(me) < departureTimeCriterion(they))
                    return true;
            } else {
                if (travelTimeCriterion(me) < travelTimeCriterion(they))
                    return true;
            }
        }
        if (mindTransfers && me.nTransfers  < they.nTransfers)
            return true;
        if (me.nWalkDistanceConstraintViolations < they.nWalkDistanceConstraintViolations)
            return true;

        return queueComparator.compare(me,they) <= 0;
    }

    private Long departureTimeCriterion(Label label) {
        return label.departureTime == null ? null : reverse ? label.departureTime : -label.departureTime;
    }

    private long weight(Label label) {
        return (reverse ? -1 : 1) * (label.currentTime - startTime) + (long) (label.nTransfers * betaTransfers) + (long) (label.walkTime * (betaWalkTime - 1.0));
    }

    private long travelTimeCriterion(Label label) {
        if (label.departureTime == null) {
            return label.walkTime;
        } else {
            return (reverse ? -1 : 1) * (label.currentTime - label.departureTime);
        }
    }

    int getVisitedNodes() {
        return visitedNodes;
    }

}
