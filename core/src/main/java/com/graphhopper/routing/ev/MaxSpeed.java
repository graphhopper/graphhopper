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
package com.graphhopper.routing.ev;

/**
 * This EncodedValue stores maximum speed values for car in km/h. If not initialized it returns UNSET_SPEED.
 */
public class MaxSpeed {
    public static final String KEY = "max_speed";

    /**
     * The speed value used for "none" speed limit on German Autobahn is 150=30*5 as this is the biggest value
     * not explicitly used in OSM and can be precisely returned for a factor of 5, 3, 2 and 1. It is fixed and
     * not DecimalEncodedValue.getMaxInt to allow special case handling.
     */
    public static final double UNLIMITED_SIGN_SPEED = 150;
    /**
     * The speed value used for road sections without known speed limit.
     */
    public static final double UNSET_SPEED = Double.POSITIVE_INFINITY;
    public static final double MAXSPEED_NONE = -1;

    public static DecimalEncodedValue create() {
        // if we would store only km/h we could live with a factor of 5 and only 5 bits
        // but there is also mph and we don't want to store the unit
        return new DecimalEncodedValueImpl(KEY, 7, 0, 2, false, true, true);
    }
}
