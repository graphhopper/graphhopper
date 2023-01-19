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
import com.graphhopper.util.PMap;

import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.toLowerCase;

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
    private int intsForFlags;
    private int intsForTurnCostFlags;

    /**
     * Instantiate manager with the given list of encoders. The manager knows several default
     * encoders using DefaultVehicleEncodedValuesFactory.
     */
    public static EncodingManager create(String flagEncodersStr) {
        return create(new DefaultVehicleEncodedValuesFactory(), flagEncodersStr);
    }

    public static EncodingManager create(VehicleEncodedValuesFactory factory, String flagEncodersStr) {
        return createBuilder(Arrays.stream(flagEncodersStr.split(",")).filter(s -> !s.trim().isEmpty()).
                map(s -> parseEncoderString(factory, s)).collect(Collectors.toList())).build();
    }

    private static EncodingManager.Builder createBuilder(List<? extends VehicleEncodedValues> vehicleEncodedValues) {
        Builder builder = new Builder();
        for (VehicleEncodedValues v : vehicleEncodedValues)
            builder.add(v);
        return builder;
    }

    public static void putEncodingManagerIntoProperties(EncodingManager encodingManager, StorableProperties properties) {
        properties.put("graph.em.version", Constants.VERSION_EM);
        properties.put("graph.em.ints_for_flags", encodingManager.intsForFlags);
        properties.put("graph.em.ints_for_turn_cost_flags", encodingManager.intsForTurnCostFlags);
        properties.put("graph.encoded_values", encodingManager.toEncodedValuesAsString());
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

        return new EncodingManager(encodedValues,
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

    public EncodingManager(LinkedHashMap<String, EncodedValue> encodedValueMap, int intsForFlags, int intsForTurnCostFlags) {
        this.encodedValueMap = encodedValueMap;
        this.intsForFlags = intsForFlags;
        this.intsForTurnCostFlags = intsForTurnCostFlags;
    }

    private EncodingManager() {
        this(new LinkedHashMap<>(), 0, 0);
    }

    public static class Builder {
        private final EncodedValue.InitializerConfig edgeConfig = new EncodedValue.InitializerConfig();
        private final EncodedValue.InitializerConfig turnCostConfig = new EncodedValue.InitializerConfig();
        private EncodingManager em = new EncodingManager();

        public Builder add(VehicleEncodedValues v) {
            checkNotBuiltAlready();
            List<EncodedValue> list = new ArrayList<>();
            v.createEncodedValues(list);
            list.forEach(this::add);

            list = new ArrayList<>();
            v.createTurnCostEncodedValues(list);
            list.forEach(this::addTurnCostEncodedValue);
            return this;
        }

        public Builder add(EncodedValue encodedValue) {
            checkNotBuiltAlready();
            if (em.hasEncodedValue(encodedValue.getName()))
                throw new IllegalArgumentException("EncodedValue already exists: " + encodedValue.getName());
            encodedValue.init(edgeConfig);
            em.encodedValueMap.put(encodedValue.getName(), encodedValue);
            return this;
        }

        public Builder addTurnCostEncodedValue(EncodedValue turnCostEnc) {
            checkNotBuiltAlready();
            if (em.hasEncodedValue(turnCostEnc.getName()))
                throw new IllegalArgumentException("Already defined: " + turnCostEnc.getName() + ". Please note that " +
                        "EncodedValues for edges and turn costs are in the same namespace.");
            turnCostEnc.init(turnCostConfig);
            em.encodedValueMap.put(turnCostEnc.getName(), turnCostEnc);
            return this;
        }

        private void checkNotBuiltAlready() {
            if (em == null)
                throw new IllegalStateException("Cannot call method after Builder.build() was called");
        }

        public EncodingManager build() {
            checkNotBuiltAlready();
            addDefaultEncodedValues();
            if (em.encodedValueMap.isEmpty())
                throw new IllegalStateException("No EncodedValues were added to the EncodingManager");
            em.intsForFlags = edgeConfig.getRequiredInts();
            em.intsForTurnCostFlags = edgeConfig.getRequiredInts();
            EncodingManager result = em;
            em = null;
            return result;
        }

        private void addDefaultEncodedValues() {
            // todo: I think ultimately these should all be removed and must be added explicitly
            if (!em.hasEncodedValue(Roundabout.KEY))
                add(Roundabout.create());
            if (!em.hasEncodedValue(RoadClass.KEY))
                add(new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class));
            if (!em.hasEncodedValue(RoadClassLink.KEY))
                add(new SimpleBooleanEncodedValue(RoadClassLink.KEY));
            if (!em.hasEncodedValue(RoadEnvironment.KEY))
                add(new EnumEncodedValue<>(RoadEnvironment.KEY, RoadEnvironment.class));
            if (!em.hasEncodedValue(MaxSpeed.KEY))
                add(MaxSpeed.create());
            if (!em.hasEncodedValue(RoadAccess.KEY))
                add(new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class));

            for (String vehicle : em.getVehicles()) {
                if (vehicle.contains("bike") || vehicle.contains("mtb")) {
                    if (!em.hasEncodedValue(BikeNetwork.KEY))
                        add(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class));
                    if (!em.hasEncodedValue(GetOffBike.KEY))
                        add(GetOffBike.create());
                    if (!em.hasEncodedValue(Smoothness.KEY))
                        add(new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class));
                } else if (vehicle.contains("foot") || vehicle.contains("hike") || vehicle.contains("wheelchair")) {
                    if (!em.hasEncodedValue(FootNetwork.KEY))
                        add(new EnumEncodedValue<>(FootNetwork.KEY, RouteNetwork.class));
                }
            }
        }
    }

    static VehicleEncodedValues parseEncoderString(VehicleEncodedValuesFactory factory, String encoderString) {
        if (!encoderString.equals(toLowerCase(encoderString)))
            throw new IllegalArgumentException("An upper case name for the vehicle is not allowed: " + encoderString);

        encoderString = encoderString.trim();
        if (encoderString.isEmpty())
            throw new IllegalArgumentException("vehicle cannot be empty. " + encoderString);

        String entryVal = "";
        if (encoderString.contains("|")) {
            entryVal = encoderString;
            encoderString = encoderString.split("\\|")[0];
        }
        PMap configuration = new PMap(entryVal);
        return factory.createVehicleEncodedValues(encoderString, configuration);
    }

    public int getIntsForFlags() {
        return intsForFlags;
    }

    public boolean hasEncodedValue(String key) {
        return encodedValueMap.get(key) != null;
    }

    public List<String> getVehicles() {
        // we define the 'vehicles' as all the prefixes for which there is an access and speed EV
        // any EVs that contain prefix_average_speed are accepted
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
            throw new IllegalArgumentException("Cannot find EncodedValue " + key + " in collection: " + encodedValueMap.keySet());
        return (T) ev;
    }

    public static String getKey(String prefix, String str) {
        return prefix + "_" + str;
    }

    // copied from janino
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "first_match",
            "abstract", "assert",
            "boolean", "break", "byte",
            "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double",
            "else", "enum", "extends",
            "false", "final", "finally", "float", "for",
            "goto",
            "if", "implements", "import", "instanceof", "int", "interface",
            "long",
            "native", "new", "non-sealed", "null",
            "package", "permits", "private", "protected", "public",
            "record", "return",
            "sealed", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try",
            "var", "void", "volatile",
            "while",
            "yield",
            "_"
    ));

    public static boolean isValidEncodedValue(String name) {
        // first character must be a lower case letter
        if (name.isEmpty() || !isLowerLetter(name.charAt(0)) || KEYWORDS.contains(name)) return false;

        int underscoreCount = 0;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                if (underscoreCount > 0) return false;
                underscoreCount++;
            } else if (!isLowerLetter(c) && !isNumber(c)) {
                return false;
            } else {
                underscoreCount = 0;
            }
        }
        return true;
    }

    private static boolean isNumber(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLowerLetter(char c) {
        return c >= 'a' && c <= 'z';
    }
}