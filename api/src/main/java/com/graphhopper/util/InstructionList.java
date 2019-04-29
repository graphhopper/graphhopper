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

import java.util.*;

/**
 * List of instructions.
 */
public class InstructionList extends AbstractList<Instruction> {

    private final List<Instruction> instructions;
    private final Translation tr;

    public InstructionList(Translation tr) {
        this(10, tr);
    }

    public InstructionList(int cap, Translation tr) {
        instructions = new ArrayList<>(cap);
        this.tr = tr;
    }

    @Override
    public int size() {
        return instructions.size();
    }

    @Override
    public Instruction get(int index) {
        return instructions.get(index);
    }

    @Override
    public Instruction set(int index, Instruction element) {
        return instructions.set(index, element);
    }

    @Override
    public void add(int index, Instruction element) {
        instructions.add(index, element);
    }

    @Override
    public Instruction remove(int index) {
        return instructions.remove(index);
    }

    /**
     * This method is useful for navigation devices to find the next instruction for the specified
     * coordinate (e.g. the current position).
     * <p>
     *
     * @param maxDistance the maximum acceptable distance to the instruction (in meter)
     * @return the next Instruction or null if too far away.
     */
    public Instruction find(double lat, double lon, double maxDistance) {
        // handle special cases
        if (size() == 0) {
            return null;
        }
        PointList points = get(0).getPoints();
        double prevLat = points.getLatitude(0);
        double prevLon = points.getLongitude(0);
        DistanceCalc distCalc = Helper.DIST_EARTH;
        double foundMinDistance = distCalc.calcNormalizedDist(lat, lon, prevLat, prevLon);
        int foundInstruction = 0;

        // Search the closest edge to the query point
        if (size() > 1) {
            for (int instructionIndex = 0; instructionIndex < size(); instructionIndex++) {
                points = get(instructionIndex).getPoints();
                for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                    double currLat = points.getLatitude(pointIndex);
                    double currLon = points.getLongitude(pointIndex);

                    if (!(instructionIndex == 0 && pointIndex == 0)) {
                        // calculate the distance from the point to the edge
                        double distance;
                        int index = instructionIndex;
                        if (distCalc.validEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon)) {
                            distance = distCalc.calcNormalizedEdgeDistance(lat, lon, currLat, currLon, prevLat, prevLon);
                            if (pointIndex > 0)
                                index++;
                        } else {
                            distance = distCalc.calcNormalizedDist(lat, lon, currLat, currLon);
                            if (pointIndex > 0)
                                index++;
                        }

                        if (distance < foundMinDistance) {
                            foundMinDistance = distance;
                            foundInstruction = index;
                        }
                    }
                    prevLat = currLat;
                    prevLon = currLon;
                }
            }
        }

        if (distCalc.calcDenormalizedDist(foundMinDistance) > maxDistance)
            return null;

        // special case finish condition
        if (foundInstruction == size())
            foundInstruction--;

        return get(foundInstruction);
    }

    public Translation getTr() {
        return tr;
    }

}
