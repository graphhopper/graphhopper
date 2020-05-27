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
package com.graphhopper.routing.util.spatialrules.countries;

import java.util.List;

import org.locationtech.jts.geom.Polygon;

import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.spatialrules.AbstractSpatialRule;
import com.graphhopper.routing.util.spatialrules.TransportationMode;

/**
 * Defines the default rules for Austria roads
 *
 * @author Robin Boldt
 */
public class AustriaSpatialRule extends AbstractSpatialRule {

    public AustriaSpatialRule(List<Polygon> borders) {
        super(borders);
    }
    
    @Override
    public double getMaxSpeed(RoadClass roadClass, TransportationMode transport, double currentMaxSpeed) {
        if (currentMaxSpeed > 0 || transport != TransportationMode.MOTOR_VEHICLE) {
            return currentMaxSpeed;
        }
        
        switch (roadClass) {
        case MOTORWAY:
            return 130;
        case TRUNK:
            return 100;
        case PRIMARY:
            return 100;
        case SECONDARY:
            return 100;
        case TERTIARY:
            return 100;
        case UNCLASSIFIED:
            return 100;
        case RESIDENTIAL:
            return 50;
        case LIVING_STREET:
            return 20;
        default:
            return -1;
        }
    }
    
    @Override
    public RoadAccess getAccess(RoadClass roadClass, TransportationMode transport, RoadAccess currentRoadAccess) {
        if (currentRoadAccess != RoadAccess.YES) {
            return currentRoadAccess;
        }
        
        if (transport != TransportationMode.MOTOR_VEHICLE) {
            return RoadAccess.YES;
        }
        
        switch (roadClass) {
        case LIVING_STREET:
            return RoadAccess.DESTINATION;
        case TRACK:
            return RoadAccess.FORESTRY;
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
    public String getId() {
        return Country.AUT.toString();
    }
}
