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
import com.graphhopper.util.details.PathDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class simplifies the path.
 * <p>
 * The tricky part about this is that if we have Instructions and PathDetails, we can only simplify the parts of the
 * route as we need to keep the reference points in the points array. We are looking for non-conflicting Instructions or
 * PathDetails and simplify them.
 * <p>
 *
 * @author Robin Boldt
 */
public class PathSimplification {

    private PointList pointList;
    private Map<String, PathDetails> pathDetails;
    private List<Object> toSimplify;
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

        if (toSimplify.isEmpty() || pointList.isEmpty()) {
            return pointList;
        }

        boolean endReached = false;

        // The index of each toSimplify
        int[] index = new int[toSimplify.size()];
        // The offset of already included points
        int[] offset = new int[toSimplify.size()];
        int[] endIntervals = new int[toSimplify.size()];
        // All start at 0
        int[] startIntervals = new int[toSimplify.size()];

        int nonConflictingStart;
        int nonConflictingEnd;
        int toSimplifyIndex;
        int toShiftIndex;

        boolean simplificationPossible;

        while (!endReached) {
            endIntervals = calculateEndIntervals(endIntervals, startIntervals, offset, toSimplify);

            simplificationPossible = true;
            nonConflictingStart = 0;
            nonConflictingEnd = Integer.MAX_VALUE;
            toSimplifyIndex = -1;
            toShiftIndex = -1;

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
                if(nonConflictingEnd - nonConflictingStart > 1){
                    // Simplify
                    int removed = douglasPeucker.simplify(pointList, nonConflictingStart, nonConflictingEnd -1 );
                    if (removed > 0) {
                        for (int i = 0; i < toSimplify.size(); i++) {
                            reduceNumberOfPoints(toSimplify.get(i), offset[i], removed, startIntervals[i], endIntervals[i] - removed);
                        }
                    }
                }
            }

            if (toShiftIndex < 0) {
                throw new IllegalStateException("Shift Index cannot be below 0");
            }
            int length = getNumberOfPoints(toSimplify.get(toShiftIndex), offset[toShiftIndex]);
            startIntervals[toShiftIndex] += length;
            offset[toShiftIndex]++;
            if (offset[toShiftIndex] >= getLength(toSimplify.get(toShiftIndex))) {
                endReached = true;
            }
        }
        return pointList;
    }

    private int getLengthIL(InstructionList il) {
        return il.size();
    }

    private int getLengthPD(PathDetails pd) {
        return pd.getDetails().size();
    }

    private int getLength(Object o) {
        if (o instanceof InstructionList) {
            return getLengthIL((InstructionList) o);
        }
        if (o instanceof PathDetails) {
            return getLengthPD((PathDetails) o);
        }
        throw new IllegalStateException("We can only handle PathDetails or InstructionList in PathSimplification");
    }

    private int getNumberOfPointsIL(InstructionList il, int index) {
        return il.get(index).getPoints().size();
    }

    private int getNumberOfPointsPD(PathDetails pd, int index) {
        return pd.getDetails().get(index).numberOfPoints;
    }

    private int getNumberOfPoints(Object o, int index) {
        if (o instanceof InstructionList) {
            return getNumberOfPointsIL((InstructionList) o, index);
        }
        if (o instanceof PathDetails) {
            return getNumberOfPointsPD((PathDetails) o, index);
        }
        throw new IllegalStateException("We can only handle PathDetails or InstructionList in PathSimplification");
    }

    private void reduceNumberOfPointsIl(InstructionList il, int index, int startIndex, int newEndIndex) {
        il.get(index).setPoints(this.pointList.copy(startIndex, newEndIndex));

    }

    private void reduceNumberOfPointsPD(PathDetails pd, int index, int reduceBy) {
        pd.getDetails().get(index).numberOfPoints -= reduceBy;
    }

    private void reduceNumberOfPoints(Object o, int index, int reduceBy, int startIndex, int newEndIndex) {
        if (o instanceof InstructionList) {
            reduceNumberOfPointsIl((InstructionList) o, index, startIndex, newEndIndex);
        } else if (o instanceof PathDetails) {
            reduceNumberOfPointsPD((PathDetails) o, index, reduceBy);
        } else {
            throw new IllegalStateException("We can only handle PathDetails or InstructionList in PathSimplification");
        }
    }

    private int[] calculateEndIntervals(int[] endIntervals, int[] startIntervals, int[] offset, List<Object> toSimplify) {
        for (int i = 0; i < toSimplify.size(); i++) {
            endIntervals[i] = startIntervals[i] + getNumberOfPoints(toSimplify.get(i), offset[i]);
        }
        return endIntervals;
    }

}
