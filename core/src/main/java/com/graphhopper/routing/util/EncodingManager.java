/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.ITurnCostTableEntry;
import com.graphhopper.reader.Node;
import com.graphhopper.reader.Relation;
import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.TurnRelation;
import com.graphhopper.reader.Way;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.StorableProperties;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

/**
 * Manager class to register encoder, assign their flag values and check objects with all encoders
 * during parsing.
 * <p/>
 * @author Peter Karich
 * @author Nop
 */
public class EncodingManager
{
    public static final String CAR = "car";
    public static final String BIKE = "bike";
    public static final String BIKE2 = "bike2";
    public static final String RACINGBIKE = "racingbike";
    public static final String MOUNTAINBIKE = "mtb";
    public static final String FOOT = "foot";
    public static final String FOOT2 = "foot2";
    public static final String MOTORCYCLE = "motorcycle";

    private final List<AbstractFlagEncoder> edgeEncoders = new ArrayList<AbstractFlagEncoder>();

    private int nextWayBit = 0;
    private int nextNodeBit = 0;
    private int nextRelBit = 0;
    private int nextTurnBit = 0;
    private final int bitsForEdgeFlags;
    private final int bitsForTurnFlags = 8 * 4;
    private boolean enableInstructions = true;

    /**
     * Instantiate manager with the given list of encoders. The manager knows the default encoders:
     * CAR, FOOT and BIKE (ignoring the case). Custom encoders can be specified by giving a full
     * class name e.g. "car:com.graphhopper.myproject.MyCarEncoder"
     * <p/>
     * @param flagEncodersStr comma delimited list of encoders. The order does not matter.
     */
    public EncodingManager( String flagEncodersStr )
    {
        this(flagEncodersStr, 8);
    }

    public EncodingManager( String flagEncodersStr, int bytesForFlags )
    {
        this(parseEncoderString(flagEncodersStr), bytesForFlags);
    }

    /**
     * Instantiate manager with the given list of encoders.
     * <p/>
     * @param flagEncoders comma delimited list of encoders. The order does not matter.
     */
    public EncodingManager( FlagEncoder... flagEncoders )
    {
        this(Arrays.asList(flagEncoders));
    }

    /**
     * Instantiate manager with the given list of encoders.
     * <p/>
     * @param flagEncoders comma delimited list of encoders. The order does not matter.
     */
    public EncodingManager( List<? extends FlagEncoder> flagEncoders )
    {
        this(flagEncoders, 4);
    }

    public EncodingManager( List<? extends FlagEncoder> flagEncoders, int bytesForEdgeFlags )
    {
        if (bytesForEdgeFlags != 4 && bytesForEdgeFlags != 8)
            throw new IllegalStateException("For 'edge flags' currently only 4 or 8 bytes supported");

        this.bitsForEdgeFlags = bytesForEdgeFlags * 8;
        for (FlagEncoder flagEncoder : flagEncoders)
        {
            registerEncoder((AbstractFlagEncoder) flagEncoder);
        }

        if (edgeEncoders.isEmpty())
            throw new IllegalStateException("No vehicles found");
    }

    public int getBytesForFlags()
    {
        return bitsForEdgeFlags / 8;
    }

    static List<FlagEncoder> parseEncoderString( String encoderList )
    {
        if (encoderList.contains(":"))
            throw new IllegalArgumentException("EncodingManager does no longer use reflection instantiate encoders directly.");

        String[] entries = encoderList.split(",");
        List<FlagEncoder> resultEncoders = new ArrayList<FlagEncoder>();

        for (String entry : entries)
        {
            entry = entry.trim().toLowerCase();
            if (entry.isEmpty())
                continue;

            String entryVal = "";
            if (entry.contains("|"))
            {
                entryVal = entry;
                entry = entry.split("\\|")[0];
            }

            AbstractFlagEncoder fe;
            if (entry.equals(CAR))
                fe = new OsCarFlagEncoder(entryVal);

            else if (entry.equals(BIKE))
                fe = new OsBikeFlagEncoder(entryVal);

            else if (entry.equals(BIKE2))
                fe = new Bike2WeightFlagEncoder(entryVal);

            else if (entry.equals(RACINGBIKE))
                fe = new RacingBikeFlagEncoder(entryVal);

            else if (entry.equals(MOUNTAINBIKE))
                fe = new MountainBikeFlagEncoder(entryVal);

            else if (entry.equals(FOOT))
                fe = new FootFlagEncoder(entryVal);

            else if (entry.equals(FOOT2))
                fe = new OsFootFlagEncoder(entryVal);

            else if (entry.equals(MOTORCYCLE))
                fe = new MotorcycleFlagEncoder(entryVal);

            else
                throw new IllegalArgumentException("entry in encoder list not supported " + entry);

            resultEncoders.add(fe);
        }
        return resultEncoders;
    }

