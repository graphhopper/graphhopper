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

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.*;
import com.graphhopper.routing.profiles.TagParser;
import com.graphhopper.routing.weighting.PriorityWeighting;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import java.util.*;

//import static com.graphhopper.routing.profiles.TagParserFactory.Foot.*;
import static com.graphhopper.routing.util.PriorityCode.*;

/**
 * Defines bit layout for pedestrians (speed, access, surface, ...). Here we put a penalty on unsafe
 * roads only. If you wish to also prefer routes due to beauty like hiking routes use the
 * HikeFlagEncoder instead.
 *
 * @author Peter Karich
 * @author Nop
 * @author Karl Hübner
 */
public class FootFlagEncoder extends AbstractFlagEncoder {

    final Set<String> safeHighwayTags = new HashSet<String>();
    final Set<String> allowedHighwayTags = new HashSet<String>();
    final Set<String> avoidHighwayTags = new HashSet<String>();
    final Set<String> allowedSacScaleTags = new HashSet<>();
    // convert network tag of hiking routes into a way route code
    final Map<String, Integer> hikingNetworkToCode = new HashMap<String, Integer>();
    protected HashSet<String> sidewalkValues = new HashSet<String>(5);
    protected HashSet<String> sidewalksNoValues = new HashSet<String>(5);
    private IntEncodedValue priorityEnc;
    private EncodedValue08 relationCodeEncoder;

    /**
     * Should be only instantiated via EncodingManager
     */
    public FootFlagEncoder() {
        this(4, 1);
    }

    public FootFlagEncoder(PMap properties) {
        this(properties.getInt("speedBits", 4),
                properties.getDouble("speedFactor", 1));
        this.setBlockFords(properties.getBool("block_fords", true));
    }

    public FootFlagEncoder(String propertiesStr) {
        this(new PMap(propertiesStr));
    }

