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

import com.graphhopper.PathWrapper;
import com.graphhopper.util.details.PathDetail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class simplifies the path, using {@link DouglasPeucker} and by considering the intervals
 * specified by <code>listsToSimplify</code>. We have to reference the points specified by the objects
 * in <code>listsToSimplify</code>. Therefore, we can only simplify in between these intervals, without
 * simplifying over one of the points, as it might get lost during the simplification
 * and we could not reference this point anymore.
 *
 * @author Robin Boldt
 */
public class PathSimplification {

    private final PointList pointList;
    private final DouglasPeucker douglasPeucker;
    private final List<Partition> partitions;

    public static PointList simplify(PathWrapper pathWrapper, DouglasPeucker douglasPeucker, boolean enableInstructions) {
        final PointList pointList = pathWrapper.getPoints();
        List<Partition> partitions = new ArrayList<>();
        if (enableInstructions) {
            final InstructionList instructions = pathWrapper.getInstructions();
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
                    instructions.get(index).setPoints(pointList.shallowCopy(start, end, false));
                }
            });
        }

        for (final Map.Entry<String, List<PathDetail>> entry : pathWrapper.getPathDetails().entrySet()) {
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

        new PathSimplification(pathWrapper.getPoints(), partitions, douglasPeucker).simplify();
        assertConsistencyOfPathDetails(pathWrapper.getPathDetails());
        assertConsistencyOfInstructions(pathWrapper.getInstructions(), pathWrapper.getPoints().size());
        return pointList;
    }

    PathSimplification(PointList pointList, List<Partition> partitions, DouglasPeucker douglasPeucker) {
        this.pointList = pointList;
        this.partitions = partitions;
        this.douglasPeucker = douglasPeucker;
    }

    void simplify() {
        if (pointList.size() <= 2) {
            pointList.makeImmutable();
            return;
        }

        // no constraints
        if (partitions.isEmpty()) {
            douglasPeucker.simplify(pointList, 0, pointList.size() - 1);
            pointList.makeImmutable();
            return;
        }

        // When there are instructions or path details we have to make sure certain points (the first and last ones
        // of each instruction/path detail interval) do not get removed by Douglas-Peucker. Douglas-Peucker never
        // removes the first/last point of a given interval, so we call it for each interval separately to make sure
        // none of these points get deleted.
        // We have to make sure to update the point indices in path details and instructions as well, for example if
        // a path detail goes from point 4 to 9 and we remove points 5 and 7 we have to update the interval to [4,7].

        // The basic idea is as follows: we iterate through the point list and whenever we hit an interval end (q) in
        // one of the partitions we run Douglas-Peucker for the interval [p,q], where p is the point where the
        // last interval ended. we need to keep track of the interval starts/ends and the interval indices in the different
        // partitions
        final int numPartitions = this.partitions.size();
        final int[] currIntervalIndex = new int[numPartitions];
        final int[] currIntervalStart = new int[numPartitions];
        final int[] currIntervalEnd = new int[numPartitions];
        final boolean[] partitionFinished = new boolean[numPartitions];
        int intervalStart = 0;
        for (int i = 0; i < numPartitions; i++) {
            currIntervalEnd[i] = this.partitions.get(i).getIntervalLength(currIntervalIndex[i]);
        }

        // to correctly update the interval indices in path details and instructions we need to keep track of how
        // many points were removed by Douglas-Peucker in the current and previous intervals
        final int[] removedPointsInCurrInterval = new int[numPartitions];
        final int[] removedPointsInPrevIntervals = new int[numPartitions];

        for (int p = 0; p < pointList.size(); p++) {
            int removed = 0;
            // first we check if we hit an interval end for one of the partitions and run douglas peucker if so
            for (int s = 0; s < numPartitions; s++) {
                if (partitionFinished[s]) {
                    continue;
                }
                if (p == currIntervalEnd[s]) {
                    // This is important for performance: we must not compress the point list after each call to
                    // simplify, otherwise a lot of data is copied, especially for long routes (e.g. many via nodes),
                    // see #1764. Note that since the point list does not get compressed we have to keep track of the
                    // total number of removed points to shift the indices into pointList correctly.
                    final boolean compress = false;
                    removed = douglasPeucker.simplify(pointList, intervalStart, currIntervalEnd[s], compress);
                    intervalStart = p;
                    break;
                }
            }
            // in case we hit an interval end we have to updated our indices for the next interval, also we need
            // to keep track of the number of removed points (for all partitions)
            for (int s = 0; s < numPartitions; s++) {
                if (partitionFinished[s]) {
                    continue;
                }
                removedPointsInCurrInterval[s] += removed;
                if (p == currIntervalEnd[s]) {
                    // we hit an interval end
                    // update the point indices for path details and instructions
                    final int updatedStart = currIntervalStart[s] - removedPointsInPrevIntervals[s];
                    final int updatedEnd = currIntervalEnd[s] - removedPointsInPrevIntervals[s] - removedPointsInCurrInterval[s];
                    this.partitions.get(s).setInterval(currIntervalIndex[s], updatedStart, updatedEnd);

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
                            p--;
                        }
                    }

                    // update the removed point counters
                    removedPointsInPrevIntervals[s] += removedPointsInCurrInterval[s];
                    removedPointsInCurrInterval[s] = 0;
                }
            }
        }

        // now we finally have to compress the pointList (actually remove the deleted points). note only after this
        // call the (now shifted) indices in path details and instructions are correct
        DouglasPeucker.removeNaN(pointList);

        // Make sure that the instruction references are not broken
        pointList.makeImmutable();

        assertConsistencyOfIntervals();
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
        int expected = numPoints - 1;
        int count = 0;
        for (Instruction instruction : instructions) {
            count += instruction.getLength();
        }
        if (count != expected) {
            throw new IllegalArgumentException("inconsistent instructions: " + count + " vs. " + expected);
        }
    }

    /**
     * Represents a partition of a {@link PointList} into consecutive intervals, for example a list with six points
     * can be partitioned into something like [0,2],[2,2],[2,3][3,5]. Note that intervals with a single point are
     * allowed, but each interval must start where the previous one ended.
     */
    interface Partition {
        int size();

        int getIntervalLength(int index);

        void setInterval(int index, int start, int end);
    }

}
