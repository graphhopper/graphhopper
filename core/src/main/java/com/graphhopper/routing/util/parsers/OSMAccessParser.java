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
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.storage.IntsRef;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.graphhopper.routing.ev.RoadAccess.YES;

// TODO rename to car_restricted or car_allowed?
//  if car_allowed == false
//  if car_access == false
public class OSMAccessParser implements TagParser {

    private final BooleanEncodedValue accessEnc;
    private final List<String> restrictions;
    private final TransportationMode transportationMode;
    private final Set<String> restrictedValues = new HashSet<>(10);
    private final Set<String> oneways = new HashSet<>(5);
    private final HashSet<String> oppositeLanes = new HashSet<>();
    private BooleanEncodedValue roundaboutEnc;

    private Set<String> intendedValues = new HashSet<>(5);

    public OSMAccessParser(String name, List<String> restrictions, TransportationMode transportationMode) {
        this.accessEnc = new SimpleBooleanEncodedValue(name, true);
        this.restrictions = restrictions;
        this.transportationMode = transportationMode;

        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");

        oneways.add("yes");
        oneways.add("true");
        oneways.add("1");
        oneways.add("-1");

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("official");
        intendedValues.add("permissive");

        oppositeLanes.add("opposite");
        oppositeLanes.add("opposite_lane");
        oppositeLanes.add("opposite_track");
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(accessEnc);
        roundaboutEnc = lookup.getBooleanEncodedValue(Roundabout.KEY);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        RoadAccess accessValue = YES;
        RoadAccess tmpAccessValue;
        for (String restriction : restrictions) {
            tmpAccessValue = RoadAccess.find(way.getTag(restriction, "yes"));
            if (tmpAccessValue != null && tmpAccessValue.ordinal() > accessValue.ordinal()) {
                accessValue = tmpAccessValue;
            }
        }

        CountryRule countryRule = way.getTag("country_rule", null);
        if (countryRule != null)
            accessValue = countryRule.getAccess(way, transportationMode, accessValue);

        boolean access = accessValue != RoadAccess.NO;
        accessEnc.setBool(false, edgeFlags, access);

        if (access) {
            boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
            if (transportationMode.isMotorVehicle() && (isOneway(way) || isRoundabout)) {
                if (isForwardOneway(way))
                    accessEnc.setBool(false, edgeFlags, true);
                if (isBackwardOneway(way))
                    accessEnc.setBool(true, edgeFlags, true);
            } else if (transportationMode == TransportationMode.BIKE
                    && (isBikeOneway(way) || isRoundabout && !way.hasTag("oneway:bicycle", "no")
                    && !way.hasTag("cycleway", oppositeLanes)
                    && !way.hasTag("cycleway:left", oppositeLanes)
                    && !way.hasTag("cycleway:right", oppositeLanes)
                    && !way.hasTag("cycleway:left:oneway", "-1")
                    && !way.hasTag("cycleway:right:oneway", "-1"))) {
                boolean isBackward = way.hasTag("oneway", "-1")
                        || way.hasTag("oneway:bicycle", "-1")
                        || way.hasTag("vehicle:forward", restrictedValues)
                        || way.hasTag("bicycle:forward", restrictedValues);
                accessEnc.setBool(isBackward, edgeFlags, true);
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
            }
        }

        return edgeFlags;
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag("oneway", "-1")
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues);
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isForwardOneway(ReaderWay way) {
        return !way.hasTag("oneway", "-1")
                && !way.hasTag("vehicle:forward", restrictedValues)
                && !way.hasTag("motor_vehicle:forward", restrictedValues);
    }

    protected boolean isOneway(ReaderWay way) {
        return way.hasTag("oneway", oneways)
                || way.hasTag("vehicle:backward", restrictedValues)
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:backward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues);
    }


    boolean isBikeOneway(ReaderWay way) {
        return way.hasTag("oneway", oneways) && !way.hasTag("oneway", "-1") && !way.hasTag("bicycle:backward", intendedValues)
                || way.hasTag("oneway", "-1") && !way.hasTag("bicycle:forward", intendedValues)
                || way.hasTag("oneway:bicycle", oneways)
                || way.hasTag("vehicle:backward", restrictedValues) && !way.hasTag("bicycle:forward", intendedValues)
                || way.hasTag("vehicle:forward", restrictedValues) && !way.hasTag("bicycle:backward", intendedValues)
                || way.hasTag("bicycle:forward", restrictedValues)
                || way.hasTag("bicycle:backward", restrictedValues);
    }
}
