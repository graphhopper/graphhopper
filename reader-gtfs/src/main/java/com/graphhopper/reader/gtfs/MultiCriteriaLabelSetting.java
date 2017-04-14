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
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

/**
 * Implements a Multi-Criteria Label Setting (MLS) path finding algorithm
 * with the criteria earliest arrival time and number of transfers.
 * <p>
 *
 * @author Michael Zilske
 * @author Peter Karich
 */
class MultiCriteriaLabelSetting {

    private final PtFlagEncoder flagEncoder;
    private final PtTravelTimeWeighting weighting;
    private final SetMultimap<Integer, Label> fromMap;
    private final PriorityQueue<Label> fromHeap;
    private final int maxVisitedNodes;
    private final boolean reverse;
    private final double maxWalkDistancePerLeg;
    private final double maxTransferDistancePerLeg;
    private final boolean mindTransfers;
    private long rangeQueryEndTime;
    private int visitedNodes;
    private final GraphExplorer explorer;

    MultiCriteriaLabelSetting(GraphExplorer explorer, Weighting weighting, boolean reverse, double maxWalkDistancePerLeg, double maxTransferDistancePerLeg, boolean mindTransfers, int maxVisitedNodes) {
        this.weighting = (PtTravelTimeWeighting) weighting;
        this.flagEncoder = (PtFlagEncoder) weighting.getFlagEncoder();
        this.maxVisitedNodes = maxVisitedNodes;
        this.explorer = explorer;
        this.reverse = reverse;
        this.maxWalkDistancePerLeg = maxWalkDistancePerLeg;
        this.maxTransferDistancePerLeg = maxTransferDistancePerLeg;
        this.mindTransfers = mindTransfers;
        fromHeap = new PriorityQueue<>(new Comparator<Label>() {
            @Override
            public int compare(Label o1, Label o) {
                return Long.compare(queueCriterion(o1), queueCriterion(o));
            }

            @Override
            public boolean equals(Object obj) {
                return false;
            }
        });
        fromMap = HashMultimap.create();
    }

    private long queueCriterion(Label o1) {
        return currentTimeCriterion(o1) + o1.nTransfers + o1.nWalkDistanceConstraintViolations;
    }

