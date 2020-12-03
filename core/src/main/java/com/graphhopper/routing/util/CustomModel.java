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
import com.graphhopper.routing.weighting.custom.CustomWeighting;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;

import java.util.*;

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
    private Map<String, Object> speedFactorMap = new LinkedHashMap<>();
    private Map<String, Object> maxSpeedMap = new LinkedHashMap<>();
    private Map<String, Object> priorityMap = new LinkedHashMap<>();
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
            Map copy = originalObject instanceof LinkedHashMap ? new LinkedHashMap<>(((Map) originalObject).size()) :
                    new HashMap<>(((Map) originalObject).size());
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

        // MaxSpeed entries we merge via picking the minimum and values above 1 are allowed
        // max_speed: { road_class == secondary : 40 }
        CheckOp ignore = o1 -> o1;
        for (Map.Entry<String, Object> queryEntry : queryModel.getMaxSpeed().entrySet()) {
            Object value = mergedCM.maxSpeedMap.get(queryEntry.getKey());
            applyChange(mergedCM.maxSpeedMap, value, queryEntry, Math::min, ignore);
        }
        // SpeedFactor and Priority will be merged via multiplication
        MergeOp multiplyOp = (o1, o2) -> o1 * o2;
        CheckOp maxFactor = o1 -> {
            if (o1 > 1) throw new IllegalArgumentException("Factor cannot be larger than 1 but was " + o1);
            return o1;
        };
        for (Map.Entry<String, Object> queryEntry : queryModel.getSpeedFactor().entrySet()) {
            Object value = mergedCM.speedFactorMap.get(queryEntry.getKey());
            applyChange(mergedCM.speedFactorMap, value, queryEntry, multiplyOp, maxFactor);
        }
        // priority:  { max_weight < 3.501: 0.7 }
        for (Map.Entry<String, Object> queryEntry : queryModel.getPriority().entrySet()) {
            Object value = mergedCM.priorityMap.get(queryEntry.getKey());
            applyChange(mergedCM.priorityMap, value, queryEntry, multiplyOp, maxFactor);
        }
        for (Map.Entry<String, JsonFeature> entry : queryModel.getAreas().entrySet()) {
            if (mergedCM.areas.containsKey(entry.getKey()))
                throw new IllegalArgumentException("area " + entry.getKey() + " already exists");
            mergedCM.areas.put(entry.getKey(), entry.getValue());
        }

        return mergedCM;
    }

    private interface MergeOp {
        double op(double op1, double op2);
    }

    private interface CheckOp {
        double op(double op1);
    }

    private static void applyChange(Map<String, Object> mergedSuperMap, Object mergedObj,
                                    Map.Entry<String, Object> querySuperEntry, MergeOp merge, CheckOp check) {
        String key = querySuperEntry.getKey();
        if (!Helper.isEmpty(key) && key.startsWith(CustomWeighting.FIRST_MATCH)) {
            mergedObj = mergedSuperMap.get(key);
            if (mergedObj == null) {
                if (!(querySuperEntry.getValue() instanceof Map))
                    throw new IllegalArgumentException(key + " must be a map");
                Map<String, Object> tmp = (Map<String, Object>) querySuperEntry.getValue();
                tmp.values().stream().forEach(o -> check.op(toNum(o)));
                mergedSuperMap.put(key, tmp);
            } else if (mergedObj instanceof LinkedHashMap) {
                Map<String, Object> map = (Map<String, Object>) querySuperEntry.getValue();
                mergedSuperMap = (Map<String, Object>) mergedSuperMap.get(key);
                for (Map.Entry<String, Object> queryEntry : map.entrySet()) {
                    Object value = map.get(queryEntry.getKey());
                    applyChange(mergedSuperMap, value, queryEntry, merge, check);
                }
            } else {
                throw new IllegalArgumentException("Merged object is not a map but was " + mergedObj.getClass());
            }
            return;
        }

        mergedSuperMap.put(key, mergedObj == null
                ? check.op(toNum(querySuperEntry.getValue()))
                : merge.op(toNum(mergedObj), check.op(toNum(querySuperEntry.getValue()))));
    }

    private static double toNum(Object queryValue) {
        if (!(queryValue instanceof Number))
            throw new IllegalArgumentException("Not a number " + queryValue);
        return ((Number) queryValue).doubleValue();
    }
}