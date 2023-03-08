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
package com.graphhopper.util;

import com.graphhopper.ResponsePath;
import com.graphhopper.util.details.PathDetail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class simplifies the path, using {@link RamerDouglasPeucker}, but also considers a given list of partitions of
 * the path. Each partition separates the points of the path into non-overlapping intervals and the simplification is
 * done such that we never simplify across the boundaries of these intervals. This is important, because the points
 * at the interval boundaries must not be removed, e.g. when they are referenced by instructions.
 * For example for a path with twenty points and three partitions like this
 * <p>
 * - (0,1,2,3)(3,4)(4,4)(4,5,6,7)(7,8,9,10,11,12)(12,13,14,15,16)(17,18,19)
 * - (0,1)(1,2,3,4)(4,5,6,7)(7,7)(8,9,10,11)(12,13,14,15)(16,17,18,19)
 * - (0,1,2,3,4,5)(6,7,8,9,10,11,12,13,14),(14,15,16,17,18)(18,18)(18,19)
 * <p>
 * we run the simplification for the following intervals:
 * <p>
 * (0,1)(1,2,3)(3,4)(4,5)(5,6,7)(7,8,9,10,11)(11,12)(12,13,14)(14,15)(15,16)(16,17,18)(18,19)
 *
 * @author Robin Boldt
 * @author easbar
 */
public class PathSimplification {

    private final PointList pointList;
    /**
     * @see PathSimplification
     */
    private final List<Partition> partitions;
    private final RamerDouglasPeucker ramerDouglasPeucker;

    // temporary variables used when traversing the different partitions
    private final int numPartitions;
    private final int[] currIntervalIndex;
    private final int[] currIntervalStart;
    private final int[] currIntervalEnd;
    private final boolean[] partitionFinished;
    // keep track of how many points were removed by Ramer-Douglas-Peucker in the current and previous intervals
    private final int[] removedPointsInCurrInterval;
    private final int[] removedPointsInPrevIntervals;

    /**
     * Convenience method used to obtain the partitions from a calculated path with details and instructions
     */
    public static PointList simplify(ResponsePath responsePath, RamerDouglasPeucker ramerDouglasPeucker, boolean enableInstructions) {
        final PointList pointList = responsePath.getPoints();
        List<Partition> partitions = new ArrayList<>();

        // make sure all waypoints are retained in the simplified point list
        // we copy the waypoint indices into temporary intervals where they will be mutated by the simplification,
        // afterwards we need to update the way point indices accordingly.
        List<Interval> intervals = new ArrayList<>();
        for (int i = 0; i < responsePath.getWaypointIndices().size() - 1; i++)
            intervals.add(new Interval(responsePath.getWaypointIndices().get(i), responsePath.getWaypointIndices().get(i + 1)));
        partitions.add(new Partition() {
            @Override
            public int size() {
                return intervals.size();
            }

            @Override
            public int getIntervalLength(int index) {
                return intervals.get(index).end - intervals.get(index).start;
            }

            @Override
            public void setInterval(int index, int start, int end) {
                intervals.get(index).start = start;
                intervals.get(index).end = end;
            }
        });

        // todo: maybe this code can be simplified if path details and instructions would be merged, see #1121
        if (enableInstructions) {
            final InstructionList instructions = responsePath.getInstructions();
            partitions.add(new Partition() {
                @Override
                public int size() {
                    return instructions.size();
                }

                @Override
                public int getIntervalLength(int index) {
                    return instructions.get(index).getLength();
                }

                @Override
                public void setInterval(int index, int start, int end) {
                    Instruction instruction = instructions.get(index);
                    if (instruction instanceof ViaInstruction || instruction instanceof FinishInstruction) {
                        if (start != end) {
                            throw new IllegalStateException("via- and finish-instructions are expected to have zero length");
                        }
                        // have to make sure that via instructions and finish instructions contain a single point
                        // even though their 'instruction length' is zero.
                        end++;
                    }
                    instruction.setPoints(pointList.shallowCopy(start, end, false));
                }
            });
        }

        for (final Map.Entry<String, List<PathDetail>> entry : responsePath.getPathDetails().entrySet()) {
            // If the pointList only contains one point, PathDetails have to be empty because 1 point => 0 edges
            final List<PathDetail> detail = entry.getValue();
            if (detail.isEmpty() && pointList.size() > 1)
                throw new IllegalStateException("PathDetails " + entry.getKey() + " must not be empty");

            partitions.add(new Partition() {
                @Override
                public int size() {
                    return detail.size();
                }

                @Override
                public int getIntervalLength(int index) {
                    return detail.get(index).getLength();
                }

                @Override
                public void setInterval(int index, int start, int end) {
                    PathDetail pd = detail.get(index);
                    pd.setFirst(start);
                    pd.setLast(end);
                }
            });
        }

        simplify(responsePath.getPoints(), partitions, ramerDouglasPeucker);

        List<Integer> simplifiedWaypointIndices = new ArrayList<>();
        simplifiedWaypointIndices.add(intervals.get(0).start);
        for (Interval interval : intervals)
            simplifiedWaypointIndices.add(interval.end);
        responsePath.setWaypointIndices(simplifiedWaypointIndices);

        assertConsistencyOfPathDetails(responsePath.getPathDetails());
        if (enableInstructions)
            assertConsistencyOfInstructions(responsePath.getInstructions(), responsePath.getPoints().size());
        return pointList;
    }

