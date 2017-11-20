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
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.graphhopper.util.Helper.toLowerCase;

/**
 * Manager class to register encoder, assign their flag values and check objects with all encoders
 * during parsing.
 * <p>
 *
 * @author Peter Karich
 * @author Nop
 */
public class EncodingManager {
    private static final String ERR = "Encoders are requesting %s bits, more than %s bits of %s flags. ";
    private static final String WAY_ERR = "Decrease the number of vehicles or increase the flags to take long via graph.bytes_for_flags=8";
    private final List<AbstractFlagEncoder> edgeEncoders = new ArrayList<AbstractFlagEncoder>();
    private final int bitsForEdgeFlags;
    private final int bitsForTurnFlags = 8 * 4;
    private int nextWayBit = 0;
    private int nextNodeBit = 0;
    private int nextRelBit = 0;
    private int nextTurnBit = 0;
    private boolean enableInstructions = true;
    private String preferredLanguage = "";

    /**
     * Instantiate manager with the given list of encoders. The manager knows several default
     * encoders ignoring case.
     * <p>
     *
     * @param flagEncodersStr comma delimited list of encoders. The order does not matter.
     */
    public EncodingManager(String flagEncodersStr) {
        this(flagEncodersStr, 4);
    }

    public EncodingManager(String flagEncodersStr, int bytesForEdgeFlags) {
        this(FlagEncoderFactory.DEFAULT, flagEncodersStr, bytesForEdgeFlags);
    }

    public EncodingManager(FlagEncoderFactory factory, String flagEncodersStr, int bytesForEdgeFlags) {
        this(parseEncoderString(factory, flagEncodersStr), bytesForEdgeFlags);
    }

    /**
     * Instantiate manager with the given list of encoders.
     * <p>
     *
     * @param flagEncoders comma delimited list of encoders. The order does not matter.
     */
    public EncodingManager(FlagEncoder... flagEncoders) {
        this(Arrays.asList(flagEncoders));
    }

    /**
     * Instantiate manager with the given list of encoders.
     * <p>
     *
     * @param flagEncoders comma delimited list of encoders. The order does not matter.
     */
    public EncodingManager(List<? extends FlagEncoder> flagEncoders) {
        this(flagEncoders, 4);
    }

    public EncodingManager(List<? extends FlagEncoder> flagEncoders, int bytesForEdgeFlags) {
        if (bytesForEdgeFlags != 4 && bytesForEdgeFlags != 8)
            throw new IllegalStateException("For 'edge flags' currently only 4 or 8 bytes supported");

        this.bitsForEdgeFlags = bytesForEdgeFlags * 8;
        for (FlagEncoder flagEncoder : flagEncoders) {
            registerEncoder((AbstractFlagEncoder) flagEncoder);
        }

        if (edgeEncoders.isEmpty())
            throw new IllegalStateException("No vehicles found");
    }

    static List<FlagEncoder> parseEncoderString(FlagEncoderFactory factory, String encoderList) {
        if (encoderList.contains(":"))
            throw new IllegalArgumentException("EncodingManager does no longer use reflection instantiate encoders directly.");

        if (!encoderList.equals(toLowerCase(encoderList)))
            throw new IllegalArgumentException("Since 0.7 EncodingManager does no longer accept upper case profiles: " + encoderList);

        String[] entries = encoderList.split(",");
        List<FlagEncoder> resultEncoders = new ArrayList<FlagEncoder>();

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

    static String fixWayName(String str) {
        if (str == null)
            return "";
        return str.replaceAll(";[ ]*", ", ");
    }

    /**
     * Create the EncodingManager from the provided GraphHopper location. Throws an
     * IllegalStateException if it fails. Used if no EncodingManager specified on load.
     */
    public static EncodingManager create(FlagEncoderFactory factory, String ghLoc) {
        Directory dir = new RAMDirectory(ghLoc, true);
        StorableProperties properties = new StorableProperties(dir);
        if (!properties.loadExisting())
            throw new IllegalStateException("Cannot load properties to fetch EncodingManager configuration at: "
                    + dir.getLocation());

        // check encoding for compatibility
        properties.checkVersions(false);
        String acceptStr = properties.get("graph.flag_encoders");

        if (acceptStr.isEmpty())
            throw new IllegalStateException("EncodingManager was not configured. And no one was found in the graph: "
                    + dir.getLocation());

        int bytesForFlags = 4;
        if ("8".equals(properties.get("graph.bytes_for_flags")))
            bytesForFlags = 8;
        return new EncodingManager(factory, acceptStr, bytesForFlags);
    }

    public int getBytesForFlags() {
        return bitsForEdgeFlags / 8;
    }

    private void registerEncoder(AbstractFlagEncoder encoder) {
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

        usedBits = encoder.defineWayBits(encoderCount, nextWayBit);
        if (usedBits > bitsForEdgeFlags)
            throw new IllegalArgumentException(String.format(Locale.ROOT, ERR, usedBits, bitsForEdgeFlags, "way") + WAY_ERR);
        encoder.setWayBitMask(usedBits - nextWayBit, nextWayBit);
        nextWayBit = usedBits;

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

    /**
     * @return true if the specified encoder is found
     */
    public boolean supports(String encoder) {
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
            throw new IllegalArgumentException("Encoder for " + name + " not found. Existing: " + toDetailsString());
        return null;
    }

    /**
     * Determine whether a way is routable for one of the added encoders.
     */
    public long acceptWay(ReaderWay way) {
        long includeWay = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            includeWay |= encoder.acceptWay(way);
        }

        return includeWay;
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
     * directly encoded in 8 bytes.
     * <p>
     *
     * @param relationFlags The preprocessed relation flags is used to influence the way properties.
     * @return the encoded flags
     */
    public long handleWayTags(ReaderWay way, long includeWay, long relationFlags) {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            flags |= encoder.handleWayTags(way, includeWay, relationFlags & encoder.getRelBitMask());
        }

        return flags;
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

        return str.toString();
    }

    public long flagsDefault(boolean forward, boolean backward) {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders) {
            flags |= encoder.flagsDefault(forward, backward);
        }
        return flags;
    }

    /**
     * Reverse flags, to do so all encoders are called.
     */
    public long reverseFlags(long flags) {
        // performance critical
        int len = edgeEncoders.size();
        for (int i = 0; i < len; i++) {
            flags = edgeEncoders.get(i).reverseFlags(flags);
        }
        return flags;
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

    public EncodingManager setEnableInstructions(boolean enableInstructions) {
        this.enableInstructions = enableInstructions;
        return this;
    }

    public EncodingManager setPreferredLanguage(String preferredLanguage) {
        if (preferredLanguage == null)
            throw new IllegalArgumentException("preferred language cannot be null");

        this.preferredLanguage = preferredLanguage;
        return this;
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
        List<FlagEncoder> list = new ArrayList<FlagEncoder>();
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
}