    private static final String ERR = "Encoders are requesting more than %s bits of %s flags. ";
    private static final String WAY_ERR = "Decrease the number of vehicles or increase the flags to take long via osmreader.bytesForFlags=8";

    private void registerEncoder( AbstractFlagEncoder encoder )
    {
        int encoderCount = edgeEncoders.size();
        int usedBits = encoder.defineNodeBits(encoderCount, nextNodeBit);
        if (usedBits > bitsForEdgeFlags)
            throw new IllegalArgumentException(String.format(ERR, bitsForEdgeFlags, "node"));
        encoder.setNodeBitMask(usedBits - nextNodeBit, nextNodeBit);
        nextNodeBit = usedBits;

        usedBits = encoder.defineWayBits(encoderCount, nextWayBit);
        if (usedBits > bitsForEdgeFlags)
            throw new IllegalArgumentException(String.format(ERR, bitsForEdgeFlags, "way") + WAY_ERR);
        encoder.setWayBitMask(usedBits - nextWayBit, nextWayBit);
        nextWayBit = usedBits;

        usedBits = encoder.defineRelationBits(encoderCount, nextRelBit);
        if (usedBits > bitsForEdgeFlags)
            throw new IllegalArgumentException(String.format(ERR, bitsForEdgeFlags, "relation"));
        encoder.setRelBitMask(usedBits - nextRelBit, nextRelBit);
        nextRelBit = usedBits;

        // turn flag bits are independent from edge encoder bits
        usedBits = encoder.defineTurnBits(encoderCount, nextTurnBit);
        if (usedBits > bitsForTurnFlags)
            throw new IllegalArgumentException(String.format(ERR, bitsForEdgeFlags, "turn"));
        nextTurnBit = usedBits;

        edgeEncoders.add(encoder);
    }

    /**
     * @return true if the specified encoder is found
     */
    public boolean supports( String encoder )
    {
        return getEncoder(encoder, false) != null;
    }

    public FlagEncoder getEncoder( String name )
    {
        return getEncoder(name, true);
    }

