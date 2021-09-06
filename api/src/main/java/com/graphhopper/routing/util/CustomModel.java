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
 * This class is used in combination with CustomProfile.
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
        return createContentString();
    }

    private String createContentString() {
        // used to check against stored custom models, see #2026
        return "distanceInfluence=" + distanceInfluence + "|speedFactor=" + speedFactorMap + "|maxSpeed=" + maxSpeedMap +
                "|maxSpeedFallback=" + maxSpeedFallback + "|priorityMap=" + priorityMap + "|areas=" + areas;
    }

    /**
     * A new CustomModel is created from the baseModel merged with the specified queryModel.
     */
    public static CustomModel merge(CustomModel baseModel, CustomModel queryModel) {
        // avoid changing the specified CustomModel via deep copy otherwise the server-side CustomModel would be modified (same problem if queryModel would be used as target)
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
            // no need for a merge
            mergedSuperMap.put(querySuperEntry.getKey(), querySuperEntry.getValue());
            return;
        }
        if (!(mergedObj instanceof Map))
            throw new IllegalArgumentException(querySuperEntry.getKey() + ": entry is not a map: " + mergedObj);
        Object queryObj = querySuperEntry.getValue();
        if (!(queryObj instanceof Map))
            throw new IllegalArgumentException(querySuperEntry.getKey() + ": query entry is not a map: " + queryObj);

        Map<Object, Object> mergedMap = (Map) mergedObj;
        Map<Object, Object> queryMap = (Map) queryObj;
        for (Map.Entry queryEntry : queryMap.entrySet()) {
            if (queryEntry.getKey() == null || queryEntry.getKey().toString().isEmpty())
                throw new IllegalArgumentException(querySuperEntry.getKey() + ": key cannot be null or empty");
            String key = queryEntry.getKey().toString();
            if (isComparison(key))
                continue;

            Object mergedValue = mergedMap.get(key);
            if (mergedValue == null) {
                mergedMap.put(key, queryEntry.getValue());
            } else if (multiply(queryEntry.getValue(), mergedValue) != null) {
                // existing value needs to be multiplied
                mergedMap.put(key, multiply(queryEntry.getValue(), mergedValue));
            } else {
                throw new IllegalArgumentException(querySuperEntry.getKey() + ": cannot merge value " + queryEntry.getValue() + " for key " + key + ", merged value: " + mergedValue);
            }
        }

        // now special handling for comparison keys start e.g. <2 or >3.0, see testMergeComparisonKeys
        // this could be simplified if CustomModel would be already an abstract syntax tree :)
        List<String> queryComparisonKeys = getComparisonKeys(queryMap);
        if (queryComparisonKeys.isEmpty())
            return;
        if (queryComparisonKeys.size() > 1)
            throw new IllegalArgumentException(querySuperEntry.getKey() + ": entry in " + querySuperEntry.getValue() + " must not contain more than one key comparison but contained " + queryComparisonKeys);
        char opChar = queryComparisonKeys.get(0).charAt(0);
        List<String> mergedComparisonKeys = getComparisonKeys(mergedMap);
        if (mergedComparisonKeys.isEmpty()) {
            mergedMap.put(queryComparisonKeys.get(0), queryMap.get(queryComparisonKeys.get(0)));
        } else if (mergedComparisonKeys.get(0).charAt(0) == opChar) {
            if (multiply(queryMap.get(queryComparisonKeys.get(0)), mergedMap.get(mergedComparisonKeys.get(0))) != 0)
                throw new IllegalArgumentException(querySuperEntry.getKey() + ": currently only blocking comparisons are allowed, but query was " + queryMap.get(queryComparisonKeys.get(0)) + " and server side: " + mergedMap.get(mergedComparisonKeys.get(0)));

            try {
                double comparisonMergedValue = Double.parseDouble(mergedComparisonKeys.get(0).substring(1));
                double comparisonQueryValue = Double.parseDouble(queryComparisonKeys.get(0).substring(1));
                if (opChar == '<') {
                    if (comparisonMergedValue > comparisonQueryValue)
                        throw new IllegalArgumentException(querySuperEntry.getKey() + ": only use a comparison key with a bigger value than " + comparisonMergedValue + " but was " + comparisonQueryValue);
                } else if (opChar == '>') {
                    if (comparisonMergedValue < comparisonQueryValue)
                        throw new IllegalArgumentException(querySuperEntry.getKey() + ": only use a comparison key with a smaller value than " + comparisonMergedValue + " but was " + comparisonQueryValue);
                } else {
                    throw new IllegalArgumentException(querySuperEntry.getKey() + ": only use a comparison key with < or > as operator but was " + opChar);
                }
                mergedMap.remove(mergedComparisonKeys.get(0));
                mergedMap.put(queryComparisonKeys.get(0), queryMap.get(queryComparisonKeys.get(0)));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(querySuperEntry.getKey() + ": number in one of the 'comparison' keys for " + querySuperEntry.getKey() + " wasn't parsable: " + queryComparisonKeys + " (" + mergedComparisonKeys + ")");
            }
        } else {
            throw new IllegalArgumentException(querySuperEntry.getKey() + ": comparison keys must match but did not: " + queryComparisonKeys.get(0) + " vs " + mergedComparisonKeys.get(0));
        }
    }

    static Double multiply(Object queryValue, Object mergedValue) {
        if (queryValue instanceof Number && mergedValue instanceof Number)
            return ((Number) queryValue).doubleValue() * ((Number) mergedValue).doubleValue();
        return null;
    }

    static boolean isComparison(String key) {
        return key.startsWith("<") || key.startsWith(">");
    }

    private static List<String> getComparisonKeys(Map<Object, Object> map) {
        List<String> list = new ArrayList<>();
        for (Map.Entry queryEntry : map.entrySet()) {
            String key = queryEntry.getKey().toString();
            if (isComparison(key)) list.add(key);
        }
        return list;
    }
}