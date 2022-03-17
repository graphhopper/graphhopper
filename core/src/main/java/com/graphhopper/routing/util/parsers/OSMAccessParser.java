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

import static com.graphhopper.routing.ev.RoadAccess.NO;
import static com.graphhopper.routing.ev.RoadAccess.YES;

/**
 * This class defines the access of a single TransportationMode. It feeds an EncodedValue named [transportation_mode]_access.
 */
public class OSMAccessParser implements TagParser {

    private final BooleanEncodedValue accessEnc;
    private final List<String> restrictions;
    private final TransportationMode transportationMode;
    private final HashSet<String> oppositeLanes = new HashSet<>();

    private final Set<String> fwdOneway = new HashSet<>(5);
    private final Set<String> intendedValues = new HashSet<>(5);

    public OSMAccessParser(String name, TransportationMode transportationMode) {
        this.accessEnc = new SimpleBooleanEncodedValue(name, true);
        this.transportationMode = transportationMode;
        this.restrictions = OSMRoadAccessParser.toOSMRestrictions(transportationMode);

        fwdOneway.add("yes");
        fwdOneway.add("true");
        fwdOneway.add("1");

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
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        RoadAccess accessValue = YES;
        String restrictionTag = way.getFirstPriorityTag(restrictions);
        if (!intendedValues.contains(restrictionTag) && !restrictionTag.isEmpty())
            accessValue = RoadAccess.find(restrictionTag);

        CountryRule countryRule = way.getTag("country_rule", null);
        if (countryRule != null)
            accessValue = countryRule.getAccess(way, transportationMode, accessValue);

        if (accessValue == NO) return edgeFlags;

        if (transportationMode.isMotorVehicle()) {
            if (way.hasTag("oneway", fwdOneway) || way.hasTag("vehicle:backward", "no") || way.hasTag("motor_vehicle:backward", "no")
                    || transportationMode == TransportationMode.HGV && way.hasTag("hgv:backward", "no")) {
                accessEnc.setBool(false, edgeFlags, true);
            } else if (way.hasTag("oneway", "-1") || way.hasTag("vehicle:forward", "no") || way.hasTag("motor_vehicle:forward", "no")
                    || transportationMode == TransportationMode.HGV && way.hasTag("hgv:forward", "no")) {
                accessEnc.setBool(true, edgeFlags, true);
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
            }
        } else if (transportationMode == TransportationMode.BIKE) {
            // note the special tag "oneway:bicycle=no" which marks valid access in both directions
            boolean ignoreOneway = way.hasTag("oneway:bicycle", "no")
                    || way.hasTag("cycleway", oppositeLanes)
                    || way.hasTag("cycleway:left", oppositeLanes)
                    || way.hasTag("cycleway:right", oppositeLanes);

            if ((way.hasTag("oneway", fwdOneway) || way.hasTag("vehicle:backward", "no") || way.hasTag("bicycle:backward", "no")
                    || way.hasTag("oneway:bicycle", fwdOneway) /* oneway=yes should be preferred over oneway:bicycle, still for forward direction too common (>45k) */)
                    && !way.hasTag("bicycle:backward", intendedValues) && !ignoreOneway) {
                accessEnc.setBool(false, edgeFlags, true);
            } else if ((way.hasTag("oneway", "-1") || way.hasTag("vehicle:forward", "no") || way.hasTag("bicycle:forward", "no"))
                    && !way.hasTag("bicycle:forward", intendedValues) && !ignoreOneway) {
                accessEnc.setBool(true, edgeFlags, true);
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
            }
        } else if (transportationMode == TransportationMode.FOOT) {
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        } else {
            throw new IllegalArgumentException("TransportationMode " + transportationMode + " not yet supported");
        }
        return edgeFlags;
    }
}
