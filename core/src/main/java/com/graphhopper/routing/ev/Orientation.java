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

public class Orientation {
    public static final String KEY = "orientation";

    // Sentinel value used to mark edges whose orientation cannot be derived from their geometry
    // (e.g. zero-length barrier edges). Real azimuths are always in [0, 360); 372° is the next
    // storable value above that range (5 bits × factor 12 → max storable = 372), so it can never
    // collide with a real azimuth.
    public static final double UNDEFINED = 372;

    // Due to pillar nodes we need 2 values: the orientation at the adjacent node and the reverse
    // value for orientation at the base node. Store in degrees.
    public static DecimalEncodedValue create() {
        return new DecimalEncodedValueImpl(KEY, 5, 0, 360 / 30.0, false, true, false);
    }
}
