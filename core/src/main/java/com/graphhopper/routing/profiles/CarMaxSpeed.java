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
package com.graphhopper.routing.profiles;

/**
 * This EncodedValue stores speed values and 0 stands for the default i.e. no maxspeed sign (does not imply no speed limit).
 */
public class CarMaxSpeed {
    public static final String KEY = "max_speed";

    /**
     * speed value used for "none" speed limit on German Autobahn
     */
    public static final double UNLIMITED_SIGN_SPEED = 140;

    /**
     * speed value used for road sections without known speed limit.
     */
    public static final double UNSET_SPEED = 0;

    public static DecimalEncodedValue create() {
        return new FactorizedDecimalEncodedValue(KEY, 5, 5, true);
    }
}
