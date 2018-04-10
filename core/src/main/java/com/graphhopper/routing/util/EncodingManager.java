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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.json.GHJson;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.profiles.tagparsers.TagParser;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manager class to register encoder, assign their flag values and check objects with all encoders
 * during parsing.
 *
 * @author Peter Karich
 * @author Nop
 */
public class EncodingManager implements EncodedValueLookup {

    private static Logger LOGGER = LoggerFactory.getLogger(EncodingManager.class);

    private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();
    // for serialization we should not rely on the correct order, instead we force order with this List
    @JsonProperty("encoded_values")
    private final List<EncodedValue> encodedValueList = new ArrayList<>();
    private final Map<String, TagParser> parsers = new LinkedHashMap<>();
    private final Collection<ReaderWayFilter> filters = new HashSet<>();
    @JsonProperty("extended_data_size")
    private final int extendedDataSize;

    /**
     * for backward compatibility we have to specify an encoder name ("vehicle")
     */
    public static final String ENCODER_NAME = "weighting";
    private static final String ERR = "Encoders are requesting %s bits, more than %s bits of %s flags. ";
    private final List<AbstractFlagEncoder> edgeEncoders = new ArrayList<>();
    @JsonProperty("bits_for_edge_flags")
    private int bitsForEdgeFlags;
    @JsonProperty("bits_for_turn_flags")
    private final int bitsForTurnFlags = 8 * 4;
    @JsonProperty("enable_instructions")
    private boolean enableInstructions = true;
    @JsonProperty("preferred_language")
    private String preferredLanguage = "";

    /**
     * thematically structured encoders. Double encoders will be added once only.
     */

    public static String[] footEncodedValues = {
            TagParserFactory.FOOT_ACCESS,
            TagParserFactory.FOOT_AVERAGE_SPEED,
            TagParserFactory.CAR_MAX_SPEED,
            TagParserFactory.ROAD_CLASS,
            TagParserFactory.ROAD_ENVIRONMENT
            };
    public static String[] bikeEncodedValues = {
            TagParserFactory.BIKE_ACCESS,
            TagParserFactory.BIKE_AVERAGE_SPEED,
            TagParserFactory.ROAD_CLASS,
            TagParserFactory.ROAD_ENVIRONMENT,
            TagParserFactory.CAR_MAX_SPEED
    };
    public static String[] carEncodedValues = {
            TagParserFactory.CAR_ACCESS,
            TagParserFactory.CAR_AVERAGE_SPEED,
            TagParserFactory.CAR_MAX_SPEED,
            TagParserFactory.ROAD_CLASS,
            TagParserFactory.ROAD_ENVIRONMENT,
            TagParserFactory.ROUNDABOUT,
            TagParserFactory.MAX_HEIGHT,
            TagParserFactory.MAX_WEIGHT,
            TagParserFactory.MAX_WIDTH,
            TagParserFactory.CURVATURE
    };
    public static String[] globalEncodedValues = {
            TagParserFactory.ROUNDABOUT,
            TagParserFactory.ROAD_CLASS,
            TagParserFactory.ROAD_ENVIRONMENT,
            TagParserFactory.CURVATURE
    };

    /**
     * This constructor creates the object that orchestrates the edge properties, so called EncodedValues.
     *
     * @param extendedDataSpaceInBytes in bytes
     */
    private EncodingManager(int extendedDataSpaceInBytes) {
        this.extendedDataSize = Math.max(1, extendedDataSpaceInBytes / 4) * 4;
    }

    private EncodingManager() {
        extendedDataSize = 4;
    }

    public IntsRef createIntsRef() {
        return new IntsRef(extendedDataSize / 4);
    }

    // for serialization
    public String getFlagEncoderDetailsList() {
        return toDetailsString();
    }

    public static class Builder {
        private boolean buildCalled;
        private final EncodingManager em;
        private int nextNodeBit = 0;
        private int nextRelBit = 0;
        private int nextTurnBit = 0;

        /**
         * used only for tests
         */
        public Builder() {
            this(8);
        }

        public Builder(int extendedDataSize) {
            this.em = new EncodingManager(extendedDataSize);
        }

        /**
         * This method adds some EncodedValues that are required like roundabout and road_class
         */

        public Builder addGlobalEncodedValues(String... parsers) {
            for (String parser : parsers){
                add(TagParserFactory.createParser(parser));

            }
            return this;
        }

