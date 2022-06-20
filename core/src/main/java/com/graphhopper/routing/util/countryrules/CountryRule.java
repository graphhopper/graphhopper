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

package com.graphhopper.routing.util.countryrules;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.routing.util.TransportationMode;

/**
 * GraphHopper uses country rules to adjust the routing behavior based on the country an edge is located in
 */
public interface CountryRule {
    default double getMaxSpeed(ReaderWay readerWay, TransportationMode transportationMode, double currentMaxSpeed) {
        return currentMaxSpeed;
    }

    default RoadAccess getAccess(ReaderWay readerWay, TransportationMode transportationMode, RoadAccess currentRoadAccess) {
        return currentRoadAccess;
    }
    
    default Toll getToll(ReaderWay readerWay, Toll currentToll) {
        return currentToll;
    }
}
