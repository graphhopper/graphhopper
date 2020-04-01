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

import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.util.Parameters;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is used in combination with CustomProfileConfig.
 */
public class CustomModel {

    public static final String KEY = "custom_model";

    static double DEFAULT_D_I = 70;
    // optional:
    private Double maxSpeedFallback, vehicleWeight, vehicleWidth, vehicleHeight, vehicleLength;
    private Double headingPenalty = Parameters.Routing.DEFAULT_HEADING_PENALTY;
    // default value derived from the cost for time e.g. 25€/hour and for distance 0.5€/km, for trucks this is usually larger
    private double distanceInfluence = DEFAULT_D_I;
    private Map<String, Object> speedFactor = new HashMap<>();
    private Map<String, Object> maxSpeed = new HashMap<>();
    private Map<String, Object> priorityMap = new HashMap<>();
    private Map<String, JsonFeature> areas = new HashMap<>();

    public CustomModel() {
    }

    public CustomModel setVehicleWeight(Double vehicleWeight) {
        this.vehicleWeight = vehicleWeight;
        return this;
    }

    public Double getVehicleWeight() {
        return vehicleWeight;
    }

    public CustomModel setVehicleHeight(Double vehicleHeight) {
        this.vehicleHeight = vehicleHeight;
        return this;
    }

    public Double getVehicleHeight() {
        return vehicleHeight;
    }

    public CustomModel setVehicleLength(Double vehicleLength) {
        this.vehicleLength = vehicleLength;
        return this;
    }

    public Double getVehicleLength() {
        return vehicleLength;
    }

    public CustomModel setVehicleWidth(Double vehicleWidth) {
        this.vehicleWidth = vehicleWidth;
        return this;
    }

    public Double getVehicleWidth() {
        return vehicleWidth;
    }

    public Map<String, Object> getSpeedFactor() {
        return speedFactor;
    }

    public Map<String, Object> getMaxSpeed() {
        return maxSpeed;
    }

    public CustomModel setMaxSpeedFallback(Double maxSpeedFallback) {
        this.maxSpeedFallback = maxSpeedFallback;
        return this;
    }

    public Double getMaxSpeedFallback() {
        return maxSpeedFallback;
    }

    public Map<String, Object> getPriority() {
        return priorityMap;
    }

    public CustomModel setAreas(Map<String, JsonFeature> areas) {
        this.areas = areas;
        return this;
    }

    public Map<String, JsonFeature> getAreas() {
        return areas;
    }

    public CustomModel setDistanceInfluence(double distanceFactor) {
        this.distanceInfluence = distanceFactor;
        return this;
    }

    public double getDistanceInfluence() {
        return distanceInfluence;
    }

    public void setHeadingPenalty(double headingPenalty) {
        this.headingPenalty = headingPenalty;
    }

    public double getHeadingPenalty() {
        return headingPenalty;
    }

    @Override
    public String toString() {
        return "CustomModel{" +
                ", maxSpeedFallback=" + maxSpeedFallback +
                ", vehicleWeight=" + vehicleWeight +
                ", vehicleWidth=" + vehicleWidth +
                ", vehicleHeight=" + vehicleHeight +
                ", vehicleLength=" + vehicleLength +
                ", distanceInfluence=" + distanceInfluence +
                ", speedFactor=" + speedFactor +
                ", maxSpeed=" + maxSpeed +
                ", priorityMap=" + priorityMap +
                ", #areas=" + areas.size() +
                '}';
    }