    public static void simplify(PointList pointList, List<Partition> partitions, RamerDouglasPeucker ramerDouglasPeucker) {
        new PathSimplification(pointList, partitions, ramerDouglasPeucker).simplify();
    }

    private PathSimplification(PointList pointList, List<Partition> partitions, RamerDouglasPeucker ramerDouglasPeucker) {
        this.pointList = pointList;
        this.partitions = partitions;
        this.ramerDouglasPeucker = ramerDouglasPeucker;
        numPartitions = this.partitions.size();
        currIntervalIndex = new int[numPartitions];
        currIntervalStart = new int[numPartitions];
        currIntervalEnd = new int[numPartitions];
        partitionFinished = new boolean[numPartitions];
        removedPointsInCurrInterval = new int[numPartitions];
        removedPointsInPrevIntervals = new int[numPartitions];
    }

    private void simplify() {
        if (pointList.size() <= 2) {
            pointList.makeImmutable();
            return;
        }

        // no partitions -> no constraints, just simplify the entire point list
        if (partitions.isEmpty()) {
            ramerDouglasPeucker.simplify(pointList, 0, pointList.size() - 1);
            pointList.makeImmutable();
            return;
        }

        // Ramer-Douglas-Peucker never removes the first/last point of a given interval, so as long as we only run it
        // on each interval we can be sure that the interval boundaries will remain in the point list.
        // Whenever we remove points from an interval we have to update the interval indices of all partitions.
        // For example if an interval goes from point 4 to 9 and we remove points 5 and 7 we have to update the interval
        // to [4,7].
        // The basic idea to do this is as follows: We iterate through the point list and whenever we hit an interval
        // end (q) in one of the partitions we run Ramer-Douglas-Peucker for the interval [p,q], where p is the point where
        // the last interval ended. We keep track of the number of removed points in the current and previous intervals
        // to be able to calculate the updated indices.

        // prepare for the first interval in each partition
        int intervalStart = 0;
        for (int i = 0; i < numPartitions; i++) {
            currIntervalEnd[i] = this.partitions.get(i).getIntervalLength(currIntervalIndex[i]);
        }

        // iterate the point list and simplify and update the intervals on the go
        for (int p = 0; p < pointList.size(); p++) {
            int removed = 0;
            // first we check if we hit the end of an interval for one of the partitions and run Ramer-Douglas-Peucker if we do
            for (int s = 0; s < numPartitions; s++) {
                if (partitionFinished[s]) {
                    continue;
                }
                if (p == currIntervalEnd[s]) {
                    // This is important for performance: we must not compress the point list after each call to
                    // simplify, otherwise a lot of data is copied, especially for long routes (e.g. many via nodes),
                    // see #1764. Note that since the point list does not get compressed here yet we have to keep track
                    // of the total number of removed points to calculate the new interval boundaries later
                    final boolean compress = false;
                    removed = ramerDouglasPeucker.simplify(pointList, intervalStart, currIntervalEnd[s], compress);
                    intervalStart = p;
                    break;
                }
            }

            // now we have (possibly) removed some points we need to update the current intervals in all partitions
            for (int s = 0; s < numPartitions; s++) {
                if (partitionFinished[s]) {
                    continue;
                }
                removedPointsInCurrInterval[s] += removed;
                // if the current interval of this partition ends at p, we update the interval boundaries. there is
                // just a special catch: there can be multiple consecutive intervals that end with p, because there
                // are intervals with a single point, for example p=3 and a partition=[0,3][3,3][3,3]
                boolean nextIntervalHasOnlyOnePoint;
                do {
                    if (p == currIntervalEnd[s]) {
                        nextIntervalHasOnlyOnePoint = updateInterval(p, s);
                    } else {
                        break;
                    }
                } while (nextIntervalHasOnlyOnePoint);
            }
        }

        // now we finally have to compress the pointList (actually remove the deleted points). note only after this
        // call the (now shifted) indices in path details and instructions are correct
        RamerDouglasPeucker.removeNaN(pointList);

        // Make sure that the instruction references are not broken
        pointList.makeImmutable();

        assertConsistencyOfIntervals();
    }

