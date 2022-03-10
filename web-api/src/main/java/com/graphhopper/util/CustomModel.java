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

import static com.graphhopper.json.Statement.Keyword.ELSE;
import static com.graphhopper.json.Statement.Keyword.IF;

/**
 * This class is used in combination with CustomProfile.
 */
public class CustomModel {

    public static final String KEY = "custom_model";

    // e.g. 70 means that the time costs are 25€/hour and for the distance 0.5€/km (for trucks this is usually larger)
    static double DEFAULT_DISTANCE_INFLUENCE = 70;
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
        // do not copy "internal"

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

    public CustomModel setDistanceInfluence(double distanceFactor) {
        this.distanceInfluence = distanceFactor;
        return this;
    }

    public double getDistanceInfluence() {
        return distanceInfluence == null ? DEFAULT_DISTANCE_INFLUENCE : distanceInfluence;
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
     * This method throws an exception when this CustomModel would decrease the edge weight compared to the specified
     * baseModel as in such a case the optimality of A* with landmarks can no longer be guaranteed (as the preparation
     * is based on baseModel).
     */
    public void checkLMConstraints(CustomModel baseModel) {
        if (isInternal())
            throw new IllegalArgumentException("CustomModel of query cannot be internal");
        if (distanceInfluence != null && distanceInfluence < baseModel.getDistanceInfluence())
            throw new IllegalArgumentException("CustomModel in query can only use " +
                    "distance_influence bigger or equal to " + baseModel.getDistanceInfluence() +
                    ", given: " + distanceInfluence);

        checkMultiplyValue(getPriority());
        double maxPrio = findMaxPriority(1);
        if (maxPrio > 1)
            throw new IllegalArgumentException("priority of CustomModel in query cannot be bigger than 1. Was: " + maxPrio);

        checkMultiplyValue(getSpeed());
    }

    private static void checkMultiplyValue(List<Statement> list) {
        for (Statement statement : list) {
            if (statement.getOperation() == Statement.Op.MULTIPLY && statement.getValue() > 1)
                throw new IllegalArgumentException("factor cannot be larger than 1 but was " + statement.getValue());
        }
    }

    static double findMax(List<Statement> statements, double max, String type) {
        // we want to find the smallest value that cannot be exceeded by any edge. the 'blocks' of speed statements
        // are applied one after the other.
        List<List<Statement>> blocks = splitIntoBlocks(statements);
        for (List<Statement> block : blocks) max = findMaxForBlock(block, max);
        if (max <= 0) throw new IllegalArgumentException(type + " cannot be negative or 0 (was " + max + ")");
        return max;
    }

    public double findMaxPriority(final double maxPriority) {
        return findMax(getPriority(), maxPriority, "priority");
    }

    public double findMaxSpeed(final double maxSpeed) {
        return findMax(getSpeed(), maxSpeed, "vehicle speed");
    }

    static double findMaxForBlock(List<Statement> block, final double max) {
        if (block.isEmpty() || !IF.equals(block.get(0).getKeyword()))
            throw new IllegalArgumentException("Every block must start with an if-statement");
        if (block.get(0).getCondition().trim().equals("true"))
            return block.get(0).apply(max);

        double blockMax = block.stream()
                .mapToDouble(statement -> statement.apply(max))
                .max()
                .orElse(max);
        // if there is no 'else' statement it's like there is a 'neutral' branch that leaves the initial value as is
        if (block.stream().noneMatch(st -> ELSE.equals(st.getKeyword())))
            blockMax = Math.max(blockMax, max);
        return blockMax;
    }

    /**
     * Splits the specified list into several list of statements starting with if
     */
    static List<List<Statement>> splitIntoBlocks(List<Statement> statements) {
        List<List<Statement>> result = new ArrayList<>();
        List<Statement> block = null;
        for (Statement st : statements) {
            if (IF.equals(st.getKeyword())) result.add(block = new ArrayList<>());
            if (block == null) throw new IllegalArgumentException("Every block must start with an if-statement");
            block.add(st);
        }
        return result;
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
        // we only overwrite the distance influence if a non-default value was used
        if (queryModel.distanceInfluence != null)
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