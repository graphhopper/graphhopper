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

    /**
     * In Germany there are roads without a speed limit. For these roads, this method
     * will return {@link MaxSpeed#UNLIMITED_SIGN_SPEED}.
     * <p>
     * Your implementation should be able to handle these cases.
     */
    @Override
    public double getMaxSpeed(ReaderWay readerWay, TransportationMode transportationMode, double currentMaxSpeed) {
        if (!Double.isNaN(currentMaxSpeed) || !transportationMode.isMotorVehicle())
            return currentMaxSpeed;

        RoadClass roadClass = RoadClass.find(readerWay.getTag("highway", ""));
        // As defined in: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar
        switch (roadClass) {
            case MOTORWAY:
            case TRUNK:
                return MaxSpeed.UNLIMITED_SIGN_SPEED;
            case PRIMARY:
            case SECONDARY:
            case TERTIARY:
            case UNCLASSIFIED:
            case RESIDENTIAL:
                return 100;
            case LIVING_STREET:
                return 4;
            default:
                return Double.NaN;
        }
    }

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
