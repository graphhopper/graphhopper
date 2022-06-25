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
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
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
    private final LinkedHashMap<String, VehicleEncodedValues> flagEncoders;
    private final EncodedValue.InitializerConfig edgeConfig;
    private final EncodedValue.InitializerConfig turnCostConfig;

    /**
     * Instantiate manager with the given list of encoders. The manager knows several default
     * encoders using DefaultFlagEncoderFactory.
     */
    public static EncodingManager create(String flagEncodersStr) {
        return create(new DefaultFlagEncoderFactory(), flagEncodersStr);
    }

    public static EncodingManager create(FlagEncoderFactory factory, String flagEncodersStr) {
        return createBuilder(Arrays.stream(flagEncodersStr.split(",")).filter(s -> !s.trim().isEmpty()).
                map(s -> parseEncoderString(factory, s)).collect(Collectors.toList())).build();
    }

    /**
     * Instantiate manager with the given list of encoders.
     */
    public static EncodingManager create(FlagEncoder... flagEncoders) {
        return EncodingManager.createBuilder(Arrays.asList(flagEncoders)).build();
    }

    private static EncodingManager.Builder createBuilder(List<? extends FlagEncoder> flagEncoders) {
        Builder builder = new Builder();
        for (FlagEncoder flagEncoder : flagEncoders)
            builder.add(flagEncoder);
        return builder;
    }

    /**
     * Starts the build process of an EncodingManager
     */
    public static Builder start() {
        return new Builder();
    }

    public EncodingManager(LinkedHashMap<String, EncodedValue> encodedValueMap, LinkedHashMap<String, VehicleEncodedValues> flagEncoders, EncodedValue.InitializerConfig edgeConfig, EncodedValue.InitializerConfig turnCostConfig) {
        this.flagEncoders = flagEncoders;
        this.encodedValueMap = encodedValueMap;
        this.turnCostConfig = turnCostConfig;
        this.edgeConfig = edgeConfig;
        flagEncoders.values().forEach(f -> f.setEncodedValueLookup(this));
    }

    private EncodingManager() {
        this(new LinkedHashMap<>(), new LinkedHashMap<>(), new EncodedValue.InitializerConfig(), new EncodedValue.InitializerConfig());
    }

    public static class Builder {
        private EncodingManager em = new EncodingManager();

        public Builder add(FlagEncoder encoder) {
            checkNotBuiltAlready();
            if (em.flagEncoders.containsKey(encoder.getName()))
                throw new IllegalArgumentException("FlagEncoder already exists: " + encoder.getName());
            VehicleEncodedValues v = (VehicleEncodedValues) encoder;
            v.setEncodedValueLookup(em);
            List<EncodedValue> list = new ArrayList<>();
            v.createEncodedValues(list);
            list.forEach(this::add);

            list = new ArrayList<>();
            v.createTurnCostEncodedValues(list);
            list.forEach(this::addTurnCostEncodedValue);

            em.flagEncoders.put(v.getName(), v);
            return this;
        }

        public Builder add(EncodedValue encodedValue) {
            checkNotBuiltAlready();
            if (em.hasEncodedValue(encodedValue.getName()))
                throw new IllegalArgumentException("EncodedValue already exists: " + encodedValue.getName());
            encodedValue.init(em.edgeConfig);
            em.encodedValueMap.put(encodedValue.getName(), encodedValue);
            return this;
        }

        public Builder addTurnCostEncodedValue(EncodedValue turnCostEnc) {
            checkNotBuiltAlready();
            if (em.hasEncodedValue(turnCostEnc.getName()))
                throw new IllegalArgumentException("Already defined: " + turnCostEnc.getName() + ". Please note that " +
                        "EncodedValues for edges and turn costs are in the same namespace.");
            turnCostEnc.init(em.turnCostConfig);
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

            for (VehicleEncodedValues encoder : em.flagEncoders.values()) {
                if (encoder.getName().contains("bike") || encoder.getName().contains("mtb")) {
                    if (!em.hasEncodedValue(BikeNetwork.KEY))
                        add(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class));
                    if (!em.hasEncodedValue(GetOffBike.KEY))
                        add(GetOffBike.create());
                    if (!em.hasEncodedValue(Smoothness.KEY))
                        add(new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class));
                } else if (encoder.getName().contains("foot") || encoder.getName().contains("hike") || encoder.getName().contains("wheelchair")) {
                    if (!em.hasEncodedValue(FootNetwork.KEY))
                        add(new EnumEncodedValue<>(FootNetwork.KEY, RouteNetwork.class));
                }
            }
        }
    }

    static FlagEncoder parseEncoderString(FlagEncoderFactory factory, String encoderString) {
        if (!encoderString.equals(toLowerCase(encoderString)))
            throw new IllegalArgumentException("An upper case name for the FlagEncoder is not allowed: " + encoderString);

        encoderString = encoderString.trim();
        if (encoderString.isEmpty())
            throw new IllegalArgumentException("FlagEncoder cannot be empty. " + encoderString);

        String entryVal = "";
        if (encoderString.contains("|")) {
            entryVal = encoderString;
            encoderString = encoderString.split("\\|")[0];
        }
        PMap configuration = new PMap(entryVal);
        return factory.createFlagEncoder(encoderString, configuration);
    }

    public int getIntsForFlags() {
        return edgeConfig.getRequiredInts();
    }

    public boolean hasEncodedValue(String key) {
        return encodedValueMap.get(key) != null;
    }

    public String toFlagEncodersAsString() {
        return flagEncoders.values().stream().map(VehicleEncodedValues::toSerializationString).collect(Collectors.joining(","));
    }

    public String toEncodedValuesAsString() {
        List<String> serializedEVsList = encodedValueMap.values().stream().map(EncodedValueSerializer::serializeEncodedValue).collect(Collectors.toList());
        try {
            return Jackson.newObjectMapper().writeValueAsString(serializedEVsList);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String toEdgeConfigAsString() {
        return EncodedValueSerializer.serializeInitializerConfig(edgeConfig);
    }

    public String toTurnCostConfigAsString() {
        return EncodedValueSerializer.serializeInitializerConfig(turnCostConfig);
    }

    @Override
    public String toString() {
        return flagEncoders.values().stream().map(Object::toString).collect(Collectors.joining(","));
    }

    // TODO hide IntsRef even more in a later version: https://gist.github.com/karussell/f4c2b2b1191be978d7ee9ec8dd2cd48f
    public IntsRef createEdgeFlags() {
        return new IntsRef(getIntsForFlags());
    }

    public IntsRef createRelationFlags() {
        // for backward compatibility use 2 ints
        return new IntsRef(2);
    }

    public List<FlagEncoder> fetchEdgeEncoders() {
        return new ArrayList<>(flagEncoders.values());
    }

    public boolean needsTurnCostsSupport() {
        for (FlagEncoder encoder : flagEncoders.values())
            if (encoder.supportsTurnCosts())
                return true;
        return false;
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

    /**
     * All EncodedValue names that are created from a FlagEncoder should use this method to mark them as
     * "none-shared" across the other FlagEncoders.
     */
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