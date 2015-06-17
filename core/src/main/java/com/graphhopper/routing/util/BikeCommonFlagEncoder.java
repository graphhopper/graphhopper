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

import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.OSMRelation;

import static com.graphhopper.routing.util.PriorityCode.*;

import com.graphhopper.util.Helper;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.Translation;

import java.util.*;

/**
 * Defines bit layout of bicycles (not motorcycles) for speed, access and relations (network).
 * <p/>
 * @author Peter Karich
 * @author Nop
 * @author ratrun
 */
public class BikeCommonFlagEncoder extends AbstractFlagEncoder
{
    /**
     * Reports wether this edge is unpaved.
     */
    public static final int K_UNPAVED = 100;
    protected static final int PUSHING_SECTION_SPEED = 4;
    private long unpavedBit = 0;
    // Pushing section heighways are parts where you need to get off your bike and push it (German: Schiebestrecke)
    protected final HashSet<String> pushingSections = new HashSet<String>();
    protected final HashSet<String> oppositeLanes = new HashSet<String>();
    protected final Set<String> preferHighwayTags = new HashSet<String>();
    protected final Set<String> avoidHighwayTags = new HashSet<String>();
    protected final Set<String> unpavedSurfaceTags = new HashSet<String>();
    private final Map<String, Integer> trackTypeSpeeds = new HashMap<String, Integer>();
    private final Map<String, Integer> surfaceSpeeds = new HashMap<String, Integer>();
    private final Set<String> roadValues = new HashSet<String>();
    private final Map<String, Integer> highwaySpeeds = new HashMap<String, Integer>();
    // convert network tag of bicycle routes into a way route code
    private final Map<String, Integer> bikeNetworkToCode = new HashMap<String, Integer>();
    protected EncodedValue relationCodeEncoder;
    private EncodedValue wayTypeEncoder;
    private EncodedValue preferWayEncoder;

    // Car speed limit which switches the preference from UNCHANGED to AVOID_IF_POSSIBLE
    private int avoidSpeedLimit;

    // This is the specific bicycle class
    private String specificBicycleClass;

