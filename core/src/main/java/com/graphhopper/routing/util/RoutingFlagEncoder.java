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

import java.util.List;

public class RoutingFlagEncoder implements FlagEncoder {
    private final EVCollection evCollection;
    private final String prefix;
    private final TransportationMode transportationMode;
    private final double maxSpeed;

    public static RoutingFlagEncoder forTest(String prefix, EVCollection evCollection) {
        // road_access is required by FastestWeighting, so we add it here
        if (!evCollection.hasEncodedValue(RoadAccess.KEY)) {
            EnumEncodedValue<RoadAccess> roadAccessEnc = new EnumEncodedValue<>(RoadAccess.KEY, RoadAccess.class);
            evCollection.addEncodedValue(roadAccessEnc, false);
        }
        DecimalEncodedValue speedEnc = new DecimalEncodedValueImpl(EncodingManager.getKey(prefix, "average_speed"), 5, 5, true);
        BooleanEncodedValue accessEnc = new SimpleBooleanEncodedValue(EncodingManager.getKey(prefix, "access"), true);
        evCollection.addEncodedValue(speedEnc, true);
        evCollection.addEncodedValue(accessEnc, true);
        return new RoutingFlagEncoder(evCollection, prefix, TransportationMode.CAR, 140);
    }

    public RoutingFlagEncoder(EVCollection evCollection, String prefix, TransportationMode transportationMode, double maxSpeed) {
        this.evCollection = evCollection;
        this.prefix = prefix;
        this.transportationMode = transportationMode;
        this.maxSpeed = maxSpeed;
    }

    public EVCollection getEvCollection() {
        return evCollection;
    }

    @Override
    public List<EncodedValue> getEncodedValues() {
        return evCollection.getEncodedValues();
    }

    @Override
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType) {
        return evCollection.getEncodedValue(key, encodedValueType);
    }

    @Override
    public BooleanEncodedValue getBooleanEncodedValue(String key) {
        return evCollection.getBooleanEncodedValue(key);
    }

    @Override
    public IntEncodedValue getIntEncodedValue(String key) {
        return evCollection.getIntEncodedValue(key);
    }

    @Override
    public DecimalEncodedValue getDecimalEncodedValue(String key) {
        return evCollection.getDecimalEncodedValue(key);
    }

    @Override
    public <T extends Enum<?>> EnumEncodedValue<T> getEnumEncodedValue(String key, Class<T> enumType) {
        return evCollection.getEnumEncodedValue(key, enumType);
    }

    @Override
    public StringEncodedValue getStringEncodedValue(String key) {
        return evCollection.getStringEncodedValue(key);
    }

    @Override
    public boolean hasEncodedValue(String key) {
        return evCollection.hasEncodedValue(key);
    }

    @Override
    public TransportationMode getTransportationMode() {
        // todonow: in the routing part this is only used in FastestWeighting to decide which destination and private
        //          shall be applied. but this should merely be settings in FastestWeighting and this method should be
        //          removed from here.
        return transportationMode;
    }

    @Override
    public double getMaxSpeed() {
        // todonow: currently we do not make sure anywhere that our prefix$average_speed EV never exceeds the speed
        //          not sure if we should yet
        return maxSpeed;
    }

    @Override
    public BooleanEncodedValue getAccessEnc() {
        return evCollection.getBooleanEncodedValue(EncodingManager.getKey(prefix, "access"));
    }

    @Override
    public DecimalEncodedValue getAverageSpeedEnc() {
        return evCollection.getDecimalEncodedValue(EncodingManager.getKey(prefix, "average_speed"));
    }

    @Override
    public boolean supportsTurnCosts() {
        return evCollection.hasEncodedValue(TurnCost.key(prefix));
    }

    @Override
    public boolean isRegistered() {
        // todonow: this method should not be here
        return true;
    }

    @Override
    public String toString() {
        return prefix;
    }
}