        public Builder addGlobalEncodedValues(boolean surface, boolean carMaxSpeed) {
            if(surface) add(TagParserFactory.createParser(TagParserFactory.SURFACE));
            if(carMaxSpeed) add(TagParserFactory.createParser(TagParserFactory.CAR_MAX_SPEED));
            addGlobalEncodedValues(globalEncodedValues);
            return this;
        }

        /**
         * This method adds some EncodedValues that are required like roundabout and road_class
         */
        public Builder addGlobalEncodedValues() {
            return addGlobalEncodedValues(true, true);
        }

        public Builder add(TagParser parser) {
            check();
            TagParser old = em.parsers.get(parser.getName());
            if (old != null)
                throw new IllegalArgumentException("Cannot add parser " + old.getName() + ". Already existing: " + parser.getName());

            em.parsers.put(parser.getName(), parser);
            em.encodedValueMap.put(parser.getName(), parser.getEncodedValue());
            em.encodedValueList.add(parser.getEncodedValue());
            em.filters.add(parser.getReadWayFilter());
            return this;
        }

        public Builder addBikeEncodedValues(){
            for(String parser : bikeEncodedValues){
                TagParser old = em.parsers.get(parser);
                if (old == null)
                    // Add the parser if one with same name is not yet registered
                    add(TagParserFactory.createParser(parser));

            }
            return this;
        }

        public Builder addCarEncodedValues(){
            for(String parser : carEncodedValues){
                TagParser old = em.parsers.get(parser);
                if (old == null)
                    // Add the parser if one with same name is not yet registered
                    add(TagParserFactory.createParser(parser));

            }
            return this;
        }

        public Builder addFootEncodedValues(){
            for(String parser : footEncodedValues){
                TagParser old = em.parsers.get(parser);
                if (old == null)
                    // Add the parser if one with same name is not yet registered
                    add(TagParserFactory.createParser(parser));
                
            }
            return this;
        }

        public Builder addAllFlagEncoders(String encoderList) {
            addAll(FlagEncoderFactory.DEFAULT, encoderList, 4, true);
            return this;
        }

        /**
         * Use TagParsers instead of FlagEncoder
         */
        public Builder addAll(FlagEncoderFactory factory, String encoderList, int bytesForFlags) {
            addAll(parseEncoderString(factory, encoderList), bytesForFlags, true);
            return this;
        }

        /**
         * Use TagParsers instead of FlagEncoder
         */
        public Builder addAll(FlagEncoderFactory factory, String encoderList, int bytesForFlags, boolean addEncodedValues) {
            addAll(parseEncoderString(factory, encoderList), bytesForFlags, addEncodedValues);
            return this;
        }

        /**
         * Use TagParsers instead of FlagEncoder
         */
        public Builder addAll(FlagEncoder... encoders) {
            addAll(Arrays.asList(encoders), 4);
            return this;
        }

        /**
         * Use TagParsers instead of FlagEncoder
         */
        public Builder addAll(List<? extends FlagEncoder> list, int bytesForFlags) {
            return addAll(list, bytesForFlags, true);
        }

        public Builder addAll(List<? extends FlagEncoder> list, int bytesForFlags, boolean addEncodedValues) {
            em.bitsForEdgeFlags = bytesForFlags * 8;
            for (FlagEncoder flagEncoder : list) {
                add(flagEncoder, addEncodedValues);
            }
            return this;
        }

        private Builder add(FlagEncoder flagEncoder, boolean addEncodedValues) {
            check();

            AbstractFlagEncoder encoder = (AbstractFlagEncoder) flagEncoder;
            for (FlagEncoder fe : em.edgeEncoders) {
                if (fe.toString().equals(encoder.toString()))
                    throw new IllegalArgumentException("Cannot register edge encoder. Name already exists: " + fe.toString());
                if (fe.toString().equalsIgnoreCase(ENCODER_NAME))
                    throw new IllegalArgumentException("Cannot register edge encoder with reserved name " + ENCODER_NAME);
            }
            if (encoder.isRegistered())
                throw new IllegalStateException("You must not register the FlagEncoder '" + encoder.toString() + "' twice!");
            encoder.setEncodedValueLookup(em);
            em.edgeEncoders.add(encoder);

            for (Map.Entry<String, TagParser> entry : encoder.createTagParsers(getPrefix(encoder)).entrySet()) {
                if (addEncodedValues) {
                    if (entry.getValue() == null) {
                        if (!em.parsers.containsKey(entry.getKey()))
                            throw new IllegalArgumentException("FlagEncoder " + encoder.toString() + " requires the TagParser&EncodedValue '" + entry.getKey() + "' and this must be created before adding the FlagEncoder");

                    } else {
                        add(entry.getValue());
                    }
                } else {
                    if (!em.parsers.containsKey(entry.getKey()))
                        throw new IllegalArgumentException("FlagEncoder " + encoder.toString() + " requires the TagParser&EncodedValue '" + entry.getKey() + "' and this must be created before adding the FlagEncoder");
                }
            }

            return this;
        }