    /**
     * This method assumes that this object is a per-request object so we can apply the changes and keep baseCustomModel
     * unchanged.
     */
    public CustomModel merge(CustomModel baseCustomModel) {
        if (vehicleWeight == null)
            vehicleWeight = baseCustomModel.getVehicleWeight();
        else if (baseCustomModel.getVehicleWeight() != null & vehicleWeight < baseCustomModel.getVehicleWeight())
            throw new IllegalArgumentException("CustomModel in query can only use vehicle_weight bigger or equal to " + vehicleWeight);

        if (vehicleHeight == null)
            vehicleHeight = baseCustomModel.getVehicleHeight();
        else if (baseCustomModel.getVehicleHeight() != null && vehicleHeight < baseCustomModel.getVehicleHeight())
            throw new IllegalArgumentException("CustomModel in query can only use vehicle_height bigger or equal to " + vehicleHeight);
        if (vehicleLength == null)
            vehicleLength = baseCustomModel.getVehicleLength();
        else if (baseCustomModel.getVehicleLength() != null && vehicleLength < baseCustomModel.getVehicleLength())
            throw new IllegalArgumentException("CustomModel in query can only use vehicle_length bigger or equal to " + vehicleLength);
        if (vehicleWidth == null)
            vehicleWidth = baseCustomModel.getVehicleWidth();
        else if (baseCustomModel.getVehicleWidth() != null && vehicleWidth < baseCustomModel.getVehicleWidth())
            throw new IllegalArgumentException("CustomModel in query can only use vehicle_width bigger or equal to " + vehicleWidth);

        if (maxSpeedFallback == null)
            maxSpeedFallback = baseCustomModel.getMaxSpeedFallback();
        else if (baseCustomModel.getMaxSpeedFallback() != null && maxSpeedFallback < baseCustomModel.getMaxSpeedFallback())
            throw new IllegalArgumentException("CustomModel in query can only use max_speed_fallback bigger or equal to " + maxSpeedFallback);
        if (Math.abs(distanceInfluence - CustomModel.DEFAULT_D_I) < 0.01)
            distanceInfluence = baseCustomModel.getDistanceInfluence();
        else if (distanceInfluence < baseCustomModel.getDistanceInfluence())
            throw new IllegalArgumentException("CustomModel in query can only use distance_influence bigger or equal to " + distanceInfluence);

        // example
        // max_speed: { road_class: { secondary : 10 } }
        for (Map.Entry<String, Object> baseEntry : baseCustomModel.getMaxSpeed().entrySet()) {
            Object value = maxSpeed.get(baseEntry.getKey());
            if (value instanceof Map && baseEntry.getValue() instanceof Map)
                applyChange((Map) baseEntry.getValue(), (Map) value);
        }
        for (Map.Entry<String, Object> baseEntry : baseCustomModel.getSpeedFactor().entrySet()) {
            Object value = speedFactor.get(baseEntry.getKey());
            if (value instanceof Map && baseEntry.getValue() instanceof Map)
                applyChange((Map) baseEntry.getValue(), (Map) value);
        }
        for (Map.Entry<String, Object> baseEntry : baseCustomModel.getPriority().entrySet()) {
            Object value = priorityMap.get(baseEntry.getKey());
            if (value instanceof Map && baseEntry.getValue() instanceof Map)
                applyChange((Map) baseEntry.getValue(), (Map) value);
        }
        for (Map.Entry<String, JsonFeature> entry : baseCustomModel.getAreas().entrySet()) {
            JsonFeature feature = areas.get(entry.getKey());
            if (feature == null)
                areas.put(entry.getKey(), entry.getValue());
            else
                throw new IllegalArgumentException("area " + entry.getKey() + " already exists");
        }

        return this;
    }

    private void applyChange(Map<Object, Object> base, Map<Object, Object> change) {
        for (Map.Entry baseEntry : base.entrySet()) {
            Object value = change.get(baseEntry.getKey());
            if (value == null) {
                change.put(baseEntry.getKey(), baseEntry.getValue());
            } else if (baseEntry.getValue() instanceof Number && value instanceof Number) {
                change.put(baseEntry.getKey(), ((Number) baseEntry.getValue()).doubleValue() * ((Number) value).doubleValue());
            } else {
                throw new IllegalArgumentException("Cannot merge value " + baseEntry.getValue() + " for key " + baseEntry.getKey());
            }
        }
    }
}