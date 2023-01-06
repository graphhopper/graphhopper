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

import com.graphhopper.json.Statement;

import java.util.*;

/**
 * This class is used in combination with CustomProfile.
 */
public class CustomModel {

    public static final String KEY = "custom_model";

    // 'Double' instead of 'double' is required to know if it was 0 or not specified in the request.
    private Double distanceInfluence;
    private double headingPenalty = Parameters.Routing.DEFAULT_HEADING_PENALTY;
    private boolean internal;
    private List<Statement> speedStatements = new ArrayList<>();
    private List<Statement> priorityStatements = new ArrayList<>();
    private Map<String, JsonFeature> areas = new HashMap<>();

    public CustomModel() {
    }

    public CustomModel(CustomModel toCopy) {
        this.headingPenalty = toCopy.headingPenalty;
        this.distanceInfluence = toCopy.distanceInfluence;
        // do not copy "internal" boolean

        speedStatements = deepCopy(toCopy.getSpeed());
        priorityStatements = deepCopy(toCopy.getPriority());

        areas.putAll(toCopy.getAreas());
    }

    /**
     * This method is for internal usage only! Parsing a CustomModel is expensive and so we cache the result, which is
     * especially important for fast landmark queries (hybrid mode). Now this method ensures that all server-side custom
     * models are cached in a special internal cache which does not remove seldom accessed entries.
     */
    public CustomModel internal() {
        this.internal = true;
        return this;
    }

    public boolean isInternal() {
        return internal;
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

    public List<Statement> getSpeed() {
        return speedStatements;
    }

    public CustomModel addToSpeed(Statement st) {
        getSpeed().add(st);
        return this;
    }

    public List<Statement> getPriority() {
        return priorityStatements;
    }

    public CustomModel addToPriority(Statement st) {
        getPriority().add(st);
        return this;
    }

    public CustomModel setAreas(Map<String, JsonFeature> areas) {
        this.areas = areas;
        return this;
    }

    public Map<String, JsonFeature> getAreas() {
        return areas;
    }

    public boolean hasDistanceInfluence() {
        return distanceInfluence != null;
    }

    public CustomModel setDistanceInfluence(Double distanceFactor) {
        this.distanceInfluence = distanceFactor;
        return this;
    }

    /**
     * @return the distance influence of this CustomModel. 0 is returned if it wasn't set (i.e. it was null)
     * @see #hasDistanceInfluence()
     */
    public Double getDistanceInfluence() {
        return hasDistanceInfluence() ? distanceInfluence : 0;
    }

    public CustomModel setHeadingPenalty(double headingPenalty) {
        this.headingPenalty = headingPenalty;
        return this;
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
        return "distanceInfluence=" + distanceInfluence + "|headingPenalty=" + headingPenalty
                + "|speedStatements=" + speedStatements + "|priorityStatements=" + priorityStatements + "|areas=" + areas;
    }

    /**
     * A new CustomModel is created from the baseModel merged with the specified queryModel. Returns the baseModel if
     * queryModel is null.
     */
    public static CustomModel merge(CustomModel baseModel, CustomModel queryModel) {
        if (queryModel == null) return baseModel;
        // avoid changing the specified CustomModel via deep copy otherwise the server-side CustomModel would be
        // modified (same problem if queryModel would be used as target)
        CustomModel mergedCM = new CustomModel(baseModel);

        if(queryModel.hasDistanceInfluence())
            mergedCM.distanceInfluence = queryModel.distanceInfluence;
        mergedCM.speedStatements.addAll(queryModel.getSpeed());
        mergedCM.priorityStatements.addAll(queryModel.getPriority());

        for (Map.Entry<String, JsonFeature> entry : queryModel.getAreas().entrySet()) {
            if (mergedCM.areas.containsKey(entry.getKey()))
                throw new IllegalArgumentException("area " + entry.getKey() + " already exists");
            mergedCM.areas.put(entry.getKey(), entry.getValue());
        }

        return mergedCM;
    }
}