    protected BikeCommonFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
    {
        super(speedBits, speedFactor, maxTurnCosts);
        // strict set, usually vehicle and agricultural/forestry are ignored by cyclists
        restrictions.addAll(Arrays.asList("bicycle", "access"));
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");

        setBlockByDefault(false);
        potentialBarriers.add("gate");
        // potentialBarriers.add("lift_gate");
        potentialBarriers.add("swing_gate");

        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");

        // make intermodal connections possible but mark as pushing section
        acceptedRailways.add("platform");

        unpavedSurfaceTags.add("unpaved");
        unpavedSurfaceTags.add("gravel");
        unpavedSurfaceTags.add("ground");
        unpavedSurfaceTags.add("dirt");
        unpavedSurfaceTags.add("grass");
        unpavedSurfaceTags.add("compacted");
        unpavedSurfaceTags.add("earth");
        unpavedSurfaceTags.add("fine_gravel");
        unpavedSurfaceTags.add("grass_paver");
        unpavedSurfaceTags.add("ice");
        unpavedSurfaceTags.add("mud");
        unpavedSurfaceTags.add("salt");
        unpavedSurfaceTags.add("sand");
        unpavedSurfaceTags.add("wood");

        roadValues.add("living_street");
        roadValues.add("road");
        roadValues.add("service");
        roadValues.add("unclassified");
        roadValues.add("residential");
        roadValues.add("trunk");
        roadValues.add("trunk_link");
        roadValues.add("primary");
        roadValues.add("primary_link");
        roadValues.add("secondary");
        roadValues.add("secondary_link");
        roadValues.add("tertiary");
        roadValues.add("tertiary_link");

        maxPossibleSpeed = 30;

        setTrackTypeSpeed("grade1", 18); // paved
        setTrackTypeSpeed("grade2", 12); // now unpaved ...
        setTrackTypeSpeed("grade3", 8);
        setTrackTypeSpeed("grade4", 6);
        setTrackTypeSpeed("grade5", 4); // like sand/grass     

        setSurfaceSpeed("paved", 18);
        setSurfaceSpeed("asphalt", 18);
        setSurfaceSpeed("cobblestone", 8);
        setSurfaceSpeed("cobblestone:flattened", 10);
        setSurfaceSpeed("sett", 10);
        setSurfaceSpeed("concrete", 18);
        setSurfaceSpeed("concrete:lanes", 16);
        setSurfaceSpeed("concrete:plates", 16);
        setSurfaceSpeed("paving_stones", 12);
        setSurfaceSpeed("paving_stones:30", 12);
        setSurfaceSpeed("unpaved", 14);
        setSurfaceSpeed("compacted", 16);
        setSurfaceSpeed("dirt", 10);
        setSurfaceSpeed("earth", 12);
        setSurfaceSpeed("fine_gravel", 18);
        setSurfaceSpeed("grass", 8);
        setSurfaceSpeed("grass_paver", 8);
        setSurfaceSpeed("gravel", 12);
        setSurfaceSpeed("ground", 12);
        setSurfaceSpeed("ice", PUSHING_SECTION_SPEED / 2);
        setSurfaceSpeed("metal", 10);
        setSurfaceSpeed("mud", 10);
        setSurfaceSpeed("pebblestone", 16);
        setSurfaceSpeed("salt", 6);
        setSurfaceSpeed("sand", 6);
        setSurfaceSpeed("wood", 6);

        setHighwaySpeed("living_street", 6);
        setHighwaySpeed("steps", PUSHING_SECTION_SPEED / 2);

        setHighwaySpeed("cycleway", 18);
        setHighwaySpeed("path", 12);
        setHighwaySpeed("footway", 6);
        setHighwaySpeed("pedestrian", 6);
        setHighwaySpeed("track", 12);
        setHighwaySpeed("service", 14);
        setHighwaySpeed("residential", 18);
        // no other highway applies:
        setHighwaySpeed("unclassified", 16);
        // unknown road:
        setHighwaySpeed("road", 12);

        setHighwaySpeed("trunk", 18);
        setHighwaySpeed("trunk_link", 18);
        setHighwaySpeed("primary", 18);
        setHighwaySpeed("primary_link", 18);
        setHighwaySpeed("secondary", 18);
        setHighwaySpeed("secondary_link", 18);
        setHighwaySpeed("tertiary", 18);
        setHighwaySpeed("tertiary_link", 18);

        // special case see tests and #191
        setHighwaySpeed("motorway", 18);
        setHighwaySpeed("motorway_link", 18);
        avoidHighwayTags.add("motorway");
        avoidHighwayTags.add("motorway_link");

        setCyclingNetworkPreference("icn", PriorityCode.BEST.getValue());
        setCyclingNetworkPreference("ncn", PriorityCode.BEST.getValue());
        setCyclingNetworkPreference("rcn", PriorityCode.VERY_NICE.getValue());
        setCyclingNetworkPreference("lcn", PriorityCode.PREFER.getValue());
        setCyclingNetworkPreference("mtb", PriorityCode.UNCHANGED.getValue());

        setCyclingNetworkPreference("deprecated", PriorityCode.AVOID_AT_ALL_COSTS.getValue());

        setAvoidSpeedLimit(71);
    }

