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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.graphhopper.jackson.CustomModelAreasDeserializer;
import com.graphhopper.json.Statement;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CustomModel {

    public static final String KEY = "custom_model";

    // 'Double' instead of 'double' is required to know if it was 0 or not specified in the request.
    private Double distanceInfluence;
    private Double headingPenalty;
    @JsonIgnore
    private boolean internal;
    private List<Statement> speedStatements = new ArrayList<>();
    private List<Statement> priorityStatements = new ArrayList<>();
    private List<Statement> turnTimeStatements = new ArrayList<>();

    private JsonFeatureCollection areas = new JsonFeatureCollection();

    public CustomModel() {
    }

    public CustomModel(CustomModel toCopy) {
        this.internal = false; // true only when explicitly set
        this.headingPenalty = toCopy.headingPenalty;
        this.distanceInfluence = toCopy.distanceInfluence;
        // do not copy "internal" boolean

        speedStatements = deepCopy(toCopy.getSpeed());
        priorityStatements = deepCopy(toCopy.getPriority());
        turnTimeStatements = deepCopy(toCopy.getTurnTimeStatements());

        addAreas(toCopy.getAreas());
    }

    public static Map<String, JsonFeature> getAreasAsMap(JsonFeatureCollection areas) {
        return areas.getFeatures().stream().collect(Collectors.toMap(JsonFeature::getId,
                Function.identity(), (existing, duplicate) -> {
                    throw new IllegalArgumentException("Cannot handle duplicate area " + duplicate.getId());
                }
        ));
    }

    public void addAreas(JsonFeatureCollection externalAreas) {
        Set<String> indexed = areas.getFeatures().stream().map(JsonFeature::getId).collect(Collectors.toSet());
        for (JsonFeature ext : externalAreas.getFeatures()) {
            if (!JsonFeature.isValidId("in_" + ext.getId()))
                throw new IllegalArgumentException("The area '" + ext.getId() + "' has an invalid id. Only letters, numbers and underscore are allowed.");
            if (indexed.contains(ext.getId()))
                throw new IllegalArgumentException("area " + ext.getId() + " already exists");
            areas.getFeatures().add(ext);
            indexed.add(ext.getId());
        }
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

    @JsonProperty("turn_time")
    public List<Statement> getTurnTimeStatements() {
        return turnTimeStatements;
    }

    public CustomModel addToTurnTime(Statement st) {
        getTurnTimeStatements().add(st);
        return this;
    }

    @JsonDeserialize(using = CustomModelAreasDeserializer.class)
    public CustomModel setAreas(JsonFeatureCollection areas) {
        this.areas = areas;
        return this;
    }

    public JsonFeatureCollection getAreas() {
        return areas;
    }

    public CustomModel setDistanceInfluence(Double distanceFactor) {
        this.distanceInfluence = distanceFactor;
        return this;
    }

    public Double getDistanceInfluence() {
        return distanceInfluence;
    }

    public CustomModel setHeadingPenalty(double headingPenalty) {
        this.headingPenalty = headingPenalty;
        return this;
    }

    public Double getHeadingPenalty() {
        return headingPenalty;
    }

    @Override
    public String toString() {
        return createContentString();
    }

    private String createContentString() {
        // used to check against stored custom models, see #2026
        return "distanceInfluence=" + distanceInfluence + "|headingPenalty=" + headingPenalty
                + "|speedStatements=" + speedStatements + "|priorityStatements=" + priorityStatements
                + "|turnTimeStatements=" + turnTimeStatements
                + "|areas=" + areas;
    }

    /**
     * A new CustomModel is created from the baseModel merged with the specified queryModel. Returns the baseModel if
     * queryModel is null.
     */
    public static CustomModel merge(CustomModel baseModel, CustomModel queryModel) {
        // avoid changing the specified CustomModel via deep copy otherwise the server-side CustomModel would be
        // modified (same problem if queryModel would be used as target)
        CustomModel mergedCM = new CustomModel(baseModel);
        if (queryModel == null) return mergedCM;

        if (queryModel.getDistanceInfluence() != null)
            mergedCM.distanceInfluence = queryModel.distanceInfluence;
        if (queryModel.getHeadingPenalty() != null)
            mergedCM.headingPenalty = queryModel.headingPenalty;
        mergedCM.speedStatements.addAll(queryModel.getSpeed());
        mergedCM.priorityStatements.addAll(queryModel.getPriority());
        mergedCM.turnTimeStatements.addAll(queryModel.getTurnTimeStatements());

        mergedCM.addAreas(queryModel.getAreas());
        return mergedCM;
    }
}
