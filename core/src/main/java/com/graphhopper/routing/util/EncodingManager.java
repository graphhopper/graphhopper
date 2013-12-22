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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.TurnCostEncoder.NoTurnCostsEncoder;

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
    private static final Map<String, String> defaultEdgeFlagEncoders = new HashMap<String, String>();
    private static final Map<String, String> defaultTurnFlagEncoders = new HashMap<String, String>();

    static
    {
        defaultEdgeFlagEncoders.put(CAR, CarFlagEncoder.class.getName());
        defaultEdgeFlagEncoders.put(BIKE, BikeFlagEncoder.class.getName());
        defaultEdgeFlagEncoders.put(FOOT, FootFlagEncoder.class.getName());

        defaultTurnFlagEncoders.put(CAR, DefaultTurnCostEncoder.class.getName());
        defaultTurnFlagEncoders.put(BIKE, DefaultTurnCostEncoder.class.getName());
        defaultTurnFlagEncoders.put(FOOT, NoTurnCostsEncoder.class.getName());
    }

    public static final int MAX_BITS = 32;

    //edge encoders
    private List<AbstractFlagEncoder> encoders = new ArrayList<AbstractFlagEncoder>();
    private int edgeEncoderCount = 0;
    private int edgeEncoderNextBit = 0;

    private List<TurnCostEncoder> turnCostEncoders = new ArrayList<TurnCostEncoder>();
    private int turnEncoderCount = 0;
    private int turnEncoderNextBit = 0;

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
    public EncodingManager( String encoderList )
    {
        this(encoderList, "");
    }

    /**
     * Instantiate manager with the given list of encoders. The manager knows the default encoders:
     * CAR, FOOT and BIKE Custom encoders can be added by giving a full class name e.g.
     * "CAR:com.graphhopper.myproject.MyCarEncoder"
     * <p/>
     * @param encoderList comma delimited list of encoders. The order does not matter.
     * @param turnCostEncoderList comma delimited list of turn cost encoders. The order does not matter.
     */
    public EncodingManager( String encoderList, String turnCostEncoderList )
    {

        for (AbstractFlagEncoder flagEncoder : test(defaultEdgeFlagEncoders, encoderList, AbstractFlagEncoder.class))
        {
            registerEdgeFlagEncoder(flagEncoder);
        }

        for (TurnCostEncoder turnCostEncoder : test(defaultTurnFlagEncoders, turnCostEncoderList, TurnCostEncoder.class))
        {
            registerTurnCostFlagEncoder(turnCostEncoder);
        }

        /*
         // comment this in to detect involuntary changes of the encoding
         if( instance != null )
         {
         System.out.println( "WARNING: Overwriting Encoding Manager " + instance.toString() + " with new instance " + toString());
         StackTraceElement[] trace = Thread.currentThread().getStackTrace();
         for( int i = 2; i < trace.length; i++ ) {
         System.out.println( trace[i] );
         }
         }
         */
        // instance = this;
    }

    private <T> List<T> test( Map<String, String> defaultEncoders, String encoderList, Class<T> encoderClass )
    {
        String[] entries = encoderList.split(",");
        Arrays.sort(entries);

        List<T> resultEncoders = new ArrayList<T>();

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
                @SuppressWarnings("unchecked")
                Class<T> cls = (Class<T>) Class.forName(className);
                resultEncoders.add((T) cls.getDeclaredConstructor().newInstance());
            } catch (Exception e)
            {
                throw new IllegalArgumentException("Cannot instantiate class " + className, e);
            }
        }
        return resultEncoders;
    }

    public void registerEdgeFlagEncoder( AbstractFlagEncoder encoder )
    {
        encoders.add(encoder);

        int usedBits = encoder.defineBits(edgeEncoderCount, edgeEncoderNextBit);
        if (usedBits >= MAX_BITS)
        {
            throw new IllegalArgumentException("Encoders are requesting more than 32 bits of flags");
        }

        edgeEncoderNextBit = usedBits;
        edgeEncoderCount = encoders.size();
    }

    public void registerTurnCostFlagEncoder( TurnCostEncoder encoder )
    {
        turnCostEncoders.add(encoder);

        int usedBits = encoder.defineBits(turnEncoderCount, turnEncoderNextBit);
        if (usedBits >= MAX_BITS)
        {
            throw new IllegalArgumentException("Encoders are requesting more than 32 bits of flags");
        }

        turnEncoderNextBit = usedBits;
        turnEncoderCount = turnCostEncoders.size();
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

    public TurnCostEncoder getTurnCostEncoder( String name )
    {
        return getTurnCostEncoder(name, true);
    }

    private AbstractFlagEncoder getEncoder( String name, boolean throwExc )
    {
        for (int i = 0; i < edgeEncoderCount; i++)
        {
            if (name.equalsIgnoreCase(encoders.get(i).toString()))
                return encoders.get(i);
        }
        if (throwExc)
            throw new IllegalArgumentException("Encoder for " + name + " not found.");
        return null;
    }

    private TurnCostEncoder getTurnCostEncoder( String name, boolean throwExc )
    {
        for (int i = 0; i < turnEncoderCount; i++)
        {
            if (name.equalsIgnoreCase(turnCostEncoders.get(i).toString()))
                return turnCostEncoders.get(i);
        }
        if (throwExc)
            throw new IllegalArgumentException("Turn Cost Encoder for " + name + " not found.");
        return null;
    }

    /**
     * Determine whether an osm way is a routable way for one of its encoders.
     */
    public int accept( OSMWay way )
    {
        int includeWay = 0;
        for (int i = 0; i < edgeEncoderCount; i++)
        {
            includeWay |= encoders.get(i).isAllowed(way);
        }

        return includeWay;
    }

    /**
     * Processes way properties of different kind to determine speed and direction. Properties are
     * directly encoded in 4-Byte flags.
     * <p/>
     * @return the encoded flags
     */
    public long handleWayTags( int includeWay, OSMWay way )
    {
        long flags = 0;
        for (int i = 0; i < edgeEncoderCount; i++)
        {
            flags |= encoders.get(i).handleWayTags(includeWay, way);
        }

        return flags;
    }

    public int getVehicleCount()
    {
        return edgeEncoderCount;
    }

    @Override
    public String toString()
    {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < edgeEncoderCount; i++)
        {
            if (str.length() > 0)
            {
                str.append(",");
            }
            str.append(encoders.get(i).toString());
        }

        return str.toString();
    }

    public String encoderList()
    {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < edgeEncoderCount; i++)
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

    public int flagsDefault( boolean forward, boolean backward )
    {
        int flags = 0;
        for (int i = 0; i < edgeEncoderCount; i++)
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
        for (int i = 0; i < edgeEncoderCount; i++)
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
    public int analyzeNode( OSMNode node )
    {
        int flags = 0;
        for (int i = 0; i < edgeEncoderCount; i++)
        {
            flags |= encoders.get(i).analyzeNodeTags(node);
        }

        return flags;
    }

    public String getWayInfo( OSMWay way )
    {
        String str = "";
        for (int i = 0; i < edgeEncoderCount; i++)
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