    /**
     * @param p point index
     * @param s partition index
     */
    private boolean updateInterval(int p, int s) {
        boolean nextIntervalHasOnlyOnePoint = false;
        // update interval boundaries
        final int updatedStart = currIntervalStart[s] - removedPointsInPrevIntervals[s];
        final int updatedEnd = currIntervalEnd[s] - removedPointsInPrevIntervals[s] - removedPointsInCurrInterval[s];
        this.partitions.get(s).setInterval(currIntervalIndex[s], updatedStart, updatedEnd);

        // update the removed point counters
        removedPointsInPrevIntervals[s] += removedPointsInCurrInterval[s];
        removedPointsInCurrInterval[s] = 0;

        // prepare for the next interval
        currIntervalIndex[s]++;
        currIntervalStart[s] = p;
        if (currIntervalIndex[s] >= this.partitions.get(s).size()) {
            partitionFinished[s] = true;
        } else {
            int length = this.partitions.get(s).getIntervalLength(currIntervalIndex[s]);
            currIntervalEnd[s] += length;
            // special case at via points etc.
            if (length == 0) {
                nextIntervalHasOnlyOnePoint = true;
            }
        }
        return nextIntervalHasOnlyOnePoint;
    }

    private void assertConsistencyOfIntervals() {
        final int expected = pointList.size() - 1;
        for (int i = 0; i < partitions.size(); i++) {
            final Partition partition = partitions.get(i);
            int count = 0;
            for (int j = 0; j < partition.size(); j++) {
                count += partition.getIntervalLength(j);
            }
            if (count != expected) {
                throw new IllegalStateException("Simplified intervals are inconsistent: " + count + " vs. " + expected + " for intervals with index: " + i);
            }
        }
    }

    private static void assertConsistencyOfPathDetails(Map<String, List<PathDetail>> pathDetails) {
        for (Map.Entry<String, List<PathDetail>> pdEntry : pathDetails.entrySet()) {
            List<PathDetail> list = pdEntry.getValue();
            if (list.isEmpty())
                continue;

            PathDetail prevPD = list.get(0);
            for (int i = 1; i < list.size(); i++) {
                if (prevPD.getLast() != list.get(i).getFirst())
                    throw new IllegalStateException("PathDetail list " + pdEntry.getKey() + " is inconsistent due to entries " + prevPD + " vs. " + list.get(i));

                prevPD = list.get(i);
            }
        }
    }

    private static void assertConsistencyOfInstructions(InstructionList instructions, int numPoints) {
        // the total length of the instruction intervals must match the length of the point list.
        // todo: it would be even better to make sure each instruction interval starts where the previous one ended, but
        // currently instructions do not offer this
        int expected = numPoints - 1;
        int count = 0;
        for (Instruction instruction : instructions) {
            count += instruction.getLength();
        }
        if (count != expected) {
            throw new IllegalArgumentException("inconsistent instructions, total interval length: " + count + " vs. point list length " + expected);
        }
    }

    /**
     * Represents a partition of a {@link PointList} into consecutive intervals, for example a list with six points
     * can be partitioned into something like [0,2],[2,2],[2,3][3,5]. Note that intervals with a single point are
     * allowed, but each interval must start where the previous one ended.
     */
    interface Partition {
        int size();

        // todo: it would be nice to be able to retrieve the actual start and end of each interval to make the
        // code here more straight-forward, but currently instructions only offer the length of the interval
        int getIntervalLength(int index);

        void setInterval(int index, int start, int end);
    }

    public static class Interval {
        public int start;
        public int end;

        public Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "[" + start + ", " + end + "]";
        }
    }
}
