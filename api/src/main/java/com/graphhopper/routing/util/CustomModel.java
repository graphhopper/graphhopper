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
package com.graphhopper.routing.util;

import com.graphhopper.util.Helper;

import java.util.HashMap;
import java.util.Map;

public class CustomModel {

    /**
     * Converting to seconds is not necessary but makes adding other penalties easier (e.g. turn
     * costs or traffic light costs etc)
     */
    public final static double SPEED_CONV = 3.6;
    // required
    private String base;
    // optional:
    private Double vehicleMaxSpeed, vehicleWeight, vehicleWidth, vehicleHeight, vehicleLength;
    private double maxPriority = 10;
    // max_priority and max_speed have a significant influence on the min_weight estimate, i.e. on quality vs. speed for A* with beeline
    // it also limits possibility to prefer a road
    private double distanceFactor = 1;
    private Map<String, Object> speedFactor = new HashMap<>();
    private Map<String, Object> averageSpeed = new HashMap<>();
    private Map<String, Object> priorityMap = new HashMap<>();
    private Map<String, Object> delayMap = new HashMap<>();

    public CustomModel() {
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getBase() {
        if (Helper.isEmpty(base))
            throw new IllegalArgumentException("No base specified");
        return base;
    }

    public void setVehicleWeight(Double vehicleWeight) {
        this.vehicleWeight = vehicleWeight;
    }

    public Double getVehicleWeight() {
        return vehicleWeight;
    }

    public void setVehicleHeight(Double vehicleHeight) {
        this.vehicleHeight = vehicleHeight;
    }

    public Double getVehicleHeight() {
        return vehicleHeight;
    }

    public void setVehicleLength(Double vehicleLength) {
        this.vehicleLength = vehicleLength;
    }

    public Double getVehicleLength() {
        return vehicleLength;
    }

    public void setVehicleWidth(Double vehicleWidth) {
        this.vehicleWidth = vehicleWidth;
    }

    public Double getVehicleWidth() {
        return vehicleWidth;
    }

    public void setVehicleMaxSpeed(Double vehicleMaxSpeed) {
        this.vehicleMaxSpeed = vehicleMaxSpeed;
    }

    public Double getVehicleMaxSpeed() {
        return vehicleMaxSpeed;
    }

    public void setMaxPriority(double maxPriority) {
        this.maxPriority = maxPriority;
    }

    public double getMaxPriority() {
        return maxPriority;
    }

    public void setDistanceFactor(double distanceFactor) {
        this.distanceFactor = distanceFactor;
    }

    public double getDistanceFactor() {
        return distanceFactor;
    }

    public Map<String, Object> getSpeedFactor() {
        return speedFactor;
    }

    public Map<String, Object> getAverageSpeed() {
        return averageSpeed;
    }

    public Map<String, Object> getPriority() {
        return priorityMap;
    }

    public Map<String, Object> getDelay() {
        return delayMap;
    }
}