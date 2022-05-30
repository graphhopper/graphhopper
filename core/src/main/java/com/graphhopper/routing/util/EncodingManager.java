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

import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

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
    private final List<FlagEncoder> edgeEncoders;
    private final Map<String, EncodedValue> encodedValueMap;
    private final EncodedValue.InitializerConfig turnCostConfig;
    private final EncodedValue.InitializerConfig edgeConfig;

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
        return create(Arrays.asList(flagEncoders));
    }

    /**
     * Instantiate manager with the given list of encoders.
     */
    public static EncodingManager create(List<? extends FlagEncoder> flagEncoders) {
        return createBuilder(flagEncoders).build();
    }

    private static EncodingManager.Builder createBuilder(List<? extends FlagEncoder> flagEncoders) {
        Builder builder = new Builder();
        for (FlagEncoder flagEncoder : flagEncoders) {
            builder.add(flagEncoder);
        }
        return builder;
    }

    /**
     * Starts the build process of an EncodingManager
     */
    public static Builder start() {
        return new Builder();
    }

    public EncodingManager(
            List<FlagEncoder> edgeEncoders,
            Map<String, EncodedValue> encodedValueMap,
            EncodedValue.InitializerConfig turnCostConfig,
            EncodedValue.InitializerConfig edgeConfig) {
        this.edgeEncoders = edgeEncoders;
        this.encodedValueMap = encodedValueMap;
        this.turnCostConfig = turnCostConfig;
        this.edgeConfig = edgeConfig;
    }

    private EncodingManager() {
        this(
                new ArrayList<>(), new LinkedHashMap<>(),
                new EncodedValue.InitializerConfig(),
                new EncodedValue.InitializerConfig()
        );
    }

    public static class Builder {
        private EncodingManager em;
        private DateRangeParser dateRangeParser;
        private final Map<String, VehicleTagParser> flagEncoderMap = new LinkedHashMap<>();
        private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();

        public Builder() {
            em = new EncodingManager();
        }

        public Builder add(FlagEncoder encoder) {
            check();
            if (flagEncoderMap.containsKey(encoder.toString()))
                throw new IllegalArgumentException("FlagEncoder already exists: " + encoder);
            flagEncoderMap.put(encoder.toString(), (VehicleTagParser) encoder);
            return this;
        }

        public Builder add(EncodedValue encodedValue) {
            check();
            if (encodedValueMap.containsKey(encodedValue.getName()))
                throw new IllegalArgumentException("EncodedValue already exists: " + encodedValue.getName());
            encodedValueMap.put(encodedValue.getName(), encodedValue);
            return this;
        }

        private void check() {
            if (em == null)
                throw new IllegalStateException("Cannot call method after Builder.build() was called");
        }

        public EncodingManager build() {
            check();

            for (EncodedValue ev : encodedValueMap.values()) {
                em.addEncodedValue(ev);
            }

            if (!em.hasEncodedValue(Roundabout.KEY))
                em.addEncodedValue(Roundabout.create());
            if (!em.hasEncodedValue(RoadClass.KEY))
                em.addEncodedValue(new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class));
            if (!em.hasEncodedValue(RoadClassLink.KEY))
                em.addEncodedValue(new SimpleBooleanEncodedValue(RoadClassLink.KEY));
            if (!em.hasEncodedValue(RoadEnvironment.KEY))
                em.addEncodedValue(new EnumEncodedValue<>(RoadEnvironment.KEY, RoadEnvironment.class));
            if (!em.hasEncodedValue(MaxSpeed.KEY))
                em.addEncodedValue(MaxSpeed.create());
            if (!em.hasEncodedValue(RoadAccess.KEY)) {
                em.addEncodedValue(new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class));
            }

            if (dateRangeParser == null)
                dateRangeParser = new DateRangeParser(DateRangeParser.createCalendar());

            for (FlagEncoder encoder : flagEncoderMap.values()) {
                if (encoder instanceof BikeCommonTagParser) {
                    if (!em.hasEncodedValue(RouteNetwork.key("bike")))
                        em.addEncodedValue(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class));
                    if (!em.hasEncodedValue(GetOffBike.KEY))
                        em.addEncodedValue(GetOffBike.create());
                    if (!em.hasEncodedValue(Smoothness.KEY))
                        em.addEncodedValue(new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class));
                } else if (encoder instanceof FootTagParser) {
                    if (!em.hasEncodedValue(RouteNetwork.key("foot")))
                        em.addEncodedValue(new EnumEncodedValue<>(FootNetwork.KEY, RouteNetwork.class));
                }
            }

            for (VehicleTagParser encoder : flagEncoderMap.values()) {
                encoder.init(dateRangeParser);
                em.addEncoder(encoder);
            }

            if (em.encodedValueMap.isEmpty())
                throw new IllegalStateException("No EncodedValues found");

            EncodingManager tmp = em;
            em = null;
            return tmp;
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

    private void addEncoder(VehicleTagParser encoder) {
        encoder.setEncodedValueLookup(this);
        List<EncodedValue> list = new ArrayList<>();
        encoder.createEncodedValues(list);
        for (EncodedValue ev : list)
            addEncodedValue(ev);
        list = new ArrayList<>();
        encoder.createTurnCostEncodedValues(list);
        for (EncodedValue ev : list)
            addTurnCostEncodedValue(ev);
        edgeEncoders.add(encoder);
    }

    private void addEncodedValue(EncodedValue ev) {
        if (hasEncodedValue(ev.getName()))
            throw new IllegalStateException("EncodedValue " + ev.getName() + " collides with " + ev.getName());
        ev.init(edgeConfig);
        encodedValueMap.put(ev.getName(), ev);
    }

    private void addTurnCostEncodedValue(EncodedValue turnCostEnc) {
        if (encodedValueMap.containsKey(turnCostEnc.getName()))
            throw new IllegalArgumentException("Already defined: " + turnCostEnc.getName() + ". Please note that " +
                    "EncodedValues for edges and turn cost are in the same namespace.");
        turnCostEnc.init(turnCostConfig);
        encodedValueMap.put(turnCostEnc.getName(), turnCostEnc);
    }

    public boolean hasEncodedValue(String key) {
        return encodedValueMap.get(key) != null;
    }

    /**
     * @return true if the specified encoder is found
     */
    public boolean hasEncoder(String encoder) {
        return getEncoder(encoder, false) != null;
    }

    public FlagEncoder getEncoder(String name) {
        return getEncoder(name, true);
    }

    private FlagEncoder getEncoder(String name, boolean throwExc) {
        for (FlagEncoder encoder : edgeEncoders) {
            if (name.equalsIgnoreCase(encoder.toString()))
                return encoder;
        }
        if (throwExc)
            throw new IllegalArgumentException("FlagEncoder for " + name + " not found. Existing: " + edgeEncoders.stream().map(FlagEncoder::toString).collect(Collectors.joining(",")));
        return null;
    }

    public enum Access {
        WAY, FERRY, OTHER, CAN_SKIP;

        public boolean isFerry() {
            return this.ordinal() == FERRY.ordinal();
        }

        public boolean isWay() {
            return this.ordinal() == WAY.ordinal();
        }

        public boolean isOther() {
            return this.ordinal() == OTHER.ordinal();
        }

        public boolean canSkip() {
            return this.ordinal() == CAN_SKIP.ordinal();
        }
    }

    public String toFlagEncodersAsString() {
        StringBuilder str = new StringBuilder();
        for (FlagEncoder encoder : edgeEncoders) {
            if (str.length() > 0)
                str.append(",");

            str.append(encoder.toString())
                    .append("|")
                    .append(((VehicleTagParser) encoder).getSharedEncodedValueString());
        }

        return str.toString();
    }

    public String toEncodedValuesAsString() {
        StringBuilder str = new StringBuilder();
        for (EncodedValue ev : encodedValueMap.values()) {
            if (str.length() > 0)
                str.append(",");

            str.append(ev.toString());
        }

        return str.toString();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (FlagEncoder encoder : edgeEncoders) {
            if (str.length() > 0)
                str.append(",");

            str.append(encoder.toString());
        }

        return str.toString();
    }

    // TODO hide IntsRef even more in a later version: https://gist.github.com/karussell/f4c2b2b1191be978d7ee9ec8dd2cd48f
    public IntsRef createEdgeFlags() {
        return new IntsRef(getIntsForFlags());
    }

    public IntsRef createRelationFlags() {
        // for backward compatibility use 2 ints
        return new IntsRef(2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncodingManager that = (EncodingManager) o;
        return edgeEncoders.equals(that.edgeEncoders) &&
                encodedValueMap.equals(that.encodedValueMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeEncoders, encodedValueMap);
    }

    public List<FlagEncoder> fetchEdgeEncoders() {
        return new ArrayList<>(edgeEncoders);
    }

    public boolean needsTurnCostsSupport() {
        for (FlagEncoder encoder : edgeEncoders) {
            if (encoder.supportsTurnCosts())
                return true;
        }
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
        if (ev == null)
            throw new IllegalArgumentException("Cannot find EncodedValue " + key + " in collection: " + ev);
        return (T) ev;
    }

    /**
     * All EncodedValue names that are created from a FlagEncoder should use this method to mark them as
     * "none-shared" across the other FlagEncoders.
     */
    public static String getKey(FlagEncoder encoder, String str) {
        return getKey(encoder.toString(), str);
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