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
import java.util.function.IntToLongFunction;
import java.util.stream.Collectors;
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
    private final long maxProfileDuration;
    private final boolean reverse;
    private final boolean mindTransfers;
    private final boolean profileQuery;
    private final GraphExplorer explorer;
    private double betaTransfers = 0.0;
    private IntToLongFunction transferPenaltiesByRouteType = (routeType -> 0L);
    private double betaStreetTime = 1.0;
    private long limitTripTime = Long.MAX_VALUE;
    private long limitStreetTime = Long.MAX_VALUE;

    public MultiCriteriaLabelSetting(GraphExplorer explorer, PtEncodedValues flagEncoder, boolean reverse, boolean mindTransfers, boolean profileQuery, long maxProfileDuration, List<Label> solutions) {
        this.explorer = explorer;
        this.reverse = reverse;
        this.mindTransfers = mindTransfers;
        this.profileQuery = profileQuery;
        this.maxProfileDuration = maxProfileDuration;
        this.targetLabels = solutions;
        this.typeEnc = flagEncoder.getTypeEnc();

        queueComparator = Comparator
                .comparingLong(this::weight)
                .thenComparingLong(l -> l.nTransfers)
                .thenComparingLong(l -> l.streetTime)
                .thenComparingLong(l -> departureTimeCriterion(l) != null ? departureTimeCriterion(l) : 0)
                .thenComparingLong(l -> l.impossible ? 1 : 0);
        fromHeap = new PriorityQueue<>(queueComparator);
        fromMap = new IntObjectHashMap<>();
    }

    public Stream<Label> calcLabels(int from, Instant startTime) {
        this.startTime = startTime.toEpochMilli();
        return StreamSupport.stream(new MultiCriteriaLabelSettingSpliterator(from), false);
    }

    void setBetaTransfers(double betaTransfers) {
        this.betaTransfers = betaTransfers;
    }

    void setBetaStreetTime(double betaWalkTime) {
        this.betaStreetTime = betaWalkTime;
    }

    void setBoardingPenaltyByRouteType(IntToLongFunction transferPenaltiesByRouteType) {
        this.transferPenaltiesByRouteType = transferPenaltiesByRouteType;
    }

    private class MultiCriteriaLabelSettingSpliterator extends Spliterators.AbstractSpliterator<Label> {

        MultiCriteriaLabelSettingSpliterator(int from) {
            super(0, 0);
            Label label = new Label(startTime, EdgeIterator.NO_EDGE, from, 0, null, 0, 0L, 0, false, null);
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
                    int nTransfers = label.nTransfers + explorer.nTransfers(edge);
                    long extraWeight = label.extraWeight;
                    Long firstPtDepartureTime = label.departureTime;
                    GtfsStorage.EdgeType edgeType = edge.get(typeEnc);
                    if (!reverse && (edgeType == GtfsStorage.EdgeType.ENTER_PT) || reverse && (edgeType == GtfsStorage.EdgeType.EXIT_PT)) {
                        int currentRouteType = explorer.routeType(edge);
                        extraWeight += transferPenaltiesByRouteType.applyAsLong(currentRouteType);
                    }
                    if (edgeType == GtfsStorage.EdgeType.TRANSFER) {
                        int currentRouteType = explorer.routeType(edge);
                        extraWeight += transferPenaltiesByRouteType.applyAsLong(currentRouteType);
                    }
                    if (!reverse && (edgeType == GtfsStorage.EdgeType.ENTER_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT)) {
                        if (label.nTransfers == 0) {
                            firstPtDepartureTime = nextTime - label.streetTime;
                        }
                    } else if (reverse && (edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK || edgeType == GtfsStorage.EdgeType.WAIT_ARRIVAL)) {
                        if (label.nTransfers == 0) {
                            firstPtDepartureTime = nextTime + label.streetTime;
                        }
                    }
                    long walkTime = label.streetTime + (edgeType == GtfsStorage.EdgeType.HIGHWAY || edgeType == GtfsStorage.EdgeType.ENTER_PT || edgeType == GtfsStorage.EdgeType.EXIT_PT ? ((reverse ? -1 : 1) * (nextTime - label.currentTime)) : 0);
                    if (walkTime > limitStreetTime)
                        return;
                    if (Math.abs(nextTime - startTime) > limitTripTime)
                        return;
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
                        Label newImpossibleLabelForDelayedTrip = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, firstPtDepartureTime, walkTime, extraWeight, residualDelay, true, label);
                        insertIfNotDominated(newImpossibleLabelForDelayedTrip);
                        nextTime += residualDelay;
                        residualDelay = 0;
                        Label newLabel = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, firstPtDepartureTime, walkTime, extraWeight, residualDelay, impossible, label);
                        insertIfNotDominated(newLabel);
                    } else {
                        Label newLabel = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, firstPtDepartureTime, walkTime, extraWeight, residualDelay, impossible, label);
                        insertIfNotDominated(newLabel);
                    }
                });
                return true;
            }
        }
    }

    void insertIfNotDominated(Label me) {
        List<Label> filteredTargetLabels = profileQuery && me.departureTime != null ? partitionByProfileCriterion(me, targetLabels).get(true) : targetLabels;
        if (isNotDominatedByAnyOf(me, filteredTargetLabels)) {
            List<Label> sptEntries = fromMap.get(me.adjNode);
            if (sptEntries == null) {
                sptEntries = new ArrayList<>(1);
                fromMap.put(me.adjNode, sptEntries);
            }
            List<Label> filteredSptEntries;
            List<Label> otherSptEntries;
            if (profileQuery && me.departureTime != null) {
                Map<Boolean, List<Label>> partitionedSptEntries = partitionByProfileCriterion(me, sptEntries);
                filteredSptEntries = new ArrayList<>(partitionedSptEntries.get(true));
                otherSptEntries = new ArrayList<>(partitionedSptEntries.get(false));
            } else {
                filteredSptEntries = new ArrayList<>(sptEntries);
                otherSptEntries = Collections.emptyList();
            }
            if (isNotDominatedByAnyOf(me, filteredSptEntries)) {
                removeDominated(me, filteredSptEntries);
                sptEntries.clear();
                sptEntries.addAll(filteredSptEntries);
                sptEntries.addAll(otherSptEntries);
                sptEntries.add(me);
                fromHeap.add(me);
            }
        }
    }

    Map<Boolean, List<Label>> partitionByProfileCriterion(Label me, List<Label> sptEntries) {
        if (!reverse) {
            return sptEntries.stream().collect(Collectors.partitioningBy(they -> they.departureTime != null && (they.departureTime >= me.departureTime || they.departureTime >= startTime + maxProfileDuration)));
        } else {
            return sptEntries.stream().collect(Collectors.partitioningBy(they -> they.departureTime != null && (they.departureTime <= me.departureTime || they.departureTime <= startTime - maxProfileDuration)));
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

        if (mindTransfers && me.nTransfers > they.nTransfers)
            return false;
        if (me.impossible && !they.impossible)
            return false;

        if (weight(me) < weight(they))
            return true;
        if (mindTransfers && me.nTransfers < they.nTransfers)
            return true;

        return queueComparator.compare(me, they) <= 0;
    }

    private Long departureTimeCriterion(Label label) {
        return label.departureTime == null ? null : reverse ? label.departureTime : -label.departureTime;
    }

    long weight(Label label) {
        return timeSinceStartTime(label) + (long) (label.nTransfers * betaTransfers) + (long) (label.streetTime * (betaStreetTime - 1.0)) + label.extraWeight;
    }

    long timeSinceStartTime(Label label) {
        return (reverse ? -1 : 1) * (label.currentTime - startTime);
    }

    Long departureTimeSinceStartTime(Label label) {
        return label.departureTime != null ? (reverse ? -1 : 1) * (label.departureTime - startTime) : null;
    }

    public void setLimitTripTime(long limitTripTime) {
        this.limitTripTime = limitTripTime;
    }

    public void setLimitStreetTime(long limitStreetTime) {
        this.limitStreetTime = limitStreetTime;
    }

}
