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
    private List<List> listsToSimplify;
    private DouglasPeucker douglasPeucker;

    public PathSimplification(PathWrapper pathWrapper, DouglasPeucker douglasPeucker, boolean enableInstructions) {
        this.pointList = pathWrapper.getPoints();
        listsToSimplify = new ArrayList<>();
        if (enableInstructions)
            listsToSimplify.add(pathWrapper.getInstructions());

        this.pathDetails = pathWrapper.getPathDetails();
        for (String name : pathDetails.keySet()) {
            List<PathDetail> pathDetailList = pathDetails.get(name);
            // If the pointList only contains one point, PathDetails have to be empty because 1 point => 0 edges
            if (pathDetailList.isEmpty() && pointList.size() > 1)
                throw new IllegalStateException("PathDetails " + name + " must not be empty");

            listsToSimplify.add(pathDetailList);
        }
        this.douglasPeucker = douglasPeucker;
    }

    public PointList simplify() {
        if (pointList.size() <= 2) {
            pointList.makeImmutable();
            return pointList;
        }

        // no constraints
        if (listsToSimplify.isEmpty()) {
            douglasPeucker.simplify(pointList, 0, pointList.size() - 1);
            pointList.makeImmutable();
            return pointList;
        }

        // The offset of already included points
        int[] offsets = new int[listsToSimplify.size()];
        int[] endIntervals = new int[listsToSimplify.size()];
        // All start at 0
        int[] startIntervals = new int[listsToSimplify.size()];

        while (true) {
            boolean simplificationPossible = true;
            int nonConflictingStart = 0;
            int nonConflictingEnd = Integer.MAX_VALUE;
            int listIndexToSimplify = -1;
            int listIndexToShift = -1;

            endIntervals = calculateEndIntervals(endIntervals, startIntervals, offsets, listsToSimplify);

            // Find the intervals to run a simplification, if possible, and where to shift
            for (int i = 0; i < listsToSimplify.size(); i++) {
                if (startIntervals[i] >= nonConflictingEnd || endIntervals[i] <= nonConflictingStart) {
                    simplificationPossible = false;
                }
                if (startIntervals[i] > nonConflictingStart) {
                    listIndexToSimplify = -1;
                    nonConflictingStart = startIntervals[i];
                }
                if (endIntervals[i] < nonConflictingEnd) {
                    listIndexToSimplify = -1;
                    nonConflictingEnd = endIntervals[i];
                    // Remember the lowest endInterval
                    listIndexToShift = i;
                }
                if (startIntervals[i] >= nonConflictingStart && endIntervals[i] <= nonConflictingEnd) {
                    listIndexToSimplify = i;
                }
            }

            if (listIndexToSimplify >= 0 && simplificationPossible) {
                // Only simplify if there is more than one point
                if (nonConflictingEnd - nonConflictingStart > 1) {
                    int removed = douglasPeucker.simplify(pointList, nonConflictingStart, nonConflictingEnd);
                    if (removed > 0) {
                        for (int i = 0; i < listsToSimplify.size(); i++) {
                            List pathDetails = listsToSimplify.get(i);
                            reduceLength(pathDetails, offsets[i], startIntervals[i], endIntervals[i] - removed);
                            // This is not needed for Instructions, as they don't contain references, but PointLists
                            if (pathDetails.get(0) instanceof PathDetail) {
                                for (int j = offsets[i] + 1; j < pathDetails.size(); j++) {
                                    PathDetail pd = (PathDetail) pathDetails.get(j);
                                    reduceLength(pathDetails, j, pd.getFirst() - removed, pd.getLast() - removed);
                                }
                            }
                        }
                    }
                }
            }

            if (listIndexToShift < 0)
                throw new IllegalStateException("toShiftIndex cannot be negative");

            int length = getLength(listsToSimplify.get(listIndexToShift), offsets[listIndexToShift]);
            startIntervals[listIndexToShift] += length;
            offsets[listIndexToShift]++;
            if (offsets[listIndexToShift] >= listsToSimplify.get(listIndexToShift).size())
                break;
        }

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
        // Make sure that the instruction references are not broken
        pointList.makeImmutable();
        return pointList;
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

    private int[] calculateEndIntervals(int[] endIntervals, int[] startIntervals, int[] offset, List<List> toSimplify) {
        for (int i = 0; i < toSimplify.size(); i++) {
            endIntervals[i] = startIntervals[i] + getLength(toSimplify.get(i), offset[i]);
        }
        return endIntervals;
    }

}
