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

import com.graphhopper.util.PMap;

/**
 * @author Peter Karich
 */
public interface FlagEncoderFactory {
    final String CAR = "car";
    final String CAR4WD = "car4wd";
    final String BIKE = "bike";
    final String BIKE2 = "bike2";
    final String RACINGBIKE = "racingbike";
    final String MOUNTAINBIKE = "mtb";
    final String FOOT = "foot";
    final String HIKE = "hike";
    final String MOTORCYCLE = "motorcycle";
    final String GENERIC = "generic";
    final FlagEncoderFactory DEFAULT = new DefaultFlagEncoderFactory();

    FlagEncoder createFlagEncoder(String name, PMap configuration);
}
