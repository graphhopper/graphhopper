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
package com.graphhopper.routing.util.spatialrules;

import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Polygon;

import com.graphhopper.routing.profiles.RoadAccess;
import com.graphhopper.routing.profiles.RoadClass;

/**
 * @author Robin Boldt
 */
public abstract class AbstractSpatialRule implements SpatialRule {

    private final List<Polygon> borders;
    
    public AbstractSpatialRule(List<Polygon> borders) {
        this.borders = borders;
    }
    
    public AbstractSpatialRule(Polygon border) {
        this(Collections.singletonList(border));
    }

    public List<Polygon> getBorders() {
        return borders;
    }
    
    @Override
    public double getMaxSpeed(RoadClass roadClass, double currentMaxSpeed) {
        // We tried to estimate reasonable values: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed#Motorcar
        // We did not always used the highest value available, but we used a high value
        switch (roadClass) {
            case MOTORWAY:
                return 130;
            case TRUNK:
                return 130;
            case PRIMARY:
                return 100;
            case SECONDARY:
                return 100;
            case TERTIARY:
                return 100;
            case UNCLASSIFIED:
                return 100;
            case RESIDENTIAL:
                return 90;
            case LIVING_STREET:
                return 20;
            default:
                return currentMaxSpeed;
        }
    }

    @Override
    public RoadAccess getAccess(RoadClass roadClass, TransportationMode transportationMode, RoadAccess currentRoadAccess) {
        // As defined in: https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access-Restriction
        // We tried to find generally forbidden tags
        if (transportationMode != TransportationMode.MOTOR_VEHICLE) {
            return currentRoadAccess;
        }
        
        switch (roadClass) {
            case PATH:
            case BRIDLEWAY:
            case CYCLEWAY:
            case FOOTWAY:
            case PEDESTRIAN:
                return RoadAccess.NO;
            default:
                return currentRoadAccess;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SpatialRule) {
            if (((SpatialRule) obj).getId().equals(this.getId()))
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return getId();
    }
}