        public EncodingManager build() {
            check();

            int bits = 0;
            int maxBits = em.extendedDataSize * 8;
            EncodedValue.InitializerConfig initializer = new EncodedValue.InitializerConfig();
            for (EncodedValue ev : em.encodedValueList) {
                bits += ev.init(initializer);

                if (bits >= maxBits)
                    throw new IllegalArgumentException("Too few space reserved for EncodedValues data. Maximum bits: " + maxBits + ", requested " + bits +
                            ". Current EncodedValue " + ev.toString() + ", all: " + em.parsers +
                            ". Decrease the number of vehicles or increase graph.bytes_for_flags to allow more bytes");
            }

            if (em.edgeEncoders.isEmpty()) {
                // TODO NOW it should not fail when using 0 bits, if 0 it seems that the storage access is wrong or overwrites the bit space reserved for the waygeometry.
                em.bitsForEdgeFlags = 4 * 8;

            } else {

                for (FlagEncoder flagEncoder : em.edgeEncoders) {
                    AbstractFlagEncoder encoder = (AbstractFlagEncoder) flagEncoder;

                    int currentEncoderIndex = em.edgeEncoders.size();
                    int usedBits = encoder.defineNodeBits(currentEncoderIndex, nextNodeBit);
                    if (usedBits > em.bitsForEdgeFlags)
                        throw new IllegalArgumentException(String.format(ERR, usedBits, em.bitsForEdgeFlags, "node"));
                    encoder.setNodeBitMask(usedBits - nextNodeBit, nextNodeBit);
                    nextNodeBit = usedBits;

                    encoder.initEncodedValues(getPrefix(encoder), currentEncoderIndex);
                    encoder.setRegistered(true);
                    usedBits = encoder.defineRelationBits(currentEncoderIndex, nextRelBit);
                    if (usedBits > em.bitsForEdgeFlags)
                        throw new IllegalArgumentException(String.format(ERR, usedBits, em.bitsForEdgeFlags, "relation"));
                    encoder.setRelBitMask(usedBits - nextRelBit, nextRelBit);
                    nextRelBit = usedBits;

                    // turn flag bits are independent from edge encoder bits
                    usedBits = encoder.defineTurnBits(currentEncoderIndex, nextTurnBit);
                    if (usedBits > em.bitsForTurnFlags)
                        throw new IllegalArgumentException(String.format(ERR, usedBits, em.bitsForTurnFlags, "turn"));
                    nextTurnBit = usedBits;

                    if (em.bitsForEdgeFlags == 0)
                        throw new IllegalStateException("bytes_for_flags was not specified?");
                }
            }

            buildCalled = true;
            return em;
        }

        static String getPrefix(FlagEncoder encoder) {
            return encoder.toString() + ".";
        }

        private void check() {
            if (buildCalled)
                throw new IllegalStateException("EncodingManager.Builder.build() already called");
        }

        public void addEncodedValue(EncodedValue encodedValue) {
            em.encodedValueList.add(encodedValue);
            em.encodedValueMap.put(encodedValue.getName(), encodedValue);
        }
    }

    // TODO move later into builder
    public void setEnableInstructions(boolean enableInstructions) {
        this.enableInstructions = enableInstructions;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null)
            throw new IllegalArgumentException("preferred language cannot be null");