    Set<Label> calcPaths(int from, Set<Integer> to, long startTime, long rangeQueryEndTime) {
        this.rangeQueryEndTime = rangeQueryEndTime;
        Set<Label> targetLabels = new HashSet<>();
        Label label = new Label(startTime, EdgeIterator.NO_EDGE, from, 0, 0, 0.0, Long.MAX_VALUE, null);
        fromMap.put(from, label);
        if (to.contains(from)) {
            targetLabels.add(label);
        }
        while (true) {
            visitedNodes++;
            if (maxVisitedNodes < visitedNodes)
                break;

            for (EdgeIteratorState edge : explorer.exploreEdgesAround(label)) {
                GtfsStorage.EdgeType edgeType = flagEncoder.getEdgeType(edge.getFlags());
                long nextTime;
                if (reverse) {
                    nextTime = label.currentTime - weighting.calcTravelTimeSeconds(edge, label.currentTime);
                } else {
                    nextTime = label.currentTime + weighting.calcTravelTimeSeconds(edge, label.currentTime);
                }
                int nTransfers = label.nTransfers + weighting.calcNTransfers(edge);
                long firstPtDepartureTime = label.firstPtDepartureTime;
                if (!reverse && edgeType == GtfsStorage.EdgeType.BOARD && firstPtDepartureTime == Long.MAX_VALUE) {
                    firstPtDepartureTime = nextTime;
                }
                if (reverse && edgeType == GtfsStorage.EdgeType.LEAVE_TIME_EXPANDED_NETWORK && firstPtDepartureTime == Long.MAX_VALUE) {
                    firstPtDepartureTime = nextTime;
                }
                double walkDistanceOnCurrentLeg = (edgeType == GtfsStorage.EdgeType.BOARD) ? 0 : (label.walkDistanceOnCurrentLeg + weighting.getWalkDistance(edge));
                boolean isTryingToReEnterPtAfterTransferWalking = edgeType == GtfsStorage.EdgeType.ENTER_PT && label.nTransfers > 0 && label.walkDistanceOnCurrentLeg > maxTransferDistancePerLeg;
                int nWalkDistanceConstraintViolations = Math.min(1, label.nWalkDistanceConstraintViolations + (
                        isTryingToReEnterPtAfterTransferWalking ? 1 : (label.walkDistanceOnCurrentLeg <= maxWalkDistancePerLeg && walkDistanceOnCurrentLeg > maxWalkDistancePerLeg ? 1 : 0)));
                Set<Label> sptEntries = fromMap.get(edge.getAdjNode());
                Label nEdge = new Label(nextTime, edge.getEdge(), edge.getAdjNode(), nTransfers, nWalkDistanceConstraintViolations, walkDistanceOnCurrentLeg, firstPtDepartureTime, label);
                if (isNotEqualToAnyOf(nEdge, sptEntries) && isNotDominatedByAnyOf(nEdge, sptEntries) && isNotDominatedWithoutTieBreaksByAnyOf(nEdge, targetLabels)) {
                    removeDominated(nEdge, sptEntries);
                    if (to.contains(edge.getAdjNode())) {
                        removeDominated(nEdge, targetLabels);
                    }
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
        }
        return filterTargetLabels(targetLabels);
    }

    private boolean isNotEqualToAnyOf(Label me, Set<Label> sptEntries) {
        for (Label they : sptEntries) {
            if (me.currentTime == they.currentTime && me.nTransfers == they.nTransfers && me.nWalkDistanceConstraintViolations == they.nWalkDistanceConstraintViolations && me.firstPtDepartureTime == they.firstPtDepartureTime) {
                return false;
            }
        }
        return true;
    }

    private Set<Label> filterTargetLabels(Set<Label> targetLabels) {
        HashSet<Label> filteredLabels = new HashSet<>(targetLabels);
        for (Label me : new ArrayList<>(filteredLabels)) {
            filteredLabels.removeIf(they -> dominatesForFiltering(me, they));
        }
        filteredLabels.removeIf(they -> they.nWalkDistanceConstraintViolations > 0);
        return filteredLabels;
    }

    private boolean dominatesForFiltering(Label me, Label they) {
        if (currentTimeCriterion(me) > currentTimeCriterion(they)) {
            return false;
        }
        if (mindTransfers) {
            if (me.nTransfers > they.nTransfers) {
                return false;
            }
        }
        if (me.firstPtDepartureTime != Long.MAX_VALUE && they.firstPtDepartureTime != Long.MAX_VALUE
                && firstPtDepartureTimeCriterion(me) < firstPtDepartureTimeCriterion(they)) {
            return false;
        }
        if (me.nWalkDistanceConstraintViolations > they.nWalkDistanceConstraintViolations) {
            return false;
        }
        if (currentTimeCriterion(me) < currentTimeCriterion(they)) {
            return true;
        }
        if (me.nTransfers < they.nTransfers) {
            return true;
        }
        if (me.firstPtDepartureTime != Long.MAX_VALUE && they.firstPtDepartureTime != Long.MAX_VALUE
                && firstPtDepartureTimeCriterion(me) > firstPtDepartureTimeCriterion(they)) {
            return true;
        }
        if (me.nWalkDistanceConstraintViolations > they.nWalkDistanceConstraintViolations) {
            return true;
        }
        return false;
    }

    private boolean isNotDominatedByAnyOf(Label me, Set<Label> sptEntries) {
        for (Label they : sptEntries) {
            if (dominates(they, me)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNotDominatedWithoutTieBreaksByAnyOf(Label me, Set<Label> sptEntries) {
        for (Label they : sptEntries) {
            if (dominatesWithoutTieBreaks(they, me)) {
                return false;
            }
        }
        return true;
    }


    private void removeDominated(Label me, Set<Label> sptEntries) {
        for (Iterator<Label> iterator = sptEntries.iterator(); iterator.hasNext();) {
            Label sptEntry = iterator.next();
            if (dominatesWithoutTieBreaks(me, sptEntry)) {
                fromHeap.remove(sptEntry);
                iterator.remove();
            }
        }
    }

    private boolean dominates(Label me, Label they) {
        if (currentTimeCriterion(me) + currentTimeSlack(me, they) > currentTimeCriterion(they))
            return false;
        if (mindTransfers) {
            if (me.nTransfers + nTransfersSlack(me, they) > they.nTransfers)
                return false;
        }
        if (me.nWalkDistanceConstraintViolations + nWalkDistanceConstraintViolationsSlack(me, they) > they.nWalkDistanceConstraintViolations)
            return false;
        if (currentTimeCriterion(me) + currentTimeSlack(me, they) < currentTimeCriterion(they))
            return true;
        if (mindTransfers) {
            if (me.nTransfers + nTransfersSlack(me, they) < they.nTransfers)
                return true;
        }
        if (me.nWalkDistanceConstraintViolations + nWalkDistanceConstraintViolationsSlack(me, they) < they.nWalkDistanceConstraintViolations)
            return true;

        if (!reverse) {
            // Break ties: Fewer transfers is better
            if (me.nTransfers < they.nTransfers) {
                return true;
            }

//            Break ties: Leaving later / arriving earlier is better
            if (firstPtDepartureTimeCriterion(me) != Long.MAX_VALUE
                    && firstPtDepartureTimeCriterion(they) != Long.MAX_VALUE
                    && firstPtDepartureTimeCriterion(me) > firstPtDepartureTimeCriterion(they)) {
                return true;
            }
        }
        return false;
    }

    private boolean dominatesWithoutTieBreaks(Label me, Label they) {
        if (currentTimeCriterion(me) + currentTimeSlack(me, they) > currentTimeCriterion(they))
            return false;
        if (mindTransfers) {
            if (me.nTransfers + nTransfersSlack(me, they) > they.nTransfers)
                return false;
        }
        if (me.nWalkDistanceConstraintViolations + nWalkDistanceConstraintViolationsSlack(me, they) > they.nWalkDistanceConstraintViolations)
            return false;
        if (currentTimeCriterion(me) + currentTimeSlack(me, they) < currentTimeCriterion(they))
            return true;
        if (mindTransfers) {
            if (me.nTransfers + nTransfersSlack(me, they) < they.nTransfers)
                return true;
        }
        if (me.nWalkDistanceConstraintViolations + nWalkDistanceConstraintViolationsSlack(me, they) < they.nWalkDistanceConstraintViolations)
            return true;
        return false;
    }

    private long currentTimeCriterion(Label label) {
        return reverse ? -label.currentTime : label.currentTime;
    }

    private double nWalkDistanceConstraintViolationsSlack(Label me, Label they) {
        return profileQuerySlackComponent(me, they);
    }

    private double profileQuerySlackComponent(Label me, Label they) {
        if ((they.firstPtDepartureTime == Long.MAX_VALUE && me.firstPtDepartureTime != Long.MAX_VALUE && currentTimeCriterion(they) <= rangeQueryEndTimeConstraint()) ||
                (they.firstPtDepartureTime != Long.MAX_VALUE && me.firstPtDepartureTime != Long.MAX_VALUE &&
                firstPtDepartureTimeCriterion(they) > firstPtDepartureTimeCriterion(me) &&
                firstPtDepartureTimeCriterion(they) <= rangeQueryEndTimeConstraint())) {
            return Double.POSITIVE_INFINITY;
        } else {
            return 0;
        }
    }

    private long rangeQueryEndTimeConstraint() {
        return reverse ? -rangeQueryEndTime : rangeQueryEndTime;
    }

    private long firstPtDepartureTimeCriterion(Label label) {
        return reverse ? -label.firstPtDepartureTime : label.firstPtDepartureTime;
    }

    private double nTransfersSlack(Label me, Label they) {
        return profileQuerySlackComponent(me, they);
    }

    private double currentTimeSlack(Label me, Label they) {
        return profileQuerySlackComponent(me, they);
    }

    int getVisitedNodes() {
        return visitedNodes;
    }

}
