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

import com.graphhopper.reader.OSMTurnRelation;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.*;
import java.util.stream.Collectors;

import static com.graphhopper.util.Helper.toLowerCase;
import static java.util.Collections.emptyMap;

/**
 * Manager class to register encoder, assign their flag values and check objects with all encoders
 * during parsing. Create one via:
 * <p>
 * EncodingManager.start(4).add(new CarFlagEncoder()).build();
 *
 * @author Peter Karich
 * @author Nop
 */
public class TagParserManager implements EncodedValueLookup {
    private final List<VehicleTagParser> edgeEncoders = new ArrayList<>();
    private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();
    private final List<RelationTagParser> relationTagParsers = new ArrayList<>();
    private final List<TagParser> edgeTagParsers = new ArrayList<>();
    private final Map<String, TurnCostParser> turnCostParsers = new LinkedHashMap<>();
    private final EncodedValue.InitializerConfig turnCostConfig;
    private final EncodedValue.InitializerConfig relationConfig;
    private final EncodedValue.InitializerConfig edgeConfig;
    private EncodingManager encodingManager;

    /**
     * Instantiate manager with the given list of encoders. The manager knows several default
     * encoders using DefaultFlagEncoderFactory.
     */
    public static TagParserManager create(String flagEncodersStr) {
        return create(new DefaultFlagEncoderFactory(), flagEncodersStr);
    }

    public static TagParserManager create(FlagEncoderFactory factory, String flagEncodersStr) {
        return createBuilder(Arrays.stream(flagEncodersStr.split(",")).filter(s -> !s.trim().isEmpty()).
                map(s -> parseEncoderString(factory, s)).collect(Collectors.toList())).build();
    }

    /**
     * Instantiate manager with the given list of encoders.
     */
    public static TagParserManager create(FlagEncoder... flagEncoders) {
        return create(Arrays.asList(flagEncoders));
    }

    /**
     * Instantiate manager with the given list of encoders.
     */
    public static TagParserManager create(List<? extends FlagEncoder> flagEncoders) {
        return createBuilder(flagEncoders).build();
    }

    private static TagParserManager.Builder createBuilder(List<? extends FlagEncoder> flagEncoders) {
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

    private TagParserManager() {
        this.turnCostConfig = new EncodedValue.InitializerConfig();
        this.relationConfig = new EncodedValue.InitializerConfig();
        this.edgeConfig = new EncodedValue.InitializerConfig();
    }

    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    public void releaseParsers() {
        turnCostParsers.clear();
        edgeTagParsers.clear();
        relationTagParsers.clear();
    }

    public static class Builder {
        private TagParserManager em;
        private DateRangeParser dateRangeParser;
        private final Map<String, VehicleTagParser> flagEncoderMap = new LinkedHashMap<>();
        private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();
        private final Set<TagParser> tagParserSet = new LinkedHashSet<>();
        private final List<TurnCostParser> turnCostParsers = new ArrayList<>();
        private final List<RelationTagParser> relationTagParsers = new ArrayList<>();

        public Builder() {
            em = new TagParserManager();
        }

        public boolean addIfAbsent(FlagEncoderFactory factory, String flagEncoderString) {
            check();
            String key = flagEncoderString.split("\\|")[0].trim();
            if (flagEncoderMap.containsKey(key))
                return false;
            FlagEncoder fe = parseEncoderString(factory, flagEncoderString);
            flagEncoderMap.put(fe.toString(), (VehicleTagParser) fe);
            return true;
        }

        public boolean addIfAbsent(EncodedValueFactory encodedValueFactory, TagParserFactory factory, String tagParserString) {
            check();
            tagParserString = tagParserString.trim();
            if (tagParserString.isEmpty()) return false;

            if (!tagParserString.equals(toLowerCase(tagParserString)))
                throw new IllegalArgumentException("Use lower case for TagParser: " + tagParserString);

            add(encodedValueFactory.create(tagParserString));

            TagParser tagParser = factory.create(new EncodedValueLookup() {
                @Override
                public List<EncodedValue> getEncodedValues() {
                    return new ArrayList<>(encodedValueMap.values());
                }

                @Override
                public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
                    return (T) encodedValueMap.get(key);
                }

                @Override
                public BooleanEncodedValue getBooleanEncodedValue(String key) {
                    return (BooleanEncodedValue) encodedValueMap.get(key);
                }

                @Override
                public IntEncodedValue getIntEncodedValue(String key) {
                    return (IntEncodedValue) encodedValueMap.get(key);
                }

                @Override
                public DecimalEncodedValue getDecimalEncodedValue(String key) {
                    return (DecimalEncodedValue) encodedValueMap.get(key);
                }

                @Override
                public <T extends Enum<?>> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> enumType) {
                    return (EnumEncodedValue<T>) encodedValueMap.get(key);
                }

                @Override
                public StringEncodedValue getStringEncodedValue(String key) {
                    return (StringEncodedValue) encodedValueMap.get(key);
                }

                @Override
                public boolean hasEncodedValue(String key) {
                    return encodedValueMap.containsKey(key);
                }
            }, tagParserString);
            return tagParserSet.add(tagParser);
        }

