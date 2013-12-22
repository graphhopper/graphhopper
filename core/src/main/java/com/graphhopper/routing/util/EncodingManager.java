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

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.OSMRelation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

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
    public static final String FOOT = "foot";
    private static final HashMap<String, String> defaultEncoders = new HashMap<String, String>();

    static
    {
        defaultEncoders.put(CAR, CarFlagEncoder.class.getName());
        defaultEncoders.put(BIKE, BikeFlagEncoder.class.getName());
        defaultEncoders.put(FOOT, FootFlagEncoder.class.getName());
    }
    public static final int MAX_BITS = 32;
    private ArrayList<AbstractFlagEncoder> encoders = new ArrayList<AbstractFlagEncoder>();
    private int nextWayBit = 0;
    private int nextRelBit = 0;
    private int nextNodeBit = 0;

    public EncodingManager()
    {
    }

    /**
     * Instantiate manager with the given list of encoders. The manager knows the default encoders:
     * CAR, FOOT and BIKE Custom encoders can be added by giving a full class name e.g.
     * "CAR:com.graphhopper.myproject.MyCarEncoder"
     * <p/>
     * @param encoderList comma delimited list of encoders. The order does not matter.
     */
    @SuppressWarnings("unchecked")
    public EncodingManager( String encoderList )
    {
        String[] entries = encoderList.split(",");
        Arrays.sort(entries);

        for (String entry : entries)
        {
            entry = entry.trim();
            if (entry.isEmpty())
                continue;

            String className = null;
            int pos = entry.indexOf(":");
            if (pos > 0)
            {
                className = entry.substring(pos + 1);
            } else
            {
                className = defaultEncoders.get(entry.toLowerCase());
                if (className == null)
                    throw new IllegalArgumentException("Unknown encoder name " + entry);
            }

            try
            {
                Class cls = Class.forName(className);
                register((AbstractFlagEncoder) cls.getDeclaredConstructor().newInstance());
            } catch (Exception e)
            {
                throw new IllegalArgumentException("Cannot instantiate class " + className, e);
            }
        }
    }

    protected void register( AbstractFlagEncoder encoder )
    {
        int encoderCount = encoders.size();
        encoders.add(encoder);        

        int usedBits = encoder.defineNodeBits(encoderCount, nextNodeBit);
        if (usedBits >= MAX_BITS)
            throw new IllegalArgumentException("Encoders are requesting more than " + MAX_BITS + " bits of node flags");
        encoder.setNodeBitMask(usedBits - nextNodeBit, nextNodeBit);
        nextNodeBit = usedBits;

        usedBits = encoder.defineWayBits(encoderCount, nextWayBit);
        if (usedBits >= MAX_BITS)
            throw new IllegalArgumentException("Encoders are requesting more than " + MAX_BITS + " bits of way flags");
        encoder.setWayBitMask(usedBits - nextWayBit, nextWayBit);
        nextWayBit = usedBits;

        usedBits = encoder.defineRelationBits(encoderCount, nextRelBit);
        if (usedBits >= MAX_BITS)
            throw new IllegalArgumentException("Encoders are requesting more than " + MAX_BITS + " bits of relation flags");
        encoder.setRelBitMask(usedBits - nextRelBit, nextRelBit);
        nextRelBit = usedBits;        
    }

    /**
     * @return true if the specified encoder is found
     */
    public boolean supports( String encoder )
    {
        return getEncoder(encoder, false) != null;
    }

    public AbstractFlagEncoder getEncoder( String name )
    {
        return getEncoder(name, true);
    }

    private AbstractFlagEncoder getEncoder( String name, boolean throwExc )
    {
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            if (name.equalsIgnoreCase(encoders.get(i).toString()))
                return encoders.get(i);
        }
        if (throwExc)
            throw new IllegalArgumentException("Encoder for " + name + " not found.");
        return null;
    }

    /**
     * Determine whether an osm way is a routable way for one of its encoders.
     */
    public long accept( OSMWay way )
    {
        long includeWay = 0;
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            includeWay |= encoders.get(i).isAllowed(way);
        }

        return includeWay;
    }

    public long handleRelationTags( OSMRelation relation, long oldRelationFlags )
    {
        long flags = 0;
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            flags |= encoders.get(i).handleRelationTags(relation, oldRelationFlags);
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
    public long handleWayTags( OSMWay way, long includeWay, long relationFlags )
    {
        long flags = 0;
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            AbstractFlagEncoder encoder = encoders.get(i);
            flags |= encoder.handleWayTags(way, includeWay, relationFlags & encoder.getWayBitMask());
        }

        return flags;
    }

    public int getVehicleCount()
    {
        return encoders.size();
    }

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder();
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            if (str.length() > 0)
            {
                str.append(",");
            }
            str.append(encoders.get(i).toString());
        }

        return str.toString();
    }

    public String getEncoderList()
    {
        StringBuilder str = new StringBuilder();
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            if (str.length() > 0)
            {
                str.append(",");
            }
            str.append(encoders.get(i).toString());
            str.append(":");
            str.append(encoders.get(i).getClass().getName());
        }

        return str.toString();
    }

    public FlagEncoder getSingle()
    {
        if (getVehicleCount() > 1)
        {
            throw new IllegalStateException("multiple encoders are active. cannot return one:" + toString());
        }
        return getFirst();
    }

    private FlagEncoder getFirst()
    {
        if (getVehicleCount() == 0)
        {
            throw new IllegalStateException("no encoder is active!");
        }
        return encoders.get(0);
    }

    public long flagsDefault( boolean forward, boolean backward )
    {
        long flags = 0;
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            flags |= encoders.get(i).flagsDefault(forward, backward);
        }
        return flags;
    }

    /**
     * Swap direction for all encoders
     */
    public long swapDirection( long flags )
    {
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            flags = encoders.get(i).swapDirection(flags);
        }
        return flags;
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
        hash = 53 * hash + (this.encoders != null ? this.encoders.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final EncodingManager other = (EncodingManager) obj;
        if (this.encoders != other.encoders && (this.encoders == null || !this.encoders.equals(other.encoders)))
        {
            return false;
        }
        return true;
    }

    /**
     * Analyze tags on osm node
     * <p/>
     * @param node
     */
    public long analyzeNode( OSMNode node )
    {
        long flags = 0;
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            flags |= encoders.get(i).analyzeNodeTags(node);
        }

        return flags;
    }

    public String getWayInfo( OSMWay way )
    {
        String str = "";
        int encoderCount = encoders.size();
        for (int i = 0; i < encoderCount; i++)
        {
            String tmpWayInfo = encoders.get(i).getWayInfo(way);
            if (tmpWayInfo.isEmpty())
                continue;
            if (!str.isEmpty())
                str += ", ";
            str += tmpWayInfo;
        }
        return str;
    }

}