        this.preferredLanguage = preferredLanguage;
    }

    /**
     * Size of arbitrary storage per edge and in bytes
     */
    public int getExtendedDataSize() {
        return extendedDataSize;
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

    @Override
    public StringEncodedValue getStringEncodedValue(String key) {
        return getEncodedValue(key, StringEncodedValue.class);
    }

    @Override
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
        EncodedValue ev = encodedValueMap.get(key);
        if (ev == null)
            throw new IllegalArgumentException("Cannot find encoded value " + key + " in collection: " + ev);
        return (T) ev;
    }

    static String fixWayName(String str) {
        if (str == null)
            return "";
        return str.replaceAll(";[ ]*", ", ");
    }

    static List<FlagEncoder> parseEncoderString(FlagEncoderFactory factory, String encoderList) {
        if (encoderList.contains(":"))
            throw new IllegalArgumentException("EncodingManager does no longer use reflection: instantiate encoders directly.");

        if (!encoderList.equals(encoderList.toLowerCase()))
            throw new IllegalArgumentException("Since 0.7 the EncodingManager does no longer accept upper case vehicles: " + encoderList);

        String[] entries = encoderList.split(",");
        List<FlagEncoder> resultEncoders = new ArrayList<>();

        for (String entry : entries) {
            entry = entry.trim().toLowerCase();
            if (entry.isEmpty())
                continue;

            String entryVal = "";
            if (entry.contains("|")) {
                entryVal = entry;
                entry = entry.split("\\|")[0];
            }
            PMap configuration = new PMap(entryVal);

            FlagEncoder fe = factory.createFlagEncoder(entry, configuration);

            if (configuration.has("version") && fe.getVersion() != configuration.getInt("version", -1))
                throw new IllegalArgumentException("Encoder " + entry + " was used in version "
                        + configuration.getLong("version", -1) + ", but current version is " + fe.getVersion());

            resultEncoders.add(fe);
        }
        return resultEncoders;
    }

    /**
     * Create the EncodingManager from the provided GraphHopper location. Throws an
     * IllegalStateException if it fails. Used if no EncodingManager specified on load.
     */
    public static EncodingManager create(FlagEncoderFactory factory, GHJson json, String ghLoc) {
        Directory dir = new RAMDirectory(ghLoc, true);
        StorableProperties properties = new StorableProperties(dir, json);
        if (!properties.loadExisting())
            throw new IllegalStateException("Cannot load properties to fetch EncodingManager configuration at: " + dir.getLocation());

        // check encoding for compatibility
        properties.checkVersions(false);
        String acceptStr = properties.get("graph.flag_encoders");

        if (acceptStr.isEmpty())
            throw new IllegalStateException("EncodingManager was not configured. And no one was found in the graph: " + dir.getLocation());

        int bytesForFlags = 4;
        if ("8".equals(properties.get("graph.bytes_for_flags")))
            bytesForFlags = 8;

        // TODO NOW create EncodingManager from ghLoc or storableProperties!
        EncodingManager.Builder builder = new EncodingManager.Builder();
        builder.addAll(factory, acceptStr, bytesForFlags, true);
        return builder.build();
    }

    @JsonIgnore
    public int getBytesForFlags() {
        return bitsForEdgeFlags / 8;
    }

    /**
     * @return true if the specified encoder or encoded value is found
     */
    @Override
    public boolean supports(String key) {
        if (encodedValueMap.containsKey(key))
            return true;
        if (edgeEncoders.isEmpty())
            return key.equals(ENCODER_NAME);
        return getEncoder(key, false) != null;
    }

    public FlagEncoder getEncoder(String name) {
        return getEncoder(name, true);
    }

    private FlagEncoder getEncoder(String name, boolean throwExc) {
        if (name.equals(ENCODER_NAME))
            // TODO NOW too dangerous?
            return null;

        for (FlagEncoder encoder : edgeEncoders) {
            if (name.equalsIgnoreCase(encoder.toString()))
                return encoder;
        }
        if (throwExc)
            throw new IllegalArgumentException("Encoder for " + name + " not found. Existing: " + toDetailsString());
        return null;
    }

    /**
     * Determine whether a way is routable for one of the added encoders.
     */
    public boolean acceptWay(ReaderWay way, AcceptWay acceptWay) {
        if (!acceptWay.isEmpty())
            throw new IllegalArgumentException("AcceptWay must be empty");

        for (AbstractFlagEncoder encoder : edgeEncoders) {
            acceptWay.put(encoder.toString(), encoder.getAccess(way));
        }
        for (Map.Entry<String, TagParser> e : parsers.entrySet()) {
            if (e.getValue().getReadWayFilter().accept(way)) {
                // TODO NOW how to trigger ferry parsing?
                acceptWay.put(e.getKey(), Access.WAY);
            }
        }
        return acceptWay.hasAccepted();
    }

    public static class AcceptWay {
        private Map<String, Access> accessMap;
        boolean hasAccepted = false;

        public AcceptWay() {
            this.accessMap = new HashMap<>(5);
        }

        private Access get(String key) {
            Access res = accessMap.get(key);
            if (res == null)
                throw new IllegalArgumentException("Couldn't fetch access value for key " + key);

            return res;
        }

        private AcceptWay put(String key, Access access) {
            accessMap.put(key, access);
            if (access != Access.CAN_SKIP)
                hasAccepted = true;
            return this;
        }

        public boolean isEmpty() {
            return accessMap.isEmpty();
        }

        public boolean hasAccepted() {
            return hasAccepted;
        }

        private boolean has(String key) {
            return accessMap.containsKey(key);
        }
    }

    public enum Access {
        WAY, FERRY, OTHER, CAN_SKIP;

        boolean isFerry() {
            return this.ordinal() == FERRY.ordinal();
        }

        boolean isWay() {
            return this.ordinal() == WAY.ordinal();
        }

        boolean isOther() {
            return this.ordinal() == OTHER.ordinal();
        }

        boolean canSkip() {
            return this.ordinal() == CAN_SKIP.ordinal();
        }
    }

    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            flags |= encoder.handleRelationTags(relation, oldRelationFlags);
        }

        return flags;
    }

    /**
     * Processes way properties of different kind to determine speed and direction. Properties are
     * directly encoded in the provided IntsRef.
     *
     * @param relationFlags The preprocessed relation flags is used to influence the way properties.
     */
    public IntsRef handleWayTags(IntsRef ints, ReaderWay way, AcceptWay acceptWay, long relationFlags) {
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            if (acceptWay.has(encoder.toString()))
                encoder.handleWayTags(ints, way, acceptWay.get(encoder.toString()), relationFlags & encoder.getRelBitMask());
        }

        try {
            for (TagParser tagParser : parsers.values()) {
                if (tagParser.getReadWayFilter().accept(way))
                    // parsing should allow to call edgeState.set multiple times (e.g. for composed values) without reimplementing this set method
                    tagParser.parse(ints, way);
            }
        } catch (Exception ex) {
            // TODO for now do not stop when there are errors
            LOGGER.error("Cannot parse way to store edge properties. Way: " + way, ex);
        }
        return ints;
    }

    /**
     * Analyze tags on osm node. Store node tags (barriers etc) for later usage while parsing way.
     */
    public long handleNodeTags(ReaderNode node) {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            flags |= encoder.handleNodeTags(node);
        }

        return flags;
    }

    /**
     * Method called after edge is created
     */
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

        for (AbstractFlagEncoder encoder : edgeEncoders) {
            encoder.applyWayTags(way, edge);
        }
    }

    /**
     * The returned list is never empty.
     */
    public List<FlagEncoder> fetchEdgeEncoders() {
        List<FlagEncoder> list = new ArrayList<>();
        list.addAll(edgeEncoders);
        return list;
    }

    public boolean needsTurnCostsSupport() {
        for (FlagEncoder encoder : edgeEncoders) {
            if (encoder.supports(TurnWeighting.class))
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + (this.edgeEncoders != null ? this.edgeEncoders.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        final EncodingManager other = (EncodingManager) obj;
        if (this.edgeEncoders != other.edgeEncoders
                && (this.edgeEncoders == null || !this.edgeEncoders.equals(other.edgeEncoders))) {
            return false;
        }
        return true;
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

    void parsersToString(StringBuilder str) {
        for (TagParser p : parsers.values()) {
            if (str.length() > 0)
                str.append(", ");
            str.append(p.getName());
        }
    }

    public String toDetailsString() {
        StringBuilder str = new StringBuilder();
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            if (str.length() > 0)
                str.append(",");

            str.append(encoder.toString())
                    .append("|")
                    .append(encoder.getPropertiesString())
                    .append("|version=")
                    .append(encoder.getVersion());
        }
        parsersToString(str);
        return str.toString();
    }
}
