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

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.util.parsers.OSMRoundaboutParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.routing.util.parsers.TagParserFactory;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import java.util.*;

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
    private static final String ERR = "Encoders are requesting %s bits, more than %s bits of %s flags. ";
    private final List<AbstractFlagEncoder> edgeEncoders = new ArrayList<>();
    private final Map<String, EncodedValue> encodedValueMap = new LinkedHashMap<>();
    private final List<TagParser> tagParserList = new ArrayList<>();
    private final int bitsForEdgeFlags;
    private final int bitsForTurnFlags = 8 * 4;
    private int nextNodeBit = 0;
    private int nextRelBit = 0;
    private int nextTurnBit = 0;
    private boolean enableInstructions = true;
    private String preferredLanguage = "";
    private EncodedValue.InitializerConfig config;

    /**
     * Instantiate manager with the given list of encoders. The manager knows several default
     * encoders ignoring case.
     * <p>
     *
     * @param flagEncodersStr comma delimited list of encoders. The order does not matter.
     */
    public static EncodingManager create(String flagEncodersStr) {
        return create(flagEncodersStr, 4);
    }

    public static EncodingManager create(String flagEncodersStr, int bytesForEdgeFlags) {
        return create(new DefaultFlagEncoderFactory(), flagEncodersStr, bytesForEdgeFlags);
    }

    public static EncodingManager create(FlagEncoderFactory factory, String flagEncodersStr, int bytesForEdgeFlags) {
        return createBuilder(factory, flagEncodersStr, bytesForEdgeFlags).build();
    }

    public static EncodingManager.Builder createBuilder(FlagEncoderFactory factory, String flagEncodersStr, int bytesForEdgeFlags) {
        return createBuilder(parseEncoderString(factory, flagEncodersStr), bytesForEdgeFlags);
    }

    /**
     * Instantiate manager with the given list of encoders.
     *
     * @param flagEncoders comma delimited list of encoders. The order does not matter.
     */
    public static EncodingManager create(FlagEncoder... flagEncoders) {
        return create(Arrays.asList(flagEncoders));
    }

    /**
     * Instantiate manager with the given list of encoders.
     *
     * @param flagEncoders comma delimited list of encoders. The order does not matter.
     */
    public static EncodingManager create(List<? extends FlagEncoder> flagEncoders) {
        return create(flagEncoders, 4);
    }

    public static EncodingManager create(List<? extends FlagEncoder> flagEncoders, int bytesForEdgeFlags) {
        return createBuilder(flagEncoders, bytesForEdgeFlags).build();
    }

    private static EncodingManager.Builder createBuilder(List<? extends FlagEncoder> flagEncoders, int bytesForEdgeFlags) {
        Builder builder = new Builder(bytesForEdgeFlags);
        for (FlagEncoder flagEncoder : flagEncoders) {
            builder.add(flagEncoder);
        }
        return builder;
    }

    /**
     * Create the EncodingManager from the provided GraphHopper location. Throws an
     * IllegalStateException if it fails. Used if no EncodingManager specified on load.
     */
    public static EncodingManager create(EncodedValueFactory evFactory, FlagEncoderFactory flagEncoderFactory, String ghLoc) {
        Directory dir = new RAMDirectory(ghLoc, true);
        StorableProperties properties = new StorableProperties(dir);
        if (!properties.loadExisting())
            throw new IllegalStateException("Cannot load properties to fetch EncodingManager configuration at: "
                    + dir.getLocation());

        // check encoding for compatibility
        properties.checkVersions(false);
        int bytesForFlags = 4;
        try {
            bytesForFlags = Integer.parseInt(properties.get("graph.bytes_for_flags"));
        } catch (NumberFormatException ex) {
        }

        EncodingManager.Builder builder = new EncodingManager.Builder(bytesForFlags, false);
        String encodedValuesStr = properties.get("graph.encoded_values");
        if (!Helper.isEmpty(encodedValuesStr))
            builder.addAll(evFactory, encodedValuesStr);
        String flagEncoderValuesStr = properties.get("graph.flag_encoders");
        if (!Helper.isEmpty(flagEncoderValuesStr))
            builder.addAll(flagEncoderFactory, flagEncoderValuesStr);

        if (Helper.isEmpty(flagEncoderValuesStr) && Helper.isEmpty(encodedValuesStr))
            throw new IllegalStateException("EncodingManager was not configured. And no one was found in the graph: "
                    + dir.getLocation());


        return builder.build();
    }

    /**
     * Starts the build process of an EncodingManager
     */
    public static Builder start() {
        return new Builder(4);
    }

    private EncodingManager(int bytes) {
        if (bytes <= 0 || (bytes / 4) * 4 != bytes)
            throw new IllegalStateException("bytesForEdgeFlags can be only a multiple of 4");

        this.bitsForEdgeFlags = bytes * 8;
        this.config = new EncodedValue.InitializerConfig();
    }

    public static class Builder {
        private EncodingManager em;

        public Builder(int bytes) {
            this(bytes, true);
        }

        private Builder(int bytes, boolean addRoundabout) {
            em = new EncodingManager(bytes);
            if (addRoundabout)
                add(new OSMRoundaboutParser());
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

        /**
         * For backward compatibility provide a way to add multiple FlagEncoders
         */
        public Builder addAll(FlagEncoderFactory factory, String flagEncodersStr) {
            for (FlagEncoder fe : parseEncoderString(factory, flagEncodersStr)) {
                add(fe);
            }
            return this;
        }

        public Builder addAll(EncodedValueFactory factory, String encodedValueString) {
            em.add(this, factory, encodedValueString);
            return this;
        }

        public Builder addAll(TagParserFactory factory, String tagParserString) {
            em.add(this, factory, tagParserString);
            return this;
        }

        public Builder add(FlagEncoder encoder) {
            check();
            em.addEncoder((AbstractFlagEncoder) encoder);
            return this;
        }

        public Builder add(EncodedValue encodedValue) {
            check();
            if (!em.edgeEncoders.isEmpty())
                throw new IllegalArgumentException("Always add shared EncodedValues before FlagEncoders to ensure they can be loaded first");

            em.addEncodedValue(encodedValue, false);
            return this;
        }

        /**
         * This method adds the specified TagParser and automatically adds EncodedValues as requested in
         * createEncodedValues.
         */
        public Builder add(TagParser tagParser) {
            List<EncodedValue> list = new ArrayList<>();
            tagParser.createEncodedValues(em, list);
            for (EncodedValue ev : list) {
                em.addEncodedValue(ev, false);
            }
            em.tagParserList.add(tagParser);
            return this;
        }

        private void check() {
            if (em == null)
                throw new IllegalStateException("Cannot call method after Builder.build() was called");
        }

        public EncodingManager build() {
            check();
            if (em.encodedValueMap.isEmpty())
                throw new IllegalStateException("No EncodedValues found");

            EncodingManager tmp = em;
            em = null;
            return tmp;
        }
    }

    static List<FlagEncoder> parseEncoderString(FlagEncoderFactory factory, String encoderList) {
        if (encoderList.contains(":"))
            throw new IllegalArgumentException("EncodingManager does no longer use reflection instantiate encoders directly.");

        if (!encoderList.equals(toLowerCase(encoderList)))
            throw new IllegalArgumentException("Since 0.7 EncodingManager does no longer accept upper case profiles: " + encoderList);

        String[] entries = encoderList.split(",");
        List<FlagEncoder> resultEncoders = new ArrayList<>();

        for (String entry : entries) {
            entry = toLowerCase(entry.trim());
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

    private void add(Builder builder, EncodedValueFactory factory, String evList) {
        if (!evList.equals(toLowerCase(evList)))
            throw new IllegalArgumentException("Use lower case for EncodedValues: " + evList);

        for (String entry : evList.split(",")) {
            entry = toLowerCase(entry.trim());
            if (entry.isEmpty())
                continue;

            EncodedValue evObject = factory.create(entry);
            builder.add(evObject);
            PMap map = new PMap(entry);
            if (!map.has("version"))
                throw new IllegalArgumentException("encoded value must have a version specified but it was " + entry);

            int version = map.getInt("version", Integer.MIN_VALUE);
            int stored = evObject.getVersion();
            if (stored != version)
                throw new IllegalArgumentException("Version of EncodedValue " + evObject + " does not match " + entry + ". Stored " + stored + " vs. in code " + version);
        }
    }

    private void add(Builder builder, TagParserFactory factory, String tpList) {
        if (!tpList.equals(toLowerCase(tpList)))
            throw new IllegalArgumentException("Use lower case for TagParser: " + tpList);

        for (String entry : tpList.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty())
                continue;

            PMap map = new PMap(entry);
            TagParser tp = factory.create(entry, map);
            builder.add(tp);
        }
    }

    static String fixWayName(String str) {
        if (str == null)
            return "";
        return str.replaceAll(";[ ]*", ", ");
    }

    public int getBytesForFlags() {
        return bitsForEdgeFlags / 8;
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

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    private void addEncoder(AbstractFlagEncoder encoder) {
        if (encoder.isRegistered())
            throw new IllegalStateException("You must not register a FlagEncoder (" + encoder.toString() + ") twice!");

        for (FlagEncoder fe : edgeEncoders) {
            if (fe.toString().equals(encoder.toString()))
                throw new IllegalArgumentException("Cannot register edge encoder. Name already exists: " + fe.toString());
        }

        encoder.setRegistered(true);

        int encoderCount = edgeEncoders.size();
        int usedBits = encoder.defineNodeBits(encoderCount, nextNodeBit);
        if (usedBits > bitsForEdgeFlags)
            throw new IllegalArgumentException(String.format(Locale.ROOT, ERR, usedBits, bitsForEdgeFlags, "node"));
        encoder.setNodeBitMask(usedBits - nextNodeBit, nextNodeBit);
        nextNodeBit = usedBits;

        encoder.setEncodedValueLookup(this);
        List<EncodedValue> list = new ArrayList<>();
        encoder.createEncodedValues(list, encoder.toString(), encoderCount);
        for (EncodedValue ev : list) {
            addEncodedValue(ev, true);
        }

        usedBits = encoder.defineRelationBits(encoderCount, nextRelBit);
        if (usedBits > bitsForEdgeFlags)
            throw new IllegalArgumentException(String.format(Locale.ROOT, ERR, usedBits, bitsForEdgeFlags, "relation"));
        encoder.setRelBitMask(usedBits - nextRelBit, nextRelBit);
        nextRelBit = usedBits;

        // turn flag bits are independent from edge encoder bits
        usedBits = encoder.defineTurnBits(encoderCount, nextTurnBit);
        if (usedBits > bitsForTurnFlags)
            throw new IllegalArgumentException(String.format(Locale.ROOT, ERR, usedBits, bitsForTurnFlags, "turn"));
        nextTurnBit = usedBits;

        edgeEncoders.add(encoder);
    }

    private void addEncodedValue(EncodedValue ev, boolean encValBoundToFlagEncoder) {
        if (encodedValueMap.containsKey(ev.getName()))
            throw new IllegalStateException("EncodedValue " + ev.getName() + " already exists " + encodedValueMap.get(ev.getName()) + " vs " + ev);
        if (!encValBoundToFlagEncoder && ev.getName().contains(SPECIAL_SEPARATOR))
            throw new IllegalArgumentException("EncodedValue " + ev.getName() + " must not contain '" + SPECIAL_SEPARATOR + "' as reserved for FlagEncoder");

        ev.init(config);
        if (config.getRequiredBits() > getBytesForFlags() * 8)
            throw new IllegalArgumentException(String.format(Locale.ROOT, ERR + "(Attempt to add EncodedValue " + ev.getName() + ") ",
                    config.getRequiredBits(), bitsForEdgeFlags, "edge") +
                    "Decrease the number of vehicles or increase the flags to more bytes via graph.bytes_for_flags: " + (config.getRequiredBits() / 8));

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
            throw new IllegalArgumentException("Encoder for " + name + " not found. Existing: " + toFlagEncodersAsString());
        return null;
    }

    /**
     * Determine whether a way is routable for one of the added encoders.
     *
     * @return if at least one encoder consumes the specified way. Additionally the specified acceptWay is changed
     * to provide more details.
     */
    public boolean acceptWay(ReaderWay way, AcceptWay acceptWay) {
        if (!acceptWay.isEmpty())
            throw new IllegalArgumentException("AcceptWay must be empty");

        for (AbstractFlagEncoder encoder : edgeEncoders) {
            acceptWay.put(encoder.toString(), encoder.getAccess(way));
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
                throw new IllegalArgumentException("Couldn't fetch Access value for encoder key " + key);

            return res;
        }

        public AcceptWay put(String key, Access access) {
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

        public Access getAccess() {
            if (accessMap.isEmpty())
                throw new IllegalStateException("Cannot determine Access if map is empty");
            return accessMap.values().iterator().next();
        }
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

    public long handleRelationTags(long oldRelationFlags, ReaderRelation relation) {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            flags |= encoder.handleRelationTags(oldRelationFlags, relation);
        }

        return flags;
    }

    /**
     * Processes way properties of different kind to determine speed and direction. Properties are
     * directly encoded in 8 bytes.
     *
     * @param relationFlags The preprocessed relation flags is used to influence the way properties.
     */
    public IntsRef handleWayTags(ReaderWay way, AcceptWay acceptWay, long relationFlags) {
        IntsRef edgeFlags = createEdgeFlags();
        // return if way or ferry
        Access access = acceptWay.getAccess();
        for (TagParser parser : tagParserList) {
            parser.handleWayTags(edgeFlags, way, access, relationFlags);
        }
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            encoder.handleWayTags(edgeFlags, way, acceptWay.get(encoder.toString()), relationFlags & encoder.getRelBitMask());
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
                    .append(encoder.getPropertiesString())
                    .append("|version=")
                    .append(encoder.getVersion());
        }

        return str.toString();
    }

    public String toEncodedValuesAsString() {
        StringBuilder str = new StringBuilder();
        for (EncodedValue ev : encodedValueMap.values()) {
            if (ev.getName().contains(SPECIAL_SEPARATOR))
                continue;

            if (str.length() > 0)
                str.append(",");

            str.append(ev.toString());
        }

        return str.toString();
    }

    // TODO hide IntsRef even more in a later version: https://gist.github.com/karussell/f4c2b2b1191be978d7ee9ec8dd2cd48f
    public IntsRef createEdgeFlags() {
        return new IntsRef(bitsForEdgeFlags / 32);
    }

    public IntsRef flagsDefault(boolean forward, boolean backward) {
        IntsRef intsRef = createEdgeFlags();
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            encoder.flagsDefault(intsRef, forward, backward);
        }
        return intsRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncodingManager that = (EncodingManager) o;
        return bitsForEdgeFlags == that.bitsForEdgeFlags &&
                enableInstructions == that.enableInstructions &&
                edgeEncoders.equals(that.edgeEncoders) &&
                encodedValueMap.equals(that.encodedValueMap) &&
                preferredLanguage.equals(that.preferredLanguage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeEncoders, encodedValueMap, bitsForEdgeFlags, enableInstructions, preferredLanguage);
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
        return new ArrayList<FlagEncoder>(edgeEncoders);
    }

    public boolean needsTurnCostsSupport() {
        for (FlagEncoder encoder : edgeEncoders) {
            if (encoder.supports(TurnWeighting.class))
                return true;
        }
        return false;
    }

    public List<BooleanEncodedValue> getAccessEncFromNodeFlags(long importNodeFlags) {
        List<BooleanEncodedValue> list = new ArrayList<>(edgeEncoders.size());
        for (int i = 0; i < edgeEncoders.size(); i++) {
            FlagEncoder encoder = edgeEncoders.get(i);
            if (((1L << i) & importNodeFlags) != 0)
                list.add(encoder.getAccessEnc());
        }
        return list;
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
    @SuppressWarnings("unchecked")
    public <T extends Enum> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> type) {
        return (EnumEncodedValue<T>) getEncodedValue(key, EnumEncodedValue.class);
    }

    @Override
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
        EncodedValue ev = encodedValueMap.get(key);
        if (ev == null)
            throw new IllegalArgumentException("Cannot find EncodedValue " + key + " in collection: " + ev);
        return (T) ev;
    }

    private static String SPECIAL_SEPARATOR = "-";

    /**
     * All EncodedValue names that are created from a FlagEncoder should use this method to mark them as
     * "none-shared" across the other FlagEncoders. E.g. average_speed for the CarFlagEncoder will
     * be named car-average_speed
     */
    public static String getKey(FlagEncoder encoder, String str) {
        return getKey(encoder.toString(), str);
    }

    public static String getKey(String prefix, String str) {
        return prefix + SPECIAL_SEPARATOR + str;
    }
}