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
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.parsers.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.*;
import java.util.regex.Pattern;
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
    private static final Pattern WAY_NAME_PATTERN = Pattern.compile("; *");
    private final List<AbstractFlagEncoder> edgeEncoders = new ArrayList<>();
    private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();
    private final List<RelationTagParser> relationTagParsers = new ArrayList<>();
    private final List<TagParser> edgeTagParsers = new ArrayList<>();
    private final Map<String, TurnCostParser> turnCostParsers = new LinkedHashMap<>();
    private boolean enableInstructions = true;
    private String preferredLanguage = "";
    private EncodedValue.InitializerConfig turnCostConfig;
    private EncodedValue.InitializerConfig relationConfig;
    private EncodedValue.InitializerConfig edgeConfig;

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
     * Create the EncodingManager from the provided GraphHopper location. Throws an
     * IllegalStateException if it fails. Used if no EncodingManager specified on load.
     */
    public static EncodingManager create(EncodingManager.Builder builder, EncodedValueFactory evFactory, FlagEncoderFactory flagEncoderFactory, StorableProperties properties) {
        String encodedValuesStr = properties.get("graph.encoded_values");
        for (String evString : encodedValuesStr.split(",")) {
            builder.addIfAbsent(evFactory, evString);
        }
        String flagEncoderValuesStr = properties.get("graph.flag_encoders");
        for (String encoderString : flagEncoderValuesStr.split(",")) {
            builder.addIfAbsent(flagEncoderFactory, encoderString);
        }
        return builder.build();
    }

    /**
     * Starts the build process of an EncodingManager
     */
    public static Builder start() {
        return new Builder();
    }

    private EncodingManager() {
        this.turnCostConfig = new EncodedValue.InitializerConfig();
        this.relationConfig = new EncodedValue.InitializerConfig();
        this.edgeConfig = new EncodedValue.InitializerConfig();
    }

    public void releaseParsers() {
        turnCostParsers.clear();
        edgeTagParsers.clear();
        relationTagParsers.clear();
    }

    public static class Builder {
        private EncodingManager em;
        private DateRangeParser dateRangeParser;
        private final Map<String, AbstractFlagEncoder> flagEncoderMap = new LinkedHashMap<>();
        private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();
        private final Set<TagParser> tagParserSet = new LinkedHashSet<>();
        private final List<TurnCostParser> turnCostParsers = new ArrayList<>();
        private final List<RelationTagParser> relationTagParsers = new ArrayList<>();

        public Builder() {
            em = new EncodingManager();
        }

        /**
         * This method specifies the preferred language for way names during import.
         * <p>
         * Language code as defined in ISO 639-1 or ISO 639-2.
         * <ul>
         * <li>If no preferred language is specified, only the default language with no tag will be
         * imported.</li>
         * <li>If a language is specified, it will be imported if its tag is found, otherwise fall back
         * to default language.</li>
         * </ul>
         */
        public Builder setPreferredLanguage(String language) {
            check();
            em.setPreferredLanguage(language);
            return this;
        }

        /**
         * This method specifies if the import should include way names to be able to return
         * instructions for a route.
         */
        public Builder setEnableInstructions(boolean enable) {
            check();
            em.setEnableInstructions(enable);
            return this;
        }

        public boolean addIfAbsent(FlagEncoderFactory factory, String flagEncoderString) {
            check();
            String key = flagEncoderString.split("\\|")[0].trim();
            if (flagEncoderMap.containsKey(key))
                return false;
            FlagEncoder fe = parseEncoderString(factory, flagEncoderString);
            flagEncoderMap.put(fe.toString(), (AbstractFlagEncoder) fe);
            return true;
        }

        public boolean addIfAbsent(EncodedValueFactory factory, String encodedValueString) {
            check();
            String key = encodedValueString.split("\\|")[0].trim();
            if (encodedValueMap.containsKey(key))
                return false;
            EncodedValue ev = parseEncodedValueString(factory, encodedValueString);
            encodedValueMap.put(ev.getName(), ev);
            return true;
        }

        public boolean addIfAbsent(TagParserFactory factory, String tagParserString) {
            check();
            tagParserString = tagParserString.trim();
            if (tagParserString.isEmpty()) return false;

            TagParser tagParser = em.parseEncodedValueString(factory, tagParserString);
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
            flagEncoderMap.put(encoder.toString(), (AbstractFlagEncoder) encoder);
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

        private void _addEdgeTagParser(TagParser tagParser, boolean withNamespace) {
            if (!em.edgeEncoders.isEmpty())
                throw new IllegalStateException("Avoid mixing encoded values from FlagEncoder with shared encoded values until we have a more clever mechanism, see #1862");

            List<EncodedValue> list = new ArrayList<>();
            tagParser.createEncodedValues(em, list);
            for (EncodedValue ev : list) {
                em.addEncodedValue(ev, withNamespace);
            }
            em.edgeTagParsers.add(tagParser);
        }

        private void _addRelationTagParser(RelationTagParser tagParser) {
            List<EncodedValue> list = new ArrayList<>();
            tagParser.createRelationEncodedValues(em, list);
            for (EncodedValue ev : list) {
                ev.init(em.relationConfig);
            }
            em.relationTagParsers.add(tagParser);

            _addEdgeTagParser(tagParser, false);
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

        public EncodingManager build() {
            check();

            for (RelationTagParser tagParser : relationTagParsers) {
                _addRelationTagParser(tagParser);
            }

            for (TagParser tagParser : tagParserSet) {
                _addEdgeTagParser(tagParser, false);
            }

            for (EncodedValue ev : encodedValueMap.values()) {
                em.addEncodedValue(ev, false);
            }

            if (!em.hasEncodedValue(Roundabout.KEY))
                _addEdgeTagParser(new OSMRoundaboutParser(), false);
            if (!em.hasEncodedValue(RoadClass.KEY))
                _addEdgeTagParser(new OSMRoadClassParser(), false);
            if (!em.hasEncodedValue(RoadClassLink.KEY))
                _addEdgeTagParser(new OSMRoadClassLinkParser(), false);
            if (!em.hasEncodedValue(RoadEnvironment.KEY))
                _addEdgeTagParser(new OSMRoadEnvironmentParser(), false);
            if (!em.hasEncodedValue(MaxSpeed.KEY))
                _addEdgeTagParser(new OSMMaxSpeedParser(), false);
            if (!em.hasEncodedValue(RoadAccess.KEY)) {
                // TODO introduce road_access for different vehicles? But how to create it in DefaultTagParserFactory?
                _addEdgeTagParser(new OSMRoadAccessParser(), false);
            }

            if (dateRangeParser == null)
                dateRangeParser = new DateRangeParser(DateRangeParser.createCalendar());

            for (AbstractFlagEncoder encoder : flagEncoderMap.values()) {
                if (encoder instanceof BikeCommonFlagEncoder) {
                    if (!em.hasEncodedValue(RouteNetwork.key("bike")))
                        _addRelationTagParser(new OSMBikeNetworkTagParser());
                    if (!em.hasEncodedValue(GetOffBike.KEY))
                        _addEdgeTagParser(new OSMGetOffBikeParser(), false);
                    if (!em.hasEncodedValue(Smoothness.KEY))
                        _addEdgeTagParser(new OSMSmoothnessParser(), false);
                    if (!em.hasEncodedValue(Cycleway.KEY))
                        _addEdgeTagParser(new OSMCyclewayParser(), false);
                } else if (encoder instanceof FootFlagEncoder) {
                    if (!em.hasEncodedValue(RouteNetwork.key("foot")))
                        _addRelationTagParser(new OSMFootNetworkTagParser());
                }
            }

            for (AbstractFlagEncoder encoder : flagEncoderMap.values()) {
                encoder.init(dateRangeParser);
                em.addEncoder(encoder);
            }

            for (TurnCostParser parser : turnCostParsers) {
                _addTurnCostParser(parser);
            }

            // FlagEncoder can demand TurnCostParsers => add them after the explicitly added ones
            for (AbstractFlagEncoder encoder : flagEncoderMap.values()) {
                if (encoder.supportsTurnCosts() && !em.turnCostParsers.containsKey(encoder.toString()))
                    _addTurnCostParser(new OSMTurnRelationParser(encoder.toString(), encoder.getMaxTurnCosts(), encoder.getRestrictions()));
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

    static EncodedValue parseEncodedValueString(EncodedValueFactory factory, String encodedValueString) {
        if (!encodedValueString.equals(toLowerCase(encodedValueString)))
            throw new IllegalArgumentException("Use lower case for EncodedValues: " + encodedValueString);

        encodedValueString = encodedValueString.trim();
        if (encodedValueString.isEmpty())
            throw new IllegalArgumentException("EncodedValue cannot be empty. " + encodedValueString);

        EncodedValue evObject = factory.create(encodedValueString);
        PMap map = new PMap(encodedValueString);
        if (!map.has("version"))
            throw new IllegalArgumentException("EncodedValue must have a version specified but it was " + encodedValueString);
        return evObject;
    }

    private TagParser parseEncodedValueString(TagParserFactory factory, String tagParserString) {
        if (!tagParserString.equals(toLowerCase(tagParserString)))
            throw new IllegalArgumentException("Use lower case for TagParser: " + tagParserString);

        PMap map = new PMap(tagParserString);
        return factory.create(tagParserString, map);
    }

    static String fixWayName(String str) {
        if (str == null)
            return "";
        return WAY_NAME_PATTERN.matcher(str).replaceAll(", ");
    }

    public int getIntsForFlags() {
        return (int) Math.ceil((double) edgeConfig.getRequiredBits() / 32.0);
    }

    private void setEnableInstructions(boolean enableInstructions) {
        this.enableInstructions = enableInstructions;
    }

    public boolean isEnableInstructions() {
        return enableInstructions;
    }

    private void setPreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null)
            throw new IllegalArgumentException("preferred language cannot be null");

        this.preferredLanguage = preferredLanguage;
    }

    private void addEncoder(AbstractFlagEncoder encoder) {
        encoder.setEncodedValueLookup(this);
        List<EncodedValue> list = new ArrayList<>();
        encoder.createEncodedValues(list, encoder.toString());
        for (EncodedValue ev : list)
            addEncodedValue(ev, true);
        edgeEncoders.add(encoder);
    }

    private void addEncodedValue(EncodedValue ev, boolean withNamespace) {
        String normalizedKey = ev.getName().replaceAll(SPECIAL_SEPARATOR, "_");
        if (hasEncodedValue(normalizedKey))
            throw new IllegalStateException("EncodedValue " + ev.getName() + " collides with " + normalizedKey);
        if (!withNamespace && !isSharedEncodedValues(ev))
            throw new IllegalArgumentException("EncodedValue " + ev.getName() + " must not contain namespace character '" + SPECIAL_SEPARATOR + "'");
        if (withNamespace && isSharedEncodedValues(ev))
            throw new IllegalArgumentException("EncodedValue " + ev.getName() + " must contain namespace character '" + SPECIAL_SEPARATOR + "'");
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
            throw new IllegalArgumentException("FlagEncoder for " + name + " not found. Existing: " + toFlagEncodersAsString());
        return null;
    }

    /**
     * Determine whether a way is routable for one of the added encoders.
     *
     * @return if at least one encoder consumes the specified way
     */
    public boolean acceptWay(ReaderWay way) {
        return edgeEncoders.stream().anyMatch(encoder -> !encoder.getAccess(way).equals(Access.CAN_SKIP));
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
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            encoder.handleWayTags(edgeFlags, way);
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

    public String toFlagEncodersAsString() {
        StringBuilder str = new StringBuilder();
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            if (str.length() > 0)
                str.append(",");

            str.append(encoder.toString())
                    .append("|")
                    .append(encoder.getPropertiesString());
        }

        return str.toString();
    }

    public String toEncodedValuesAsString() {
        StringBuilder str = new StringBuilder();
        for (EncodedValue ev : encodedValueMap.values()) {
            if (!isSharedEncodedValues(ev))
                continue;

            if (str.length() > 0)
                str.append(",");

            str.append(ev.toString());
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
        return enableInstructions == that.enableInstructions &&
                edgeEncoders.equals(that.edgeEncoders) &&
                encodedValueMap.equals(that.encodedValueMap) &&
                preferredLanguage.equals(that.preferredLanguage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeEncoders, encodedValueMap, enableInstructions, preferredLanguage);
    }

    /**
     * Updates the given edge flags based on node tags
     */
    public IntsRef handleNodeTags(Map<String, Object> nodeTags, IntsRef edgeFlags) {
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            // for now we just create a dummy reader node, because our encoders do not make use of the coordinates anyway
            ReaderNode readerNode = new ReaderNode(0, 0, 0, nodeTags);
            // block access for all encoders that treat this node as a barrier
            if (encoder.isBarrier(readerNode)) {
                BooleanEncodedValue accessEnc = encoder.getAccessEnc();
                accessEnc.setBool(false, edgeFlags, false);
                accessEnc.setBool(true, edgeFlags, false);
            }
        }
        return edgeFlags;
    }

    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        // storing the road name does not yet depend on the flagEncoder so manage it directly
        if (enableInstructions) {
            // String wayInfo = carFlagEncoder.getWayInfo(way);
            // http://wiki.openstreetmap.org/wiki/Key:name
            String name = "";
            if (!preferredLanguage.isEmpty())
                name = fixWayName(way.getTag("name:" + preferredLanguage));
            if (name.isEmpty())
                name = fixWayName(way.getTag("name"));
            // http://wiki.openstreetmap.org/wiki/Key:ref
            String refName = fixWayName(way.getTag("ref"));
            if (!refName.isEmpty()) {
                if (name.isEmpty())
                    name = refName;
                else
                    name += ", " + refName;
            }

            edge.setName(name);
        }

        if (Double.isInfinite(edge.getDistance()))
            throw new IllegalStateException("Infinite distance should not happen due to #435. way ID=" + way.getId());
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            encoder.applyWayTags(way, edge);
        }
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

    private static final String SPECIAL_SEPARATOR = "$";

    private static boolean isSharedEncodedValues(EncodedValue ev) {
        return isValidEncodedValue(ev.getName()) && !ev.getName().contains(SPECIAL_SEPARATOR);
    }

    /**
     * All EncodedValue names that are created from a FlagEncoder should use this method to mark them as
     * "none-shared" across the other FlagEncoders.
     */
    public static String getKey(FlagEncoder encoder, String str) {
        return getKey(encoder.toString(), str);
    }

    public static String getKey(String prefix, String str) {
        return prefix + SPECIAL_SEPARATOR + str;
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

        int dollarCount = 0, underscoreCount = 0;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '$') {
                if (dollarCount > 0) return false;
                dollarCount++;
            } else if (c == '_') {
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
