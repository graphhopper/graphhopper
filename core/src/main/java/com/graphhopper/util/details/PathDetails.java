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
package com.graphhopper.util.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the details for one or multiple path objects.
 *
 * @author Robin Boldt
 */
public class PathDetails {

    private final String name;
    private List<PathDetail> pathDetails;

    public PathDetails(String name, List<PathDetail> pathDetails) {
        this.name = name;
        this.pathDetails = pathDetails;
    }

    @JsonIgnore
    public List<PathDetail> getDetails() {
        return this.pathDetails;
    }

    /**
     * Merges the provided <code>pathDetails</code> into this object. Only pathDetails with the same
     * name can be merged.
     * <p>
     * This method makes sure that PathDetails around waypoints are merged correctly, by adding an
     * additional point for the ViaInstruction. See #1091 and the misplaced PathDetails after waypoints.
     *
     * @param pathDetails The PathDetails to be merged into this object.
     */
    public void merge(PathDetails pathDetails) {
        if (!this.name.equals(pathDetails.getName())) {
            throw new IllegalArgumentException("Only PathDetails with the same name can be merged");
        }
        List<PathDetail> otherDetails = pathDetails.getDetails();

        // Make sure that PathDetails are merged correctly at waypoints
        if (!this.pathDetails.isEmpty() && !otherDetails.isEmpty()) {
            PathDetail lastDetail = this.pathDetails.get(this.pathDetails.size() - 1);
            // Add Via Point
            lastDetail.numberOfPoints++;
            if (lastDetail.value.equals(otherDetails.get(0).value)) {
                lastDetail.numberOfPoints += otherDetails.get(0).numberOfPoints;
                otherDetails.remove(0);
            }
        }

        this.pathDetails.addAll(otherDetails);
    }

    public String getName() {
        return this.name;
    }

    /**
     * This method is used to serialize the PathDetails into JSON using jackson.
     */
    @JsonProperty("details")
    public Map<Object, List<int[]>> getPathDetailsMap() {
        Map<Object, List<int[]>> detailsMap = new HashMap<>();

        int pointer = 0;

        for (PathDetail detail : this.pathDetails) {
            List<int[]> detailIntervals;
            if (detailsMap.containsKey(detail.value)) {
                detailIntervals = detailsMap.get(detail.value);
            } else {
                detailIntervals = new ArrayList<>();
                detailsMap.put(detail.value, detailIntervals);
            }
            detailIntervals.add(new int[]{pointer, pointer + detail.numberOfPoints});
            pointer += detail.numberOfPoints;
        }

        return detailsMap;
    }

}