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

import java.util.Arrays;
import java.util.List;

import static com.graphhopper.routing.util.VehicleEncodedValuesFactory.*;

public class VehicleEncodedValues {
    public static final List<String> OUTDOOR_VEHICLES = Arrays.asList(BIKE, RACINGBIKE, MOUNTAINBIKE, FOOT, WHEELCHAIR);

    private final String name;
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue avgSpeedEnc;
    private final DecimalEncodedValue priorityEnc;
    private final BooleanEncodedValue turnRestrictionEnc;

    public static VehicleEncodedValues foot(PMap properties) {
        String name = properties.getString("name", "foot");
        int speedBits = properties.getInt("speed_bits", 4);
        double speedFactor = properties.getDouble("speed_factor", 1);
        boolean speedTwoDirections = properties.getBool("speed_two_directions", false);
        boolean turnCosts = properties.getBool("turn_costs", false);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = VehiclePriority.create(name, 4, PriorityCode.getFactor(1), false);
        BooleanEncodedValue turnRestrictionEnc = turnCosts ? TurnRestriction.create(name) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, turnRestrictionEnc);
    }

    public static VehicleEncodedValues wheelchair(PMap properties) {
        if (properties.has("speed_two_directions"))
            throw new IllegalArgumentException("wheelchair always uses two directions");
        return foot(new PMap(properties)
                .putObject("name", properties.getString("name", "wheelchair"))
                .putObject("speed_two_directions", true)
        );
    }

    public static VehicleEncodedValues bike(PMap properties) {
        String name = properties.getString("name", "bike");
        int speedBits = properties.getInt("speed_bits", 4);
        double speedFactor = properties.getDouble("speed_factor", 2);
        boolean speedTwoDirections = properties.getBool("speed_two_directions", false);
        boolean turnCosts = properties.getBool("turn_costs", false);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = VehiclePriority.create(name, 4, PriorityCode.getFactor(1), false);
        BooleanEncodedValue turnRestrictionEnc = turnCosts ? TurnRestriction.create(name) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, turnRestrictionEnc);
    }

    public static VehicleEncodedValues racingbike(PMap properties) {
        return bike(new PMap(properties).putObject("name", properties.getString("name", "racingbike")));
    }

    public static VehicleEncodedValues mountainbike(PMap properties) {
        return bike(new PMap(properties).putObject("name", properties.getString("name", "mtb")));
    }

    public static VehicleEncodedValues car(PMap properties) {
        String name = properties.getString("name", "car");
        int speedBits = properties.getInt("speed_bits", 7);
        double speedFactor = properties.getDouble("speed_factor", 2);
        boolean turnCosts = properties.getBool("turn_costs", false);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, true);
        BooleanEncodedValue turnRestrictionEnc = turnCosts ? TurnRestriction.create(name) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, null, turnRestrictionEnc);
    }

    public static VehicleEncodedValues roads(PMap properties) {
        String name = properties.getString("name", "roads");
        int speedBits = properties.getInt("speed_bits", 7);
        double speedFactor = properties.getDouble("speed_factor", 2);
        boolean speedTwoDirections = properties.getBool("speed_two_directions", true);
        boolean turnCosts = properties.getBool("turn_costs", false);
        BooleanEncodedValue accessEnc = VehicleAccess.create(name);
        DecimalEncodedValue speedEnc = VehicleSpeed.create(name, speedBits, speedFactor, speedTwoDirections);
        BooleanEncodedValue turnRestrictionEnc = turnCosts ? TurnRestriction.create(name) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, null, turnRestrictionEnc);
    }

    public VehicleEncodedValues(String name, BooleanEncodedValue accessEnc, DecimalEncodedValue avgSpeedEnc,
                                DecimalEncodedValue priorityEnc, BooleanEncodedValue turnRestrictionEnc) {
        this.name = name;
        this.accessEnc = accessEnc;
        this.avgSpeedEnc = avgSpeedEnc;
        this.priorityEnc = priorityEnc;
        this.turnRestrictionEnc = turnRestrictionEnc;
    }

    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue) {
        if (accessEnc != null)
            registerNewEncodedValue.add(accessEnc);
        if (avgSpeedEnc != null)
            registerNewEncodedValue.add(avgSpeedEnc);
        if (priorityEnc != null)
            registerNewEncodedValue.add(priorityEnc);
    }

    public void createTurnCostEncodedValues(List<EncodedValue> registerNewTurnCostEncodedValues) {
        if (turnRestrictionEnc != null)
            registerNewTurnCostEncodedValues.add(turnRestrictionEnc);
    }

    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    public DecimalEncodedValue getAverageSpeedEnc() {
        return avgSpeedEnc;
    }

    public DecimalEncodedValue getPriorityEnc() {
        return priorityEnc;
    }

    public BooleanEncodedValue getTurnRestrictionEnc() {
        return turnRestrictionEnc;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
