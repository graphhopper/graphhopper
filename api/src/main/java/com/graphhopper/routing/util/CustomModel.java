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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used in combination with CustomProfileConfig.
 */
public class CustomModel {

    public static final String KEY = "custom_model";

    static double DEFAULT_D_I = 70;
    // optional:
    private Double maxSpeedFallback;
    private Double headingPenalty = Parameters.Routing.DEFAULT_HEADING_PENALTY;
    // default value derived from the cost for time e.g. 25€/hour and for distance 0.5€/km, for trucks this is usually larger
    private double distanceInfluence = DEFAULT_D_I;
    private Map<String, Object> speedFactorMap = new HashMap<>();
    private Map<String, Object> maxSpeedMap = new HashMap<>();
    private Map<String, Object> priorityMap = new HashMap<>();
    private Map<String, JsonFeature> areas = new HashMap<>();

    public CustomModel() {
    }

    public CustomModel(CustomModel toCopy) {
        this.maxSpeedFallback = toCopy.maxSpeedFallback;
        this.headingPenalty = toCopy.headingPenalty;
        this.distanceInfluence = toCopy.distanceInfluence;

        speedFactorMap = deepCopy(toCopy.getSpeedFactor());
        maxSpeedMap = deepCopy(toCopy.getMaxSpeed());
        priorityMap = deepCopy(toCopy.getPriority());

        areas.putAll(toCopy.getAreas());
    }

    private <T> T deepCopy(T originalObject) {
        if (originalObject instanceof List) {
            List<Object> newList = new ArrayList<>(((List) originalObject).size());
            for (Object item : (List) originalObject) {
                newList.add(deepCopy(item));
            }
            return (T) newList;
        } else if (originalObject instanceof Map) {
            Map copy = new HashMap<>(((Map) originalObject).size());
            for (Object o : ((Map) originalObject).entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return (T) copy;
        } else {
            return originalObject;
        }
    }

    public Map<String, Object> getSpeedFactor() {
        return speedFactorMap;
    }

    public Map<String, Object> getMaxSpeed() {
        return maxSpeedMap;
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
                "distanceInfluence=" + distanceInfluence +
                ", speedFactor=" + speedFactorMap +
                ", maxSpeed=" + maxSpeedMap +
                ", maxSpeedFallback=" + maxSpeedFallback +
                ", priorityMap=" + priorityMap +
                ", #areas=" + areas.size() +
                '}';
    }

    /**
     * This method assumes that this object is a per-request object so we can apply the changes and keep baseCustomModel
     * unchanged.
     */
    public static CustomModel merge(CustomModel baseModel, CustomModel queryModel) {
        // avoid changing the specified CustomModel via deep copy otherwise query-CustomModel would be modified
        CustomModel mergedCM = new CustomModel(baseModel);
        if (queryModel.maxSpeedFallback != null) {
            if (mergedCM.maxSpeedFallback != null && mergedCM.maxSpeedFallback > queryModel.maxSpeedFallback)
                throw new IllegalArgumentException("CustomModel in query can only use max_speed_fallback bigger or equal to " + mergedCM.maxSpeedFallback);
            mergedCM.maxSpeedFallback = queryModel.maxSpeedFallback;
        }
        if (Math.abs(queryModel.distanceInfluence - CustomModel.DEFAULT_D_I) > 0.01) {
            if (mergedCM.distanceInfluence > queryModel.distanceInfluence)
                throw new IllegalArgumentException("CustomModel in query can only use distance_influence bigger or equal to " + mergedCM.distanceInfluence);
            mergedCM.distanceInfluence = queryModel.distanceInfluence;
        }

        // example
        // max_speed: { road_class: { secondary : 0.4 } }
        // or
        // priority:  { max_weight: { "<3.501": 0.7 } }
        for (Map.Entry<String, Object> queryEntry : queryModel.getMaxSpeed().entrySet()) {
            Object value = mergedCM.maxSpeedMap.get(queryEntry.getKey());
            applyChange(mergedCM.maxSpeedMap, value, queryEntry);
        }
        for (Map.Entry<String, Object> queryEntry : queryModel.getSpeedFactor().entrySet()) {
            Object value = mergedCM.speedFactorMap.get(queryEntry.getKey());
            applyChange(mergedCM.speedFactorMap, value, queryEntry);
        }
        for (Map.Entry<String, Object> queryEntry : queryModel.getPriority().entrySet()) {
            Object value = mergedCM.priorityMap.get(queryEntry.getKey());
            applyChange(mergedCM.priorityMap, value, queryEntry);
        }
        for (Map.Entry<String, JsonFeature> entry : queryModel.getAreas().entrySet()) {
            if (mergedCM.areas.containsKey(entry.getKey()))
                throw new IllegalArgumentException("area " + entry.getKey() + " already exists");
            mergedCM.areas.put(entry.getKey(), entry.getValue());
        }

        return mergedCM;
    }

    private static void applyChange(Map<String, Object> mergedSuperMap,
                                    Object mergedObj, Map.Entry<String, Object> querySuperEntry) {
        if (mergedObj == null) {
            // no need for a "merge"
            mergedSuperMap.put(querySuperEntry.getKey(), querySuperEntry.getValue());
            return;
        }
        if (!(mergedObj instanceof Map))
            throw new IllegalArgumentException("entry is not a map: " + mergedObj);
        Object queryObj = querySuperEntry.getValue();
        if (!(queryObj instanceof Map))
            throw new IllegalArgumentException("query entry is not a map: " + queryObj);

        // TODO NOW how to merge different ranges of DecimalEncodedValue => if range size is decreased => it is fine
        Map<Object, Object> mergedMap = (Map) mergedObj;
        Map<Object, Object> queryMap = (Map) queryObj;
        for (Map.Entry queryEntry : queryMap.entrySet()) {
            Object mergedValue = mergedMap.get(queryEntry.getKey());
            if (mergedValue == null) {
                mergedMap.put(queryEntry.getKey(), queryEntry.getValue());
            } else if (queryEntry.getValue() instanceof Number && mergedValue instanceof Number) {
                mergedMap.put(queryEntry.getKey(), ((Number) queryEntry.getValue()).doubleValue() * ((Number) mergedValue).doubleValue());
            } else {
                throw new IllegalArgumentException("Cannot merge value " + queryEntry.getValue() + " for key " + queryEntry.getKey() + ", merged value: " + mergedValue);
            }
        }
    }
}