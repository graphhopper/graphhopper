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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.graphhopper.jackson.CustomModelAreasDeserializer;
import com.graphhopper.json.MinMax;
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
    private List<Statement> turnPenaltyStatements = new ArrayList<>();
    // numbers and booleans usable in the expressions of the statements; on merge they are
    // overridden per key (unlike statements, which are appended)
    private Map<String, Object> parameters = new LinkedHashMap<>();
    // the allowed value range of a number parameter; can only be specified in a server-side custom
    // model, absent means the default range, see getParameterRange
    private Map<String, MinMax> parameterRanges = new LinkedHashMap<>();

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
        turnPenaltyStatements = deepCopy(toCopy.getTurnPenalty());
        parameters.putAll(toCopy.parameters);
        toCopy.parameterRanges.forEach((name, range) -> parameterRanges.put(name, new MinMax(range.min, range.max)));

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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Statement> getSpeed() {
        return speedStatements;
    }

    public CustomModel addToSpeed(Statement st) {
        getSpeed().add(st);
        return this;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Statement> getPriority() {
        return priorityStatements;
    }

    public CustomModel addToPriority(Statement st) {
        getPriority().add(st);
        return this;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @JsonProperty("parameters")
    public CustomModel setParameters(Map<String, Object> map) {
        map.forEach(this::putParameter);
        return this;
    }

    private void putParameter(String name, Object value) {
        if (value instanceof Map<?, ?> object) {
            // the object form defines a range: {"value": 3, "min": 2, "max": 5}, min and max are optional
            for (Object key : object.keySet())
                if (!"value".equals(key) && !"min".equals(key) && !"max".equals(key))
                    throw new IllegalArgumentException("parameter '" + name + "': unexpected key '" + key + "'. Only value, min and max are allowed");
            if (!(object.get("value") instanceof Number number))
                throw new IllegalArgumentException("parameter '" + name + "': a number 'value' is required when a range is specified, but was: " + object.get("value"));
            setParameter(name, number.doubleValue(),
                    rangeLimit(name, "min", object.get("min"), 0),
                    rangeLimit(name, "max", object.get("max"), Double.POSITIVE_INFINITY));
        } else {
            parameters.put(name, value);
        }
    }

    private static double rangeLimit(String name, String key, Object limit, double defaultValue) {
        if (limit == null) return defaultValue;
        if (!(limit instanceof Number number))
            throw new IllegalArgumentException("parameter '" + name + "': '" + key + "' must be a number, but was: " + limit);
        return number.doubleValue();
    }

    public CustomModel setParameter(String name, double value) {
        parameters.put(name, value);
        return this;
    }

    public CustomModel setParameter(String name, double value, double min, double max) {
        if (min > max)
            throw new IllegalArgumentException("parameter '" + name + "': min " + min + " must not be larger than max " + max);
        if (value < min || value > max)
            throw new IllegalArgumentException("parameter '" + name + "': value " + value + " must be within its range [" + min + ", " + max + "]");
        parameters.put(name, value);
        parameterRanges.put(name, new MinMax(min, max));
        return this;
    }

    public CustomModel setParameter(String name, boolean value) {
        parameters.put(name, value);
        return this;
    }

    /**
     * @return the allowed value range of the specified number parameter, [0, Infinity) unless the
     * server-side custom model defined an explicit range
     */
    @JsonIgnore
    public MinMax getParameterRange(String name) {
        MinMax range = parameterRanges.get(name);
        return range == null ? new MinMax(0, Double.POSITIVE_INFINITY) : range;
    }

    @JsonIgnore
    public Map<String, MinMax> getParameterRanges() {
        return parameterRanges;
    }

    /**
     * Throws an exception if the query model does not just override the values of parameters that the
     * base (server-side) model defines, with the same type and within the allowed range.
     */
    public static void checkParameterOverrides(CustomModel baseModel, CustomModel queryModel) {
        if (!queryModel.parameterRanges.isEmpty())
            throw new IllegalArgumentException("a parameter range can only be specified in a server-side custom model, but got one for: "
                    + queryModel.parameterRanges.keySet());
        for (Map.Entry<String, Object> entry : queryModel.parameters.entrySet()) {
            String name = entry.getKey();
            Object baseValue = baseModel.parameters.get(name);
            if (baseValue == null)
                throw new IllegalArgumentException("parameter '" + name + "' is not defined in the server-side custom model. Only the values of "
                        + baseModel.parameters.keySet() + " can be overridden");
            if (baseValue instanceof Boolean != entry.getValue() instanceof Boolean)
                throw new IllegalArgumentException("parameter '" + name + "' must have the same type as in the server-side custom model, "
                        + "but was: " + entry.getValue());
            if (entry.getValue() instanceof Number number) {
                MinMax range = baseModel.getParameterRange(name);
                if (number.doubleValue() < range.min || number.doubleValue() > range.max)
                    throw new IllegalArgumentException("parameter '" + name + "': value " + number
                            + " must be within its range [" + range.min + ", " + range.max + "]");
            }
        }
    }

    @JsonProperty("turn_penalty")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Statement> getTurnPenalty() {
        return turnPenaltyStatements;
    }

    public CustomModel addToTurnPenalty(Statement st) {
        getTurnPenalty().add(st);
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
        // parameterRanges are excluded as they only restrict requests and do not affect the weights,
        // i.e. changing a range must not require a re-import or new preparation
        return createContentString() + "|parameters=" + parameters;
    }

    /**
     * @return the string that identifies the compiled custom weighting class, i.e. without the
     * parameter values, which the generated class reads at runtime in init (see CustomModelParser).
     * The names are not required either (the statements determine the created fields), but the types
     * are, as they determine the field types.
     */
    public String createClassKey() {
        Map<String, String> types = new TreeMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet())
            types.put(entry.getKey(), entry.getValue() instanceof Boolean ? "boolean" : "double");
        return createContentString() + "|parameterTypes=" + types;
    }

    private String createContentString() {
        // used to check against stored custom models, see #2026
        return "distanceInfluence=" + distanceInfluence + "|headingPenalty=" + headingPenalty
                + "|speedStatements=" + speedStatements + "|priorityStatements=" + priorityStatements
                + "|turnPenaltyStatements=" + turnPenaltyStatements
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
        mergedCM.turnPenaltyStatements.addAll(queryModel.getTurnPenalty());
        mergedCM.parameters.putAll(queryModel.parameters);
        queryModel.parameterRanges.forEach((name, range) -> mergedCM.parameterRanges.put(name, new MinMax(range.min, range.max)));

        mergedCM.addAreas(queryModel.getAreas());
        return mergedCM;
    }
}