        public Builder addTurnCostParser(TurnCostParser parser) {
            check();
            turnCostParsers.add(parser);
            return this;
        }

        public Builder addRelationTagParser(RelationTagParser tagParser) {
            check();
            relationTagParsers.add(tagParser);
            return this;
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

        /**
         * This method adds the specified TagParser and automatically adds EncodedValues as requested in
         * createEncodedValues.
         */
        public Builder add(TagParser tagParser) {
            check();
            if (!tagParserSet.add(tagParser))
                throw new IllegalArgumentException("TagParser already exists: " + tagParser);

            return this;
        }

        public Builder setDateRangeParser(DateRangeParser dateRangeParser) {
            check();
            this.dateRangeParser = dateRangeParser;
            return this;
        }

        private void check() {
            if (em == null)
                throw new IllegalStateException("Cannot call method after Builder.build() was called");
        }

        private void _addEdgeTagParser(TagParser tagParser) {
            if (!em.edgeEncoders.isEmpty())
                throw new IllegalStateException("Avoid mixing encoded values from FlagEncoder with shared encoded values until we have a more clever mechanism, see #1862");
            em.edgeTagParsers.add(tagParser);
        }

        private void _addRelationTagParser(RelationTagParser tagParser) {
            em.relationTagParsers.add(tagParser);
            _addEdgeTagParser(tagParser);
        }

        private void _addTurnCostParser(TurnCostParser parser) {
            List<EncodedValue> list = new ArrayList<>();
            parser.createTurnCostEncodedValues(em, list);
            for (EncodedValue ev : list) {
                ev.init(em.turnCostConfig);
                if (em.encodedValueMap.containsKey(ev.getName()))
                    throw new IllegalArgumentException("Already defined: " + ev.getName() + ". Please note that " +
                            "EncodedValues for edges and turn cost are in the same namespace.");
                em.encodedValueMap.put(ev.getName(), ev);
            }
            em.turnCostParsers.put(parser.getName(), parser);
        }

        public TagParserManager build() {
            check();

            for (RelationTagParser tagParser : relationTagParsers) {
                _addRelationTagParser(tagParser);
            }

            for (TagParser tagParser : tagParserSet) {
                _addEdgeTagParser(tagParser);
            }

            for (EncodedValue ev : encodedValueMap.values()) {
                em.addEncodedValue(ev);
            }

            if (!em.hasEncodedValue(Roundabout.KEY)) {
                em.addEncodedValue(Roundabout.create());
                _addEdgeTagParser(new OSMRoundaboutParser(em.getBooleanEncodedValue(Roundabout.KEY)));
            }
            if (!em.hasEncodedValue(RoadClass.KEY)) {
                em.addEncodedValue(new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class));
                _addEdgeTagParser(new OSMRoadClassParser(em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class)));
            }
            if (!em.hasEncodedValue(RoadClassLink.KEY)) {
                em.addEncodedValue(new SimpleBooleanEncodedValue(RoadClassLink.KEY));
                _addEdgeTagParser(new OSMRoadClassLinkParser(em.getBooleanEncodedValue(RoadClassLink.KEY)));
            }
            if (!em.hasEncodedValue(RoadEnvironment.KEY)) {
                em.addEncodedValue(new EnumEncodedValue<>(RoadEnvironment.KEY, RoadEnvironment.class));
                _addEdgeTagParser(new OSMRoadEnvironmentParser(em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class)));
            }
            if (!em.hasEncodedValue(MaxSpeed.KEY)) {
                em.addEncodedValue(MaxSpeed.create());
                _addEdgeTagParser(new OSMMaxSpeedParser(em.getDecimalEncodedValue(MaxSpeed.KEY)));
            }
            if (!em.hasEncodedValue(RoadAccess.KEY)) {
                em.addEncodedValue(new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class));
                // TODO introduce road_access for different vehicles? But how to create it in DefaultTagParserFactory?
                _addEdgeTagParser(new OSMRoadAccessParser(em.getEnumEncodedValue(RoadAccess.KEY, RoadAccess.class), OSMRoadAccessParser.toOSMRestrictions(TransportationMode.CAR)));
            }

            if (dateRangeParser == null)
                dateRangeParser = new DateRangeParser(DateRangeParser.createCalendar());

            for (VehicleTagParser encoder : flagEncoderMap.values()) {
                if (encoder instanceof BikeCommonTagParser) {
                    if (!em.hasEncodedValue(RouteNetwork.key("bike"))) {
                        em.addEncodedValue(new EnumEncodedValue<>(BikeNetwork.KEY, RouteNetwork.class));
                        _addRelationTagParser(new OSMBikeNetworkTagParser(em.getEnumEncodedValue(BikeNetwork.KEY, RouteNetwork.class), em.relationConfig));
                    }
                    if (!em.hasEncodedValue(GetOffBike.KEY)) {
                        em.addEncodedValue(GetOffBike.create());
                        _addEdgeTagParser(new OSMGetOffBikeParser(em.getBooleanEncodedValue(GetOffBike.KEY)));
                    }
                    if (!em.hasEncodedValue(Smoothness.KEY)) {
                        em.addEncodedValue(new EnumEncodedValue<>(Smoothness.KEY, Smoothness.class));
                        _addEdgeTagParser(new OSMSmoothnessParser(em.getEnumEncodedValue(Smoothness.KEY, Smoothness.class)));
                    }
                } else if (encoder instanceof FootTagParser) {
                    if (!em.hasEncodedValue(RouteNetwork.key("foot")))
                        em.addEncodedValue(new EnumEncodedValue<>(FootNetwork.KEY, RouteNetwork.class));
                    _addRelationTagParser(new OSMFootNetworkTagParser(em.getEnumEncodedValue(FootNetwork.KEY, RouteNetwork.class), em.relationConfig));
                }
            }

            for (VehicleTagParser encoder : flagEncoderMap.values()) {
                encoder.init(dateRangeParser);
                em.addEncoder(encoder);
            }

            for (TurnCostParser parser : turnCostParsers) {
                _addTurnCostParser(parser);
            }

            // FlagEncoder can demand TurnCostParsers => add them after the explicitly added ones
            for (VehicleTagParser encoder : flagEncoderMap.values()) {
                if (encoder.supportsTurnCosts() && !em.turnCostParsers.containsKey(TurnCost.key(encoder.toString()))) {
                    BooleanEncodedValue accessEnc = encoder.getAccessEnc();
                    DecimalEncodedValue turnCostEnc = encoder.getTurnCostEnc();
                    _addTurnCostParser(new OSMTurnRelationParser(accessEnc, turnCostEnc, encoder.getRestrictions()));
                }
            }

            if (em.encodedValueMap.isEmpty())
                throw new IllegalStateException("No EncodedValues found");

            em.encodingManager = new EncodingManager(new ArrayList<>(em.edgeEncoders), em.encodedValueMap,
                    em.turnCostConfig, em.edgeConfig);

            TagParserManager tmp = em;
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
        edgeEncoders.add(encoder);
    }

    private void addEncodedValue(EncodedValue ev) {
        if (hasEncodedValue(ev.getName()))
            throw new IllegalStateException("EncodedValue " + ev.getName() + " collides with " + ev.getName());
        ev.init(edgeConfig);
        encodedValueMap.put(ev.getName(), ev);
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
            throw new IllegalArgumentException("FlagEncoder for " + name + " not found. Existing: " + edgeEncoders.stream().map(FlagEncoder::toString).collect(Collectors.toList()));
        return null;
    }

    /**
     * Determine whether a way is routable for one of the added encoders.
     *
     * @return if at least one encoder consumes the specified way
     */
    public boolean acceptWay(ReaderWay way) {
        return edgeEncoders.stream().anyMatch(encoder -> !encoder.getAccess(way).equals(EncodingManager.Access.CAN_SKIP));
    }

    public IntsRef handleRelationTags(ReaderRelation relation, IntsRef relFlags) {
        for (RelationTagParser relParser : relationTagParsers) {
            relParser.handleRelationTags(relFlags, relation);
        }
        return relFlags;
    }

    public void handleTurnRelationTags(OSMTurnRelation turnRelation, TurnCostParser.ExternalInternalMap map, Graph graph) {
        for (TurnCostParser parser : turnCostParsers.values()) {
            parser.handleTurnRelationTags(turnRelation, map, graph);
        }
    }

    /**
     * Processes way properties of different kind to determine speed and direction.
     *
     * @param relationFlags The preprocessed relation flags is used to influence the way properties.
     */
    public IntsRef handleWayTags(ReaderWay way, IntsRef relationFlags) {
        IntsRef edgeFlags = createEdgeFlags();
        for (TagParser parser : edgeTagParsers) {
            parser.handleWayTags(edgeFlags, way, relationFlags);
        }
        for (VehicleTagParser encoder : edgeEncoders) {
            encoder.handleWayTags(edgeFlags, way);
            if (!edgeFlags.isEmpty()) {
                Map<String, Object> nodeTags = way.getTag("node_tags", emptyMap());
                encoder.handleNodeTags(edgeFlags, nodeTags);
            }
        }
        return edgeFlags;
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
        TagParserManager that = (TagParserManager) o;
        return edgeEncoders.equals(that.edgeEncoders) &&
                encodedValueMap.equals(that.encodedValueMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeEncoders, encodedValueMap);
    }

    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        for (VehicleTagParser encoder : edgeEncoders)
            encoder.applyWayTags(way, edge);
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

}