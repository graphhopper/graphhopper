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

import com.graphhopper.util.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the details for a path
 *
 * @author Robin Boldt
 */
public class PathDetails {

    private final String name;
    private boolean isOpen = false;

    private Detail currentDetail = new Detail();
    private List<Detail> pathDetails = new ArrayList<>();

    public PathDetails(String name) {
        this.name = name;
    }

    /**
     * It is only possible to open one interval at a time.
     *
     * @param value
     */
    public void startInterval(Object value) {
        if (isOpen) {
            throw new IllegalStateException("Path details is already open with value: " + this.currentDetail.value + " trying to open a new one with value: " + value);
        }
        this.currentDetail = new Detail(value);
        isOpen = true;
    }

    /**
     * Ending intervals multiple times is safe, we only write the interval if it was opened.
     * <p>
     * Writes the interval to the pathDetails
     *
     * @param numberOfPoints Length of the PathDetail
     */
    public void endInterval(int numberOfPoints) {
        // We don't want PathDetails
        if (isOpen && numberOfPoints > 0) {
            this.currentDetail.numberOfPoints = numberOfPoints;
            pathDetails.add(this.currentDetail);
        }
        isOpen = false;
    }

    public List<Detail> getDetails() {
        return pathDetails;
    }

    public void merge(PathDetails pD) {
        if (!this.name.equals(pD.getName())) {
            throw new IllegalArgumentException("Only PathDetails with the same name can be merged");
        }
        List<Detail> otherDetails = pD.getDetails();
        this.pathDetails.addAll(otherDetails);
    }

    public String getName() {
        return this.name;
    }

    public Map<Object, List<int[]>> getPathDetailsMap(){
        Map<Object, List<int[]>> detailsMap = new HashMap<>();

        int pointer = 0;

        for (Detail detail: this.pathDetails) {
            List<int[]> detailIntervals;
            if(detailsMap.containsKey(detail.value)){
                 detailIntervals = detailsMap.get(detail.value);
            }else {
                detailIntervals = new ArrayList<>();
            }
            detailIntervals.add(new int[]{pointer, pointer+detail.numberOfPoints});
            detailsMap.put(detail.value, detailIntervals);
            pointer += detail.numberOfPoints;
        }

        return detailsMap;
    }

    public class Detail {
        public Object value;
        public int numberOfPoints;

        public Detail() {
        }

        public Detail(Object value) {
            this.value = value;
        }
    }
}
