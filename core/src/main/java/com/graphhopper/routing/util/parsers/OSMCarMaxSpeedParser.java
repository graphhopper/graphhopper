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
import com.graphhopper.routing.profiles.CarMaxSpeed;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.spatialrules.SpatialRule;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.shapes.GHPoint;

import java.util.List;

import static com.graphhopper.routing.util.AbstractFlagEncoder.parseSpeed;

public class OSMCarMaxSpeedParser implements TagParser {

    private final DecimalEncodedValue carMaxSpeedEnc;
    private final double maxPossibleSpeed = CarMaxSpeed.UNLIMITED_SIGN;

    public OSMCarMaxSpeedParser() {
        this(CarMaxSpeed.create());
    }

    public OSMCarMaxSpeedParser(DecimalEncodedValue carMaxSpeedEnc) {
        this.carMaxSpeedEnc = carMaxSpeedEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(carMaxSpeedEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {
        double maxSpeed = parseSpeed(way.getTag("maxspeed"));

        if (maxSpeed < 0) {
            GHPoint estmCentre = way.getTag("estimated_center", null);
            SpatialRule spatialRule = way.getTag("spatial_rule", null);
            if (estmCentre != null && spatialRule != null)
                maxSpeed = spatialRule.getMaxSpeed(way.getTag("highway", ""), maxSpeed);
        }

        double fwdSpeed = parseSpeed(way.getTag("maxspeed:forward"));
        if (fwdSpeed < 0 && maxSpeed > 0)
            fwdSpeed = maxSpeed;
        if (fwdSpeed > maxPossibleSpeed)
            fwdSpeed = maxPossibleSpeed;


        double bwdSpeed = parseSpeed(way.getTag("maxspeed:backward"));
        if (bwdSpeed < 0 && maxSpeed > 0)
            bwdSpeed = maxSpeed;
        if (bwdSpeed > maxPossibleSpeed)
            bwdSpeed = maxPossibleSpeed;

        if (fwdSpeed > 0)
            carMaxSpeedEnc.setDecimal(false, edgeFlags, fwdSpeed);

        if (bwdSpeed > 0)
            carMaxSpeedEnc.setDecimal(true, edgeFlags, bwdSpeed);
        return edgeFlags;
    }
}
