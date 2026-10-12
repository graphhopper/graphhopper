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

public class Level {
    public static final String KEY = "level";

    public static DecimalEncodedValue create() {
        // factor: 0.1 in order to get half levels for stairs.
        // bits: 11 bits stores 2048 values, so 204.7 total levels.
        // minStorableValue: -400 * 0.1 = -40 (minLevel) and 204.7 - 40 = 164.7 (maxLevel)
        // For reference Burj Khalifa has 163 floors
        // negateReverseDirection is false, levels keep their sign
        // storeTwoDirections: false, only one level per way.
        return new DecimalEncodedValueImpl(KEY, 11, -40, 0.1, false, false, false);
    }
}
