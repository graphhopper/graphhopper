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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.routing.util.BikeCommonTagParser.BIKE_MAX_SPEED;
import static com.graphhopper.routing.util.CarTagParser.CAR_MAX_SPEED;
import static com.graphhopper.routing.util.EncodingManager.getKey;
import static com.graphhopper.routing.util.FootTagParser.FERRY_SPEED;
import static com.graphhopper.routing.util.MotorcycleTagParser.MOTOR_CYCLE_MAX_SPEED;
import static com.graphhopper.routing.util.RoadsTagParser.ROADS_MAX_SPEED;

public class VehicleEncodedValues implements FlagEncoder {
    private final String name;
    private final boolean isMotorVehicle;
    private final double maxPossibleSpeed;
    private final BooleanEncodedValue accessEnc;
    private final DecimalEncodedValue avgSpeedEnc;
    private final DecimalEncodedValue priorityEnc;
    private final DecimalEncodedValue curvatureEnc;
    private final DecimalEncodedValue turnCostEnc;
    private EncodedValueLookup encodedValueLookup;

    public static VehicleEncodedValues foot(PMap properties) {
        String name = properties.getString("name", "foot");
        int speedBits = properties.getInt("speed_bits", 4);
        double speedFactor = properties.getDouble("speed_factor", 1);
        boolean speedTwoDirections = properties.getBool("speed_two_directions", false);
        int maxTurnCosts = properties.getInt("max_turn_costs", properties.getBool("turn_costs", false) ? 1 : 0);
        double maxSpeed = properties.getDouble("max_speed", FERRY_SPEED);
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue(getKey(name, "access"), true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl(getKey(name, "average_speed"), speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = new DecimalEncodedValueImpl(getKey(name, "priority"), 4, PriorityCode.getFactor(1), false);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, null, turnCostEnc, speedEnc.getNextStorableValue(maxSpeed), false);
    }

    public static VehicleEncodedValues hike(PMap properties) {
        return foot(new PMap(properties).putObject("name", properties.getString("name", "hike")));
    }

    public static VehicleEncodedValues wheelchair(PMap properties) {
        if (properties.has("speed_two_directions"))
            throw new IllegalArgumentException("bike2 always uses two directions");
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
        int maxTurnCosts = properties.getInt("max_turn_costs", properties.getBool("turn_costs", false) ? 1 : 0);
        double maxSpeed = properties.getDouble("max_speed", BIKE_MAX_SPEED);
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue(getKey(name, "access"), true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl(getKey(name, "average_speed"), speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = new DecimalEncodedValueImpl(getKey(name, "priority"), 4, PriorityCode.getFactor(1), false);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, null, turnCostEnc, speedEnc.getNextStorableValue(maxSpeed), false);
    }

    public static VehicleEncodedValues bike2(PMap properties) {
        if (properties.has("speed_two_directions"))
            throw new IllegalArgumentException("bike2 always uses two directions");
        return bike(new PMap(properties)
                .putObject("name", properties.getString("name", "bike2"))
                .putObject("speed_two_directions", true)
        );
    }

    public static VehicleEncodedValues racingbike(PMap properties) {
        return bike(new PMap(properties).putObject("name", properties.getString("name", "racingbike")));
    }

    public static VehicleEncodedValues mountainbike(PMap properties) {
        return bike(new PMap(properties).putObject("name", properties.getString("name", "mtb")));
    }

    public static VehicleEncodedValues car(PMap properties) {
        String name = properties.getString("name", "car");
        int speedBits = properties.getInt("speed_bits", 5);
        double speedFactor = properties.getDouble("speed_factor", 5);
        boolean speedTwoDirections = properties.getBool("speed_two_directions", false);
        int maxTurnCosts = properties.getInt("max_turn_costs", properties.getBool("turn_costs", false) ? 1 : 0);
        double maxSpeed = properties.getDouble("max_speed", CAR_MAX_SPEED);
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue(getKey(name, "access"), true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl(getKey(name, "average_speed"), speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, null, null, turnCostEnc, speedEnc.getNextStorableValue(maxSpeed), true);
    }

    public static VehicleEncodedValues car4wd(PMap properties) {
        return car(new PMap(properties).putObject("name", properties.getString("name", "car4wd")));
    }

    public static VehicleEncodedValues motorcycle(PMap properties) {
        String name = properties.getString("name", "motorcycle");
        int speedBits = properties.getInt("speed_bits", 5);
        double speedFactor = properties.getDouble("speed_factor", 5);
        boolean speedTwoDirections = properties.getBool("speed_two_directions", true);
        int maxTurnCosts = properties.getInt("max_turn_costs", properties.getBool("turn_costs", false) ? 1 : 0);
        double maxSpeed = properties.getDouble("max_speed", MOTOR_CYCLE_MAX_SPEED);
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue(getKey(name, "access"), true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl(getKey(name, "average_speed"), speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue priorityEnc = new DecimalEncodedValueImpl(getKey(name, "priority"), 4, PriorityCode.getFactor(1), false);
        DecimalEncodedValue curvatureEnc = new DecimalEncodedValueImpl(getKey(name, "curvature"), 4, 0.1, false);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, priorityEnc, curvatureEnc, turnCostEnc, speedEnc.getNextStorableValue(maxSpeed), true);
    }

    public static VehicleEncodedValues roads() {
        String name = "roads";
        int speedBits = 7;
        double speedFactor = 2;
        boolean speedTwoDirections = true;
        int maxTurnCosts = 3;
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue(getKey(name, "access"), true);
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl(getKey(name, "average_speed"), speedBits, speedFactor, speedTwoDirections);
        DecimalEncodedValue turnCostEnc = maxTurnCosts > 0 ? TurnCost.create(name, maxTurnCosts) : null;
        return new VehicleEncodedValues(name, accessEnc, speedEnc, null, null, turnCostEnc, speedEnc.getNextStorableValue(ROADS_MAX_SPEED), true);
    }

    public VehicleEncodedValues(String name, BooleanEncodedValue accessEnc, DecimalEncodedValue avgSpeedEnc,
                                DecimalEncodedValue priorityEnc, DecimalEncodedValue curvatureEnc,
                                DecimalEncodedValue turnCostEnc, double maxPossibleSpeed, boolean isMotorVehicle) {
        this.name = name;
        this.accessEnc = accessEnc;
        this.avgSpeedEnc = avgSpeedEnc;
        this.priorityEnc = priorityEnc;
        this.curvatureEnc = curvatureEnc;
        this.turnCostEnc = turnCostEnc;
        this.maxPossibleSpeed = maxPossibleSpeed;
        this.isMotorVehicle = isMotorVehicle;
    }

    public void setEncodedValueLookup(EncodedValueLookup encodedValueLookup) {
        this.encodedValueLookup = encodedValueLookup;
    }

    @Override
    public boolean isRegistered() {
        return encodedValueLookup != null;
    }

    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue) {
        if (accessEnc != null)
            registerNewEncodedValue.add(accessEnc);
        if (avgSpeedEnc != null)
            registerNewEncodedValue.add(avgSpeedEnc);
        if (priorityEnc != null)
            registerNewEncodedValue.add(priorityEnc);
        if (curvatureEnc != null)
            registerNewEncodedValue.add(curvatureEnc);
    }

    public void createTurnCostEncodedValues(List<EncodedValue> registerNewTurnCostEncodedValues) {
        if (turnCostEnc != null)
            registerNewTurnCostEncodedValues.add(turnCostEnc);
    }

    @Override
    public double getMaxSpeed() {
        return maxPossibleSpeed;
    }

    @Override
    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    @Override
    public DecimalEncodedValue getAverageSpeedEnc() {
        return avgSpeedEnc;
    }

    @Override
    public DecimalEncodedValue getPriorityEnc() {
        return priorityEnc;
    }

    @Override
    public DecimalEncodedValue getCurvatureEnc() {
        return curvatureEnc;
    }

    @Override
    public DecimalEncodedValue getTurnCostEnc() {
        return turnCostEnc;
    }

    public String getSharedEncodedValueString() {
        return Stream.of(accessEnc, avgSpeedEnc, priorityEnc, curvatureEnc, turnCostEnc)
                .filter(Objects::nonNull)
                .map(EncodedValueSerializer::serializeEncodedValue)
                .collect(Collectors.joining(","));
    }

    @Override
    public List<EncodedValue> getEncodedValues() {
        return encodedValueLookup.getEncodedValues();
    }

    @Override
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
        return encodedValueLookup.getEncodedValue(key, encodedValueType);
    }

    @Override
    public BooleanEncodedValue getBooleanEncodedValue(String key) {
        return encodedValueLookup.getBooleanEncodedValue(key);
    }

    @Override
    public IntEncodedValue getIntEncodedValue(String key) {
        return encodedValueLookup.getIntEncodedValue(key);
    }

    @Override
    public DecimalEncodedValue getDecimalEncodedValue(String key) {
        return encodedValueLookup.getDecimalEncodedValue(key);
    }

    @Override
    public <T extends Enum<?>> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> enumType) {
        return encodedValueLookup.getEnumEncodedValue(key, enumType);
    }

    @Override
    public StringEncodedValue getStringEncodedValue(String key) {
        return encodedValueLookup.getStringEncodedValue(key);
    }

    @Override
    public boolean isMotorVehicle() {
        return isMotorVehicle;
    }

    @Override
    public boolean supportsTurnCosts() {
        return turnCostEnc != null;
    }

    @Override
    public boolean hasEncodedValue(String key) {
        return encodedValueLookup.hasEncodedValue(key);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}