    private FlagEncoder getEncoder( String name, boolean throwExc )
    {
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            if (name.equalsIgnoreCase(encoder.toString()))
                return encoder;
        }
        if (throwExc)
            throw new IllegalArgumentException("Encoder for " + name + " not found. Existing: " + toDetailsString());
        return null;
    }

    /**
     * Determine whether an osm way is a routable way for one of its encoders.
     */
    public long acceptWay( Way way )
    {
        long includeWay = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            includeWay |= encoder.acceptWay(way);
        }

        return includeWay;
    }

    public long handleRelationTags( Relation relation, long oldRelationFlags )
    {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            flags |= encoder.handleRelationTags(relation, oldRelationFlags);
        }

        return flags;
    }

    /**
     * Processes way properties of different kind to determine speed and direction. Properties are
     * directly encoded in 8 bytes.
     * <p/>
     * @param relationFlags The preprocessed relation flags is used to influence the way properties.
     * @return the encoded flags
     */
    public long handleWayTags( Way way, long includeWay, long relationFlags )
    {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            flags |= encoder.handleWayTags(way, includeWay, relationFlags & encoder.getRelBitMask());
        }

        return flags;
    }

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder();
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            if (str.length() > 0)
                str.append(",");

            str.append(encoder.toString());
        }

        return str.toString();
    }

    public String toDetailsString()
    {
        StringBuilder str = new StringBuilder();
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            if (str.length() > 0)
                str.append(",");

            str.append(encoder.toString());
            str.append("|");
            str.append(encoder.getPropertiesString());
        }

        return str.toString();
    }

    public long flagsDefault( boolean forward, boolean backward )
    {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            flags |= encoder.flagsDefault(forward, backward);
        }
        return flags;
    }

    /**
     * Reverse flags, to do so all encoders are called.
     */
    public long reverseFlags( long flags )
    {
        // performance critical
        int len = edgeEncoders.size();
        for (int i = 0; i < len; i++)
        {
            flags = edgeEncoders.get(i).reverseFlags(flags);
        }
        return flags;
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
        hash = 53 * hash + (this.edgeEncoders != null ? this.edgeEncoders.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        final EncodingManager other = (EncodingManager) obj;
        if (this.edgeEncoders != other.edgeEncoders && (this.edgeEncoders == null || !this.edgeEncoders.equals(other.edgeEncoders)))
        {
            return false;
        }
        return true;
    }

    /**
     * Analyze tags on osm node. Store node tags (barriers etc) for later usage while parsing way.
     */
    public long handleNodeTags( Node node )
    {
        long flags = 0;
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            flags |= encoder.handleNodeTags(node);
        }

        return flags;
    }

    private static int determineRequiredBits( int value )
    {
        int numberOfBits = 0;
        while (value > 0)
        {
            value = value >> 1;
            numberOfBits++;
        }
        return numberOfBits;
    }

    public Collection<ITurnCostTableEntry> analyzeTurnRelation( TurnRelation turnRelation, DataReader osmReader )
    {
        TLongObjectMap<ITurnCostTableEntry> entries = new TLongObjectHashMap<ITurnCostTableEntry>();

        int encoderCount = edgeEncoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            AbstractFlagEncoder encoder = edgeEncoders.get(i);
            for (ITurnCostTableEntry entry : encoder.analyzeTurnRelation(turnRelation, osmReader))
            {
                ITurnCostTableEntry oldEntry = entries.get(entry.getItemId());
                if (oldEntry != null)
                {
                    // merging different encoders
                    long oldFlags = oldEntry.getFlags();
                    long flags = entry.getFlags();
                    oldFlags |= flags;
                    oldEntry.setFlags(oldFlags);
                } else
                {
                    entries.put(entry.getItemId(), entry);
                }
            }
        }

        return entries.valueCollection();
    }

    public EncodingManager setEnableInstructions( boolean enableInstructions )
    {
        this.enableInstructions = enableInstructions;
        return this;
    }

    public void applyWayTags( Way way, EdgeIteratorState edge )
    {
        // storing the road name does not yet depend on the flagEncoder so manage it directly
        if (enableInstructions)
        {
            // String wayInfo = carFlagEncoder.getWayInfo(way);
            // http://wiki.openstreetmap.org/wiki/Key:name
            String name = fixWayName(way.getTag("name"));
            // http://wiki.openstreetmap.org/wiki/Key:ref
            String refName = fixWayName(way.getTag("ref"));
            if (!Helper.isEmpty(refName))
            {
                if (Helper.isEmpty(name))
                    name = refName;
                else
                    name += ", " + refName;
            }

            edge.setName(name);
        }

        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            encoder.applyWayTags(way, edge);
        }
    }

    /**
     * The returned list is never empty.
     */
    public List<FlagEncoder> fetchEdgeEncoders()
    {
        List<FlagEncoder> list = new ArrayList<FlagEncoder>();
        list.addAll(edgeEncoders);
        return list;
    }

    static String fixWayName( String str )
    {
        if (str == null)
            return "";
        return str.replaceAll(";[ ]*", ", ");
    }

    public boolean needsTurnCostsSupport()
    {
        for (FlagEncoder encoder : edgeEncoders)
        {
            if (encoder.supports(TurnWeighting.class))
                return true;
        }
        return false;
    }

    /**
     * Create the EncodingManager from the provided GraphHopper location. Throws an
     * IllegalStateException if it fails.
     */
    public static EncodingManager create( String ghLoc )
    {
        Directory dir = new RAMDirectory(ghLoc, true);
        StorableProperties properties = new StorableProperties(dir);
        if (!properties.loadExisting())
            throw new IllegalStateException("Cannot load properties to fetch EncodingManager configuration at: "
                    + dir.getLocation());

        // check encoding for compatiblity
        properties.checkVersions(false);
        String acceptStr = properties.get("graph.flagEncoders");

        if (acceptStr.isEmpty())
            throw new IllegalStateException("EncodingManager was not configured. And no one was found in the graph: "
                    + dir.getLocation());

        int bytesForFlags = 4;
        if ("8".equals(properties.get("graph.bytesForFlags")))
            bytesForFlags = 8;
        return new EncodingManager(acceptStr, bytesForFlags);
    }

    public boolean isVehicleQualifierTypeIncluded(RoutingElement routingElement) {
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            if (encoder.isVehicleQualifierTypeIncluded(routingElement))
                return true;
        }
        return false;
    }
    public boolean isVehicleQualifierTypeExcluded(RoutingElement routingElement) {
        for (AbstractFlagEncoder encoder : edgeEncoders)
        {
            if (encoder.isVehicleQualifierTypeExcluded(routingElement))
                return true;
        }
        return false;
    }

}
