/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

/**
 * @author Peter Karich
 */
public class CombinedEncoder {

    CarFlagEncoder carEncoder = new CarFlagEncoder();
    BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
    FootFlagEncoder footEncoder = new FootFlagEncoder();

    public int swapDirection(int flags) {
        flags = footEncoder.swapDirection(flags);
        flags = bikeEncoder.swapDirection(flags);
        return carEncoder.swapDirection(flags);
    }

    public int flagsDefault(boolean bothDirections) {
        int res = footEncoder.flagsDefault(bothDirections);
        res |= bikeEncoder.flagsDefault(bothDirections);
        return res | carEncoder.flagsDefault(bothDirections);
    }
}
