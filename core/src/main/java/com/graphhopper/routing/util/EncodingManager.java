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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.Constants;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manager class to register encoder, assign their flag values and check objects with all encoders
 * during parsing. Create one via:
 * <p>
 * EncodingManager.start(4).add(new CarFlagEncoder()).build();
 *
 * @author Peter Karich
 * @author Nop
 */
public class EncodingManager implements EncodedValueLookup {
    private final LinkedHashMap<String, EncodedValue> encodedValueMap;
    private final LinkedHashMap<String, EncodedValue> turnEncodedValueMap;
    private int intsForFlags;
    private int intsForTurnCostFlags;

    public static void putEncodingManagerIntoProperties(EncodingManager encodingManager, StorableProperties properties) {
        properties.put("graph.em.version", Constants.VERSION_EM);
        properties.put("graph.em.ints_for_flags", encodingManager.intsForFlags);
        properties.put("graph.em.ints_for_turn_cost_flags", encodingManager.intsForTurnCostFlags);
        properties.put("graph.encoded_values", encodingManager.toEncodedValuesAsString());
        properties.put("graph.turn_encoded_values", encodingManager.toTurnEncodedValuesAsString());
    }

    public static EncodingManager fromProperties(StorableProperties properties) {
        if (properties.containsVersion())
            throw new IllegalStateException("The GraphHopper file format is not compatible with the data you are " +
                    "trying to load. You either need to use an older version of GraphHopper or run a new import");

        String versionStr = properties.get("graph.em.version");
        if (versionStr.isEmpty() || !String.valueOf(Constants.VERSION_EM).equals(versionStr))
            throw new IllegalStateException("Incompatible encoding version. You need to use the same GraphHopper version you used to import the graph, or run a new import. "
                    + " Stored encoding version: " + (versionStr.isEmpty() ? "missing" : versionStr) + ", used encoding version: " + Constants.VERSION_EM);
        String encodedValueStr = properties.get("graph.encoded_values");
        ArrayNode evList = deserializeEncodedValueList(encodedValueStr);
        LinkedHashMap<String, EncodedValue> encodedValues = new LinkedHashMap<>();
        evList.forEach(serializedEV -> {
            EncodedValue encodedValue = EncodedValueSerializer.deserializeEncodedValue(serializedEV.textValue());
            if (encodedValues.put(encodedValue.getName(), encodedValue) != null)
                throw new IllegalStateException("Duplicate encoded value name: " + encodedValue.getName() + " in: graph.encoded_values=" + encodedValueStr);
        });

        String turnEncodedValueStr = properties.get("graph.turn_encoded_values");
        ArrayNode tevList = deserializeEncodedValueList(turnEncodedValueStr);
        LinkedHashMap<String, EncodedValue> turnEncodedValues = new LinkedHashMap<>();
        tevList.forEach(serializedEV -> {
            EncodedValue encodedValue = EncodedValueSerializer.deserializeEncodedValue(serializedEV.textValue());
            if (turnEncodedValues.put(encodedValue.getName(), encodedValue) != null)
                throw new IllegalStateException("Duplicate turn encoded value name: " + encodedValue.getName() + " in: graph.turn_encoded_values=" + turnEncodedValueStr);
        });

        return new EncodingManager(encodedValues, turnEncodedValues,
                getIntegerProperty(properties, "graph.em.ints_for_flags"),
                getIntegerProperty(properties, "graph.em.ints_for_turn_cost_flags")
        );
    }

    private static int getIntegerProperty(StorableProperties properties, String key) {
        String str = properties.get(key);
        if (str.isEmpty())
            throw new IllegalStateException("Missing EncodingManager property: '" + key + "'");
        return Integer.parseInt(str);
    }

