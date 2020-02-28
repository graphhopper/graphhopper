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
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.MaxSpeed;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.routing.util.spatialrules.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.List;

import static com.graphhopper.routing.profiles.MaxSpeed.UNSET_SPEED;

public class OSMMaxSpeedParser implements TagParser {

    private final DecimalEncodedValue carMaxSpeedEnc;

    public OSMMaxSpeedParser() {
        this(MaxSpeed.create());
    }

    public OSMMaxSpeedParser(DecimalEncodedValue carMaxSpeedEnc) {
        if (!carMaxSpeedEnc.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue for maxSpeed must be able to store two directions");

        this.carMaxSpeedEnc = carMaxSpeedEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(carMaxSpeedEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        double maxSpeed = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed"));

        SpatialRule spatialRule = way.getTag("spatial_rule", null);
        if (spatialRule != null) {
            RoadClass roadClass = RoadClass.find(way.getTag("highway", ""));
            maxSpeed = spatialRule.getMaxSpeed(roadClass, TransportationMode.MOTOR_VEHICLE, maxSpeed);
        }
            
        double fwdSpeed = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed:forward"));
        if (fwdSpeed < 0 && maxSpeed > 0)
            fwdSpeed = maxSpeed;
        double maxPossibleSpeed = MaxSpeed.UNLIMITED_SIGN_SPEED;
        if (fwdSpeed > maxPossibleSpeed)
            fwdSpeed = maxPossibleSpeed;

        double bwdSpeed = AbstractFlagEncoder.parseSpeed(way.getTag("maxspeed:backward"));
        if (bwdSpeed < 0 && maxSpeed > 0)
            bwdSpeed = maxSpeed;
        if (bwdSpeed > maxPossibleSpeed)
            bwdSpeed = maxPossibleSpeed;

        if (fwdSpeed <= 0)
            fwdSpeed = UNSET_SPEED;
        carMaxSpeedEnc.setDecimal(false, edgeFlags, fwdSpeed);

        if (bwdSpeed <= 0)
            bwdSpeed = UNSET_SPEED;
        carMaxSpeedEnc.setDecimal(true, edgeFlags, bwdSpeed);
        return edgeFlags;
    }
}