    public FootFlagEncoder(int speedBits, double speedFactor) {
        super(speedBits, speedFactor, 0);
        restrictions.addAll(Arrays.asList("foot", "access"));
        restrictedValues.add("private");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("military");
        restrictedValues.add("emergency");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        sidewalksNoValues.add("no");
        sidewalksNoValues.add("none");
        // see #712
        sidewalksNoValues.add("separate");

        sidewalkValues.add("yes");
        sidewalkValues.add("both");
        sidewalkValues.add("left");
        sidewalkValues.add("right");

        setBlockByDefault(false);
        potentialBarriers.add("gate");

        safeHighwayTags.add("footway");
        safeHighwayTags.add("path");
        safeHighwayTags.add("steps");
        safeHighwayTags.add("pedestrian");
        safeHighwayTags.add("living_street");
        safeHighwayTags.add("track");
        safeHighwayTags.add("residential");
        safeHighwayTags.add("service");

        avoidHighwayTags.add("trunk");
        avoidHighwayTags.add("trunk_link");
        avoidHighwayTags.add("primary");
        avoidHighwayTags.add("primary_link");
        avoidHighwayTags.add("secondary");
        avoidHighwayTags.add("secondary_link");
        avoidHighwayTags.add("tertiary");
        avoidHighwayTags.add("tertiary_link");

        // for now no explicit avoiding #257
        //avoidHighwayTags.add("cycleway"); 
        allowedHighwayTags.addAll(safeHighwayTags);
        allowedHighwayTags.addAll(avoidHighwayTags);
        allowedHighwayTags.add("cycleway");
        allowedHighwayTags.add("unclassified");
        allowedHighwayTags.add("road");
        // disallowed in some countries
        //allowedHighwayTags.add("bridleway");

        hikingNetworkToCode.put("iwn", UNCHANGED.getValue());
        hikingNetworkToCode.put("nwn", UNCHANGED.getValue());
        hikingNetworkToCode.put("rwn", UNCHANGED.getValue());
        hikingNetworkToCode.put("lwn", UNCHANGED.getValue());

        // other scales are too dangerous, see http://wiki.openstreetmap.org/wiki/Key:sac_scale
        allowedSacScaleTags.add("hiking");
        allowedSacScaleTags.add("mountain_hiking");
        allowedSacScaleTags.add("demanding_mountain_hiking");
        allowedSacScaleTags.add("alpine_hiking");

        maxPossibleSpeed = TagParserFactory.Foot.FOOT_FERRY_SPEED;

        init();
    }

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public Map<String, TagParser> createTagParsers(final String prefix) {
        Map<String, TagParser> map = new HashMap<>();
        averageSpeedEnc = new DecimalEncodedValue(prefix + "average_speed", speedBits, 0, speedFactor, false);
        map.put(TagParserFactory.FOOT_AVERAGE_SPEED, TagParserFactory.createParser(TagParserFactory.FOOT_AVERAGE_SPEED));
        final ReaderWayFilter filter = new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return allowedHighwayTags.contains(way.getTag("highway"));
            }
        };
        map.put(TagParserFactory.FOOT_ACCESS, TagParserFactory.createParser(TagParserFactory.FOOT_ACCESS));
        map.put("roundabout", null);

        priorityEnc = new IntEncodedValue(prefix + "priority", 3, 0, false);
        map.put(priorityEnc.getName(),
                new TagParser() {
                    @Override
                    public String toString() {
                        return getName();
                    }

                    @Override
                    public String getName() {
                        return priorityEnc.getName();
                    }

                    @Override
                    public EncodedValue getEncodedValue() {
                        return priorityEnc;
                    }

                    @Override
                    public ReaderWayFilter getReadWayFilter() {
                        return filter;
                    }

                    @Override
                    public void parse(IntsRef ints, ReaderWay way) {

                    }
                });

        return map;
    }

    @Override
    public int defineRelationBits(int index, int shift) {
        relationCodeEncoder = new EncodedValue08("RelationCode", shift, 3, 1, 0, 7);
        return shift + relationCodeEncoder.getBits();
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     */
    @Override
    public int defineTurnBits(int index, int shift) {
        return shift;
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     *
     * @return <code>false</code>
     */
    @Override
    public boolean isTurnRestricted(long flag) {
        return false;
    }

    /**
     * Foot flag encoder does not provide any turn cost / restrictions
     *
     * @return 0
     */
    @Override
    public double getTurnCost(long flag) {
        return 0;
    }

    @Override
    public long getTurnFlags(boolean restricted, double costs) {
        return 0;
    }

    /**
     * Some ways are okay but not separate for pedestrians.
     */
    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                String footTag = way.getTag("foot");
                if (footTag == null || "yes".equals(footTag))
                    return EncodingManager.Access.FERRY;
            }

            // special case not for all acceptedRailways, only platform
            if (way.hasTag("railway", "platform"))
                return EncodingManager.Access.WAY;

            return EncodingManager.Access.CAN_SKIP;
        }

        String sacScale = way.getTag("sac_scale");
        if (!allowedSacScaleTags.isEmpty() && sacScale != null && !allowedSacScaleTags.contains(sacScale))
            return EncodingManager.Access.CAN_SKIP;

        // no need to evaluate ferries or fords - already included here
        if (way.hasTag("foot", intendedValues))
            return EncodingManager.Access.WAY;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("sidewalk", sidewalkValues))
            return EncodingManager.Access.WAY;

        if (!allowedHighwayTags.contains(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("motorroad", "yes"))
            return EncodingManager.Access.CAN_SKIP;

        // do not get our feet wet, "yes" is already included above
        if (isBlockFords() && (way.hasTag("highway", "ford") || way.hasTag("ford")))
            return EncodingManager.Access.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return EncodingManager.Access.CAN_SKIP;

        return EncodingManager.Access.WAY;
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        int code = 0;
        if (relation.hasTag("route", "hiking") || relation.hasTag("route", "foot")) {
            Integer val = hikingNetworkToCode.get(relation.getTag("network"));
            if (val != null)
                code = val;
            else
                code = hikingNetworkToCode.get("lwn");
        } else if (relation.hasTag("route", "ferry")) {
            code = PriorityCode.AVOID_IF_POSSIBLE.getValue();
        }

        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    @Override
    public IntsRef handleWayTags(IntsRef ints, ReaderWay way, EncodingManager.Access allowed, long relationFlags) {
        if (allowed.canSkip())
            return ints;

        if (allowed.isFerry()) {
            double ferrySpeed = getFerrySpeed(way, TagParserFactory.Foot.FOOT_SLOW_SPEED, TagParserFactory.Foot.FOOT_MEAN_SPEED, TagParserFactory.Foot.FOOT_FERRY_SPEED);
            averageSpeedEnc.setDecimal(false, ints, ferrySpeed);
            accessEnc.setBool(false, ints, true);
            accessEnc.setBool(true, ints, true);
        }

        int priorityFromRelation = 0;
        if (relationFlags != 0)
            priorityFromRelation = (int) relationCodeEncoder.getValue(relationFlags);

        priorityEnc.setInt(false, ints, priorityFromRelation);
        return ints;
    }

    protected int handlePriority(ReaderWay way, int priorityFromRelation) {
        TreeMap<Double, Integer> weightToPrioMap = new TreeMap<Double, Integer>();
        if (priorityFromRelation == 0)
            weightToPrioMap.put(0d, UNCHANGED.getValue());
        else
            weightToPrioMap.put(110d, priorityFromRelation);

        collect(way, weightToPrioMap);

        // pick priority with biggest order value
        return weightToPrioMap.lastEntry().getValue();
    }

    /**
     * @param weightToPrioMap associate a weight with every priority. This sorted map allows
     *                        subclasses to 'insert' more important priorities as well as overwrite determined priorities.
     */
    void collect(ReaderWay way, TreeMap<Double, Integer> weightToPrioMap) {
        String highway = way.getTag("highway");
        if (way.hasTag("foot", "designated"))
            weightToPrioMap.put(100d, PREFER.getValue());

        double maxSpeed = getMaxSpeed(way);
        if (safeHighwayTags.contains(highway) || maxSpeed > 0 && maxSpeed <= 20) {
            weightToPrioMap.put(40d, PREFER.getValue());
            if (way.hasTag("tunnel", intendedValues)) {
                if (way.hasTag("sidewalk", sidewalksNoValues))
                    weightToPrioMap.put(40d, AVOID_IF_POSSIBLE.getValue());
                else
                    weightToPrioMap.put(40d, UNCHANGED.getValue());
            }
        } else if (maxSpeed > 50 || avoidHighwayTags.contains(highway)) {
            if (!way.hasTag("sidewalk", sidewalkValues))
                weightToPrioMap.put(45d, AVOID_IF_POSSIBLE.getValue());
        }

        if (way.hasTag("bicycle", "official") || way.hasTag("bicycle", "designated"))
            weightToPrioMap.put(44d, AVOID_IF_POSSIBLE.getValue());
    }

    @Override
    public boolean supports(Class<?> feature) {
        if (super.supports(feature))
            return true;

        return PriorityWeighting.class.isAssignableFrom(feature);
    }

    @Override
    public String toString() {
        return "foot";
    }
}