    private static ArrayNode deserializeEncodedValueList(String encodedValueStr) {
        try {
            return Jackson.newObjectMapper().readValue(encodedValueStr, ArrayNode.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Starts the build process of an EncodingManager
     */
    public static Builder start() {
        return new Builder();
    }

    public EncodingManager(LinkedHashMap<String, EncodedValue> encodedValueMap,
                           LinkedHashMap<String, EncodedValue> turnEncodedValueMap,
                           int intsForFlags, int intsForTurnCostFlags) {
        this.encodedValueMap = encodedValueMap;
        this.turnEncodedValueMap = turnEncodedValueMap;
        this.intsForFlags = intsForFlags;
        this.intsForTurnCostFlags = intsForTurnCostFlags;
    }

    private EncodingManager() {
        this(new LinkedHashMap<>(), new LinkedHashMap<>(), 0, 0);
    }

    public static class Builder {
        private final EncodedValue.InitializerConfig edgeConfig = new EncodedValue.InitializerConfig();
        private final EncodedValue.InitializerConfig turnCostConfig = new EncodedValue.InitializerConfig();
        private EncodingManager em = new EncodingManager();

        public Builder add(EncodedValue encodedValue) {
            checkNotBuiltAlready();
            if (em.hasEncodedValue(encodedValue.getName()))
                throw new IllegalArgumentException("EncodedValue already exists: " + encodedValue.getName());
            if (em.hasTurnEncodedValue(encodedValue.getName()))
                throw new IllegalArgumentException("Already defined as 'turn'-EncodedValue: " + encodedValue.getName());
            encodedValue.init(edgeConfig);
            em.encodedValueMap.put(encodedValue.getName(), encodedValue);
            return this;
        }

        public Builder addTurnCostEncodedValue(EncodedValue turnCostEnc) {
            checkNotBuiltAlready();
            if (em.hasTurnEncodedValue(turnCostEnc.getName()))
                throw new IllegalArgumentException("Already defined: " + turnCostEnc.getName());
            if (em.hasEncodedValue(turnCostEnc.getName()))
                throw new IllegalArgumentException("Already defined as EncodedValue: " + turnCostEnc.getName());
            turnCostEnc.init(turnCostConfig);
            em.turnEncodedValueMap.put(turnCostEnc.getName(), turnCostEnc);
            return this;
        }

        private void checkNotBuiltAlready() {
            if (em == null)
                throw new IllegalStateException("Cannot call method after Builder.build() was called");
        }

        public EncodingManager build() {
            checkNotBuiltAlready();
            em.intsForFlags = edgeConfig.getRequiredInts();
            em.intsForTurnCostFlags = turnCostConfig.getRequiredInts();
            EncodingManager result = em;
            em = null;
            return result;
        }

    }

    public int getIntsForFlags() {
        return intsForFlags;
    }

    public boolean hasEncodedValue(String key) {
        return encodedValueMap.get(key) != null;
    }

    public boolean hasTurnEncodedValue(String key) {
        return turnEncodedValueMap.get(key) != null;
    }

    /**
     * @return list of all prefixes of xy_access and xy_average_speed encoded values.
     */
    public List<String> getVehicles() {
        return getEncodedValues().stream()
                .filter(ev -> ev.getName().endsWith("_access"))
                .map(ev -> ev.getName().replaceAll("_access", ""))
                .filter(v -> getEncodedValues().stream().anyMatch(ev -> ev.getName().contains(VehicleSpeed.key(v))))
                .collect(Collectors.toList());
    }

    public String toEncodedValuesAsString() {
        List<String> serializedEVsList = encodedValueMap.values().stream().map(EncodedValueSerializer::serializeEncodedValue).collect(Collectors.toList());
        try {
            return Jackson.newObjectMapper().writeValueAsString(serializedEVsList);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return String.join(",", getVehicles());
    }

    // TODO hide IntsRef even more in a later version: https://gist.github.com/karussell/f4c2b2b1191be978d7ee9ec8dd2cd48f
    public IntsRef createEdgeFlags() {
        return new IntsRef(getIntsForFlags());
    }

    public IntsRef createRelationFlags() {
        // for backward compatibility use 2 ints
        return new IntsRef(2);
    }

    public boolean needsTurnCostsSupport() {
        return intsForTurnCostFlags > 0;
    }

    @Override
    public List<EncodedValue> getEncodedValues() {
        return Collections.unmodifiableList(new ArrayList<>(encodedValueMap.values()));
    }

    @Override
    public BooleanEncodedValue getBooleanEncodedValue(String key) {
        return getEncodedValue(key, BooleanEncodedValue.class);
    }

    @Override
    public IntEncodedValue getIntEncodedValue(String key) {
        return getEncodedValue(key, IntEncodedValue.class);
    }

    @Override
    public DecimalEncodedValue getDecimalEncodedValue(String key) {
        return getEncodedValue(key, DecimalEncodedValue.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Enum<?>> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> type) {
        return getEncodedValue(key, EnumEncodedValue.class);
    }

    @Override
    public StringEncodedValue getStringEncodedValue(String key) {
        return getEncodedValue(key, StringEncodedValue.class);
    }

    @Override
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
        EncodedValue ev = encodedValueMap.get(key);
        // todo: why do we not just return null when EV is missing? just like java.util.Map? -> https://github.com/graphhopper/graphhopper/pull/2561#discussion_r859770067
        if (ev == null)
            throw new IllegalArgumentException("Cannot find EncodedValue '" + key + "' in collection: " + encodedValueMap.keySet());
        return (T) ev;
    }

    public List<EncodedValue> getTurnEncodedValues() {
        return Collections.unmodifiableList(new ArrayList<>(turnEncodedValueMap.values()));
    }

    public DecimalEncodedValue getTurnDecimalEncodedValue(String key) {
        return getTurnEncodedValue(key, DecimalEncodedValue.class);
    }

    public BooleanEncodedValue getTurnBooleanEncodedValue(String key) {
        return getTurnEncodedValue(key, BooleanEncodedValue.class);
    }

    public <T extends EncodedValue> T getTurnEncodedValue(String key, Class<T> encodedValueType) {
        EncodedValue ev = turnEncodedValueMap.get(key);
        // todo: why do we not just return null when EV is missing? just like java.util.Map? -> https://github.com/graphhopper/graphhopper/pull/2561#discussion_r859770067
        if (ev == null)
            throw new IllegalArgumentException("Cannot find Turn-EncodedValue " + key + " in collection: " + encodedValueMap.keySet());
        return (T) ev;
    }

    private String toTurnEncodedValuesAsString() {
        List<String> serializedEVsList = turnEncodedValueMap.values().stream().map(EncodedValueSerializer::serializeEncodedValue).collect(Collectors.toList());
        try {
            return Jackson.newObjectMapper().writeValueAsString(serializedEVsList);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String getKey(String prefix, String str) {
        return prefix + "_" + str;
    }
}
