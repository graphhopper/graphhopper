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

    private PointList pointList;
    private Map<String, List<PathDetail>> pathDetails;
    private List<List> pathSegmentations;
    private DouglasPeucker douglasPeucker;

    public PathSimplification(PathWrapper pathWrapper, DouglasPeucker douglasPeucker, boolean enableInstructions) {
        this.pointList = pathWrapper.getPoints();
        pathSegmentations = new ArrayList<>();
        if (enableInstructions)
            pathSegmentations.add(pathWrapper.getInstructions());

        this.pathDetails = pathWrapper.getPathDetails();
        for (String name : pathDetails.keySet()) {
            List<PathDetail> pathDetailList = pathDetails.get(name);
            // If the pointList only contains one point, PathDetails have to be empty because 1 point => 0 edges
            if (pathDetailList.isEmpty() && pointList.size() > 1)
                throw new IllegalStateException("PathDetails " + name + " must not be empty");

            pathSegmentations.add(pathDetailList);
        }
        this.douglasPeucker = douglasPeucker;
    }

    public PointList simplify() {
        if (pointList.size() <= 2) {
            pointList.makeImmutable();
            return pointList;
        }

        // no constraints
        if (pathSegmentations.isEmpty()) {
            douglasPeucker.simplify(pointList, 0, pointList.size() - 1);
            pointList.makeImmutable();
            return pointList;
        }

        // When there are instructions or path details we have to make sure certain points (the first and last ones
        // of each instruction/path detail interval) do not get removed by Douglas-Peucker. Douglas-Peucker never
        // removes the first/last point of a given interval, so we call it for each interval separately to make sure
        // none of these points get deleted.
        // We have to make sure to update the point indices in path details and instructions as well, for example if
        // a path detail goes from point 4 to 9 and we remove points 5 and 7 we have to update the interval to [4,7].

        // The basic idea is as follows: the instructions/path details we iterate through the point list and whenever we hit an interval end (q) in
        // one of the path segmentations we run Douglas-Peucker for the interval [p,q], where p is the point where the
        // last interval ended. we need to keep track of the interval starts/ends and the interval indices in the different
        // path segmentations.
        final int pathSegmentations = this.pathSegmentations.size();
        final int[] currIntervalIndex = new int[pathSegmentations];
        final int[] currIntervalStart = new int[pathSegmentations];
        final int[] currIntervalEnd = new int[pathSegmentations];
        final boolean[] segmentsFinished = new boolean[pathSegmentations];
        int intervalStart = 0;
        for (int i = 0; i < pathSegmentations; i++) {
            currIntervalEnd[i] = getLength(this.pathSegmentations.get(i), currIntervalIndex[i]);
        }

        // to correctly update the interval indices in path details and instructions we need to keep track of how
        // many points were removed by Douglas-Peucker in the current and previous intervals
        final int[] removedPointsInCurrInterval = new int[pathSegmentations];
        final int[] removedPointsInPrevIntervals = new int[pathSegmentations];

        for (int p = 0; p < pointList.size(); p++) {
            int removed = 0;
            // first we check if we hit an interval end for one of the segmenations and run douglas peucker if so
            for (int s = 0; s < pathSegmentations; s++) {
                if (segmentsFinished[s]) {
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
            // to keep track of the number of removed points (for all segmentations)
            for (int s = 0; s < pathSegmentations; s++) {
                if (segmentsFinished[s]) {
                    continue;
                }
                removedPointsInCurrInterval[s] += removed;
                // need to use >= instead of == here, because there can be empty intervals (e.g. at via nodes)
                if (p >= currIntervalEnd[s]) {
                    // we hit an interval end
                    // update the point indices for path details and instructions
                    final int updatedStart = currIntervalStart[s] - removedPointsInPrevIntervals[s];
                    final int updatedEnd = currIntervalEnd[s] - removedPointsInPrevIntervals[s] - removedPointsInCurrInterval[s];
                    reduceLength(this.pathSegmentations.get(s), currIntervalIndex[s], updatedStart, updatedEnd);

                    // prepare for the next interval
                    currIntervalIndex[s]++;
                    currIntervalStart[s] = p;
                    if (currIntervalIndex[s] >= this.pathSegmentations.get(s).size()) {
                        segmentsFinished[s] = true;
                    } else {
                        currIntervalEnd[s] += getLength(this.pathSegmentations.get(s), currIntervalIndex[s]);
                    }

                    // update the removed point counters
                    removedPointsInPrevIntervals[s] += removedPointsInCurrInterval[s];
                    removedPointsInCurrInterval[s] = 0;
                }
            }
        }

        // now we finally have to compress the pointList (actually remove the deleted points). note only after this
        // call the (now shifted) indices in path details and instructions are correct
        pointList.compress();

        assert assertConsistencyOfPathDetails();
        // Make sure that the instruction references are not broken
        pointList.makeImmutable();
        return pointList;
    }

    private boolean assertConsistencyOfPathDetails() {
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
        return true;
    }

    private int getLength(Object o, int index) {
        if (o instanceof InstructionList) {
            return ((InstructionList) o).get(index).getLength();
        }
        if (o instanceof List) {
            return ((List<PathDetail>) o).get(index).getLength();
        }
        throw new IllegalStateException("We can only handle PathDetails or InstructionList in PathSimplification");
    }

    private void reduceLength(Object o, int index, int startIndex, int newEndIndex) {
        if (o instanceof InstructionList) {
            ((InstructionList) o).get(index).setPoints(this.pointList.shallowCopy(startIndex, newEndIndex, false));
        } else if (o instanceof List) {
            PathDetail pd = ((List<PathDetail>) o).get(index);
            pd.setFirst(startIndex);
            pd.setLast(newEndIndex);
        } else {
            throw new IllegalStateException("We can only handle List<PathDetail> or InstructionList");
        }
    }

}
