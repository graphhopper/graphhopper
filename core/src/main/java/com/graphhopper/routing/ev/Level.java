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

    public static IntEncodedValue create() {
        // 8 bits stores 256 values. Burj Khalifa containes 163 stories.
        // -50 allows for plenty of space in negative values.
        // negateReverseDirection is false, levels keep their sign
        // storeTwoDirections contains the level of the starting point of the way.
        // Examples :
        // 1. a way going from level 0 to level 1, will contain 0 in the 
        // forward direction and 1 in the backward direction
        // 2. a way that stays on the same level 0, contains 0 in both directions.
        return new IntEncodedValueImpl(KEY, 8, -50, false, true);
    }
}
