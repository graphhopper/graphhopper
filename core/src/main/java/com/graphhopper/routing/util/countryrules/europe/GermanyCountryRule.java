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

package com.graphhopper.routing.util.countryrules.europe;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.Toll;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.routing.util.countryrules.CountryRule;

/**
 * @author Robin Boldt
 */
public class GermanyCountryRule implements CountryRule {

    @Override
    public RoadAccess getAccess(ReaderWay readerWay, TransportationMode transportationMode, RoadAccess currentRoadAccess) {
        if (currentRoadAccess != RoadAccess.YES)
            return currentRoadAccess;
        if (!transportationMode.isMotorVehicle())
            return RoadAccess.YES;
        RoadClass roadClass = RoadClass.find(readerWay.getTag("highway", ""));
        switch (roadClass) {
            case TRACK:
                return RoadAccess.DESTINATION;
            case PATH:
            case BRIDLEWAY:
            case CYCLEWAY:
            case FOOTWAY:
            case PEDESTRIAN:
                return RoadAccess.NO;
            default:
                return RoadAccess.YES;
        }
    }

    @Override
    public Toll getToll(ReaderWay readerWay, Toll currentToll) {
        if (currentToll != Toll.MISSING) {
            return currentToll;
        }

        RoadClass roadClass = RoadClass.find(readerWay.getTag("highway", ""));
        if (roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.TRUNK || roadClass == RoadClass.PRIMARY) {
            return Toll.HGV;
        }

        return currentToll;
    }
}
