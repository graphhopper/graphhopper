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
 * specified by <code>toSimplify</code>. We have to reference the points specified by the objects
 * in <code>toSimplify</code>. Therefore, we can only simplify in between these intervals, without
 * simplifying over one the points, as one of the points might be lost during the simplification
 * and we could not reference this point anymore.
 *
 * @author Robin Boldt
 */
public class PathSimplification {

    private PointList pointList;
    private Map<String, List<PathDetail>> pathDetails;
    private List<List> toSimplify;
    private DouglasPeucker douglasPeucker;

    public PathSimplification(PathWrapper pathWrapper, DouglasPeucker douglasPeucker, boolean enableInstructions) {
        this.pointList = pathWrapper.getPoints();
        pathDetails = pathWrapper.getPathDetails();
        toSimplify = new ArrayList<>();
        if (enableInstructions)
            toSimplify.add(pathWrapper.getInstructions());
        for (String name : pathDetails.keySet()) {
            toSimplify.add(pathDetails.get(name));
        }
        this.douglasPeucker = douglasPeucker;
    }

    public PointList simplify() {
        if (toSimplify.isEmpty() || pointList.isEmpty())
            return pointList;

        // The offset of already included points
        int[] offset = new int[toSimplify.size()];
        int[] endIntervals = new int[toSimplify.size()];
        // All start at 0
        int[] startIntervals = new int[toSimplify.size()];

        while (true) {
            boolean simplificationPossible = true;
            int nonConflictingStart = 0;
            int nonConflictingEnd = Integer.MAX_VALUE;
            int toSimplifyIndex = -1;
            int toShiftIndex = -1;

            endIntervals = calculateEndIntervals(endIntervals, startIntervals, offset, toSimplify);

            // Find the intervals to run a simplification, if possible, and where to shift
            for (int i = 0; i < toSimplify.size(); i++) {
                if (startIntervals[i] >= nonConflictingEnd || endIntervals[i] <= nonConflictingStart) {
                    simplificationPossible = false;
                }
                if (startIntervals[i] > nonConflictingStart) {
                    toSimplifyIndex = -1;
                    nonConflictingStart = startIntervals[i];
                }
                if (endIntervals[i] < nonConflictingEnd) {
                    toSimplifyIndex = -1;
                    nonConflictingEnd = endIntervals[i];
                    // Remember the lowest endInterval
                    toShiftIndex = i;
                }
                if (startIntervals[i] >= nonConflictingStart && endIntervals[i] <= nonConflictingEnd) {
                    toSimplifyIndex = i;
                }
            }

            if (toSimplifyIndex >= 0 && simplificationPossible) {
                // Only simplify if there is more than one point
                if (nonConflictingEnd - nonConflictingStart > 1) {
                    int removed = douglasPeucker.simplify(pointList, nonConflictingStart, nonConflictingEnd);
                    if (removed > 0) {
                        for (int i = 0; i < toSimplify.size(); i++) {
                            reduceLength(toSimplify.get(i), offset[i], startIntervals[i], endIntervals[i] - removed);
                        }
                    }
                }
            }

            if (toShiftIndex < 0)
                throw new IllegalStateException("toShiftIndex cannot be negative");

            int length = getLength(toSimplify.get(toShiftIndex), offset[toShiftIndex]);
            startIntervals[toShiftIndex] += length;
            offset[toShiftIndex]++;
            if (offset[toShiftIndex] >= toSimplify.get(toShiftIndex).size())
                break;
        }
        return pointList;
    }

    private int getLength(Object o, int index) {
        if (o instanceof InstructionList) {
            // we do not store the last point of an instruction
            int size = ((InstructionList) o).get(index).getPoints().size();
            if (size == 0)
                throw new IllegalStateException("PointList of instruction should not be empty " + o);
            // the last point of instruction (i.e. first point of next instruction) is not included
            return size;
        }
        if (o instanceof List) {
            return ((List<PathDetail>) o).get(index).getLength();
        }
        throw new IllegalStateException("We can only handle PathDetails or InstructionList in PathSimplification");
    }

    private void reduceLength(Object o, int index, int startIndex, int newEndIndex) {
        if (o instanceof InstructionList) {
            ((InstructionList) o).get(index).setPoints(this.pointList.copy(startIndex, newEndIndex));
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
