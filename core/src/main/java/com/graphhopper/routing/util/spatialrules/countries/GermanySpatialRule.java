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

import com.graphhopper.routing.profiles.Country;
import com.graphhopper.routing.profiles.RoadAccess;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.util.spatialrules.AbstractSpatialRule;
import com.graphhopper.routing.util.spatialrules.TransportationMode;

/**
 * Defines the default rules for German roads
 *
 * @author Robin Boldt
 */
public class GermanySpatialRule extends AbstractSpatialRule {
    
    public GermanySpatialRule(List<Polygon> borders) {
        super(borders);
    }

    /**
     * Germany contains roads with no speed limit. For these roads, this method will return Integer.MAX_VALUE.
     * Your implementation should be able to handle these cases.
     */
    @Override
    public double getDefaultMaxSpeed(RoadClass roadClass) {
        // As defined in: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar
        switch (roadClass) {
            case MOTORWAY:
            case TRUNK:
                return Integer.MAX_VALUE;
            case PRIMARY:
                return 100;
            case SECONDARY:
                return 100;
            case TERTIARY:
                return 100;
            case UNCLASSIFIED:
                return 100;
            case RESIDENTIAL:
                return 100;
            case LIVING_STREET:
                return 4;
            default:
                return -1;
        }
    }
    
    @Override
    public RoadAccess getDefaultAccess(RoadClass roadClass, TransportationMode transportationMode) {
        if (transportationMode != TransportationMode.MOTOR_VEHICLE) {
            return RoadAccess.YES;
        }

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
    public String getId() {
        return Country.DEU.toString();
    }
}
