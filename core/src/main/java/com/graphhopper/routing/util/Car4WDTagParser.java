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

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;


/**
 * Defines bit layout for cars with four wheel drive
 *
 * @author zstadler
 */
public class Car4WDTagParser extends CarTagParser {

    public Car4WDTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key(properties.getString("name", "car4wd"))),
                lookup.getDecimalEncodedValue(VehicleSpeed.key(properties.getString("name", "car4wd"))),
                lookup.hasEncodedValue(TurnCost.key(properties.getString("name", "car4wd"))) ? lookup.getDecimalEncodedValue(TurnCost.key(properties.getString("name", "car4wd"))) : null,
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                new PMap(properties).putObject("name", "car4wd"),
                TransportationMode.CAR
        );
    }

    public Car4WDTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue turnCostEnc,
                           BooleanEncodedValue roundaboutEnc,
                           PMap properties, TransportationMode transportationMode) {
        super(accessEnc, speedEnc, turnCostEnc, roundaboutEnc, new PMap(properties).putObject("name", properties.getString("name", "car4wd")), transportationMode,
                speedEnc.getNextStorableValue(CAR_MAX_SPEED));
        trackTypeSpeedMap.put("grade4", 5); // ... some hard or compressed materials
        trackTypeSpeedMap.put("grade5", 5); // ... no hard materials. soil/sand/grass
    }
}
