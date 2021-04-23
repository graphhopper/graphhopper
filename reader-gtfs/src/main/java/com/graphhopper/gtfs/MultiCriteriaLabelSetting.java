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
package com.graphhopper.gtfs;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.util.EdgeIterator;

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
public class MultiCriteriaLabelSetting {

    public interface SPTVisitor {
        void visit(Label label);
    }

    private final Comparator<Label> queueComparator;
    private final List<Label> targetLabels;
    private long startTime;
    private final EnumEncodedValue<GtfsStorage.EdgeType> typeEnc;
    private final IntObjectMap<List<Label>> fromMap;
    private final PriorityQueue<Label> fromHeap;
    private final int maxVisitedNodes;
    private final boolean reverse;
    private final boolean mindTransfers;
    private final boolean profileQuery;
    private int visitedNodes;
    private final GraphExplorer explorer;
    private double betaTransfers;
    private double betaWalkTime = 1.0;
    private long limitStreetTime = Long.MAX_VALUE;

    public MultiCriteriaLabelSetting(GraphExplorer explorer, PtEncodedValues flagEncoder, boolean reverse, boolean mindTransfers, boolean profileQuery, int maxVisitedNodes, List<Label> solutions) {
        this.maxVisitedNodes = maxVisitedNodes;
        this.explorer = explorer;
        this.reverse = reverse;
        this.mindTransfers = mindTransfers;
        this.profileQuery = profileQuery;
        this.targetLabels = solutions;
        this.typeEnc = flagEncoder.getTypeEnc();

        queueComparator = Comparator
                .comparingLong(this::weight)
                .thenComparingLong(l -> l.nTransfers)
                .thenComparingLong(l -> l.walkTime)
                .thenComparingLong(l -> departureTimeCriterion(l) != null ? departureTimeCriterion(l) : 0)
                .thenComparingLong(l -> l.impossible ? 1 : 0);
        fromHeap = new PriorityQueue<>(queueComparator);
        fromMap = new IntObjectHashMap<>();
    }

    public Stream<Label> calcLabels(int from, Instant startTime) {
        this.startTime = startTime.toEpochMilli();
        return StreamSupport.stream(new MultiCriteriaLabelSettingSpliterator(from), false)
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

        MultiCriteriaLabelSettingSpliterator(int from) {
            super(0, 0);
            Label label = new Label(startTime, EdgeIterator.NO_EDGE, from, 0, null, 0, 0, false, null);
            ArrayList<Label> labels = new ArrayList<>(1);
            labels.add(label);
            fromMap.put(from, labels);
            fromHeap.add(label);
        }

        @Override
        public boolean tryAdvance(Consumer<? super Label> action) {
            while (!fromHeap.isEmpty() && fromHeap.peek().deleted)
                fromHeap.poll();
            if (fromHeap.isEmpty()) {
                return false;
            } else {
                Label label = fromHeap.poll();
                action.accept(label);
                explorer.exploreEdgesAround(label).forEach(edge -> {
                    long nextTime;
                    if (reverse) {
                        nextTime = label.currentTime - explorer.calcTravelTimeMillis(edge, label.currentTime);
                    } else {
                        nextTime = label.currentTime + explorer.calcTravelTimeMillis(edge, label.currentTime);
                    }
                    int nTransfers = label.nTransfers + explorer.calcNTransfers(edge);
                    Long firstPtDepartureTime = label.departureTime;
                    GtfsStorage.EdgeType edgeType = edge.get(typeEnc);
                    if (!reverse && (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT)) {
                        if (label.nTransfers == 0) {
                            firstPtDepartureTime = nextTime - label.walkTime;
                        }
                    } else if (reverse && (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL)) {
                        if (label.nTransfers == 0) {
                            firstPtDepartureTime = nextTime + label.walkTime;
                        }
                    }
                    long walkTime = label.walkTime + (edgeType == GtfsStorage.EdgeType.HIGHWAY || edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT ? ((reverse ? -1 : 1) * (nextTime - label.currentTime)) : 0);
                    if (walkTime > limitStreetTime)
                        return;
                    List<Label> sptEntries = fromMap.get(edge.getAdjNode());
                    if (sptEntries == null) {
                        sptEntries = new ArrayList<>(1);
                        fromMap.put(edge.getAdjNode(), sptEntries);
                    }
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
                        Label newImpossibleLabelForDelayedTrip = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, firstPtDepartureTime, walkTime, residualDelay, true, label);
                        insertIfNotDominated(sptEntries, newImpossibleLabelForDelayedTrip);
                        nextTime += residualDelay;
                        residualDelay = 0;
                        Label newLabel = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, firstPtDepartureTime, walkTime, residualDelay, impossible, label);
                        insertIfNotDominated(sptEntries, newLabel);
                    } else {
                        Label newLabel = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, firstPtDepartureTime, walkTime, residualDelay, impossible, label);
                        insertIfNotDominated(sptEntries, newLabel);
                    }
                });
                return true;
            }
        }

        private void insertIfNotDominated(Collection<Label> sptEntries, Label label) {
            if (isNotDominatedByAnyOf(label, sptEntries)) {
                if (isNotDominatedByAnyOf(label, targetLabels)) {
                    removeDominated(label, sptEntries);
                    sptEntries.add(label);
                    fromHeap.add(label);
                }
            }
        }
    }

    boolean isNotDominatedByAnyOf(Label me, Collection<Label> sptEntries) {
        for (Label they : sptEntries) {
            if (dominates(they, me)) {
                return false;
            }
        }
        return true;
    }

    void removeDominated(Label me, Collection<Label> sptEntries) {
        for (Iterator<Label> iterator = sptEntries.iterator(); iterator.hasNext(); ) {
            Label sptEntry = iterator.next();
            if (dominates(me, sptEntry)) {
                sptEntry.deleted = true;
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
        if (mindTransfers && me.nTransfers < they.nTransfers)
            return true;

        return queueComparator.compare(me, they) <= 0;
    }

    private Long departureTimeCriterion(Label label) {
        return label.departureTime == null ? null : reverse ? label.departureTime : -label.departureTime;
    }

    long weight(Label label) {
        return timeSinceStartTime(label) + (long) (label.nTransfers * betaTransfers) + (long) (label.walkTime * (betaWalkTime - 1.0));
    }

    long timeSinceStartTime(Label label) {
        return (reverse ? -1 : 1) * (label.currentTime - startTime);
    }

    private long travelTimeCriterion(Label label) {
        if (label.departureTime == null) {
            return label.walkTime;
        } else {
            return (reverse ? -1 : 1) * (label.currentTime - label.departureTime);
        }
    }

    public void setLimitStreetTime(long limitStreetTime) {
        this.limitStreetTime = limitStreetTime;
    }

    int getVisitedNodes() {
        return visitedNodes;
    }

}