    @Override
    public int getVersion()
    {
        return 1;
    }

    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, highwaySpeeds.get("cycleway"),
                maxPossibleSpeed);
        shift += speedEncoder.getBits();

        unpavedBit = 1L << shift++;
        // 2 bits
        wayTypeEncoder = new EncodedValue("WayType", shift, 2, 1, 0, 3, true);
        shift += wayTypeEncoder.getBits();

        preferWayEncoder = new EncodedValue("PreferWay", shift, 3, 1, 0, 7);
        shift += preferWayEncoder.getBits();

        return shift;
    }

    @Override
    public int defineRelationBits( int index, int shift )
    {
        relationCodeEncoder = new EncodedValue("RelationCode", shift, 3, 1, 0, 7);
        return shift + relationCodeEncoder.getBits();
    }

    @Override
    public long acceptWay( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                // if bike is NOT explictly tagged allow bike but only if foot is not specified
                String bikeTag = way.getTag("bicycle");
                if (bikeTag == null && !way.hasTag("foot") || "yes".equals(bikeTag))
                    return acceptBit | ferryBit;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                return acceptBit;

            return 0;
        }

        if (!highwaySpeeds.containsKey(highwayValue))
            return 0;

        // use the way if it is tagged for bikes
        if (way.hasTag("bicycle", intendedValues))
            return acceptBit;

        // accept only if explicitely tagged for bike usage
        if ("motorway".equals(highwayValue) || "motorway_link".equals(highwayValue))
            return 0;

        if (way.hasTag("motorroad", "yes"))
            return 0;

        // do not use fords with normal bikes, flagged fords are in included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues))
            return 0;

        // do not accept railways (sometimes incorrectly mapped!)
        if (way.hasTag("railway") && !way.hasTag("railway", acceptedRailways))
            return 0;

        String sacScale = way.getTag("sac_scale");
        if (sacScale != null)
        {
            if ((way.hasTag("highway", "cycleway"))
                    && (way.hasTag("sac_scale", "hiking")))
                return acceptBit;
            if (!allowedSacScale(sacScale))
                return 0;
        }
        return acceptBit;
    }

    boolean allowedSacScale( String sacScale )
    {
        // other scales are nearly impossible by an ordinary bike, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        return "hiking".equals(sacScale);
    }

    @Override
    public long handleRelationTags( OSMRelation relation, long oldRelationFlags )
    {
        int code = 0;
        if (relation.hasTag("route", "bicycle"))
        {
            Integer val = bikeNetworkToCode.get(relation.getTag("network"));
            if (val != null)
                code = val;
        } else if (relation.hasTag("route", "ferry"))
        {
            code = PriorityCode.AVOID_IF_POSSIBLE.getValue();
        }

        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationFlags )
    {
        if (!isAccept(allowed))
            return 0;

        long encoded = 0;
        if (!isFerry(allowed))
        {
            double speed = getSpeed(way);
            int priorityFromRelation = 0;
            if (relationFlags != 0)
                priorityFromRelation = (int) relationCodeEncoder.getValue(relationFlags);

            encoded = setLong(encoded, PriorityWeighting.KEY, handlePriority(way, priorityFromRelation));

            // bike maxspeed handling is different from car as we don't increase speed
            speed = applyMaxSpeed(way, speed, false);
            encoded = handleSpeed(way, speed, encoded);
            encoded = handleBikeRelated(way, encoded, relationFlags > UNCHANGED.getValue());

            boolean isRoundabout = way.hasTag("junction", "roundabout");
            if (isRoundabout)
            {
                encoded = setBool(encoded, K_ROUNDABOUT, true);
            }

        } else
        {
            encoded = handleFerryTags(way,
                    highwaySpeeds.get("living_street"),
                    highwaySpeeds.get("track"),
                    highwaySpeeds.get("primary"));
            encoded |= directionBitMask;
        }
        return encoded;
    }

    int getSpeed( OSMWay way )
    {
        int speed = PUSHING_SECTION_SPEED;
        String highwayTag = way.getTag("highway");
        Integer highwaySpeed = highwaySpeeds.get(highwayTag);

        String s = way.getTag("surface");
        if (!Helper.isEmpty(s))
        {
            Integer surfaceSpeed = surfaceSpeeds.get(s);
            if (surfaceSpeed != null)
            {
                speed = surfaceSpeed;
                // Boost handling for good surfaces
                if (highwaySpeed != null && surfaceSpeed > highwaySpeed)
                {
                    // Avoid boosting if pushing section
                    if (pushingSections.contains(highwayTag))
                        speed = highwaySpeed;
                    else
                        speed = surfaceSpeed;
                }
            }
        } else
        {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt))
            {
                Integer tInt = trackTypeSpeeds.get(tt);
                if (tInt != null)
                    speed = tInt;
            } else
            {
                if (highwaySpeed != null)
                {
                    if (!way.hasTag("service"))
                        speed = highwaySpeed;
                    else
                        speed = highwaySpeeds.get("living_street");
                }
            }
        }

        // Until now we assumed that the way is no pushing section
        // Now we check, but only in case that our speed is bigger compared to the PUSHING_SECTION_SPEED
        if ((speed > PUSHING_SECTION_SPEED)
                && (!way.hasTag("bicycle", intendedValues) && way.hasTag("highway", pushingSections)))
        {
            if (way.hasTag("highway", "steps"))
                speed = PUSHING_SECTION_SPEED / 2;
            else
                speed = PUSHING_SECTION_SPEED;
        }

        return speed;
    }

    @Override
    public InstructionAnnotation getAnnotation( long flags, Translation tr )
    {
        int paveType = 0; // paved
        if (isBool(flags, K_UNPAVED))
            paveType = 1; // unpaved        

        int wayType = (int) wayTypeEncoder.getValue(flags);
        String wayName = getWayName(paveType, wayType, tr);
        return new InstructionAnnotation(0, wayName);
    }

    String getWayName( int pavementType, int wayType, Translation tr )
    {
        String pavementName = "";
        if (pavementType == 1)
            pavementName = tr.tr("unpaved");

        String wayTypeName = "";
        switch (wayType)
        {
            case 0:
                wayTypeName = tr.tr("road");
                break;
            case 1:
                wayTypeName = tr.tr("off_bike");
                break;
            case 2:
                wayTypeName = tr.tr("cycleway");
                break;
            case 3:
                wayTypeName = tr.tr("way");
                break;
        }

        if (pavementName.isEmpty())
        {
            if (wayType == 0 || wayType == 3)
                return "";
            return wayTypeName;
        } else
        {
            if (wayTypeName.isEmpty())
                return pavementName;
            else
                return wayTypeName + ", " + pavementName;
        }
    }

    /**
     * In this method we prefer cycleways or roads with designated bike access and avoid big roads
     * or roads with trams or pedestrian.
     * <p/>
     * @return new priority based on priorityFromRelation and on the tags in OSMWay.
     */
    protected int handlePriority( OSMWay way, int priorityFromRelation )
    {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<Double, Integer>();
        if (priorityFromRelation == 0)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        collect(way, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    // Conversion of class value to priority. See http://wiki.openstreetmap.org/wiki/Class:bicycle
    private PriorityCode convertCallValueToPriority( String tagvalue )
    {
        int classvalue;
        try
        {
            classvalue = Integer.parseInt(tagvalue);
        } catch (NumberFormatException e)
        {
            return PriorityCode.UNCHANGED;
        }

        switch (classvalue)
        {
            case 3:
                return PriorityCode.BEST;
            case 2:
                return PriorityCode.VERY_NICE;
            case 1:
                return PriorityCode.PREFER;
            case 0:
                return PriorityCode.UNCHANGED;
            case -1:
                return PriorityCode.AVOID_IF_POSSIBLE;
            case -2:
                return PriorityCode.REACH_DEST;
            case -3:
                return PriorityCode.AVOID_AT_ALL_COSTS;
            default:
                return PriorityCode.UNCHANGED;
        }
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     * subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect( OSMWay way, TreeMap<Double, Integer> weightToPrioMap )
    {
        String service = way.getTag("service");
        String highway = way.getTag("highway");
        if (way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());
        if ("cycleway".equals(highway))
            weightToPrioMap.put(100d, VERY_NICE.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (preferHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 30)
        {
            if (maxSpeed < avoidSpeedLimit)
            {
                weightToPrioMap.put(40d, PREFER.getValue());
                if (way.hasTag("tunnel", intendedValues))
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else
        {
            if (avoidHighwayTags.contains(highway) || ((maxSpeed >= avoidSpeedLimit) && (highway != "track")))
            {
                weightToPrioMap.put(50d, REACH_DEST.getValue());
                if (way.hasTag("tunnel", intendedValues))
                    weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());
            }
        }

        if (pushingSections.contains(highway)
                || way.hasTag("bicycle", "use_sidepath")
                || "parking_aisle".equals(service))
        {
            if (way.hasTag("bicycle", "yes"))
                weightToPrioMap.put(100d, UNCHANGED.getValue());
            else
                weightToPrioMap.put(50d, AVOID_IF_POSSIBLE.getValue());
        }

        if (way.hasTag("railway", "tram"))
            weightToPrioMap.put(50d, AVOID_AT_ALL_COSTS.getValue());

        String classBicycleSpecific = way.getTag(specificBicycleClass);
        if (classBicycleSpecific != null)
        {
            // We assume that humans are better in classifying preferences compared to our algorithm above -> weight = 100
            weightToPrioMap.put(100d, convertCallValueToPriority(classBicycleSpecific).getValue());
        } else
        {
            String classBicycle = way.getTag("class:bicycle");
            if (classBicycle != null)
            {
                weightToPrioMap.put(100d, convertCallValueToPriority(classBicycle).getValue());
            }
        }

    }

    /**
     * Handle surface and wayType encoding
     */
    long handleBikeRelated( OSMWay way, long encoded, boolean partOfCycleRelation )
    {
        String surfaceTag = way.getTag("surface");
        String highway = way.getTag("highway");
        String trackType = way.getTag("tracktype");

        // Populate bits at wayTypeMask with wayType            
        WayType wayType = WayType.OTHER_SMALL_WAY;
        boolean isPusingSection = isPushingSection(way);
        if (isPusingSection && !partOfCycleRelation || "steps".equals(highway))
            wayType = WayType.PUSHING_SECTION;

        if ("track".equals(highway) && (trackType == null || !"grade1".equals(trackType))
                || "path".equals(highway) && surfaceTag == null
                || unpavedSurfaceTags.contains(surfaceTag))
        {
            encoded = setBool(encoded, K_UNPAVED, true);
        }

        if (way.hasTag("bicycle", intendedValues))
        {
            if (isPusingSection && !way.hasTag("bicycle", "designated"))
                wayType = WayType.OTHER_SMALL_WAY;
            else
                wayType = WayType.CYCLEWAY;
        } else if ("cycleway".equals(highway))
            wayType = WayType.CYCLEWAY;
        else if (roadValues.contains(highway))
            wayType = WayType.ROAD;

        return wayTypeEncoder.setValue(encoded, wayType.getValue());
    }

    @Override
    public long setBool( long flags, int key, boolean value )
    {
        switch (key)
        {
            case K_UNPAVED:
                return value ? flags | unpavedBit : flags & ~unpavedBit;
            default:
                return super.setBool(flags, key, value);
        }
    }

    @Override
    public boolean isBool( long flags, int key )
    {
        switch (key)
        {
            case K_UNPAVED:
                return (flags & unpavedBit) != 0;
            default:
                return super.isBool(flags, key);
        }
    }

    @Override
    public double getDouble( long flags, int key )
    {
        switch (key)
        {
            case PriorityWeighting.KEY:
                double prio = preferWayEncoder.getValue(flags);
                if (prio == 0)
                    return (double) UNCHANGED.getValue() / BEST.getValue();

                return prio / BEST.getValue();
            default:
                return super.getDouble(flags, key);
        }
    }

    @Override
    public long getLong( long flags, int key )
    {
        switch (key)
        {
            case PriorityWeighting.KEY:
                return preferWayEncoder.getValue(flags);
            default:
                return super.getLong(flags, key);
        }
    }

    @Override
    public long setLong( long flags, int key, long value )
    {
        switch (key)
        {
            case PriorityWeighting.KEY:
                return preferWayEncoder.setValue(flags, value);
            default:
                return super.setLong(flags, key, value);
        }
    }

    boolean isPushingSection( OSMWay way )
    {
        return way.hasTag("highway", pushingSections) || way.hasTag("railway", "platform");
    }

    protected long handleSpeed( OSMWay way, double speed, long encoded )
    {
        encoded = setSpeed(encoded, speed);

        // handle oneways        
        boolean isOneway = way.hasTag("oneway", oneways)
                || way.hasTag("oneway:bicycle", oneways)
                || way.hasTag("vehicle:backward")
                || way.hasTag("vehicle:forward")
                || way.hasTag("bicycle:forward");

        if ((isOneway || way.hasTag("junction", "roundabout"))
                && !way.hasTag("oneway:bicycle", "no")
                && !way.hasTag("bicycle:backward")
                && !way.hasTag("cycleway", oppositeLanes))
        {
            boolean isBackward = way.hasTag("oneway", "-1")
                    || way.hasTag("oneway:bicycle", "-1")
                    || way.hasTag("vehicle:forward", "no")
                    || way.hasTag("bicycle:forward", "no");
            if (isBackward)
                encoded |= backwardBit;
            else
                encoded |= forwardBit;

        } else
        {
            encoded |= directionBitMask;
        }
        return encoded;
    }

    private enum WayType
    {
        ROAD(0),
        PUSHING_SECTION(1),
        CYCLEWAY(2),
        OTHER_SMALL_WAY(3);

        private final int value;

        private WayType( int value )
        {
            this.value = value;
        }

        public int getValue()
        {
            return value;
        }
    };

    protected void setHighwaySpeed( String highway, int speed )
    {
        highwaySpeeds.put(highway, speed);
    }

    protected int getHighwaySpeed( String key )
    {
        return highwaySpeeds.get(key);
    }

    void setTrackTypeSpeed( String tracktype, int speed )
    {
        trackTypeSpeeds.put(tracktype, speed);
    }

    void setSurfaceSpeed( String surface, int speed )
    {
        surfaceSpeeds.put(surface, speed);
    }

    void setCyclingNetworkPreference( String network, int code )
    {
        bikeNetworkToCode.put(network, code);
    }

    void addPushingSection( String highway )
    {
        pushingSections.add(highway);
    }

    @Override
    public boolean supports( Class<?> feature )
    {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    public void setAvoidSpeedLimit( int limit )
    {
        avoidSpeedLimit = limit;
    }

    public void setSpecificBicycleClass( String subkey )
    {
        specificBicycleClass = "class:bicycle:" + subkey.toString();
    }

}
